const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');

const bridgeSource = fs.readFileSync(path.resolve(__dirname, '../../../web/orbit/bridge.js'), 'utf8');

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

async function settle() {
  await new Promise(resolve => setImmediate(resolve));
  await Promise.resolve();
}

async function harness() {
  const gets = [];
  const writes = [];
  const elements = [];
  const sdk = {
    ready: () => Promise.resolve(),
    player: { get: () => Promise.resolve({ id: 'p', nickname: 'Player' }) },
    saves: {
      get: slot => { const call = deferred(); gets.push({ slot, ...call }); return call.promise; },
      put: (slot, data, options) => { writes.push({ slot, data, options }); return Promise.resolve({ data, revision: options.expectedRevision + 1 }); }
    },
    assets: { list: async () => ({}), getUrl: async () => ({}) },
    achievements: { unlock: async () => ({}) },
    multiplayer: { list: async () => ({ items: [] }), create: async () => ({}), join: async () => ({}), close: async () => ({ ok: true }) },
    leaderboard: { list: async () => ({ items: [] }) },
    dispose() {}
  };
  const document = {
    readyState: 'complete',
    body: { append() {} },
    createElement() {
      const child = {
        style: {}, disabled: false, textContent: '', attributes: {},
        append(...children) { this.children = children; },
        setAttribute(key, value) { this.attributes[key] = value; }
      };
      elements.push(child);
      return child;
    },
    addEventListener() {}
  };
  const window = { addEventListener() {} };
  window.parent = {};
  const context = vm.createContext({
    window, document, Orbit: { createGameSdk: () => sdk },
    JAVITY_ORBIT_CONFIG: { launcherOrigins: ['https://launcher.example'] }
  });
  vm.runInContext(bridgeSource, context, { filename: 'bridge.js' });
  const api = context.JavityOrbit;
  for (let i = 0; i < 10 && gets.length === 0; i++) await settle();
  assert.equal(gets.length, 1, 'panel should begin its initial save load');
  return { api, gets, writes, elements, context };
}

test('stale null load keeps the newer typed revision and marks the panel loaded', async () => {
  const h = await harness();
  h.api.syncSaveRevision(1);
  h.api.syncSaveRevision(0);
  h.gets[0].resolve(null);
  await settle();
  assert.equal(h.api.state.revision, 1);
  assert.equal(h.api.takeRestore(), null);
  const saveButton = h.elements.find(element => element.textContent === 'Zapisz w chmurze');
  assert.ok(saveButton);
  assert.equal(saveButton.disabled, false);
  h.context.javityWasm = { disposed: false, x: 2, z: -3 };
  await saveButton.onclick();
  assert.equal(h.writes.length, 1);
  assert.equal(h.writes[0].options.expectedRevision, 1);
  assert.equal(h.api.state.revision, 2);
});

test('current load applies a valid snapshot and publishes its revision', async () => {
  const h = await harness();
  h.gets[0].resolve({ data: { kind: 'javity-demo-position-v1', x: 4, z: -5 }, revision: 7 });
  await settle();
  assert.equal(h.api.state.revision, 7);
  assert.deepEqual(JSON.parse(JSON.stringify(h.api.takeRestore())), { x: 4, z: -5, revision: 7 });
  assert.equal(h.api.takeSaveRevision(), 7);
});

test('older non-null load response is ignored after a newer write', async () => {
  const h = await harness();
  h.api.syncSaveRevision(3);
  h.gets[0].resolve({ data: { kind: 'javity-demo-position-v1', x: 1, z: 1 }, revision: 2 });
  await settle();
  assert.equal(h.api.state.revision, 3);
  assert.equal(h.api.takeRestore(), null);
});
