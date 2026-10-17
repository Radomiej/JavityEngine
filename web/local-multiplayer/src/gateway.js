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

export async function createGateway({ host = '127.0.0.1', port = 2567, workerFactory = defaultWorkerFactory(), preview = true } = {}) {
  if (host !== '127.0.0.1' && host !== '::1') throw new Error('Local multiplayer gateway must bind to loopback');
  const app = express();
  app.disable('x-powered-by');
  app.get('/health', (_request, response) => response.json({ status: 'ok' }));
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
  colyseus.define(ROOM_NAME, createRoomClass(workerFactory));
  await colyseus.listen(port, host);
  const address = httpServer.address();
  const actualPort = typeof address === 'object' && address ? address.port : port;
  let stopped = false;
  return {
    host,
    port: actualPort,
    url: `http://${host.includes(':') ? `[${host}]` : host}:${actualPort}`,
    server: colyseus,
    httpServer,
    async stop() {
      if (stopped) return;
      stopped = true;
      await colyseus.gracefullyShutdown(false);
      if (httpServer.listening) await new Promise((resolve, reject) => httpServer.close((error) => error ? reject(error) : resolve()));
    },
  };
}
