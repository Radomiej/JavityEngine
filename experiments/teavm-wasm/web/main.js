import Yoga, {Direction, FlexDirection} from 'yoga-layout';

const status = document.querySelector('#status');
const events = [];
globalThis.debugEvents = events;
let root;
let ammo;
let world;
let body;
let motionState;
let transform;
let origin;
let velocityY;

function yogaCreate() {
  root = Yoga.Node.create();
  root.setWidth(320);
  root.setHeight(200);
  root.setFlexDirection(FlexDirection.Row);
  const child = Yoga.Node.create();
  child.setWidth(80);
  child.setHeight(40);
  root.insertChild(child, 0);
  root.calculateLayout(320, 200, Direction.LTR);
  const layout = [child.getComputedLeft(), child.getComputedTop(), child.getComputedWidth(), child.getComputedHeight()];
  events.push(`yoga:${layout.join(',')}`);
  return {child, layout};
}

async function bulletCreate() {
  ammo = await Ammo({ locateFile: (path) => `ammo/${path}` });
  const collisionConfig = new ammo.btDefaultCollisionConfiguration();
  const dispatcher = new ammo.btCollisionDispatcher(collisionConfig);
  const broadphase = new ammo.btDbvtBroadphase();
  const solver = new ammo.btSequentialImpulseConstraintSolver();
  world = new ammo.btDiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfig);
  const gravity = new ammo.btVector3(0, -10, 0);
  world.setGravity(gravity);
  ammo.destroy(gravity);
  events.push("gravity:destroy:ok");
  const shape = new ammo.btSphereShape(0.5);
  origin = new ammo.btVector3(0, 5, 0);
  transform = new ammo.btTransform();
  transform.setIdentity();
  transform.setOrigin(origin);
  motionState = new ammo.btDefaultMotionState(transform);
  const inertia = new ammo.btVector3(0, 0, 0);
  shape.calculateLocalInertia(1, inertia);
  const info = new ammo.btRigidBodyConstructionInfo(1, motionState, shape, inertia);
  body = new ammo.btRigidBody(info);
  world.addRigidBody(body);
  events.push('ammo:create');
  return [collisionConfig, dispatcher, broadphase, solver, shape, inertia, info, world];
}

let childNode;
let yogaLayout;
let ammoOwned = [];
let hostReady = Promise.resolve();
const host = {
  async create() {
    events.push('java:create');
    try {
      ({child: childNode, layout: yogaLayout} = yogaCreate());
      hostReady = bulletCreate().then(owned => { ammoOwned = owned; });
    } catch (error) { events.push(`createError:${error.stack || error}`); throw error; }
  },
  tick(delta) {
    events.push('java:tick');
    world?.stepSimulation(delta, 1, 1 / 60);
    if (body) {
      body.getMotionState().getWorldTransform(transform);
      velocityY = transform.getOrigin().y();
    }
  },
  input(key, down) { events.push(`java:input:${key}:${down}`); },
  resize(width, height) { events.push(`java:resize:${width}x${height}`); },
  dispose() {
    events.push('java:dispose');
    if (world && body) {
      try { world.removeRigidBody(body); events.push('removeBody:ok'); }
      catch (error) { events.push(`removeBody:error:${error}`); }
    }
    const [collisionConfig, dispatcher, broadphase, solver, shape, inertia, info] = ammoOwned;
    const owned = [body, motionState, info, transform, origin, inertia, shape, world, solver, broadphase, dispatcher, collisionConfig].filter(Boolean);
    for (const value of owned) {
      try { ammo?.destroy(value); events.push('destroy:ok'); }
      catch (error) { events.push(`destroy:error:${error}`); }
    }
    world = null;
    root?.freeRecursive();
    root = childNode = undefined;
    events.push('bridge:dispose');
  }
};
globalThis.javityHost = host;

try {
  const loaded = await TeaVM.wasmGC.load('wasm-gc/app.wasm');
  const api = loaded.instance.exports;
  api.create();
  await hostReady;
  api.resize(320, 200);
  api.input(32, 1);
  for (let i = 0; i < 60; i++) api.tick(1 / 60);
  const frame = api.frame();
  const state = api.state();
  const y = velocityY;
  api.dispose();
  const evidence = {frame, state, ballYAfter60Ticks: y, ammoFrees: events.filter(event => event === "destroy:ok" || event === "gravity:destroy:ok").length, tickCallbacks: events.filter(event => event === 'java:tick').length, events: events.filter(event => event !== 'java:tick'), exports: Object.keys(api).sort()};
  status.textContent = JSON.stringify(evidence, null, 2);
  globalThis.spikeEvidence = evidence;
} catch (error) {
  status.textContent = `${error.stack || String(error)}\n${JSON.stringify(events)}`;
  throw error;
}
