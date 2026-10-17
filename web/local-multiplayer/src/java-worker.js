import { spawn } from 'node:child_process';
import { EventEmitter } from 'node:events';
import { StringDecoder } from 'node:string_decoder';

const MAX_LINE_BYTES = 8192;
const MAX_PENDING = 64;
const READY_TIMEOUT_MS = 5000;
const COMMAND_TIMEOUT_MS = 2000;
const KILL_TIMEOUT_MS = 1000;

export class JavaWorkerError extends Error {
  constructor(message, code = 'WORKER_FAILURE') {
    super(message);
    this.name = 'JavaWorkerError';
    this.code = code;
  }
}

export class JavaRoomWorker extends EventEmitter {
  #child;
  #roomId;
  #decoder = new StringDecoder('utf8');
  #stdout = '';
  #lineBytes = 0;
  #pending = new Map();
  #writeQueue = [];
  #writing = false;
  #activeWrite;
  #readyResolve;
  #readyReject;
  #exitPromise;
  #closed = false;
  #stopping = false;
  #stopPromise;
  #failed;

  constructor(child, roomId) {
    super();
    this.#child = child;
    this.#roomId = roomId;
    this.#exitPromise = new Promise((resolve) => {
      let settled = false;
      const finish = (result) => {
        if (settled) return;
        settled = true;
        resolve(result);
      };
      child.once('exit', (code, signal) => finish({ code, signal }));
      child.once('error', (error) => {
        if (child.pid === undefined) finish({ code: null, signal: null, spawnError: error.message });
      });
      child.once('close', (code, signal) => finish({ code, signal }));
    });
    child.stdout.on('data', (chunk) => this.#onStdout(chunk));
    child.stdout.on('end', () => this.#onStdoutEnd());
    child.stderr.on('data', (chunk) => this.emit('diagnostic', chunk.toString('utf8')));
    child.stdin.on('error', (error) => this.#fail(new JavaWorkerError(`Java worker stdin failed: ${error.message}`, 'WORKER_WRITE')));
    child.once('error', (error) => this.#fail(new JavaWorkerError(`Could not start Java worker: ${error.message}`, 'WORKER_START')));
    child.once('exit', (code, signal) => {
      if (!this.#closed) this.#fail(new JavaWorkerError(`Java worker exited (${signal ?? code})`, 'WORKER_EXIT'));
    });
  }

  static async start({ command, args = [], cwd, env = process.env, roomId, startupTimeoutMs = READY_TIMEOUT_MS }) {
    if (!roomId || typeof roomId !== 'string') throw new TypeError('roomId is required');
    const child = spawn(command, [...args, roomId], { cwd, env, shell: false, stdio: ['pipe', 'pipe', 'pipe'] });
    const worker = new JavaRoomWorker(child, roomId);
    try {
      await worker.#waitReady(startupTimeoutMs);
      return worker;
    } catch (error) {
      await worker.stop().catch(() => {});
      throw error;
    }
  }

  get closed() { return this.#closed; }
  get pid() { return this.#child.pid; }
  get exitPromise() { return this.#exitPromise; }

  #waitReady(timeoutMs) {
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.#readyResolve = undefined;
        this.#readyReject = undefined;
        reject(new JavaWorkerError('Timed out waiting for Java worker ready frame', 'WORKER_START_TIMEOUT'));
      }, timeoutMs);
      timer.unref?.();
      this.#readyResolve = () => { clearTimeout(timer); resolve(); };
      this.#readyReject = (error) => { clearTimeout(timer); reject(error); };
    });
  }

  #onStdout(chunk) {
    if (this.#closed || this.#failed) return;
    this.#stdout += this.#decoder.write(chunk);
    let newline;
    while ((newline = this.#stdout.indexOf('\n')) !== -1) {
      const line = this.#stdout.slice(0, newline);
      this.#stdout = this.#stdout.slice(newline + 1);
      if (Buffer.byteLength(line, 'utf8') > MAX_LINE_BYTES) {
        this.#fail(new JavaWorkerError('Java worker exceeded the 8192-byte protocol line limit', 'WORKER_FRAME'));
        return;
      }
      this.#onFrame(line);
      if (this.#failed || this.#closed) return;
    }
    if (Buffer.byteLength(this.#stdout, 'utf8') > MAX_LINE_BYTES) {
      this.#fail(new JavaWorkerError('Java worker exceeded the 8192-byte protocol line limit', 'WORKER_FRAME'));
    }
  }

  #onStdoutEnd() {
    const tail = this.#decoder.end();
    if (tail) this.#stdout += tail;
    if (this.#stdout.length > 0 && !this.#closed) this.#fail(new JavaWorkerError('Java worker ended with an incomplete protocol frame', 'WORKER_FRAME'));
  }

  #onFrame(line) {
    let frame;
    try { frame = JSON.parse(line); } catch { this.#fail(new JavaWorkerError('Java worker emitted invalid JSON', 'WORKER_FRAME')); return; }
    if (!frame || frame.v !== 1 || frame.roomId !== this.#roomId || typeof frame.type !== 'string') {
      this.#fail(new JavaWorkerError('Java worker emitted a mismatched protocol frame', 'WORKER_FRAME'));
      return;
    }
    if (frame.type === 'ready') {
      if (Object.keys(frame).sort().join(',') !== 'roomId,type,v' || !this.#readyResolve) {
        this.#fail(new JavaWorkerError('Unexpected Java worker ready frame', 'WORKER_FRAME'));
        return;
      }
      const ready = this.#readyResolve;
      this.#readyResolve = undefined;
      this.#readyReject = undefined;
      ready();
      return;
    }
    if (frame.type === 'snapshot') {
      if (!Number.isSafeInteger(frame.tick) || !Array.isArray(frame.players)) {
        this.#fail(new JavaWorkerError('Malformed Java worker snapshot', 'WORKER_FRAME'));
        return;
      }
      this.emit('snapshot', frame);
      return;
    }
    if ((frame.type === 'ack' || frame.type === 'error') && typeof frame.requestId === 'string') {
      const pending = this.#pending.get(frame.requestId);
      if (!pending) return;
      clearTimeout(pending.timer);
      this.#pending.delete(frame.requestId);
      if (frame.type === 'ack') pending.resolve(frame);
      else pending.reject(new JavaWorkerError(`Java worker rejected ${pending.type}: ${frame.code} ${frame.message}`, frame.code));
      return;
    }
    this.#fail(new JavaWorkerError(`Unexpected Java worker frame type: ${frame.type}`, 'WORKER_FRAME'));
  }

  #fail(error) {
    if (this.#failed || this.#closed) return;
    this.#failed = error;
    this.#readyReject?.(error);
    this.#readyResolve = undefined;
    this.#readyReject = undefined;
    for (const pending of this.#pending.values()) {
      clearTimeout(pending.timer);
      pending.reject(error);
    }
    this.#pending.clear();
    for (const queued of this.#writeQueue.splice(0)) queued.reject(error);
    this.#activeWrite?.reject(error);
    this.emit('failure', error);
  }

  send(type, fields = {}, timeoutMs = COMMAND_TIMEOUT_MS) {
    if (this.#closed || this.#stopping || this.#failed) return Promise.reject(this.#failed ?? new JavaWorkerError('Java worker is closed'));
    if (this.#pending.size + this.#writeQueue.length + Number(this.#writing) >= MAX_PENDING) return Promise.reject(new JavaWorkerError('Java worker command queue is full', 'WORKER_OVERLOAD'));
    const requestId = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
    const frame = { v: 1, roomId: this.#roomId, type, requestId, ...fields };
    const line = `${JSON.stringify(frame)}\n`;
    const bytes = Buffer.byteLength(line, 'utf8');
    if (bytes > MAX_LINE_BYTES + 1) return Promise.reject(new JavaWorkerError('Command exceeds Java worker line limit', 'WORKER_FRAME'));
    return this.#sendFrame(frame, type, timeoutMs);
  }

  #sendFrame(frame, type, timeoutMs) {
    const requestId = frame.requestId;
    const line = `${JSON.stringify(frame)}\n`;
    const bytes = Buffer.byteLength(line, 'utf8');
    if (bytes > MAX_LINE_BYTES + 1) return Promise.reject(new JavaWorkerError('Command exceeds Java worker line limit', 'WORKER_FRAME'));
    const acknowledgement = new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.#pending.delete(requestId);
        reject(new JavaWorkerError(`Timed out waiting for Java worker ${type} acknowledgement`, 'WORKER_COMMAND_TIMEOUT'));
      }, timeoutMs);
      timer.unref?.();
      this.#pending.set(requestId, { resolve, reject, timer, type });
    });
    this.#enqueue(line).catch((error) => {
      const pending = this.#pending.get(requestId);
      if (pending) {
        clearTimeout(pending.timer);
        this.#pending.delete(requestId);
        pending.reject(error);
      }
    });
    return acknowledgement;
  }

  sendInput(sessionId, sequence, x, z) {
    if (this.#closed || this.#stopping || this.#failed) return Promise.reject(this.#failed ?? new JavaWorkerError('Java worker is closed'));
    if (this.#pending.size + this.#writeQueue.length + Number(this.#writing) >= MAX_PENDING) return Promise.reject(new JavaWorkerError('Java worker command queue is full', 'WORKER_OVERLOAD'));
    const requestId = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
    const line = `${JSON.stringify({ v: 1, roomId: this.#roomId, type: 'input', requestId, sessionId, sequence, x, z })}\n`;
    if (Buffer.byteLength(line, 'utf8') > MAX_LINE_BYTES + 1) return Promise.reject(new JavaWorkerError('Command exceeds Java worker line limit', 'WORKER_FRAME'));
    return this.#enqueue(line);
  }

  #enqueue(line) {
    if (this.#writeQueue.length + Number(this.#writing) >= MAX_PENDING) return Promise.reject(new JavaWorkerError('Java worker write queue is full', 'WORKER_OVERLOAD'));
    return new Promise((resolve, reject) => {
      this.#writeQueue.push({ line, resolve, reject });
      this.#pumpWrites();
    });
  }

  #pumpWrites() {
    if (this.#writing || this.#closed || this.#failed) return;
    const queued = this.#writeQueue.shift();
    if (!queued) return;
    this.#writing = true;
    this.#activeWrite = queued;
    this.#child.stdin.write(queued.line, (error) => {
      this.#writing = false;
      this.#activeWrite = undefined;
      if (error) {
        const failure = new JavaWorkerError(`Could not write Java worker command: ${error.message}`, 'WORKER_WRITE');
        queued.reject(failure);
        this.#fail(failure);
      } else queued.resolve();
      this.#pumpWrites();
    });
  }

  stop() {
    if (this.#stopPromise) return this.#stopPromise;
    this.#stopPromise = this.#stop();
    return this.#stopPromise;
  }

  async #stop() {
    if (this.#closed) return this.#exitPromise;
    this.#stopping = true;
    if (!this.#failed && this.#child.exitCode === null && !this.#child.killed) {
      // shutdown gets a bounded attempt; close remains authoritative if ack or child is stuck.
      try { await this.#sendFrame({ v: 1, roomId: this.#roomId, type: 'shutdown', requestId: `${Date.now().toString(36)}-shutdown` }, 'shutdown', 500); }
      catch { /* terminate below */ }
    }
    this.#closed = true;
    const stopped = new JavaWorkerError('Java worker stopped');
    this.#readyReject?.(stopped);
    this.#readyResolve = undefined;
    this.#readyReject = undefined;
    for (const pending of this.#pending.values()) {
      clearTimeout(pending.timer);
      pending.reject(stopped);
    }
    this.#pending.clear();
    for (const queued of this.#writeQueue.splice(0)) queued.reject(stopped);
    this.#activeWrite?.reject(stopped);
    this.#child.stdin.end();
    let exit = await Promise.race([
      this.#exitPromise,
      new Promise((resolve) => setTimeout(() => resolve(null), KILL_TIMEOUT_MS)),
    ]);
    if (!exit && this.#child.exitCode === null) {
      this.#child.kill('SIGTERM');
      exit = await Promise.race([
        this.#exitPromise,
        new Promise((resolve) => setTimeout(() => resolve(null), KILL_TIMEOUT_MS)),
      ]);
    }
    if (!exit && this.#child.exitCode === null) {
      this.#child.kill('SIGKILL');
      exit = await this.#exitPromise;
    }
    return exit;
  }
}
