import assert from 'node:assert/strict';
import { test } from 'node:test';
import { spawn } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { setTimeout as delay } from 'node:timers/promises';
import { Client } from '@colyseus/sdk';
import { createGateway } from '../src/gateway.js';

const root = new URL('../../../', import.meta.url).pathname;
async function until(fn, label, timeout = 10000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) { const result = await fn(); if (result) return result; await delay(30); }
  throw new Error(`Timed out: ${label}`);
}
const alive = (pid) => {
  try {
    process.kill(pid, 0);
    // An orphan's zombie entry belongs to the host init, not a running service.
    try { if (/State:\s+Z/.test(readFileSync(`/proc/${pid}/status`, 'utf8'))) return false; } catch {}
    return true;
  } catch { return false; }
};

test('development server view observes actual Java snapshots without creating a player', async (t) => {
  const gateway = await createGateway({ port: 0, devtools: true });
  t.after(() => gateway.stop());
  const client = new Client(gateway.url);
  const room = await client.create('javity-local');
  room.reconnection.enabled = false;
  const snapshot = await until(async () => {
    const response = await fetch(`${gateway.url}/dev/rooms/${room.roomId}`);
    assert.equal(response.status, 200);
    const data = await response.json();
    return data.snapshot?.players.length === 1 && data;
  }, 'authoritative inspector baseline');
  assert.equal(snapshot.snapshot.players[0].sessionId, room.sessionId);
  assert.ok(snapshot.workerPid > 0);
  assert.ok(snapshot.snapshot.tick > 0);
  const response = await fetch(`${gateway.url}/dev/rooms`);
  assert.deepEqual((await response.json()).rooms, [room.roomId]);
  await delay(100);
  assert.equal(room.state.players.size, 1, 'inspection cannot join or add a player');
  const workerPid = snapshot.workerPid;
  await room.leave();
  await until(async () => (await fetch(`${gateway.url}/dev/rooms/${room.roomId}`)).status === 404, 'disposed room disappears');
  await until(() => !alive(workerPid), 'inspected worker reaped');
});

test('development endpoints are opt-in and unavailable for public binding', async (t) => {
  const gateway = await createGateway({ port: 0 });
  t.after(() => gateway.stop());
  assert.equal((await fetch(`${gateway.url}/dev/rooms`)).status, 404);
  await assert.rejects(createGateway({ host: '0.0.0.0', allowRemote: true, devtools: true }), /development|devtools/i);
});

for (const termination of ['SIGTERM', 'SIGKILL']) test(`Java supervises Colyseus and stops workers after parent ${termination}`, { timeout: 25000 }, async (t) => {
  const java = process.env.JAVA_HOME ? `${process.env.JAVA_HOME}/bin/java` : 'java';
  const child = spawn(java, ['-cp', `${root}/javity-rooms/build/install/javity-rooms/lib/*`,
    'org.javity.rooms.LocalDevServerMain', `--workspace=${root}`, '--port=0'], { cwd: root, stdio: ['pipe', 'pipe', 'pipe'] });
  let output = '', diagnostics = '', exited = false;
  child.stdout.on('data', (d) => { output += d; });
  child.stderr.on('data', (d) => { diagnostics += d; });
  const exit = new Promise((resolve) => child.once('exit', (code, signal) => { exited = true; resolve({ code, signal }); }));
  t.after(async () => { if (!exited) child.kill('SIGTERM'); await Promise.race([exit, delay(8000)]); if (!exited) child.kill('SIGKILL'); });
  const ready = await until(() => {
    if (exited) throw new Error(`Java dev launcher exited: ${diagnostics}`);
    const line = output.split('\n').find((line) => line.startsWith('JAVITY_DEV_READY '));
    return line && JSON.parse(line.slice('JAVITY_DEV_READY '.length));
  }, 'supervised gateway ready');
  const client = new Client(ready.url);
  const room = await client.create('javity-local');
  room.reconnection.enabled = false;
  const data = await until(async () => {
    const data = await fetch(`${ready.url}/dev/rooms/${room.roomId}`).then((r) => r.json());
    return data.snapshot?.players.length === 1 && data;
  }, 'supervised Java authority');
  assert.ok(alive(ready.pid));
  assert.ok(alive(data.workerPid));
  child.kill(termination);
  await Promise.race([exit, delay(10000).then(() => { throw new Error('launcher termination hung'); })]);
  await until(() => !alive(ready.pid) && !alive(data.workerPid), 'owned Node and Java processes stopped');
  assert.doesNotMatch(diagnostics, /EPIPE|Unhandled 'error'/, 'parent loss must not crash Node while cleaning up');
});

test('supervised CLI finishes authority cleanup when its stdout reader disappears', { timeout: 12000 }, async (t) => {
  const child = spawn(process.execPath, ['web/local-multiplayer/src/cli.js'], {
    cwd: root, env: { ...process.env, PORT: '0', JAVITY_DEV_SUPERVISED: '1' }, stdio: ['pipe', 'pipe', 'pipe'],
  });
  let output = '', diagnostics = '', exited = false;
  child.stdout.on('data', (data) => { output += data; });
  child.stderr.on('data', (data) => { diagnostics += data; });
  const exit = new Promise((resolve) => child.once('exit', (code) => { exited = true; resolve(code); }));
  t.after(async () => { if (!exited) child.kill('SIGTERM'); await Promise.race([exit, delay(3000)]); if (!exited) child.kill('SIGKILL'); });
  const ready = await until(() => {
    const line = output.split('\n').find((line) => line.startsWith('JAVITY_DEV_READY '));
    return line && JSON.parse(line.slice(17));
  }, 'CLI readiness');
  const room = await new Client(ready.url).create('javity-local');
  room.reconnection.enabled = false;
  const data = await until(async () => {
    const data = await fetch(`${ready.url}/dev/rooms/${room.roomId}`).then((r) => r.json());
    return data.snapshot?.players.length === 1 && data;
  }, 'active authority');
  child.stdout.destroy();
  child.stdin.end();
  assert.equal(await Promise.race([exit, delay(8000).then(() => -999)]), 0, diagnostics);
  await until(() => !alive(data.workerPid), 'worker exits even without stdout reader');
  assert.doesNotMatch(diagnostics, /EPIPE|Unhandled 'error'/);
});
