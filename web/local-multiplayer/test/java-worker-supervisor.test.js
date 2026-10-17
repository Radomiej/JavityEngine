import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { test } from 'node:test';
import { JavaRoomWorker } from '../src/java-worker.js';

test('stdin failure rejects pending commands and repeated stop calls share bounded child cleanup', { timeout: 5000 }, async () => {
  const childProgram = [
    "process.stdout.write(JSON.stringify({v:1,roomId:process.argv[1],type:'ready'})+'\\n');",
    'setTimeout(()=>process.stdin.destroy(),120);',
    'setTimeout(()=>process.exit(0),600);',
  ].join('');
  const worker = await JavaRoomWorker.start({ command: process.execPath, args: ['-e', childProgram], roomId: 'stdin-failure' });
  const pending = worker.send('join', { sessionId: 'owner-generated' });
  await delay(180);
  await assert.rejects(pending, /stdin failed|write|exited/i);
  const started = Date.now();
  const [firstStop, secondStop] = await Promise.all([worker.stop(), worker.stop()]);
  assert.deepEqual(firstStop, secondStop);
  assert.ok(Date.now() - started < 2000, 'stop after child stdin failure must settle within the supervisor deadline');
  await assert.rejects(worker.sendInput('owner-generated', 1, 1, 0), /stopped|closed|stdin failed|exited/i);
});

test('startup timeout terminates and reaps a child that never sends ready', { timeout: 4000 }, async () => {
  const directory = await mkdtemp(join(tmpdir(), 'javity-room-startup-'));
  const pidFile = join(directory, 'pid');
  const childProgram = `require('node:fs').writeFileSync(process.argv[1], String(process.pid)); setInterval(()=>{}, 1000);`;
  const started = Date.now();
  try {
    await assert.rejects(JavaRoomWorker.start({
      command: process.execPath,
      args: ['-e', childProgram, pidFile],
      roomId: 'startup-timeout',
      startupTimeoutMs: 100,
    }), /Timed out waiting for Java worker ready frame/);
    const pid = Number(await readFile(pidFile, 'utf8'));
    assert.ok(Number.isInteger(pid) && pid > 0);
    assert.throws(() => process.kill(pid, 0), { code: 'ESRCH' }, 'startup failure must reap the spawned child');
    assert.ok(Date.now() - started < 2500, 'startup cleanup must remain inside its terminate/kill deadline');
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
