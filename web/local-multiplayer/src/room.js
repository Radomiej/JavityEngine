import { Room } from '@colyseus/core';
import { RoomState, PlayerState } from './state.js';
import { JavaRoomWorker } from './java-worker.js';

export const ROOM_NAME = 'javity-local';

function validInput(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const keys = Object.keys(value).sort();
  return keys.join(',') === 'sequence,x,z'
    && Number.isSafeInteger(value.sequence) && value.sequence >= 0
    && Number.isFinite(value.x) && Math.abs(value.x) <= 1
    && Number.isFinite(value.z) && Math.abs(value.z) <= 1;
}

export class LocalGameRoom extends Room {
  #workerFactory;
  #worker;
  #sessions = new Map();
  #failed = false;

  async onCreate(options) {
    this.autoDispose = true;
    this.maxClients = 8;
    this.setState(new RoomState());
    this.#workerFactory = options.workerFactory;
    this.#worker = await this.#workerFactory(this.roomId);
    this.#worker.on('snapshot', (snapshot) => this.#applySnapshot(snapshot));
    this.#worker.on('failure', () => {
      this.#failed = true;
      void this.disconnect().catch(() => {});
    });
    this.onMessage('input', (client, message) => this.#input(client, message));
  }

  async onJoin(client) {
    if (this.#failed) throw new Error('Java authority is unavailable');
    const sessionId = client.sessionId;
    await this.#worker.send('join', { sessionId });
    this.#sessions.set(client.sessionId, sessionId);
  }

  async onLeave(client) {
    const sessionId = this.#sessions.get(client.sessionId);
    if (!sessionId) return;
    this.#sessions.delete(client.sessionId);
    this.state.players.delete(sessionId);
    if (!this.#failed && !this.#worker.closed) {
      try { await this.#worker.send('leave', { sessionId }); }
      catch (error) { this.#worker.emit('failure', error); }
    }
  }

  #input(client, message) {
    const sessionId = this.#sessions.get(client.sessionId);
    if (!sessionId || !validInput(message)) return;
    void this.#worker.sendInput(sessionId, message.sequence, message.x, message.z)
      .catch((error) => this.#worker.emit('failure', error));
  }

  #applySnapshot(snapshot) {
    if (snapshot.roomId !== this.roomId || this.#failed) return;
    this.state.tick = snapshot.tick;
    const seen = new Set();
    for (const source of snapshot.players) {
      if (!source || typeof source.sessionId !== 'string' || !Number.isFinite(source.x) || !Number.isFinite(source.z)
        || !Number.isSafeInteger(source.lastSequence)) {
        this.#worker.emit('failure', new Error('Malformed player snapshot'));
        return;
      }
      if (!this.#sessions.has(source.sessionId)) continue;
      seen.add(source.sessionId);
      let player = this.state.players.get(source.sessionId);
      if (!player) {
        player = new PlayerState();
        this.state.players.set(source.sessionId, player);
      }
      player.x = source.x;
      player.z = source.z;
      player.lastSequence = source.lastSequence;
    }
    for (const sessionId of this.state.players.keys()) {
      if (!seen.has(sessionId) && this.#sessions.has(sessionId)) {
        // A newly joined player may precede the worker's first periodic snapshot.
        const player = this.state.players.get(sessionId);
        if (player.lastSequence !== -1) this.state.players.delete(sessionId);
      }
    }
  }

  async onDispose() {
    await this.#worker?.stop();
    this.#sessions.clear();
  }
}

export function createRoomClass(workerFactory) {
  return class ConfiguredLocalGameRoom extends LocalGameRoom {
    onCreate(options = {}) {
      options.workerFactory = workerFactory;
      return super.onCreate(options);
    }
  };
}
