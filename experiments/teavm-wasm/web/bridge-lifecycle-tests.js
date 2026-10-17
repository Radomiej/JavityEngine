import Yoga, {Direction, FlexDirection} from 'yoga-layout';
import {createBrowserHost} from './browser-host.js';

const realAmmoFactory = globalThis.Ammo;
const events = [];
globalThis.bridgeUnhandledRejections = [];
addEventListener('unhandledrejection', event => { bridgeUnhandledRejections.push(String(event.reason)); event.preventDefault(); });
const record = event => events.push(event);
const actualAmmo = () => realAmmoFactory({locateFile: path => `ammo/${path}`});
const assert = (condition, message) => { if (!condition) throw new Error(message); };

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((done, fail) => { resolve = done; reject = fail; });
  return {promise, resolve, reject};
}

function instrumentAmmo(ammo, failAt = null, options = {}) {
  const constructed = [];
  const attempted = [];
  const destroyed = [];
  const vectors = [];
  const wrapped = new Proxy(ammo, {
    get(target, key, receiver) {
      if (key === 'destroy') return value => {
        attempted.push(value);
        if (options.failFirstVectorDestroy && value === vectors[0]) throw new Error('injected gravity destroy failure');
        const result = target.destroy(value);
        destroyed.push(value);
        return result;
      };
      const value = Reflect.get(target, key, receiver);
      if (typeof value !== 'function' || !String(key).startsWith('bt')) return value;
      return new Proxy(value, {
        construct(constructor, args) {
          if (key === failAt) throw new Error(`injected ${String(key)} failure`);
          const object = Reflect.construct(constructor, args);
          constructed.push(object);
          if (key === 'btVector3') vectors.push(object);
          return object;
        }
      });
    }
  });
  return {ammo: wrapped, constructed, attempted, destroyed, vectors};
}

function assertAllFreed(probe, count, scenario) {
  assert(probe.constructed.length === count,
      `${scenario}: expected ${count} constructors, got ${probe.constructed.length}`);
  assert(probe.destroyed.length === count,
      `${scenario}: expected ${count} frees, got ${probe.destroyed.length}`);
  assert(new Set(probe.destroyed).size === probe.destroyed.length,
      `${scenario}: an Ammo object was freed twice`);
  assert(probe.constructed.every(object => probe.destroyed.includes(object)),
      `${scenario}: a constructed Ammo object was not freed`);
}


async function testInactiveTicksAreSafe() {
  const unstarted = createBrowserHost({});
  unstarted.tick(1 / 60);
  unstarted.dispose();
  unstarted.tick(1 / 60);

  const probe = instrumentAmmo(await actualAmmo());
  const disposed = createBrowserHost({Yoga, Direction, FlexDirection, loadAmmo: async () => probe.ammo, record});
  const startup = disposed.create();
  await startup;
  disposed.dispose();
  disposed.tick(1 / 60);
}

async function testDestroyFailureIsReportedAndCleanupContinues() {
  const hostEvents = [];
  const probe = instrumentAmmo(await actualAmmo(), null, {failFirstVectorDestroy: true});
  const host = createBrowserHost({Yoga, Direction, FlexDirection, loadAmmo: async () => probe.ammo, record: event => hostEvents.push(event)});
  await host.create();
  host.dispose();
  assert(hostEvents.some(event => event.startsWith('gravity:destroy:error:')), 'failed temporary gravity free must be reported as failed');
  assert(!hostEvents.includes('gravity:destroy:ok'), 'failed temporary gravity free must not be reported as successful');
  assert(probe.attempted.length === 13, `expected each Ammo object destroy attempt once, got ${probe.attempted.length}`);
  assert(probe.destroyed.length === 12, `only the injected gravity free should fail, got ${probe.destroyed.length} successful frees`);
  assert(!probe.destroyed.includes(probe.vectors[0]), 'failed gravity pointer must not be claimed as freed');
  assert(probe.constructed.filter(value => value !== probe.vectors[0]).every(value => probe.destroyed.includes(value)), 'cleanup must free all other objects after a gravity free error');
}

async function testCancelledLoadRejectionIsQuietAndCurrentFailureRemainsAwaitable() {
  const oldLoad = deferred();
  const currentLoad = deferred();
  let loads = 0;
  const hostEvents = [];
  const host = createBrowserHost({Yoga, Direction, FlexDirection, loadAmmo: () => loads++ === 0 ? oldLoad.promise : currentLoad.promise, record: event => hostEvents.push(event)});
  host.create(); // BrowserBridge.create intentionally ignores the Promise returned by this host callback.
  const currentStartup = host.create();
  oldLoad.reject(new Error('late superseded loader failure'));
  currentLoad.resolve(instrumentAmmo(await actualAmmo()).ammo);
  await currentStartup;
  await new Promise(resolve => setTimeout(resolve, 0));
  assert(bridgeUnhandledRejections.length === 0, `cancelled loader rejection escaped: ${bridgeUnhandledRejections.join('; ')}`);
  assert(hostEvents.filter(event => event === 'ammo:create').length === 1, 'late rejected startup must not disturb the current world');
  host.dispose();

  const failedLoad = deferred();
  const failingHost = createBrowserHost({Yoga, Direction, FlexDirection, loadAmmo: () => failedLoad.promise, record: hostEvents.push.bind(hostEvents)});
  const awaitedFailure = failingHost.create();
  failedLoad.reject(new Error('current loader failure'));
  let preserved = false;
  try { await awaitedFailure; } catch (error) { preserved = String(error).includes('current loader failure'); }
  assert(preserved, 'an active startup failure must remain observable to an awaiting caller');
}

