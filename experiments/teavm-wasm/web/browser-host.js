/** Owns the experimental Yoga and Ammo objects used by the Java lifecycle facade. */
export function createBrowserHost({Yoga, Direction, FlexDirection, loadAmmo, record = () => {}}) {
  let generation = 0;
  let current = null;
  let lastStartup = Promise.resolve();

  const emit = message => record(message);
  const isCurrent = session => session !== null && current === session && !session.closed;

  function own(session, value) {
    session.ammoOwned.push(value);
    return value;
  }

  function destroy(session, value, report = true) {
    if (!value || session.destroyed.has(value)) return null;
    session.destroyed.add(value);
    try {
      session.ammo?.destroy(value);
      if (report) emit('destroy:ok');
      return null;
    } catch (error) {
      if (report) emit(`destroy:error:${error}`);
      return error;
    }
  }

  function createYoga(session) {
    let unattached = null;
    try {
      session.root = Yoga.Node.create();
      session.root.setWidth(320);
      session.root.setHeight(200);
      session.root.setFlexDirection(FlexDirection.Row);
      unattached = Yoga.Node.create();
      unattached.setWidth(80);
      unattached.setHeight(40);
      session.root.insertChild(unattached, 0);
      session.child = unattached;
      unattached = null;
      session.root.calculateLayout(320, 200, Direction.LTR);
      const child = session.child;
      const layout = [child.getComputedLeft(), child.getComputedTop(), child.getComputedWidth(), child.getComputedHeight()];
      emit(`yoga:${layout.join(',')}`);
    } catch (error) {
      if (unattached) unattached.free();
      throw error;
    }
  }

  function createAmmo(session) {
    const ammo = session.ammo;
    const collisionConfig = own(session, new ammo.btDefaultCollisionConfiguration());
    const dispatcher = own(session, new ammo.btCollisionDispatcher(collisionConfig));
    const broadphase = own(session, new ammo.btDbvtBroadphase());
    const solver = own(session, new ammo.btSequentialImpulseConstraintSolver());
    session.world = own(session, new ammo.btDiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfig));

    const gravity = own(session, new ammo.btVector3(0, -10, 0));
    let gravityError = null;
    let gravitySetError = null;
    try {
      session.world.setGravity(gravity);
    } catch (error) {
      gravitySetError = error;
    } finally {
      session.ammoOwned.splice(session.ammoOwned.indexOf(gravity), 1);
      gravityError = destroy(session, gravity, false);
    }
    if (gravityError) emit(`gravity:destroy:error:${gravityError}`);
    else emit('gravity:destroy:ok');
    if (gravitySetError) throw gravitySetError;

    const shape = own(session, new ammo.btSphereShape(0.5));
    const origin = own(session, new ammo.btVector3(0, 5, 0));
    const transform = own(session, new ammo.btTransform());
    transform.setIdentity();
    transform.setOrigin(origin);
    session.transform = transform;
    session.motionState = own(session, new ammo.btDefaultMotionState(transform));
    const inertia = own(session, new ammo.btVector3(0, 0, 0));
    shape.calculateLocalInertia(1, inertia);
    const info = own(session, new ammo.btRigidBodyConstructionInfo(1, session.motionState, shape, inertia));
    session.body = own(session, new ammo.btRigidBody(info));
    session.world.addRigidBody(session.body);
    session.bodyAdded = true;
    emit('ammo:create');
  }

  function closeSession(session, announce = true) {
    if (!session || session.closed) return;
    session.closed = true;
    if (current === session) current = null;
    if (announce) emit('java:dispose');
    if (session.world && session.body && session.bodyAdded) {
      try {
        session.world.removeRigidBody(session.body);
        emit('removeBody:ok');
      } catch (error) {
        emit(`removeBody:error:${error}`);
      }
      session.bodyAdded = false;
    }
    for (let index = session.ammoOwned.length - 1; index >= 0; index--) destroy(session, session.ammoOwned[index]);
    session.ammoOwned.length = 0;
    session.body = session.motionState = session.transform = session.world = session.ammo = null;
    if (session.root) {
      try { session.root.freeRecursive(); }
      catch (error) { emit(`yoga:disposeError:${error}`); }
      session.root = session.child = null;
    }
    emit('bridge:dispose');
  }

  function create() {
    closeSession(current);
    const session = {
      generation: ++generation,
      closed: false,
      root: null,
      child: null,
      ammo: null,
      world: null,
      body: null,
      motionState: null,
      transform: null,
      ballY: 0,
      bodyAdded: false,
      ammoOwned: [],
      destroyed: new Set()
    };
    current = session;
    emit('java:create');

    try {
      createYoga(session);
    } catch (error) {
      closeSession(session, false);
      lastStartup = Promise.reject(error);
      return lastStartup;
    }

    lastStartup = (async () => {
      try {
        const ammo = await loadAmmo();
        if (!isCurrent(session) || session.generation !== generation) return;
        session.ammo = ammo;
        createAmmo(session);
      } catch (error) {
        const cancelled = session.closed || current !== session || session.generation !== generation;
        closeSession(session, false);
        if (!cancelled) throw error;
      }
    })();
    return lastStartup;
  }

  function dispose() {
    generation++;
    closeSession(current);
  }

  const host = {
    create,
    whenReady: () => lastStartup,
    tick(delta) {
      const session = current;
      emit('java:tick');
      if (isCurrent(session) && session.world) {
        session.world.stepSimulation(delta, 1, 1 / 60);
        if (session.body && session.motionState && session.transform) {
          session.body.getMotionState().getWorldTransform(session.transform);
          session.ballY = session.transform.getOrigin().y();
        }
      }
    },
    ballY: () => current?.ballY ?? 0,
    input(key, down) { emit(`java:input:${key}:${down}`); },
    resize(width, height) { emit(`java:resize:${width}x${height}`); },
    dispose
  };

  return host;
}
