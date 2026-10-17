import assert from 'node:assert/strict';
import { setTimeout as delay } from 'node:timers/promises';
import { test } from 'node:test';
import { Client } from '@colyseus/sdk';
import { createGateway, defaultWorkerFactory } from '../src/gateway.js';
import { JavaRoomWorker } from '../src/java-worker.js';
import { ROOM_NAME } from '../src/room.js';

const WORKER = new URL('../../../javity-rooms/build/install/javity-rooms/bin/javity-rooms', import.meta.url).pathname;
const REPO_ROOT = new URL('../../../', import.meta.url).pathname;

async function waitFor(predicate, label, timeoutMs = 5000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const value = predicate();
    if (value) return value;
    await delay(20);
  }
  throw new Error('Timed out waiting for ' + label);
}

function noReconnect(room) {
  room.reconnection.enabled = false;
  return room;
}

function startMovement(room, direction = { x: 1, z: 0 }) {
  let sequence = 0;
  const timer = setInterval(() => room.send('input', { sequence: sequence++, ...direction }), 60);
  return () => clearInterval(timer);
}

test('real Colyseus SDK clients are replicated from isolated Java room workers', { timeout: 45000 }, async (t) => {
  const failedGateway = await createGateway({
    port: 0,
    workerFactory: defaultWorkerFactory({ command: '/definitely/missing/javity-room-worker', cwd: REPO_ROOT }),
    preview: false,
  });
  t.after(() => failedGateway.stop());
  const startupClient = new Client(failedGateway.url);
  const startupStarted = Date.now();
  await assert.rejects(startupClient.create(ROOM_NAME), /seat reservation expired|Could not start Java worker|timed out/i);
  assert.ok(Date.now() - startupStarted < 3000, 'a missing worker launcher must fail without leaving an unbounded room start');
  await failedGateway.stop();

  const workers = [];
  const gateway = await createGateway({
    port: 0,
    workerFactory: async (roomId) => {
      const worker = await JavaRoomWorker.start({ command: WORKER, cwd: REPO_ROOT, roomId });
      workers.push(worker);
      return worker;
    },
  });
  t.after(async () => {
    await gateway.stop();
    for (const worker of workers) await worker.stop();
  });

  assert.deepEqual(await fetch(gateway.url + '/health').then((response) => response.json()), { status: 'ok' });
  const sdkResponse = await fetch(gateway.url + '/sdk/colyseus.js');
  assert.equal(sdkResponse.status, 200);
  assert.match(await sdkResponse.text(), /Colyseus/);
  assert.match(await fetch(gateway.url).then((response) => response.text()), /Create room/);

  const clientA = new Client(gateway.url);
  const clientB = new Client(gateway.url);
  const first = noReconnect(await clientA.create(ROOM_NAME));
  const firstSession = first.sessionId;
  const firstWorker = workers.at(-1);
  assert.ok(firstWorker.pid > 0);
  await waitFor(() => first.state.players?.has(firstSession), 'worker-authoritative initial player snapshot');

  const stopFirstMovement = startMovement(first);
  await waitFor(() => first.state.players.get(firstSession)?.x > 0.35, 'Java-authoritative movement');
  stopFirstMovement();
  await delay(350);
  const positionBeforeInvalid = first.state.players.get(firstSession).x;
  const sequenceBeforeInvalid = first.state.players.get(firstSession).lastSequence;

  const late = noReconnect(await clientB.joinById(first.roomId));
  const lateBaseline = await waitFor(() => late.state.players?.size === 2 && late.state.players.get(firstSession), 'late-join full baseline');
  assert.ok(Math.abs(lateBaseline.x - positionBeforeInvalid) < 0.2, 'late join must receive current Java position');
  const lateSession = late.sessionId;
  assert.equal(late.state.players.get(lateSession).lastSequence, -1);

  first.send('input', { sequence: 1000, x: 1, z: 1, position: { x: 999999, z: 999999 } });
  first.send('input', { sequence: 1001, x: 1, z: 1, sessionId: lateSession });
  first.send('input', { sequence: Number.MAX_SAFE_INTEGER + 1, x: 1, z: 1 });
  first.send('input', { sequence: sequenceBeforeInvalid, x: 0, z: -1 });
  await delay(250);
  assert.ok(Math.abs(first.state.players.get(firstSession).x - positionBeforeInvalid) < 0.3,
    'malformed and teleport-shaped client messages cannot change position');
  assert.equal(first.state.players.get(firstSession).lastSequence, sequenceBeforeInvalid,
    'malformed input and Java-rejected stale/unsafe sequences do not renew authoritative input');

  const otherClient = new Client(gateway.url);
  const other = noReconnect(await otherClient.create(ROOM_NAME));
  const isolatedWorker = workers.at(-1);
  const isolatedSession = other.sessionId;
  await waitFor(() => other.state.players?.has(isolatedSession), 'independent room baseline');
  assert.notEqual(other.roomId, first.roomId);
  assert.equal(other.state.players.size, 1);
  const otherMovement = startMovement(other, { x: 0, z: 1 });
  await waitFor(() => other.state.players.get(isolatedSession)?.z > 0.35, 'second room movement');
  otherMovement();
  assert.equal(first.state.players.size, 2, 'second room must not add a player to the first room');

  await first.leave();
  await waitFor(() => late.state.players?.size === 1, 'leave replication');
  const rejoined = noReconnect(await clientA.joinById(late.roomId));
  await waitFor(() => rejoined.state.players?.size === 2 && rejoined.state.players.has(rejoined.sessionId), 'leave/rejoin lifecycle');
  assert.notEqual(rejoined.sessionId, firstSession);

  await late.leave();
  await rejoined.leave();
  const firstExit = await Promise.race([firstWorker.exitPromise, delay(3000).then(() => null)]);
  assert.ok(firstExit, 'the final leave must stop that room Java worker');
  assert.equal(firstWorker.closed, true);

  const crashClient = new Client(gateway.url);
  const crashingRoom = noReconnect(await crashClient.create(ROOM_NAME));
  const crashingWorker = workers.at(-1);
  await delay(5200);
  const left = new Promise((resolve) => crashingRoom.onLeave((code) => resolve(code)));
  process.kill(crashingWorker.pid, 'SIGKILL');
  const closeCode = await Promise.race([left, delay(3000).then(() => null)]);
  assert.notEqual(closeCode, null, 'abrupt Java worker failure must disconnect its Colyseus room');
  assert.ok(await Promise.race([crashingWorker.exitPromise, delay(1000).then(() => null)]));
  await delay(500);
  assert.equal(workers.filter((worker) => worker.pid === crashingWorker.pid).length, 1,
    'a failed worker is never restarted implicitly');

  const uncertainOwnerClient = new Client(gateway.url);
  const uncertainOwnerRoom = noReconnect(await uncertainOwnerClient.create(ROOM_NAME));
  const uncertainWorker = workers.at(-1);
  await waitFor(() => uncertainOwnerRoom.state.players?.has(uncertainOwnerRoom.sessionId), 'uncertain-join test owner baseline');
  const ownerLeftAfterUncertainJoin = new Promise((resolve) => uncertainOwnerRoom.onLeave((code) => resolve(code)));
  process.kill(uncertainWorker.pid, 'SIGSTOP');
  const uncertainJoinClient = new Client(gateway.url);
  const uncertainJoin = uncertainJoinClient.joinById(uncertainOwnerRoom.roomId).then(
    (room) => ({ room: noReconnect(room) }),
    (error) => ({ error }),
  );
  const uncertainResult = await Promise.race([uncertainJoin, delay(6000).then(() => null)]);
  assert.ok(uncertainResult?.error, 'a join with an uncertain Java acknowledgement must be rejected');
  assert.ok(uncertainResult.error.message || uncertainResult.error.name, 'the SDK reports the rejected join');
  const uncertainExit = await Promise.race([uncertainWorker.exitPromise, delay(4000).then(() => null)]);
  assert.ok(uncertainExit, 'uncertain join failure must terminate its Java authority');
  assert.equal(uncertainWorker.closed, true);
  const ownerLeaveCode = await Promise.race([ownerLeftAfterUncertainJoin, delay(3000).then(() => null)]);
  assert.notEqual(ownerLeaveCode, null, 'the room disconnects existing members when join authority becomes uncertain');

  const otherWorker = isolatedWorker;
  process.kill(otherWorker.pid, 'SIGSTOP');
  const firstStop = gateway.stop();
  const secondStop = gateway.stop();
  assert.strictEqual(secondStop, firstStop, 'concurrent stop callers share one completion promise');
  let secondResolved = false;
  void secondStop.then(() => { secondResolved = true; });
  try {
    await delay(150);
    assert.equal(secondResolved, false, 'the second stop cannot resolve while active worker cleanup is paused');
    assert.equal(otherWorker.closed, false, 'the active worker still proves stop has not completed');
  } finally {
    process.kill(otherWorker.pid, 'SIGCONT');
  }
  await Promise.all([firstStop, secondStop]);
  assert.ok(await Promise.race([otherWorker.exitPromise, delay(3000).then(() => null)]),
    'gateway shutdown closes workers that still have active clients');
});