async function testDisposeDuringStartup() {
  const ammoCreateCount = events.filter(event => event === 'ammo:create').length;
  const gate = deferred();
  const probe = {constructed: [], destroyed: []};
  const host = createBrowserHost({Yoga, Direction, FlexDirection, loadAmmo: () => gate.promise, record});
  const startup = host.create();
  host.dispose();
  Object.assign(probe, instrumentAmmo(await actualAmmo()));
  gate.resolve(probe.ammo);
  await startup;
  host.dispose();
  assert(probe.constructed.length === 0, 'disposed startup must not allocate Ammo objects after load resolves');
  assert(events.filter(event => event === 'ammo:create').length === ammoCreateCount, 'disposed startup must not publish an Ammo world');
}

async function testRapidRecreate() {
  const ammoCreateCount = events.filter(event => event === 'ammo:create').length;
  const first = deferred();
  const second = deferred();
  const probes = [];
  let loads = 0;
  const host = createBrowserHost({Yoga, Direction, FlexDirection, loadAmmo: async () => {
    const gate = loads++ === 0 ? first : second;
    const instrumented = instrumentAmmo(await gate.promise);
    probes.push(instrumented);
    return instrumented.ammo;
  }, record});
  const oldStartup = host.create();
  const currentStartup = host.create();
  second.resolve(await actualAmmo());
  await currentStartup;
  first.resolve(await actualAmmo());
  await oldStartup;
  assert(events.filter(event => event === 'ammo:create').length === ammoCreateCount + 1,
      'superseded startup must not publish its world');
  host.dispose();
  assert(probes.length === 2, 'both delayed Ammo loads should resolve');
  assertAllFreed(probes[0], 13, 'current startup');
  assertAllFreed(probes[1], 0, 'superseded startup');
}

async function testConstructorFailureReleasesPartialOwnership() {
  const probe = instrumentAmmo(await actualAmmo(), 'btRigidBodyConstructionInfo');
  const host = createBrowserHost({Yoga, Direction, FlexDirection, loadAmmo: async () => probe.ammo, record});
  let rejected = false;
  try { await host.create(); }
  catch (error) { rejected = /injected btRigidBodyConstructionInfo failure/.test(String(error)); }
  assert(rejected, 'constructor failure should reject startup with its original error');
  host.dispose();
  host.dispose();
  assert(probe.constructed.length > 0, 'constructor-failure case must allocate resources before throwing');
  assert(probe.constructed.length === probe.destroyed.length,
      `partial startup leaked Ammo objects (${probe.constructed.length} created, ${probe.destroyed.length} freed)`);
  assert(new Set(probe.destroyed).size === probe.destroyed.length,
      'partial startup must not double-free Ammo objects');
  assert(probe.constructed.every(object => probe.destroyed.includes(object)),
      'partial startup failed to free every constructed Ammo object');
}

async function testRepeatedSuccessfulCycles() {
  const gravityBefore = events.filter(event => event === 'gravity:destroy:ok').length;
  const probes = [];
  const host = createBrowserHost({Yoga, Direction, FlexDirection, loadAmmo: async () => {
    const probe = instrumentAmmo(await actualAmmo());
    probes.push(probe);
    return probe.ammo;
  }, record});
  for (let cycle = 0; cycle < 2; cycle++) {
    await host.create();
    host.dispose();
    host.dispose();
  }
  assert(probes.length === 2, 'two create calls should load two independent Ammo modules');
  for (let i = 0; i < probes.length; i++) assertAllFreed(probes[i], 13, `successful cycle ${i + 1}`);
  assert(events.filter(event => event === 'gravity:destroy:ok').length === gravityBefore + 2,
      'each successful cycle must release its temporary gravity vector');
}

try {
  await testInactiveTicksAreSafe();
  await testDestroyFailureIsReportedAndCleanupContinues();
  await testCancelledLoadRejectionIsQuietAndCurrentFailureRemainsAwaitable();
  await testDisposeDuringStartup();
  await testRapidRecreate();
  await testConstructorFailureReleasesPartialOwnership();
  await testRepeatedSuccessfulCycles();
  globalThis.bridgeLifecycleResult = {success: true, events};
} catch (error) {
  globalThis.bridgeLifecycleResult = {success: false, error: String(error), stack: error.stack, events};
  throw error;
}
