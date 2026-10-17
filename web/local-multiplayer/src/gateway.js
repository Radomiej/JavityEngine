import express from 'express';
import { createServer } from 'node:http';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { Server } from '@colyseus/core';
import { WebSocketTransport } from '@colyseus/ws-transport';
import { JavaRoomWorker } from './java-worker.js';
import { createRoomClass, ROOM_NAME } from './room.js';

const HERE = dirname(fileURLToPath(import.meta.url));
const PACKAGE_ROOT = join(HERE, '..');
const REPO_ROOT = join(PACKAGE_ROOT, '..', '..');
const DEFAULT_WORKER = join(REPO_ROOT, 'javity-rooms', 'build', 'install', 'javity-rooms', 'bin', 'javity-rooms');

export function defaultWorkerFactory({ command = process.env.JAVITY_ROOMS_WORKER ?? DEFAULT_WORKER, args = [], cwd = REPO_ROOT, env = process.env } = {}) {
  return (roomId) => JavaRoomWorker.start({ command, args, cwd, env, roomId });
}

export async function createGateway({ host = '127.0.0.1', port = 2567, workerFactory = defaultWorkerFactory(), preview = true, devtools = false, allowRemote = false } = {}) {
  const loopback = host === '127.0.0.1' || host === '::1';
  if (devtools && !loopback) throw new Error('Development tools require loopback binding');
  if (!loopback && !(allowRemote && host === '0.0.0.0')) throw new Error('Local multiplayer gateway must bind to loopback; container binding requires explicit allowRemote');
  const rooms = new Map();
  const observedFactory = devtools ? async (roomId) => {
    const worker = await workerFactory(roomId);
    const entry = { workerPid: worker.pid, snapshot: null };
    rooms.set(roomId, entry);
    worker.on('snapshot', (snapshot) => { entry.snapshot = snapshot; });
    void worker.exitPromise.finally(() => { if (rooms.get(roomId) === entry) rooms.delete(roomId); });
    return worker;
  } : workerFactory;
  const app = express();
  app.disable('x-powered-by');
  app.get('/health', (_request, response) => response.json({ status: 'ok' }));
  if (devtools) {
    app.use('/dev', (_request, response, next) => { response.set('Cache-Control', 'no-store'); next(); });
    app.get('/dev/rooms', (_request, response) => response.json({ rooms: [...rooms.keys()] }));
    app.get('/dev/rooms/:roomId', (request, response) => {
      const room = rooms.get(request.params.roomId);
      if (!room) return response.status(404).json({ error: 'Room is not running' });
      response.json(room);
    });
  }
  if (preview) {
    app.use('/sdk', express.static(join(PACKAGE_ROOT, 'node_modules', '@colyseus', 'sdk', 'dist'), { fallthrough: false, index: false, maxAge: 0 }));
    app.use('/', express.static(join(PACKAGE_ROOT, 'public'), { index: 'index.html', fallthrough: true, maxAge: 0 }));
  }
  const httpServer = createServer(app);
  const colyseus = new Server({
    transport: new WebSocketTransport({ server: httpServer }),
    greet: false,
    gracefullyShutdown: false,
  });
  colyseus.define(ROOM_NAME, createRoomClass(observedFactory));
  await colyseus.listen(port, host);
  const address = httpServer.address();
  const actualPort = typeof address === 'object' && address ? address.port : port;
  let stopPromise;
  return {
    host,
    port: actualPort,
    url: `http://${host.includes(':') ? `[${host}]` : host}:${actualPort}`,
    server: colyseus,
    httpServer,
    stop() {
      if (!stopPromise) {
        stopPromise = (async () => {
          await colyseus.gracefullyShutdown(false);
          if (httpServer.listening) await new Promise((resolve, reject) => httpServer.close((error) => error ? reject(error) : resolve()));
        })();
      }
      return stopPromise;
    },
  };
}
