import { createGateway } from './gateway.js';

const port = process.env.PORT === undefined ? 2567 : Number(process.env.PORT);
if (!Number.isInteger(port) || port < 0 || port > 65535) throw new Error('PORT must be an integer between 0 and 65535');
const gateway = await createGateway({ port });
console.log(`Javity local multiplayer: ${gateway.url}`);

let stopping = false;
async function stop(signal) {
  if (stopping) return;
  stopping = true;
  console.log(`Stopping local gateway (${signal})`);
  try { await gateway.stop(); process.exitCode = 0; }
  catch (error) { console.error(`Gateway shutdown failed: ${error.message}`); process.exitCode = 1; }
}
process.once('SIGINT', () => void stop('SIGINT'));
process.once('SIGTERM', () => void stop('SIGTERM'));
