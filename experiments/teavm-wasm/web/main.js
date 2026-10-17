import Yoga, {Direction, FlexDirection} from 'yoga-layout';
import {createBrowserHost} from './browser-host.js';

const status = document.querySelector('#status');
const events = [];
globalThis.debugEvents = events;
const host = createBrowserHost({
  Yoga, Direction, FlexDirection,
  loadAmmo: () => Ammo({locateFile: path => `ammo/${path}`}),
  record: event => events.push(event)
});
globalThis.javityHost = host;

try {
  const loaded = await TeaVM.wasmGC.load('wasm-gc/app.wasm');
  const api = loaded.instance.exports;
  api.create();
  await host.whenReady();
  api.resize(320, 200);
  api.input(32, 1);
  for (let i = 0; i < 60; i++) api.tick(1 / 60);
  const frame = api.frame();
  const state = api.state();
  const ballYAfter60Ticks = host.ballY();
  api.dispose();
  const evidence = {
    frame,
    state,
    ballYAfter60Ticks,
    tickCallbacks: events.filter(event => event === 'java:tick').length,
    events: events.filter(event => event !== 'java:tick'),
    ammoFrees: events.filter(event => event === 'destroy:ok' || event === 'gravity:destroy:ok').length,
    exports: Object.keys(api).sort()
  };
  status.textContent = JSON.stringify(evidence, null, 2);
  globalThis.spikeEvidence = evidence;
} catch (error) {
  status.textContent = `${error.stack || String(error)}\n${JSON.stringify(events)}`;
  throw error;
}
