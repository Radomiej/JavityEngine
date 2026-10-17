import { createGateway } from './gateway.js';
import { createInterface } from 'node:readline';

const port = process.env.PORT === undefined ? 2567 : Number(process.env.PORT);
if (!Number.isInteger(port) || port < 0 || port > 65535) throw new Error('PORT must be an integer between 0 and 65535');
const deployment = process.env.JAVITY_CONTAINER === '1';
const supervised = process.env.JAVITY_DEV_SUPERVISED === '1';
const gateway = await createGateway({ port, host: deployment ? '0.0.0.0' : '127.0.0.1', allowRemote: deployment, devtools: supervised && !deployment });
console.log(`Javity local multiplayer: ${gateway.url}`);
if (supervised) console.log(`JAVITY_DEV_READY ${JSON.stringify({ url: gateway.url, pid: process.pid })}`);

let stopping = false;
let control;
async function stop(signal) {
  if (stopping) return;
  stopping = true;
  control?.close();
  process.stdin.pause();
  console.log(`Stopping local gateway (${signal})`);
  try { await gateway.stop(); process.exitCode = 0; }
  catch (error) { console.error(`Gateway shutdown failed: ${error.message}`); process.exitCode = 1; }
}
process.once('SIGINT', () => void stop('SIGINT'));
process.once('SIGTERM', () => void stop('SIGTERM'));
if (supervised) {
  control = createInterface({ input: process.stdin, crlfDelay: Infinity });
  control.on('line', (line) => { if (line === 'shutdown') void stop('Java supervisor'); });
  control.once('close', () => void stop('Java supervisor disconnected'));
}
