package org.javity.physics3d;

import com.badlogic.gdx.math.*;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.collision.ClosestConvexResultCallback;
import com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback;
import com.badlogic.gdx.physics.bullet.collision.ContactResultCallback;
import com.badlogic.gdx.physics.bullet.collision.btCollisionObjectWrapper;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.badlogic.gdx.physics.bullet.linearmath.btDefaultMotionState;
import com.badlogic.gdx.utils.GdxNativesLoader;
import java.util.*;
import java.util.Optional;
import java.util.function.Consumer;
import org.javity.core.*;

/** Optional scene-owned Bullet world. Distances are world units (one unit equals one metre). */
public final class Physics3D extends GameSystem {
  public static final float FIXED_STEP = 1f / 60f;
  private static final int MAX_STEPS_PER_FRAME = 8;
  private static final double CHARACTER_NATIVE_LIMIT = Math.sqrt(Float.MAX_VALUE) / 4d;
  private static boolean bulletInitialized;

  interface FailureProbe {
    void allocation(String stage);
  }

  private static final class PreparationHook {
    final Runnable preparation;
    boolean closed;

    PreparationHook(Runnable preparation) {
      this.preparation = preparation;
    }
  }

  private final class Binding {
    final long lifetime;
    final GameObject object;
    final Transform3D transform;
    final RigidBody3D rigid;
    final List<Collider3D> colliders;
    final btRigidBody body;
    final btDefaultMotionState motion;
    final btCollisionShape shape;
    final btCompoundShape compound;
    final List<btCollisionShape> children;
    final String signature;
    final List<Collider3D> solidColliders;
    final List<TriggerBinding> triggers;
    boolean inserted;
    final Vector3 lastPosition = new Vector3();
    final Quaternion lastRotation = new Quaternion();

    Binding(
        GameObject object,
        Transform3D transform,
        RigidBody3D rigid,
        List<Collider3D> colliders,
        btRigidBody body,
        btDefaultMotionState motion,
        ShapeBuilder shape,
        String signature,
        List<Collider3D> solidColliders,
        List<TriggerBinding> triggers,
        long lifetime,
        Matrix4 pose) {
      this.lifetime = lifetime;
      this.object = object;
      this.transform = transform;
      this.rigid = rigid;
      this.colliders = colliders;
      this.body = body;
      this.motion = motion;
      this.shape = shape == null ? null : shape.root();
      this.compound = shape == null ? null : shape.compound;
      this.children = shape == null ? List.of() : List.copyOf(shape.children);
      this.signature = signature;
      this.solidColliders = List.copyOf(solidColliders);
      this.triggers = List.copyOf(triggers);
      pose.getTranslation(lastPosition);
      pose.getRotation(lastRotation, true);
    }
  }

  private static final class TriggerBinding {
    final long lifetime;
    final Collider3D collider;
    final btCollisionObject object;
    final ShapeBuilder shape;
    boolean inserted;

    TriggerBinding(
        Collider3D collider, btCollisionObject object, ShapeBuilder shape, long lifetime) {
      this.collider = collider;
      this.object = object;
      this.shape = shape;
      this.lifetime = lifetime;
    }
  }

  private record PairKey(long a, long b, long sensorA, long sensorB)
      implements Comparable<PairKey> {
    @Override
    public int compareTo(PairKey o) {
      int c = Long.compare(a, o.a);
      if (c == 0) c = Long.compare(b, o.b);
      if (c == 0) c = Long.compare(sensorA, o.sensorA);
      if (c == 0) c = Long.compare(sensorB, o.sensorB);
      return c;
    }
  }

  private record ContactState(
      PairKey key,
      Binding a,
      Binding b,
      Collider3D trigger,
      boolean triggerPair,
      Contact3D contact) {}

  private final class CharacterBinding {
    final long lifetime = nextBindingLifetime++;
    final GameObject object;
    final Transform3D transform;
    final BulletCharacterController3D component;
    final btCapsuleShape capsule;
    final btPairCachingGhostObject ghost;
    final btKinematicCharacterController action;
    boolean ghostInserted, actionInserted;
    boolean blockedThisStep;
    final Vector3 previousCentre = new Vector3();
    final Vector3 lastFeet = new Vector3();
    boolean previousGrounded, jumped;
    final Map<Long, CharacterTrigger> activeTriggers = new LinkedHashMap<>();
    final Map<Long, CharacterTrigger> currentTriggers = new LinkedHashMap<>();

    CharacterBinding(
        GameObject object,
        Transform3D transform,
        BulletCharacterController3D component,
        btCapsuleShape capsule,
        btPairCachingGhostObject ghost,
        btKinematicCharacterController action) {
      this.object = object;
      this.transform = transform;
      this.component = component;
      this.capsule = capsule;
      this.ghost = ghost;
      this.action = action;
      Matrix4 initialPose = new Matrix4();
      ghost.getWorldTransform(initialPose);
      initialPose.getTranslation(previousCentre);
      lastFeet.set(transform.position);
    }
  }

  private record CharacterTrigger(
      long lifetime,
      Binding owner,
      TriggerBinding trigger,
      Vector3 pointA,
      Vector3 pointB,
      Vector3 normalOnB,
      boolean triggerIsA) {}

  private record PendingCharacterExit(BulletCharacterController3D component, Contact3D event) {}

  private final class NativeEntry {
    final Binding binding;
    final CharacterBinding character;
    final GameObject object;
    final Collider3D collider;
    final boolean trigger;
    final int group, mask;

    NativeEntry(
        Binding binding,
        CharacterBinding character,
        GameObject object,
        Collider3D collider,
        boolean trigger,
        int group,
        int mask) {
      this.binding = binding;
      this.character = character;
      this.object = object;
      this.collider = collider;
      this.trigger = trigger;
      this.group = group;
      this.mask = mask;
    }
  }

  private abstract class FilteredRay extends ClosestRayResultCallback {
    final PhysicsFilter3D filter;

    FilteredRay(Vector3 from, Vector3 to, PhysicsFilter3D filter) {
      super(from, to);
      this.filter = filter;
    }

    @Override
    public boolean needsCollision(btBroadphaseProxy proxy) {
      try {
        if (!super.needsCollision(proxy)) return false;
        NativeEntry entry = nativeEntries.get(proxy.getClientObject());
        return entry != null
            && (filter.includeTriggers() || !entry.trigger)
            && (filter.ignoredObjectId() == null || entry.object.id != filter.ignoredObjectId());
      } catch (RuntimeException | Error failure) {
        queryCallbackFailure = combine(queryCallbackFailure, failure);
        return false;
      }
    }
  }

  private abstract class FilteredConvex extends ClosestConvexResultCallback {
    final PhysicsFilter3D filter;

    FilteredConvex(Vector3 from, Vector3 to, PhysicsFilter3D filter) {
      super(from, to);
      this.filter = filter;
    }

    @Override
    public boolean needsCollision(btBroadphaseProxy proxy) {
      try {
        if (!super.needsCollision(proxy)) return false;
        NativeEntry entry = nativeEntries.get(proxy.getClientObject());
        return entry != null
            && (filter.includeTriggers() || !entry.trigger)
            && (filter.ignoredObjectId() == null || entry.object.id != filter.ignoredObjectId());
      } catch (RuntimeException | Error failure) {
        queryCallbackFailure = combine(queryCallbackFailure, failure);
        return false;
      }
    }
  }

  private final Vector3 gravity;
  private final Map<GameObject, Binding> bindings = new LinkedHashMap<>();
  private final Map<GameObject, CharacterBinding> characters = new LinkedHashMap<>();
  private final List<PreparationHook> beforeFixedStep = new ArrayList<>();
  private final List<PendingCharacterExit> pendingCharacterExits = new ArrayList<>();
  private final Map<Long, NativeEntry> nativeEntries = new HashMap<>();
  private final TreeMap<PairKey, ContactState> activeContacts = new TreeMap<>();
  private final FailureProbe failureProbe;
  private btDefaultCollisionConfiguration collisionConfig;
  private btCollisionDispatcher dispatcher;
  private btDbvtBroadphase broadphase;
  private btSequentialImpulseConstraintSolver solver;
  private btDiscreteDynamicsWorld world;
  private Physics3DDebugView debugView;
  private btGhostPairCallback ghostPairCallback;
  private double accumulator;
  private long fixedTick;
  private double discardedTimeSeconds;
  private long nativeAllocations, nativeDisposals;
  private boolean stepping, closing;
  private boolean deliveringBeforeFixedStep;
  private boolean dispatching;
  private boolean evaluatingCharacterReadiness;
  private long nextBindingLifetime = 1;
  private Thread ownerThread;
  private Throwable gameplayCallbackFailure, contactCallbackFailure, queryCallbackFailure;

  public Physics3D(Vector3 gravity) {
    this(gravity, stage -> {});
  }

  /**
   * Registers an owner-thread preparation callback run after WORLD updates and before physics
   * synchronizes components for the next fixed step. Callbacks run in registration order and
   * outside JNI. Close the returned handle to remove the callback.
   */
  public AutoCloseable beforeFixedStep(Runnable preparation) {
    Objects.requireNonNull(preparation, "preparation");
    requireOwnerThread();
    if (getGame() == null || getGame().isDisposed() || isDisposed() || closing)
      throw new IllegalStateException("Physics3D must belong to a live Game");
    PreparationHook hook = new PreparationHook(preparation);
    beforeFixedStep.add(hook);
    return new AutoCloseable() {
      private boolean closed;

      @Override
      public void close() {
        requireOwnerThread();
        if (!closed) {
          closed = true;
          hook.closed = true;
          beforeFixedStep.remove(hook);
        }
      }
    };
  }

  /** Whether this exact scene object currently has a committed native solid body. */
  public boolean isBodyReady(GameObject object) {
    Objects.requireNonNull(object, "object");
    requireOwnerThread();
    Binding binding = bindings.get(object);
    return binding != null && binding.inserted && binding.body != null;
  }

  /**
   * Publishes a staged static terrain descriptor from a before-fixed-step callback. Native
   * replacement is transactional; failed allocation or insertion leaves the previous binding.
   */
  public boolean publishStaticCollision(GameObject object, StaticCompoundCollider3D collider) {
    Objects.requireNonNull(object, "object");
    Objects.requireNonNull(collider, "collider");
    requireWorld();
    requireOwnerThread();
    if (!deliveringBeforeFixedStep)
      throw new IllegalStateException(
          "Static collision publication is only valid before a fixed step");
    if (stepping || dispatching)
      throw new IllegalStateException("Cannot publish during native step");
    if (object.game != getGame() || !object.containsComponent(collider))
      throw new IllegalArgumentException("Static collision owner/component mismatch");
    if (!collider.isInitialized()) return false;
    if (collider.isTrigger())
      throw new IllegalArgumentException("Static terrain cannot be a trigger");
    List<Collider3D> colliders =
        object.getComponents(Collider3D.class).stream().filter(Collider3D::isEnabled).toList();
    List<Collider3D> solids = colliders.stream().filter(c -> !c.isTrigger()).toList();
    if (solids.size() != 1 || solids.getFirst() != collider)
      throw new IllegalArgumentException(
          "Static terrain must be the sole solid in the complete collider set");
    List<RigidBody3D> rigids = object.getComponents(RigidBody3D.class);
    if (rigids.size() > 1
        || (!rigids.isEmpty() && rigids.getFirst().getType() != RigidBody3D.Type.STATIC))
      throw new IllegalArgumentException("Static terrain requires static movement ownership");
    collider.publicationRequested(true);
    try {
      synchronize();
    } finally {
      collider.publicationRequested(false);
    }
    return isCollisionPublished(object, colliders);
  }

  /** Read-only exact-identity/configuration check for a complete static collision descriptor. */
  public boolean isCollisionPublished(GameObject object, List<? extends Collider3D> expected) {
    Objects.requireNonNull(object, "object");
    Objects.requireNonNull(expected, "expected");
    requireOwnerThread();
    if (object.game != getGame() || object.isDestroyed() || !object.isActive()) return false;
    List<Collider3D> copy = List.copyOf(expected);
    List<Collider3D> enabled =
        object.getComponents(Collider3D.class).stream().filter(Collider3D::isEnabled).toList();
    if (enabled.stream().anyMatch(c -> !c.isInitialized())) return false;
    List<Collider3D> attached = enabled;
    if (attached.size() != copy.size()) return false;
    for (int i = 0; i < copy.size(); i++) if (attached.get(i) != copy.get(i)) return false;
    Binding binding = bindings.get(object);
    if (binding == null || !binding.colliders.equals(copy)) return false;
    List<Transform3D> transforms = object.getComponents(Transform3D.class);
    if (transforms.size() != 1
        || !transforms.getFirst().isInitialized()
        || transforms.getFirst() != binding.transform) return false;
    if (!samePose(transforms.getFirst(), binding.lastPosition, binding.lastRotation)) return false;
    List<RigidBody3D> rigids = object.getComponents(RigidBody3D.class);
    if (rigids.size() > 1
        || (!rigids.isEmpty()
            && (!rigids.getFirst().isInitialized() || !rigids.getFirst().isEnabled())))
      return false;
    RigidBody3D rigid = rigids.isEmpty() ? null : rigids.getFirst();
    if (rigids.size() > 1 || binding.rigid != rigid) return false;
    if (!binding.signature.equals(signature(rigid, copy))) return false;
    boolean needsBody =
        copy.stream()
            .filter(c -> !c.isTrigger())
            .anyMatch(c -> !(c instanceof StaticCompoundCollider3D s) || !s.isEmpty());
    return needsBody
        ? binding.body != null && binding.inserted
        : binding.body == null && binding.inserted;
  }

  Physics3D(Vector3 gravity, FailureProbe failureProbe) {
    this.gravity = copyFinite(Objects.requireNonNull(gravity));
    this.failureProbe = Objects.requireNonNull(failureProbe);
  }

  public long getFixedTick() {
    return fixedTick;
  }

  /** Total accepted scaled simulation time discarded at the per-frame accumulator cap. */
  public double getDiscardedTimeSeconds() {
    return discardedTimeSeconds;
  }

  public int getBodyCount() {
    return (int) bindings.values().stream().filter(b -> b.inserted && b.body != null).count();
  }

  /** Number of separately allocated native trigger collision objects. */
  public int getTriggerObjectCount() {
    return (int) bindings.values().stream().flatMap(b -> b.triggers.stream()).count();
  }

  public int getCharacterCount() {
    return (int)
        characters.values().stream().filter(c -> c.ghostInserted && c.actionInserted).count();
  }

  /**
   * Creates the optional GL-backed Bullet debug drawer. Call only while a GL context is current.
   */
  public Physics3DDebugView createDebugView() {
    requireWorld();
    requireOwnerThread();
    if (debugView != null) throw new IllegalStateException("Physics3D already has a debug view");
    if (com.badlogic.gdx.Gdx.gl20 == null || com.badlogic.gdx.Gdx.graphics == null)
      throw new IllegalStateException("Physics3D debug drawing requires a current OpenGL context");
    Physics3DDebugView created = new Physics3DDebugView(this);
    debugView = created;
    try {
      created.attach();
      return created;
    } catch (RuntimeException | Error failure) {
      try {
        created.close();
      } catch (RuntimeException | Error cleanup) {
        failure.addSuppressed(cleanup);
      }
      throw failure;
    }
  }

  void attachDebugDrawer(
      Physics3DDebugView view, com.badlogic.gdx.physics.bullet.DebugDrawer drawer) {
    requireWorld();
    if (debugView != view) throw new IllegalStateException("Debug view is no longer current");
    world.setDebugDrawer(drawer);
  }

  void renderDebugWorld(Physics3DDebugView view) {
    requireWorld();
    requireOwnerThread();
    if (debugView != view) throw new IllegalStateException("Debug view is no longer current");
    world.debugDrawWorld();
  }

  void requireDebugViewOwner(Physics3DDebugView view) {
    requireOwnerThread();
    if (debugView != view)
      throw new IllegalStateException("Physics3D debug view is no longer current");
  }

  /** Whether this live world currently holds the optional debug-drawer attachment. */
  public boolean isDebugViewAttached() {
    requireOwnerThread();
    return debugView != null;
  }

  void detachDebugView(Physics3DDebugView view) {
    requireOwnerThread();
    if (debugView != view) return;
    if (world != null) world.setDebugDrawer(null);
    debugView = null;
  }

  int getNativeActionCountForTest() {
    return (int) characters.values().stream().filter(c -> c.actionInserted).count();
  }

  Vector3 getCharacterCentreForTest(GameObject owner) {
    CharacterBinding b = characters.get(owner);
    if (b == null) return null;
    Matrix4 pose = new Matrix4();
    b.ghost.getWorldTransform(pose);
    return pose.getTranslation(new Vector3());
  }

  int getCharacterOverlapCountForTest(GameObject owner) {
    CharacterBinding b = characters.get(owner);
    return b == null ? -1 : b.ghost.getNumOverlappingObjects();
  }

  Vector3 getCharacterCapsuleDimensionsForTest(GameObject owner) {
    CharacterBinding b = characters.get(owner);
    return b == null
        ? null
        : new Vector3(b.capsule.getRadius(), b.capsule.getHalfHeight(), b.capsule.getUpAxis());
  }

  public Optional<PhysicsHit3D> raycast(Vector3 from, Vector3 to, PhysicsFilter3D filter) {
    finite(from);
    finite(to);
    Objects.requireNonNull(filter);
    validateQuerySegment(from, to, 0);
    ensureQueryAllowed();
    if (from.epsilonEquals(to, 0)) return Optional.empty();
    FilteredRay callback = new FilteredRay(from.cpy(), to.cpy(), filter) {};
    nativeAllocations++;
    try {
      failureProbe.allocation("ray-query-callback");
      callback.setCollisionFilterGroup(filter.group());
      callback.setCollisionFilterMask(filter.mask());
      world.rayTest(from.cpy(), to.cpy(), callback);
      if (queryCallbackFailure != null) throwQueryCallbackFailure();
      if (!callback.hasHit()) return Optional.empty();
      NativeEntry entry =
          nativeEntries.get(btCollisionObject.getCPtr(callback.getCollisionObject()));
      if (entry == null) return Optional.empty();
      Vector3 point = new Vector3(), normal = new Vector3();
      callback.getHitPointWorld(point);
      callback.getHitNormalWorld(normal);
      return Optional.of(
          new PhysicsHit3D(
              entry.object,
              entry.collider,
              point,
              normal,
              callback.getClosestHitFraction(),
              from.dst(to) * callback.getClosestHitFraction()));
    } finally {
      queryCallbackFailure = release(callback, queryCallbackFailure);
      rethrowQueryCallbackFailure();
    }
  }

  public Optional<PhysicsHit3D> sweepSphere(
      float radius, Vector3 from, Vector3 to, PhysicsFilter3D filter) {
    positive(radius);
    finite(from);
    finite(to);
    Objects.requireNonNull(filter);
    validateQuerySegment(from, to, radius);
    ensureQueryAllowed();
    btSphereShape shape = new btSphereShape(radius);
    nativeAllocations++;
    boolean transferred = false;
    try {
      failureProbe.allocation("sweep-sphere-shape");
      transferred = true;
      return sweep(shape, from, to, filter, null);
    } finally {
      if (!transferred) release(shape, null);
    }
  }

  public Optional<PhysicsHit3D> sweepCapsule(
      float radius, float totalHeight, Matrix4 fromPose, Matrix4 toPose, PhysicsFilter3D filter) {
    positive(radius);
    positive(totalHeight);
    if (totalHeight < 2 * radius)
      throw new IllegalArgumentException("Capsule height must be at least its diameter");
    if (totalHeight > Float.MAX_VALUE / 4f || radius > Float.MAX_VALUE / 4f)
      throw new IllegalArgumentException(
          "Capsule query dimensions exceed the supported float range");
    Objects.requireNonNull(fromPose);
    Objects.requireNonNull(toPose);
    validateQueryMatrix(fromPose);
    validateQueryMatrix(toPose);
    Vector3 from = fromPose.getTranslation(new Vector3()),
        to = toPose.getTranslation(new Vector3());
    finite(from);
    finite(to);
    validateQuerySegment(from, to, Math.max(radius, totalHeight * .5f));
    Objects.requireNonNull(filter);
    ensureQueryAllowed();
    btCapsuleShape shape = new btCapsuleShape(radius, totalHeight - 2 * radius);
    nativeAllocations++;
    boolean transferred = false;
    try {
      failureProbe.allocation("sweep-capsule-shape");
      transferred = true;
      return sweep(shape, from, to, filter, new Matrix4[] {fromPose.cpy(), toPose.cpy()});
    } finally {
      if (!transferred) release(shape, null);
    }
  }

  private Optional<PhysicsHit3D> sweep(
      btConvexShape shape, Vector3 from, Vector3 to, PhysicsFilter3D filter, Matrix4[] poses) {
    Matrix4 fromMatrix = poses == null ? new Matrix4().setToTranslation(from) : poses[0];
    Matrix4 toMatrix = poses == null ? new Matrix4().setToTranslation(to) : poses[1];
    btCollisionObject overlap = null;
    try {
      overlap = new btCollisionObject();
      nativeAllocations++;
      failureProbe.allocation("sweep-overlap-object");
      overlap.setCollisionShape(shape);
      overlap.setWorldTransform(fromMatrix);
      final PhysicsHit3D[] startHit = {null};
      ContactResultCallback contact =
          new ContactResultCallback() {
            @Override
            public float addSingleResult(
                btManifoldPoint cp,
                btCollisionObjectWrapper w0,
                int p0,
                int i0,
                btCollisionObjectWrapper w1,
                int p1,
                int i1) {
              try {
                NativeEntry e0 =
                    nativeEntries.get(btCollisionObject.getCPtr(w0.getCollisionObject()));
                NativeEntry e1 =
                    nativeEntries.get(btCollisionObject.getCPtr(w1.getCollisionObject()));
                NativeEntry hit = e0 == null ? e1 : e1 == null ? e0 : null;
                if (hit == null
                    || (!filter.includeTriggers() && hit.trigger)
                    || (filter.ignoredObjectId() != null
                        && hit.object.id == filter.ignoredObjectId())
                    || !queryMutuallyFiltered(filter, hit)
                    || cp.getDistance() > 0) return 0;
                Vector3 point = new Vector3(), normal = new Vector3();
                if (e0 == null) {
                  cp.getPositionWorldOnB(point);
                  cp.getNormalWorldOnB(normal);
                } else {
                  cp.getPositionWorldOnA(point);
                  cp.getNormalWorldOnB(normal);
                  normal.scl(-1);
                }
                startHit[0] = new PhysicsHit3D(hit.object, hit.collider, point, normal, 0, 0);
              } catch (RuntimeException | Error failure) {
                queryCallbackFailure = combine(queryCallbackFailure, failure);
              }
              return 0;
            }
          };
      nativeAllocations++;
      try {
        failureProbe.allocation("sweep-overlap-callback");
        contact.setCollisionFilterGroup(filter.group());
        contact.setCollisionFilterMask(filter.mask());
        world.contactTest(overlap, contact);
      } finally {
        queryCallbackFailure = release(contact, queryCallbackFailure);
      }
      if (queryCallbackFailure != null) throwQueryCallbackFailure();
      if (startHit[0] != null) return Optional.of(startHit[0]);
      FilteredConvex callback = new FilteredConvex(from, to, filter) {};
      nativeAllocations++;
      try {
        failureProbe.allocation("sweep-query-callback");
        callback.setCollisionFilterGroup(filter.group());
        callback.setCollisionFilterMask(filter.mask());
        world.convexSweepTest(shape, fromMatrix, toMatrix, callback);
        if (queryCallbackFailure != null) throwQueryCallbackFailure();
        if (!callback.hasHit()) return Optional.empty();
        NativeEntry entry =
            nativeEntries.get(btCollisionObject.getCPtr(callback.getHitCollisionObject()));
        if (entry == null) return Optional.empty();
        Vector3 point = new Vector3(), normal = new Vector3();
        callback.getHitPointWorld(point);
        callback.getHitNormalWorld(normal);
        float fraction = callback.getClosestHitFraction();
        return Optional.of(
            new PhysicsHit3D(
                entry.object, entry.collider, point, normal, fraction, from.dst(to) * fraction));
      } finally {
        queryCallbackFailure = release(callback, queryCallbackFailure);
      }
    } finally {
      if (overlap != null) queryCallbackFailure = release(overlap, queryCallbackFailure);
      queryCallbackFailure = release(shape, queryCallbackFailure);
      rethrowQueryCallbackFailure();
    }
  }

  private void ensureQueryAllowed() {
    requireWorld();
    if (stepping) throw new IllegalStateException("Queries are unavailable during a Bullet step");
    if (!dispatching) synchronize();
  }

  private boolean queryMutuallyFiltered(PhysicsFilter3D filter, NativeEntry hit) {
    return (filter.group() & hit.mask) != 0 && (hit.group & filter.mask()) != 0;
  }

  private void throwQueryCallbackFailure() {
    Throwable t = queryCallbackFailure;
    queryCallbackFailure = null;
    if (t instanceof RuntimeException e) throw e;
    if (t instanceof Error e) throw e;
    throw new RuntimeException(t);
  }

  private void rethrowQueryCallbackFailure() {
    if (queryCallbackFailure != null) throwQueryCallbackFailure();
  }

  private static void validateQueryMatrix(Matrix4 matrix) {
    float[] m = matrix.val;
    for (float v : m)
      if (!Float.isFinite(v)) throw new IllegalArgumentException("Sweep matrix must be finite");
    if (Math.abs(m[Matrix4.M30]) > 1e-5f
        || Math.abs(m[Matrix4.M31]) > 1e-5f
        || Math.abs(m[Matrix4.M32]) > 1e-5f
        || Math.abs(m[Matrix4.M33] - 1) > 1e-5f)
      throw new IllegalArgumentException("Sweep matrix must be affine");
    Vector3 x = new Vector3(m[Matrix4.M00], m[Matrix4.M10], m[Matrix4.M20]);
    Vector3 y = new Vector3(m[Matrix4.M01], m[Matrix4.M11], m[Matrix4.M21]);
    Vector3 z = new Vector3(m[Matrix4.M02], m[Matrix4.M12], m[Matrix4.M22]);
    if (Math.abs(x.len2() - 1) > 1e-4f
        || Math.abs(y.len2() - 1) > 1e-4f
        || Math.abs(z.len2() - 1) > 1e-4f
        || Math.abs(x.dot(y)) > 1e-4f
        || Math.abs(x.dot(z)) > 1e-4f
        || Math.abs(y.dot(z)) > 1e-4f
        || x.crs(y).dot(z) < .999f)
      throw new IllegalArgumentException("Sweep matrix must be a proper unit rigid transform");
    finite(matrix.getTranslation(new Vector3()));
  }

  private static void validateQuerySegment(Vector3 from, Vector3 to, float extent) {
    finite(from);
    finite(to);
    double limit = Float.MAX_VALUE / 4d;
    for (int axis = 0; axis < 3; axis++) {
      double a = axis == 0 ? from.x : axis == 1 ? from.y : from.z;
      double b = axis == 0 ? to.x : axis == 1 ? to.y : to.z;
      if (Math.abs(a) + extent > limit || Math.abs(b) + extent > limit)
        throw new IllegalArgumentException(
            "Query pose and extent exceed the supported float range");
    }
    double dx = (double) to.x - from.x, dy = (double) to.y - from.y, dz = (double) to.z - from.z;
    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (!Double.isFinite(distance) || distance > limit)
      throw new IllegalArgumentException("Query segment exceeds the supported float range");
  }

  int getNativeCollisionObjectCount() {
    return world == null ? 0 : world.getNumCollisionObjects();
  }

  btRigidBody getNativeBodyForTest(GameObject object) {
    Binding binding = bindings.get(object);
    return binding == null ? null : binding.body;
  }

  long getNativeAllocationCount() {
    return nativeAllocations;
  }

  long getNativeDisposalCount() {
    return nativeDisposals;
  }

  @Override
  public void onCreate() {
    ownerThread = Thread.currentThread();
    try {
      GdxNativesLoader.load();
      synchronized (Physics3D.class) {
        if (!bulletInitialized) {
          Bullet.init();
          bulletInitialized = true;
        }
      }
      collisionConfig = new btDefaultCollisionConfiguration();
      allocation("collision-configuration");
      dispatcher = new btCollisionDispatcher(collisionConfig);
      allocation("dispatcher");
      broadphase = new btDbvtBroadphase();
      allocation("broadphase");
      solver = new btSequentialImpulseConstraintSolver();
      allocation("solver");
      world = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfig);
      allocation("world");
      world.setGravity(gravity.cpy());
      // KCC sweeps otherwise accept up to Bullet's 0.04 default penetration, which can leave
      // an upright capsule inside the floor and make its first upward sweep cancel a jump.
      world.getDispatchInfo().setAllowedCcdPenetration(0f);
    } catch (RuntimeException | Error e) {
      Throwable cleanup = cleanupNative();
      if (cleanup != null) e.addSuppressed(cleanup);
      throw e;
    }
  }

  @Override
  public void onBeforeUpdate(float delta) {
    synchronize();
  }

  @Override
  public void onUiUpdate(float delta) {
    // Lifecycle removals remain safe while paused, but no queued force/impulse is consumed here.
    if (world != null && !closing && !stepping) synchronize();
  }

  @Override
  public void onAfterUpdate(float delta) {
    if (delta <= 0) {
      synchronize();
      return;
    }
    double accumulated = accumulator + delta;
    double cap = MAX_STEPS_PER_FRAME * (double) FIXED_STEP;
    if (accumulated > cap) {
      discardedTimeSeconds += accumulated - cap;
      accumulated = cap;
    }
    accumulator = accumulated;
    int steps = 0;
    while (accumulator + 1e-8 >= FIXED_STEP && steps < MAX_STEPS_PER_FRAME) {
      runBeforeFixedStepPreparations();
      if (closing || isDisposed()) return;
      synchronize();
      if (closing || isDisposed()) return;
      prepareCharacterStep();
      if (closing || isDisposed()) return;
      drainInvalidatedCharactersBeforeStep();
      if (closing || isDisposed()) return;
      applyCommands();
      stepping = true;
      try {
        world.stepSimulation(FIXED_STEP, 0);
      } finally {
        stepping = false;
      }
      accumulator = Math.max(0, accumulator - FIXED_STEP);
      fixedTick++;
      steps++;
      publishTransforms();
      publishCharacters();
      if (closing || isDisposed()) return;
      updateTriggerPoses();
      snapshotAndDispatchContacts();
      if (gameplayCallbackFailure != null) {
        Throwable failure = gameplayCallbackFailure;
        gameplayCallbackFailure = null;
        if (failure instanceof RuntimeException e) throw e;
        if (failure instanceof Error e) throw e;
        throw new RuntimeException(failure);
      }
      if (closing) return;
    }
    if (accumulator + 1e-8 >= FIXED_STEP) {
      discardedTimeSeconds += accumulator;
      accumulator = 0;
    }
  }

  /** Synchronizes component lifecycle without advancing simulation time. */
  public void refresh() {
    requireWorld();
    if (stepping) throw new IllegalStateException("Cannot refresh while stepping");
    synchronize();
  }

  private void runBeforeFixedStepPreparations() {
    requireOwnerThread();
    if (deliveringBeforeFixedStep)
      throw new IllegalStateException("Recursive fixed-step preparation");
    deliveringBeforeFixedStep = true;
    try {
      for (PreparationHook hook : List.copyOf(beforeFixedStep)) {
        if (hook.closed) continue;
        if (closing || isDisposed()) return;
        hook.preparation.run();
      }
    } finally {
      deliveringBeforeFixedStep = false;
    }
  }

  private void requireOwnerThread() {
    if (ownerThread != null && Thread.currentThread() != ownerThread)
      throw new IllegalStateException("Physics3D must be used on its owning Game thread");
  }

  private void synchronize() {
    if (evaluatingCharacterReadiness) return;
    requireWorld();
    Set<GameObject> candidates = new LinkedHashSet<>(getGame().getObjectsWith(RigidBody3D.class));
    candidates.addAll(getGame().getObjectsWith(Collider3D.class));
    for (GameObject old : List.copyOf(bindings.keySet()))
      if (old.isDestroyed() || !candidates.contains(old)) removeBinding(old);
    for (GameObject object :
        candidates.stream().sorted(Comparator.comparingInt(o -> o.id)).toList()) {
      Binding old = bindings.get(object);
      List<Collider3D> attachedColliders = object.getComponents(Collider3D.class);
      List<RigidBody3D> attachedRigids = object.getComponents(RigidBody3D.class);
      boolean pendingPhysics =
          attachedColliders.stream().anyMatch(c -> !c.isInitialized())
              || attachedRigids.stream().anyMatch(r -> !r.isInitialized());
      // Pending replacements preserve the graph, not its participation in the solver. Apply
      // lifecycle deactivation before deferring the replacement until Game initializes it.
      if (old != null
          && (!object.isActive() || (old.rigid != null && !old.rigid.isEnabled()))
          && old.inserted) {
        removeParticipation(old);
      }
      // A replacement is not authoritative until Game initializes it at the next lifecycle
      // boundary. Keep the old native graph intact, and do not treat pending owners as conflicts.
      List<StaticCompoundCollider3D> staged =
          attachedColliders.stream()
              .filter(Collider3D::isEnabled)
              .filter(StaticCompoundCollider3D.class::isInstance)
              .map(StaticCompoundCollider3D.class::cast)
              .toList();
      if (old != null && pendingPhysics) continue;
      if (old == null && pendingPhysics && !staged.isEmpty()) continue;
      if (!staged.isEmpty() && staged.stream().anyMatch(c -> !c.publicationRequested())) continue;
      List<Transform3D> transforms =
          object.getComponents(Transform3D.class).stream()
              .filter(Component::isInitialized)
              .toList();
      List<RigidBody3D> rigids = attachedRigids.stream().filter(Component::isInitialized).toList();
      List<Collider3D> colliders =
          attachedColliders.stream().filter(c -> c.isInitialized() && c.isEnabled()).toList();
      if (transforms.isEmpty() && rigids.isEmpty() && colliders.isEmpty()) continue;
      if (object.isDestroyed()) continue;
      if (object.isActive() && transforms.size() != 1)
        throw new IllegalStateException(
            object.name + ": Physics3D components require exactly one initialized Transform3D");
      if (!object.isActive()) {
        if (old != null && old.inserted) {
          removeParticipation(old);
        }
        continue;
      }
      if (colliders.isEmpty()) {
        removeBinding(object);
        continue;
      }
      if (rigids.size() > 1)
        throw new IllegalStateException(object.name + ": only one RigidBody3D is allowed");
      Transform3D transform = transforms.getFirst();
      RigidBody3D rigid = rigids.isEmpty() ? null : rigids.getFirst();
      List<Collider3D> solids = colliders.stream().filter(c -> !c.isTrigger()).toList();
      validateSolidConfiguration(object, solids);
      if (solids.isEmpty() && rigid != null && rigid.getType() == RigidBody3D.Type.DYNAMIC)
        throw new IllegalStateException(object.name + ": dynamic bodies require a solid collider");
      if (object.getComponents().stream()
          .anyMatch(
              c ->
                  c.isInitialized()
                      && c != rigid
                      && !(c instanceof Collider3D)
                      && c instanceof TransformEditPolicy))
        throw new IllegalStateException(
            object.name + ": Physics3D cannot share Transform3D with another movement owner");
      validateTransform(object, transform);
      if (colliders.size() > 64)
        throw new IllegalArgumentException(
            object.name + ": at most 64 collider components are supported per body");
      int primitiveCount = 0;
      for (Collider3D collider : colliders)
        primitiveCount +=
            collider instanceof StaticCompoundCollider3D terrain
                ? terrain.getBoxes().size()
                : collider instanceof CompoundCollider3D compound
                    ? compound.getChildren().size()
                    : 1;
      if (primitiveCount > 4096)
        throw new IllegalArgumentException(
            object.name + ": at most 4096 primitive children are supported per body");
      if (rigid != null
          && rigid.isEnabled()
          && rigid.getType() == RigidBody3D.Type.DYNAMIC
          && rigid.getMass() <= 0)
        throw new IllegalStateException(object.name + ": dynamic body requires positive mass");
      validateCcd(object, rigid, colliders);
      if (old != null) {
        if (old.transform == transform && !samePose(transform, old.lastPosition, old.lastRotation))
          throw new IllegalStateException(
              object.name
                  + ": direct Transform3D writes do not move a bound physics body; use teleport or"
                  + " moveKinematic");
      }
      String signature = signature(rigid, colliders);
      if (old == null
          || old.transform != transform
          || !old.signature.equals(signature)
          || old.rigid != rigid
          || !old.colliders.equals(colliders)) {
        Binding candidate = createBinding(object, transform, rigid, colliders, old);
        if (old != null && old.transform != transform) {
          transform.position.set(candidate.lastPosition);
          transform.rotation.set(candidate.lastRotation);
        }
        try {
          if (object.isActive() && (rigid == null || rigid.isEnabled())) insert(candidate);
        } catch (RuntimeException | Error failure) {
          disposeBinding(candidate);
          throw failure;
        }
        bindings.put(object, candidate);
        if (old != null) disposeBinding(old);
        old = candidate;
      }
      boolean active = object.isActive() && (rigid == null || rigid.isEnabled());
      if (old.inserted && !active) {
        removeParticipation(old);
      } else if (!old.inserted && active) insert(old);
      syncPose(old);
    }
    synchronizeCharacters();
  }

  private void synchronizeCharacters() {
    Set<GameObject> owners =
        new LinkedHashSet<>(getGame().getObjectsWith(BulletCharacterController3D.class));
    for (GameObject owner : List.copyOf(characters.keySet()))
      if (owner.isDestroyed() || !owners.contains(owner)) removeCharacter(owner);
    for (GameObject owner : owners.stream().sorted(Comparator.comparingInt(o -> o.id)).toList()) {
      BulletCharacterController3D component = owner.getComponent(BulletCharacterController3D.class);
      CharacterBinding old = characters.get(owner);
      if (component == null || !component.isInitialized()) continue;
      if (!component.isEnabled() || !owner.isActive()) {
        if (old != null) removeCharacterParticipation(old);
        continue;
      }
      boolean pendingPhysics =
          owner.getComponents(Collider3D.class).stream().anyMatch(c -> !c.isInitialized())
              || owner.getComponents(RigidBody3D.class).stream().anyMatch(r -> !r.isInitialized());
      if (pendingPhysics) continue;
      if (owner.getComponents(BulletCharacterController3D.class).size() != 1)
        throw new IllegalStateException(owner.name + ": only one Bullet character is allowed");
      List<Transform3D> transforms =
          owner.getComponents(Transform3D.class).stream().filter(Component::isInitialized).toList();
      if (transforms.size() != 1)
        throw new IllegalStateException(owner.name + ": Bullet character requires one Transform3D");
      if (owner.getComponents(Collider3D.class).stream().anyMatch(Component::isInitialized)
          || owner.getComponents(RigidBody3D.class).stream().anyMatch(Component::isInitialized))
        throw new IllegalStateException(
            owner.name + ": character cannot share rigid-body/collider ownership");
      for (Component candidate : owner.getComponents())
        if (candidate != component
            && candidate.isInitialized()
            && candidate instanceof TransformEditPolicy)
          throw new IllegalStateException(
              owner.name + ": character conflicts with another Transform3D movement owner");
      Transform3D transform = transforms.getFirst();
      validateTransform(owner, transform);
      if (Math.abs(transform.rotation.x) > 1e-5f
          || Math.abs(transform.rotation.y) > 1e-5f
          || Math.abs(transform.rotation.z) > 1e-5f
          || Math.abs(Math.abs(transform.rotation.w) - 1f) > 1e-5f)
        throw new IllegalStateException(
            owner.name + ": Bullet character Transform3D rotation must remain identity");
      if (old != null && old.transform != transform)
        throw new IllegalStateException(
            owner.name + ": character Transform3D cannot be replaced after binding");
      if (old != null && !old.lastFeet.epsilonEquals(transform.position, 1e-5f))
        throw new IllegalStateException(
            owner.name + ": direct Transform3D writes do not move a Bullet character; use warp");
      if (Math.abs(transform.rotation.x) > 1e-5f
          || Math.abs(transform.rotation.y) > 1e-5f
          || Math.abs(transform.rotation.z) > 1e-5f
          || Math.abs(Math.abs(transform.rotation.w) - 1f) > 1e-5f)
        throw new IllegalStateException(
            owner.name + ": Bullet character Transform3D rotation must remain identity");
      if (old != null && old.component != component) {
        removeCharacter(owner);
        old = null;
      }
      if (old == null) {
        // A not-yet-published terrain spawn stays absent from Bullet until the full capsule
        // readiness envelope becomes current; this is a normal pending bridge state.
        if (!characterReadyEnvelope(component, transform.position, transform.position)) {
          if (closing || isDisposed()) return;
          continue;
        }
        if (closing || isDisposed() || !characterLive(component)) return;
        CharacterBinding made = createCharacter(owner, transform, component);
        characters.put(owner, made);
        component.setBoundToPhysics(true);
      } else if (!old.ghostInserted && !old.actionInserted) {
        // Re-enable starts from the published feet pose with no stale native vertical motion.
        Vector3 centre = transform.position.cpy().add(0, component.totalHeight() * .5f, 0);
        if (!requireCharacterReady(component, transform.position, transform.position)) {
          if (closing || isDisposed()) return;
          continue;
        }
        validateCharacterNativeDomain(component, transform.position, transform.position);
        characterDisplacement(component);
        rejectDeepCharacterOverlap(component, owner, centre);
        old.action.reset(world);
        old.action.warp(centre);
        old.ghost.setWorldTransform(new Matrix4().setToTranslation(centre));
        old.action.setWalkDirection(Vector3.Zero);
        ensureGhostPairCallback();
        boolean ghostAdded = false, actionAdded = false;
        try {
          world.addCollisionObject(
              old.ghost, component.collisionGroup(), component.collisionMask());
          ghostAdded = old.ghostInserted = true;
          nativeEntries.put(
              btCollisionObject.getCPtr(old.ghost),
              new NativeEntry(
                  null,
                  old,
                  owner,
                  null,
                  false,
                  component.collisionGroup(),
                  component.collisionMask()));
          world.addAction(old.action);
          actionAdded = old.actionInserted = true;
          old.previousCentre.set(centre);
          old.previousGrounded = false;
          component.clearInput();
          component.setBoundToPhysics(true);
        } catch (RuntimeException | Error failure) {
          if (actionAdded) {
            try {
              world.removeAction(old.action);
            } catch (RuntimeException | Error e) {
              failure.addSuppressed(e);
            }
            old.actionInserted = false;
          }
          if (ghostAdded) {
            try {
              world.removeCollisionObject(old.ghost);
            } catch (RuntimeException | Error e) {
              failure.addSuppressed(e);
            }
            old.ghostInserted = false;
            nativeEntries.remove(btCollisionObject.getCPtr(old.ghost));
          }
          throw failure;
        }
      }
    }
  }

  private CharacterBinding createCharacter(
      GameObject owner, Transform3D transform, BulletCharacterController3D component) {
    if (Math.abs(gravity.x) > 1e-6f || Math.abs(gravity.z) > 1e-6f || gravity.y > 0)
      throw new IllegalStateException(
          "Y-up Bullet characters require downward Y-only world gravity");
    Vector3 feet = transform.position.cpy();
    validateCharacterNativeDomain(component, feet, feet);
    characterDisplacement(component);
    rejectDeepCharacterOverlap(
        component, owner, feet.cpy().add(0, component.totalHeight() * .5f, 0));
    ensureGhostPairCallback();
    btCapsuleShape capsule = null;
    btPairCachingGhostObject ghost = null;
    btKinematicCharacterController action = null;
    boolean ghostInserted = false, actionInserted = false;
    try {
      capsule =
          new btCapsuleShape(component.radius(), component.totalHeight() - 2 * component.radius());
      allocation("character-capsule");
      ghost = new btPairCachingGhostObject();
      allocation("character-ghost");
      Vector3 centre = feet.cpy().add(0, component.totalHeight() * .5f, 0);
      ghost.setCollisionShape(capsule);
      ghost.setWorldTransform(new Matrix4().setToTranslation(centre));
      ghost.setCollisionFlags(btCollisionObject.CollisionFlags.CF_CHARACTER_OBJECT);
      action =
          new btKinematicCharacterController(
              ghost, capsule, component.stepHeight(), new Vector3(0, 1, 0));
      allocation("character-action");
      action.setUp(new Vector3(0, 1, 0));
      action.setGravity(new Vector3(0, gravity.y, 0));
      action.setFallSpeed(component.maxFallSpeed());
      action.setJumpSpeed(component.jumpSpeed());
      action.setStepHeight(component.stepHeight());
      action.setMaxSlope(component.maxSlopeRadiansValue());
      action.setMaxPenetrationDepth(.2f);
      action.setUseGhostSweepTest(true);
      // KCC up-axis setup may adjust the ghost basis. This engine uses an upright Y capsule;
      // restore its canonical identity rotation after all native controller configuration.
      action.warp(centre);
      ghost.setWorldTransform(new Matrix4().setToTranslation(centre));
      failureProbe.allocation("character-configured");
      world.addCollisionObject(ghost, component.collisionGroup(), component.collisionMask());
      ghostInserted = true;
      world.updateSingleAabb(ghost);
      world.addAction(action);
      actionInserted = true;
      failureProbe.allocation("character-inserted");
      CharacterBinding binding =
          new CharacterBinding(owner, transform, component, capsule, ghost, action);
      binding.ghostInserted = true;
      binding.actionInserted = true;
      nativeEntries.put(
          btCollisionObject.getCPtr(ghost),
          new NativeEntry(
              null,
              binding,
              owner,
              null,
              false,
              component.collisionGroup(),
              component.collisionMask()));
      return binding;
    } catch (RuntimeException | Error failure) {
      if (actionInserted)
        try {
          world.removeAction(action);
        } catch (RuntimeException | Error e) {
          failure.addSuppressed(e);
        }
      if (ghostInserted)
        try {
          world.removeCollisionObject(ghost);
        } catch (RuntimeException | Error e) {
          failure.addSuppressed(e);
        }
      if (ghost != null) nativeEntries.remove(btCollisionObject.getCPtr(ghost));
      if (action != null) safeDisposeTracked(action, failure);
      if (ghost != null) safeDisposeTracked(ghost, failure);
      if (capsule != null) safeDisposeTracked(capsule, failure);
      releaseGhostPairCallbackIfUnused(failure);
      throw failure;
    }
  }

  private void ensureGhostPairCallback() {
    if (ghostPairCallback != null) return;
    btGhostPairCallback callback = new btGhostPairCallback();
    try {
      allocation("ghost-pair-callback");
      broadphase.getOverlappingPairCache().setInternalGhostPairCallback(callback);
      ghostPairCallback = callback;
    } catch (RuntimeException | Error failure) {
      safeDisposeTracked(callback, failure);
      throw failure;
    }
  }

  private void prepareCharacterStep() {
    for (CharacterBinding b : List.copyOf(characters.values())) {
      if (!b.actionInserted || !b.ghostInserted || !characterLive(b.component)) continue;
      BulletCharacterController3D c = b.component;
      long jumpRequestRevision = c.jumpRequestRevision();
      Matrix4 oldPose = new Matrix4();
      b.ghost.getWorldTransform(oldPose);
      oldPose.getTranslation(b.previousCentre);
      Vector3 currentFeet = b.previousCentre.cpy().add(0, -c.totalHeight() * .5f, 0);
      Vector3 requestedWarp = c.pendingWarpFeet();
      Vector3 startFeet = requestedWarp == null ? currentFeet : requestedWarp;
      Vector3 displacement = characterDisplacement(c);
      validateCharacterNativeDomain(c, startFeet, startFeet.cpy().add(displacement));
      if (requestedWarp != null) {
        if (!requireCharacterReady(c, requestedWarp, requestedWarp)) {
          if (closing || isDisposed()) return;
          continue;
        }
        // The readiness policy is arbitrary game code and may change movement settings. Recheck
        // the complete native domain before consuming the queued warp or mutating its pose.
        Vector3 currentDisplacement = characterDisplacement(c);
        validateCharacterNativeDomain(
            c, requestedWarp, requestedWarp.cpy().add(currentDisplacement));
        Vector3 queuedWarp = c.pendingWarpFeet();
        if (queuedWarp == null) continue;
        validateCharacterNativeDomain(c, queuedWarp, queuedWarp.cpy().add(currentDisplacement));
        if (!queuedWarp.epsilonEquals(requestedWarp, 0f)) {
          if (!requireCharacterReady(c, queuedWarp, queuedWarp)) {
            if (closing || isDisposed()) return;
            continue;
          }
          Vector3 afterPolicyWarp = c.pendingWarpFeet();
          if (afterPolicyWarp == null) continue;
          validateCharacterNativeDomain(
              c, afterPolicyWarp, afterPolicyWarp.cpy().add(currentDisplacement));
          if (!afterPolicyWarp.epsilonEquals(queuedWarp, 0f)) continue;
          queuedWarp = afterPolicyWarp;
        }
        Vector3 warp = c.takeWarpFeet();
        if (warp == null) continue;
        Vector3 centre = warp.cpy().add(0, c.totalHeight() * .5f, 0);
        rejectDeepCharacterOverlap(c, b.object, centre);
        b.action.reset(world);
        b.action.warp(centre);
        b.ghost.setWorldTransform(new Matrix4().setToTranslation(centre));
        world.updateSingleAabb(b.ghost);
        b.transform.position.set(warp);
        b.previousCentre.set(centre);
        b.previousGrounded = b.action.onGround();
        currentFeet.set(warp);
      }
      b.previousGrounded = b.action.onGround();
      Vector3 feet = b.previousCentre.cpy().add(0, -c.totalHeight() * .5f, 0);
      Vector3 projected = feet.cpy().add(displacement);
      boolean admitted = characterReadyEnvelope(c, feet, projected);
      if (closing || isDisposed()) return;
      if (!characterLive(c)) continue;
      // Revalidate after policy code and before consuming input or submitting anything to Bullet.
      Vector3 currentDisplacement = characterDisplacement(c);
      validateCharacterNativeDomain(c, feet, feet.cpy().add(currentDisplacement));
      if (!admitted) {
        b.action.setWalkDirection(Vector3.Zero);
        world.removeAction(b.action);
        b.actionInserted = false;
        // A removed action must not retain stale gravity/jump state while its position is frozen.
        b.action.reset(world);
        b.action.warp(b.previousCentre);
        b.ghost.setWorldTransform(new Matrix4().setToTranslation(b.previousCentre));
        world.updateSingleAabb(b.ghost);
        b.blockedThisStep = true;
        b.jumped = false;
        continue;
      }
      boolean jumpEdge = c.consumeJumpRequest(jumpRequestRevision);
      if (jumpEdge) c.bufferedJumpTicks(c.jumpBufferTicks());
      b.action.setWalkDirection(displacement);
      boolean jumpedNow = false;
      if (admitted && (jumpEdge || c.bufferedJumpTicks() > 0) && b.action.canJump()) {
        b.action.jump(new Vector3(0, c.jumpSpeed(), 0));
        c.bufferedJumpTicks(0);
        jumpedNow = true;
      } else if (c.bufferedJumpTicks() > 0) c.bufferedJumpTicks(c.bufferedJumpTicks() - 1);
      b.jumped = jumpedNow;
    }
  }

  /** Readiness policies can change component lifetimes; reconcile their native participation. */
  private void drainInvalidatedCharactersBeforeStep() {
    if (closing || world == null) return;
    for (CharacterBinding b : List.copyOf(characters.values())) {
      if (characterLive(b.component)) continue;
      GameObject owner = b.object;
      if (!owner.isDestroyed()
          && owner.containsComponent(b.component)
          && b.component.isInitialized()) removeCharacterParticipation(b);
      else removeCharacter(owner);
    }
  }

  private void publishCharacters() {
    record NativePose(CharacterBinding binding, Vector3 centre, Vector3 feet, boolean grounded) {}
    List<NativePose> readbacks = new ArrayList<>();
    List<Map.Entry<BulletCharacterController3D, BulletCharacterController3D.State>> notifications =
        new ArrayList<>();
    for (CharacterBinding b : List.copyOf(characters.values())) {
      if (!b.ghostInserted || b.object.isDestroyed()) continue;
      Matrix4 pose = new Matrix4();
      b.ghost.getWorldTransform(pose);
      Vector3 centre = pose.getTranslation(new Vector3());
      Vector3 feet = centre.cpy().add(0, -b.component.totalHeight() * .5f, 0);
      readbacks.add(new NativePose(b, centre, feet, b.action.onGround()));
    }
    // Do not invoke an arbitrary readiness policy until every native ghost/action has been read.
    for (NativePose readback : readbacks) {
      if (closing || isDisposed()) return;
      CharacterBinding b = readback.binding();
      if (!characterLive(b.component)) continue;
      Vector3 centre = readback.centre();
      Vector3 feet = readback.feet();
      boolean admitted = b.blockedThisStep || characterReadyEnvelope(b.component, feet, feet);
      if (closing || isDisposed()) return;
      if (!characterLive(b.component)) continue;
      if (b.blockedThisStep || !admitted) {
        centre.set(b.previousCentre);
        feet.set(b.transform.position);
      }
      if (!b.blockedThisStep && !admitted) {
        b.action.reset(world);
        Vector3 safeCentre = b.previousCentre.cpy();
        b.action.warp(safeCentre);
        b.ghost.setWorldTransform(new Matrix4().setToTranslation(safeCentre));
        world.updateSingleAabb(b.ghost);
        centre.set(safeCentre);
        feet.set(safeCentre.x, safeCentre.y - b.component.totalHeight() * .5f, safeCentre.z);
      }
      Vector3 velocity =
          feet.cpy()
              .sub(b.previousCentre.cpy().add(0, -b.component.totalHeight() * .5f, 0))
              .scl(1f / FIXED_STEP);
      boolean grounded = b.blockedThisStep ? false : readback.grounded();
      boolean landed = grounded && !b.previousGrounded;
      if (b.blockedThisStep) {
        b.blockedThisStep = false;
        if (closing || isDisposed() || !characterLive(b.component)) return;
        world.addAction(b.action);
        b.actionInserted = true;
      }
      b.transform.position.set(feet);
      b.lastFeet.set(feet);
      b.previousCentre.set(centre);
      b.previousGrounded = grounded;
      notifications.add(
          Map.entry(
              b.component,
              b.component.updateState(
                  feet,
                  velocity,
                  b.ghostInserted && b.actionInserted,
                  grounded,
                  b.jumped,
                  landed)));
    }
    if (closing || isDisposed()) return;
    dispatching = true;
    try {
      for (Map.Entry<BulletCharacterController3D, BulletCharacterController3D.State> n :
          notifications) {
        if (closing || isDisposed()) break;
        if (!characterLive(n.getKey())) continue;
        try {
          n.getKey().notifyState(n.getValue());
        } catch (RuntimeException | Error failure) {
          gameplayCallbackFailure = combine(gameplayCallbackFailure, failure);
        }
      }
    } finally {
      dispatching = false;
    }
  }

  private boolean characterReadyEnvelope(
      BulletCharacterController3D c, Vector3 fromFeet, Vector3 toFeet) {
    validateCharacterNativeDomain(c, fromFeet, toFeet);
    CharacterReadiness3D policy = c.readiness();
    float r = c.radius(), h = c.totalHeight() + c.stepHeight() + .2f;
    float vertical = Math.max(c.jumpSpeed(), c.maxFallSpeed()) * FIXED_STEP;
    if (policy == null) return !closing && !isDisposed() && characterLive(c);
    boolean ready;
    evaluatingCharacterReadiness = true;
    try {
      ready =
          policy.isReady(
              Math.min(fromFeet.x, toFeet.x) - r,
              Math.min(fromFeet.y, toFeet.y) - vertical - .2f,
              Math.min(fromFeet.z, toFeet.z) - r,
              Math.max(fromFeet.x, toFeet.x) + r,
              Math.max(fromFeet.y, toFeet.y) + h + vertical,
              Math.max(fromFeet.z, toFeet.z) + r);
    } finally {
      evaluatingCharacterReadiness = false;
    }
    // Policy callbacks may close the Game, replace/disable this exact component, or query physics.
    // Queries see the committed world without recursively synchronizing during this callback.
    return ready && !closing && !isDisposed() && characterLive(c);
  }

  private static Vector3 characterDisplacement(BulletCharacterController3D c) {
    double speed = (double) c.speed() * (c.sprinting() ? c.sprintMultiplier() : 1d);
    requireCharacterNativeRange(speed, "effective horizontal speed");
    double yaw = c.yawRadians();
    double right = c.moveX(), forward = c.moveZ();
    double cos = Math.cos(yaw), sin = Math.sin(yaw);
    double dx = (right * cos - forward * sin) * speed * FIXED_STEP;
    double dz = (-right * sin - forward * cos) * speed * FIXED_STEP;
    double length = Math.hypot(dx, dz);
    requireCharacterNativeRange(length, "fixed-tick displacement");
    return new Vector3((float) dx, 0, (float) dz);
  }

  /**
   * Validates all derived KCC values before a character allocation, overlap query, pose write, or
   * command consumption. Bullet's vector length operations square floats, so this deliberately uses
   * a smaller domain than finite float coordinates alone would permit.
   */
  private void validateCharacterNativeDomain(
      BulletCharacterController3D c, Vector3 fromFeet, Vector3 toFeet) {
    finite(fromFeet);
    finite(toFeet);
    double radius = c.radius();
    double height = c.totalHeight();
    double step = c.stepHeight();
    double jump = c.jumpSpeed();
    double fall = c.maxFallSpeed();
    double gravityY = gravity.y;
    requireCharacterNativeRange(radius, "capsule radius");
    requireCharacterNativeRange(height, "capsule height");
    requireCharacterNativeRange(step, "step height");
    requireCharacterNativeRange(height - 2d * radius, "capsule cylinder height");
    requireCharacterNativeRange(jump, "jump speed");
    requireCharacterNativeRange(fall, "maximum fall speed");
    requireCharacterNativeRange(gravityY, "character gravity");
    requireCharacterNativeRange(
        (double) c.speed() * (c.sprinting() ? c.sprintMultiplier() : 1d),
        "effective horizontal speed");

    double verticalTravel = Math.max(jump, fall) * FIXED_STEP;
    double minX = Math.min(fromFeet.x, toFeet.x) - radius;
    double maxX = Math.max(fromFeet.x, toFeet.x) + radius;
    double minY = Math.min(fromFeet.y, toFeet.y) - verticalTravel - .2d;
    double maxY = Math.max(fromFeet.y, toFeet.y) + height + step + verticalTravel + .2d;
    double minZ = Math.min(fromFeet.z, toFeet.z) - radius;
    double maxZ = Math.max(fromFeet.z, toFeet.z) + radius;
    requireCharacterNativeRange(minX, "swept capsule minimum X");
    requireCharacterNativeRange(maxX, "swept capsule maximum X");
    requireCharacterNativeRange(minY, "swept capsule minimum Y");
    requireCharacterNativeRange(maxY, "swept capsule maximum Y");
    requireCharacterNativeRange(minZ, "swept capsule minimum Z");
    requireCharacterNativeRange(maxZ, "swept capsule maximum Z");
    double dx = (double) toFeet.x - fromFeet.x;
    double dy = (double) toFeet.y - fromFeet.y;
    double dz = (double) toFeet.z - fromFeet.z;
    requireCharacterNativeRange(Math.sqrt(dx * dx + dy * dy + dz * dz), "swept capsule segment");
  }

  private static void requireCharacterNativeRange(double value, String field) {
    if (!Double.isFinite(value) || Math.abs(value) > CHARACTER_NATIVE_LIMIT)
      throw new IllegalArgumentException(
          "Character " + field + " exceeds Bullet's supported finite numeric range");
  }

  private boolean requireCharacterReady(BulletCharacterController3D c, Vector3 from, Vector3 to) {
    boolean ready = characterReadyEnvelope(c, from, to);
    if (closing || isDisposed() || !characterLive(c)) return false;
    if (!ready)
      throw new IllegalStateException(
          "Character capsule position is outside loaded collision-ready space");
    return true;
  }

  private void rejectDeepCharacterOverlap(
      BulletCharacterController3D component, GameObject owner, Vector3 centre) {
    btCapsuleShape shape = null;
    btCollisionObject probe = null;
    Throwable failure = null;
    try {
      shape =
          new btCapsuleShape(component.radius(), component.totalHeight() - 2 * component.radius());
      allocation("character-overlap-shape");
      probe = new btCollisionObject();
      allocation("character-overlap-object");
      probe.setCollisionShape(shape);
      probe.setWorldTransform(new Matrix4().setToTranslation(centre));
      final boolean[] deep = {false};
      ContactResultCallback callback =
          new ContactResultCallback() {
            @Override
            public float addSingleResult(
                btManifoldPoint cp,
                btCollisionObjectWrapper w0,
                int p0,
                int i0,
                btCollisionObjectWrapper w1,
                int p1,
                int i1) {
              try {
                NativeEntry e0 =
                    nativeEntries.get(btCollisionObject.getCPtr(w0.getCollisionObject()));
                NativeEntry e1 =
                    nativeEntries.get(btCollisionObject.getCPtr(w1.getCollisionObject()));
                NativeEntry hit = e0 == null ? e1 : e1 == null ? e0 : null;
                if (hit != null
                    && !hit.trigger
                    && hit.object != owner
                    && queryMutuallyFiltered(
                        new PhysicsFilter3D(
                            component.collisionGroup(), component.collisionMask(), false, owner.id),
                        hit)
                    && cp.getDistance() < -.2f) deep[0] = true;
              } catch (RuntimeException | Error callbackFailure) {
                queryCallbackFailure = combine(queryCallbackFailure, callbackFailure);
              }
              return 0;
            }
          };
      try {
        allocation("character-overlap-callback");
        callback.setCollisionFilterGroup(component.collisionGroup());
        callback.setCollisionFilterMask(component.collisionMask());
        world.contactTest(probe, callback);
      } finally {
        queryCallbackFailure = release(callback, queryCallbackFailure);
      }
      rethrowQueryCallbackFailure();
      if (deep[0]) throw new IllegalArgumentException("Character capsule starts deeply overlapped");
    } catch (RuntimeException | Error e) {
      failure = e;
      throw e;
    } finally {
      if (probe != null) failure = release(probe, failure);
      if (shape != null) failure = release(shape, failure);
      if (failure instanceof RuntimeException e) throw e;
      if (failure instanceof Error e) throw e;
    }
  }

  private void removeCharacterParticipation(CharacterBinding b) {
    if (b.actionInserted) {
      world.removeAction(b.action);
      b.actionInserted = false;
    }
    if (b.ghostInserted) {
      world.removeCollisionObject(b.ghost);
      b.ghostInserted = false;
      nativeEntries.remove(btCollisionObject.getCPtr(b.ghost));
    }
    b.component.setBoundToPhysics(false);
  }

  private void removeCharacter(GameObject owner) {
    CharacterBinding b = characters.get(owner);
    if (b == null) return;
    for (CharacterTrigger state : b.activeTriggers.values()) {
      Vector3 pa = state.triggerIsA ? state.pointA : state.pointB;
      Vector3 pb = state.triggerIsA ? state.pointB : state.pointA;
      Vector3 normal = state.triggerIsA ? state.normalOnB : state.normalOnB.cpy().scl(-1);
      Contact3D event =
          new Contact3D(
              state.owner.object,
              b.object,
              state.trigger.collider,
              null,
              Contact3D.Phase.EXIT,
              fixedTick,
              true,
              state.owner.lifetime,
              b.lifetime,
              pa,
              pb,
              normal);
      pendingCharacterExits.add(new PendingCharacterExit(b.component, event));
    }
    characters.remove(owner);
    Throwable failure = null;
    try {
      removeCharacterParticipation(b);
    } catch (RuntimeException | Error e) {
      failure = e;
    }
    failure = release(b.action, failure);
    failure = release(b.ghost, failure);
    failure = release(b.capsule, failure);
    releaseGhostPairCallbackIfUnused(failure);
    if (failure instanceof RuntimeException e) throw e;
    if (failure instanceof Error e) throw e;
  }

  private void releaseGhostPairCallbackIfUnused(Throwable prior) {
    // Keep the world-owned callback installed until world teardown. A disabled allocated player
    // may be re-enabled after another player has been removed.
  }

  private Binding createBinding(
      GameObject object,
      Transform3D transform,
      RigidBody3D rigid,
      List<Collider3D> colliders,
      Binding old) {
    ShapeBuilder shapes = null;
    btDefaultMotionState motion = null;
    btRigidBody body = null;
    List<TriggerBinding> triggers = new ArrayList<>();
    try {
      Matrix4 pose = new Matrix4();
      if (old != null && old.body != null) old.body.getWorldTransform(pose);
      else pose.set(transform.position, normalizedCopy(transform.rotation), Vector3.One);
      Vector3 worldPosition = pose.getTranslation(new Vector3());
      List<Collider3D> solids = colliders.stream().filter(c -> !c.isTrigger()).toList();
      boolean direct = rigid != null && rigid.ccdThreshold() > 0;
      boolean hasSolidGeometry =
          solids.stream()
              .anyMatch(
                  c -> !(c instanceof StaticCompoundCollider3D terrain) || !terrain.isEmpty());
      if (hasSolidGeometry) {
        shapes =
            new ShapeBuilder(
                stage -> {
                  nativeAllocations++;
                  failureProbe.allocation(stage);
                },
                direct,
                worldPosition);
        if (shapes.compound != null) nativeAllocations++;
        failureProbe.allocation("shape");
        for (Collider3D c : solids) c.addTo(shapes);
      }
      float mass = rigid == null ? 0 : rigid.massForNative();
      Vector3 inertia = new Vector3();
      if (mass > 0 && shapes != null) {
        float inverseMass = 1f / mass;
        if (!Float.isFinite(inverseMass) || inverseMass <= 0)
          throw new IllegalArgumentException(
              object.name + ": dynamic mass reciprocal is not finite");
        shapes.root().calculateLocalInertia(mass, inertia);
        if (!finitePositive(inertia)
            || !finitePositive(new Vector3(1f / inertia.x, 1f / inertia.y, 1f / inertia.z)))
          throw new IllegalArgumentException(
              object.name + ": dynamic collider produces nonrepresentable inertia");
      }
      if (shapes != null) {
        motion = new btDefaultMotionState(pose);
        allocation("motion-state");
        body = new btRigidBody(mass, motion, shapes.root(), inertia);
        allocation("rigid-body");
        body.setFriction(solids.getFirst().getFriction());
        body.setRestitution(solids.getFirst().getRestitution());
      }
      if (rigid != null && body != null) {
        body.setDamping(rigid.linearDamping(), rigid.angularDamping());
        body.setCcdMotionThreshold(rigid.ccdThreshold());
        body.setCcdSweptSphereRadius(rigid.ccdRadius());
        if (rigid.getType() == RigidBody3D.Type.KINEMATIC) {
          body.setCollisionFlags(
              body.getCollisionFlags() & ~btCollisionObject.CollisionFlags.CF_STATIC_OBJECT
                  | btCollisionObject.CollisionFlags.CF_KINEMATIC_OBJECT);
          body.setActivationState(4); // DISABLE_DEACTIVATION
        }
      }
      if (old != null && old.body != null && body != null) {
        body.setLinearVelocity(old.body.getLinearVelocity());
        body.setAngularVelocity(old.body.getAngularVelocity());
      } else if (rigid != null && body != null) {
        body.setLinearVelocity(rigid.getLinearVelocity());
        body.setAngularVelocity(rigid.getAngularVelocity());
      }
      for (Collider3D collider : colliders) {
        if (!collider.isTrigger()) continue;
        ShapeBuilder triggerShape =
            new ShapeBuilder(
                stage -> {
                  nativeAllocations++;
                  failureProbe.allocation("trigger-" + stage);
                },
                false,
                worldPosition);
        if (triggerShape.compound != null) nativeAllocations++;
        btCollisionObject trigger = null;
        try {
          collider.addTo(triggerShape);
          trigger = new btCollisionObject();
          allocation("trigger-object");
          trigger.setCollisionShape(triggerShape.root());
          trigger.setCollisionFlags(
              trigger.getCollisionFlags()
                  | btCollisionObject.CollisionFlags.CF_NO_CONTACT_RESPONSE);
          trigger.setFriction(collider.getFriction());
          trigger.setRestitution(collider.getRestitution());
          trigger.setWorldTransform(pose);
          failureProbe.allocation("trigger-complete");
          triggers.add(new TriggerBinding(collider, trigger, triggerShape, nextBindingLifetime++));
          trigger = null;
        } catch (RuntimeException | Error failure) {
          if (trigger != null) safeDisposeTracked(trigger, failure);
          if (triggerShape.compound != null) safeDisposeTracked(triggerShape.compound, failure);
          for (btCollisionShape child : triggerShape.children) safeDisposeTracked(child, failure);
          throw failure;
        }
      }
      failureProbe.allocation("complete");
      return new Binding(
          object,
          transform,
          rigid,
          List.copyOf(colliders),
          body,
          motion,
          shapes,
          signature(rigid, colliders),
          solids,
          triggers,
          nextBindingLifetime++,
          pose);
    } catch (RuntimeException | Error e) {
      for (TriggerBinding trigger : triggers) disposeTrigger(trigger, e);
      if (body != null) safeDisposeTracked(body, e);
      if (motion != null) safeDisposeTracked(motion, e);
      if (shapes != null) {
        if (shapes.compound != null) safeDisposeTracked(shapes.compound, e);
        else if (shapes.root() != null) safeDisposeTracked(shapes.root(), e);
        for (btCollisionShape child : shapes.children) safeDisposeTracked(child, e);
      }
      throw e;
    }
  }

  private void syncPose(Binding b) {
    RigidBody3D rigid = b.rigid;
    if (b.body == null) {
      Matrix4 m =
          new Matrix4(b.transform.position, normalizedCopy(b.transform.rotation), Vector3.One);
      updateTriggerPose(b, m);
      m.getTranslation(b.lastPosition);
      m.getRotation(b.lastRotation, true);
      return;
    }
    if (rigid == null || rigid.getType() == RigidBody3D.Type.STATIC) {
      if (!samePose(b.transform, b.lastPosition, b.lastRotation)) {
        Matrix4 m =
            new Matrix4(b.transform.position, normalizedCopy(b.transform.rotation), Vector3.One);
        b.body.setWorldTransform(m);
        b.motion.setWorldTransform(m);
        b.body.setInterpolationWorldTransform(m);
        if (b.inserted) world.updateSingleAabb(b.body);
        updateTriggerPose(b, m);
        m.getTranslation(b.lastPosition);
        m.getRotation(b.lastRotation, true);
      }
    }
  }

  private void applyCommands() {
    for (Binding b : List.copyOf(bindings.values())) {
      if (!b.inserted || b.object.isDestroyed() || !b.object.isActive()) continue;
      RigidBody3D r = b.rigid;
      if (b.body == null) {
        applyBodylessPoseCommands(b, r);
        continue;
      }
      if (r == null) continue;
      RigidBody3D.Pose teleport = r.takeTeleportTarget();
      if (teleport != null) {
        Matrix4 pose = new Matrix4(teleport.position(), teleport.rotation(), Vector3.One);
        b.body.setCenterOfMassTransform(pose);
        b.body.setInterpolationWorldTransform(pose);
        b.motion.setWorldTransform(pose);
        if (r.clearVelocityOnTeleport()) {
          b.body.setLinearVelocity(Vector3.Zero);
          b.body.setAngularVelocity(Vector3.Zero);
          b.body.setInterpolationLinearVelocity(Vector3.Zero);
          b.body.setInterpolationAngularVelocity(Vector3.Zero);
          b.body.clearForces();
          r.publish(Vector3.Zero, Vector3.Zero);
        }
        b.body.activate(true);
        world.updateSingleAabb(b.body);
        pose.getTranslation(b.transform.position);
        pose.getRotation(b.transform.rotation, true);
        b.lastPosition.set(b.transform.position);
        b.lastRotation.set(b.transform.rotation);
      }
      RigidBody3D.Pose target = r.takeKinematicTarget();
      if (target != null && r.getType() == RigidBody3D.Type.KINEMATIC) {
        Matrix4 pose = new Matrix4(target.position(), target.rotation(), Vector3.One);
        // Leave the body's current/interpolation transform intact so Bullet derives target
        // velocity.
        b.motion.setWorldTransform(pose);
      }
      Vector3 lv = r.takeLinearVelocity();
      if (lv != null) {
        b.body.setLinearVelocity(lv);
        b.body.activate(true);
      }
      Vector3 av = r.takeAngularVelocity();
      if (av != null) {
        b.body.setAngularVelocity(av);
        b.body.activate(true);
      }
      while (!r.actions().isEmpty()) {
        RigidBody3D.Action a = r.actions().removeFirst();
        if (a.impulse()) b.body.applyCentralImpulse(a.value());
        else b.body.applyCentralForce(a.value());
        b.body.activate(true);
      }
    }
  }

  /** Static/kinematic sensor-only bindings publish poses at fixed boundaries without a body. */
  private void applyBodylessPoseCommands(Binding b, RigidBody3D rigid) {
    if (rigid == null) return;
    RigidBody3D.Pose teleport = rigid.takeTeleportTarget();
    if (teleport != null) applyBodylessPose(b, teleport);
    RigidBody3D.Pose target = rigid.takeKinematicTarget();
    if (target != null && rigid.getType() == RigidBody3D.Type.KINEMATIC)
      applyBodylessPose(b, target);

    // Sensor-only bindings have no physical response. Consume queued velocity/force commands at
    // their fixed boundary and publish zero velocities instead of leaving stale requested state.
    rigid.takeLinearVelocity();
    rigid.takeAngularVelocity();
    rigid.actions().clear();
    rigid.publish(Vector3.Zero, Vector3.Zero);
  }

  private void applyBodylessPose(Binding b, RigidBody3D.Pose pose) {
    b.transform.position.set(pose.position());
    b.transform.rotation.set(pose.rotation());
    Matrix4 matrix = new Matrix4(pose.position(), pose.rotation(), Vector3.One);
    matrix.getTranslation(b.lastPosition);
    matrix.getRotation(b.lastRotation, true);
    updateTriggerPose(b, matrix);
  }

  private void publishTransforms() {
    for (Binding b : bindings.values()) {
      if (!b.inserted
          || b.body == null
          || b.rigid == null
          || (b.rigid.getType() != RigidBody3D.Type.DYNAMIC
              && b.rigid.getType() != RigidBody3D.Type.KINEMATIC)) continue;
      Matrix4 pose = b.body.getWorldTransform();
      pose.getTranslation(b.transform.position);
      pose.getRotation(b.transform.rotation, true);
      Vector3 linear = b.body.getLinearVelocity().cpy();
      Vector3 angular = b.body.getAngularVelocity().cpy();
      b.rigid.publish(linear, angular);
      b.lastPosition.set(b.transform.position);
      b.lastRotation.set(b.transform.rotation);
      updateTriggerPose(b, pose);
    }
  }

  private void updateTriggerPoses() {
    for (Binding b : List.copyOf(bindings.values()))
      if (b.inserted) {
        Matrix4 pose =
            b.body == null
                ? new Matrix4(
                    b.transform.position, normalizedCopy(b.transform.rotation), Vector3.One)
                : b.body.getWorldTransform();
        updateTriggerPose(b, pose);
      }
  }

  private void updateTriggerPose(Binding b, Matrix4 pose) {
    for (TriggerBinding trigger : b.triggers)
      if (trigger.inserted) {
        trigger.object.setWorldTransform(pose);
        world.updateSingleAabb(trigger.object);
      }
  }

  private void snapshotAndDispatchContacts() {
    if (closing || dispatcher == null) return;
    for (CharacterBinding character : characters.values()) character.currentTriggers.clear();
    TreeMap<PairKey, ContactState> current = new TreeMap<>();
    for (int i = 0; i < dispatcher.getNumManifolds(); i++) {
      btPersistentManifold manifold = dispatcher.getManifoldByIndexInternal(i);
      NativeEntry a = nativeEntries.get(btCollisionObject.getCPtr(manifold.getBody0()));
      NativeEntry b = nativeEntries.get(btCollisionObject.getCPtr(manifold.getBody1()));
      if (a == null
          || b == null
          || a.trigger
          || b.trigger
          || a.binding == null
          || b.binding == null
          || a.binding == b.binding) continue;
      for (int p = 0; p < manifold.getNumContacts(); p++) {
        btManifoldPoint point = manifold.getContactPoint(p);
        if (point.getDistance() > 0) continue; // documented touch threshold is zero world units
        Vector3 pa = new Vector3(), pb = new Vector3(), normal = new Vector3();
        point.getPositionWorldOnA(pa);
        point.getPositionWorldOnB(pb);
        point.getNormalWorldOnB(normal);
        putSolidContact(current, a.binding, b.binding, pa, pb, normal);
      }
    }
    for (Binding binding : List.copyOf(bindings.values()))
      for (TriggerBinding trigger : binding.triggers) {
        if (!trigger.inserted || closing) continue;
        final TriggerBinding sensor = trigger;
        ContactResultCallback callback =
            new ContactResultCallback() {
              @Override
              public float addSingleResult(
                  btManifoldPoint cp,
                  btCollisionObjectWrapper w0,
                  int part0,
                  int index0,
                  btCollisionObjectWrapper w1,
                  int part1,
                  int index1) {
                try {
                  NativeEntry e0 =
                      nativeEntries.get(btCollisionObject.getCPtr(w0.getCollisionObject()));
                  NativeEntry e1 =
                      nativeEntries.get(btCollisionObject.getCPtr(w1.getCollisionObject()));
                  if (e0 == null || e1 == null || e0.object == e1.object) return 0;
                  if (!mutuallyFiltered(sensor.collider, e0, e1)) return 0;
                  if (cp.getDistance() > 0) return 0;
                  if (e0.character != null || e1.character != null) {
                    CharacterBinding character = e0.character != null ? e0.character : e1.character;
                    queueCharacterTrigger(character, binding, sensor, cp, e0, e1);
                    return 0;
                  }
                  Vector3 pa = new Vector3(), pb = new Vector3(), n = new Vector3();
                  cp.getPositionWorldOnA(pa);
                  cp.getPositionWorldOnB(pb);
                  cp.getNormalWorldOnB(n);
                  Binding other = e0.binding == binding ? e1.binding : e0.binding;
                  Collider3D peerCollider = e0.binding == binding ? e1.collider : e0.collider;
                  if (peerCollider == null) peerCollider = other.solidColliders.getFirst();
                  boolean triggerPair = e0.trigger && e1.trigger;
                  long peerTrigger =
                      triggerPair ? triggerLifetime(other, peerCollider) : Long.MAX_VALUE;
                  if (triggerPair && sensor.lifetime > peerTrigger) return 0;
                  putTriggerContact(
                      current,
                      binding,
                      other,
                      sensor,
                      peerCollider,
                      pa,
                      pb,
                      n,
                      e0.binding == binding,
                      triggerPair,
                      peerTrigger);
                } catch (RuntimeException | Error failure) {
                  contactCallbackFailure = combine(contactCallbackFailure, failure);
                }
                return 0;
              }
            };
        nativeAllocations++;
        try {
          failureProbe.allocation("trigger-contact-callback");
          callback.setCollisionFilterGroup(trigger.collider.getCollisionGroup());
          callback.setCollisionFilterMask(trigger.collider.getCollisionMask());
          world.contactTest(trigger.object, callback);
        } catch (RuntimeException | Error failure) {
          contactCallbackFailure = combine(contactCallbackFailure, failure);
        } finally {
          contactCallbackFailure = release(callback, contactCallbackFailure);
        }
      }
    if (contactCallbackFailure != null) {
      gameplayCallbackFailure = combine(gameplayCallbackFailure, contactCallbackFailure);
      contactCallbackFailure = null;
    }
    dispatchCharacterTriggers();
    TreeSet<PairKey> keys = new TreeSet<>();
    keys.addAll(current.keySet());
    keys.addAll(activeContacts.keySet());
    List<Delivery> deliveries = new ArrayList<>();
    for (PairKey key : keys) {
      ContactState now = current.get(key), before = activeContacts.get(key);
      Contact3D.Phase phase =
          now == null
              ? Contact3D.Phase.EXIT
              : before == null ? Contact3D.Phase.ENTER : Contact3D.Phase.STAY;
      ContactState state = now == null ? before : now;
      Contact3D event = state.contact.withPhase(phase, fixedTick);
      deliveries.add(new Delivery(state, event));
    }
    activeContacts.clear();
    pendingCharacterExits.clear();
    activeContacts.putAll(current);
    deliveries.sort(Comparator.comparing((Delivery d) -> d.state.key));
    dispatching = true;
    try {
      for (Delivery d : deliveries) {
        if (closing) break;
        deliver(d.state, d.event);
      }
    } finally {
      dispatching = false;
    }
  }

  private void queueCharacterTrigger(
      CharacterBinding character,
      Binding owner,
      TriggerBinding trigger,
      btManifoldPoint point,
      NativeEntry e0,
      NativeEntry e1) {
    boolean triggerIsA = e0.collider == trigger.collider;
    Vector3 pa = new Vector3(), pb = new Vector3(), normal = new Vector3();
    point.getPositionWorldOnA(pa);
    point.getPositionWorldOnB(pb);
    point.getNormalWorldOnB(normal);
    character.currentTriggers.put(
        trigger.lifetime,
        new CharacterTrigger(trigger.lifetime, owner, trigger, pa, pb, normal, triggerIsA));
  }

  private void dispatchCharacterTriggers() {
    dispatching = true;
    try {
      for (PendingCharacterExit pending : List.copyOf(pendingCharacterExits)) {
        if (closing) break;
        if (receiverLive(pending.event.colliderA()))
          deliverTo(pending.event.colliderA(), pending.event);
        if (characterLive(pending.component)) {
          try {
            pending.component.publishTrigger(pending.event.reversed());
          } catch (RuntimeException | Error failure) {
            gameplayCallbackFailure = combine(gameplayCallbackFailure, failure);
          }
        }
      }
      pendingCharacterExits.clear();
      for (CharacterBinding character : List.copyOf(characters.values())) {
        TreeSet<Long> keys = new TreeSet<>();
        keys.addAll(character.activeTriggers.keySet());
        keys.addAll(character.currentTriggers.keySet());
        for (long key : keys) {
          CharacterTrigger now = character.currentTriggers.get(key);
          CharacterTrigger before = character.activeTriggers.get(key);
          Contact3D.Phase phase =
              now == null
                  ? Contact3D.Phase.EXIT
                  : before == null ? Contact3D.Phase.ENTER : Contact3D.Phase.STAY;
          CharacterTrigger state = now == null ? before : now;
          boolean triggerIsA = state.triggerIsA;
          Vector3 pa = triggerIsA ? state.pointA : state.pointB;
          Vector3 pb = triggerIsA ? state.pointB : state.pointA;
          Vector3 normal = triggerIsA ? state.normalOnB : state.normalOnB.cpy().scl(-1);
          Contact3D event =
              new Contact3D(
                  state.owner.object,
                  character.object,
                  state.trigger.collider,
                  null,
                  phase,
                  fixedTick,
                  true,
                  state.owner.lifetime,
                  character.lifetime,
                  pa,
                  pb,
                  normal);
          Collider3D collider = state.trigger.collider;
          if (receiverLive(collider))
            for (Consumer<Contact3D> callback : collider.listenersSnapshot()) {
              if (!receiverLive(collider)) break;
              try {
                callback.accept(event);
              } catch (RuntimeException | Error failure) {
                gameplayCallbackFailure = combine(gameplayCallbackFailure, failure);
              }
            }
          if (characterLive(character.component))
            try {
              character.component.publishTrigger(event.reversed());
            } catch (RuntimeException | Error failure) {
              gameplayCallbackFailure = combine(gameplayCallbackFailure, failure);
            }
        }
        character.activeTriggers.clear();
        character.activeTriggers.putAll(character.currentTriggers);
      }
    } finally {
      dispatching = false;
    }
  }

  private record Delivery(ContactState state, Contact3D event) {}

  private void putSolidContact(
      Map<PairKey, ContactState> out,
      Binding one,
      Binding two,
      Vector3 pa,
      Vector3 pb,
      Vector3 normal) {
    Binding a = one.lifetime < two.lifetime ? one : two, b = a == one ? two : one;
    Contact3D event =
        one == a
            ? new Contact3D(
                a.object,
                b.object,
                null,
                null,
                Contact3D.Phase.STAY,
                fixedTick,
                false,
                a.lifetime,
                b.lifetime,
                pa,
                pb,
                normal)
            : new Contact3D(
                a.object,
                b.object,
                null,
                null,
                Contact3D.Phase.STAY,
                fixedTick,
                false,
                a.lifetime,
                b.lifetime,
                pb,
                pa,
                normal.cpy().scl(-1));
    PairKey key = new PairKey(a.lifetime, b.lifetime, 0, 0);
    out.putIfAbsent(key, new ContactState(key, a, b, null, false, event));
  }

  private void putTriggerContact(
      Map<PairKey, ContactState> out,
      Binding owner,
      Binding other,
      TriggerBinding trigger,
      Collider3D otherCollider,
      Vector3 pa,
      Vector3 pb,
      Vector3 normal,
      boolean ownerIsA,
      boolean triggerPair,
      long peerTrigger) {
    Binding a = owner.lifetime < other.lifetime ? owner : other, b = a == owner ? other : owner;
    boolean originalAIsCanonical = ownerIsA == (a == owner);
    Vector3 ca = originalAIsCanonical ? pa : pb, cb = originalAIsCanonical ? pb : pa;
    Vector3 cn = originalAIsCanonical ? normal : normal.cpy().scl(-1);
    Contact3D event =
        new Contact3D(
            a.object,
            b.object,
            a == owner ? trigger.collider : (triggerPair ? otherCollider : null),
            b == owner ? trigger.collider : (triggerPair ? otherCollider : null),
            Contact3D.Phase.STAY,
            fixedTick,
            true,
            a.lifetime,
            b.lifetime,
            ca,
            cb,
            cn);
    long sensorA = a == owner ? trigger.lifetime : triggerPair ? peerTrigger : 0;
    long sensorB = b == owner ? trigger.lifetime : triggerPair ? peerTrigger : 0;
    PairKey key = new PairKey(a.lifetime, b.lifetime, sensorA, sensorB);
    out.putIfAbsent(key, new ContactState(key, a, b, trigger.collider, triggerPair, event));
  }

  private static long triggerLifetime(Binding binding, Collider3D collider) {
    for (TriggerBinding trigger : binding.triggers)
      if (trigger.collider == collider) return trigger.lifetime;
    return Long.MAX_VALUE;
  }

  private boolean mutuallyFiltered(Collider3D sensor, NativeEntry e0, NativeEntry e1) {
    NativeEntry sensorEntry = e0.collider == sensor ? e0 : e1.collider == sensor ? e1 : null;
    NativeEntry peer = sensorEntry == e0 ? e1 : e0;
    return sensorEntry != null
        && (sensorEntry.group & peer.mask) != 0
        && (peer.group & sensorEntry.mask) != 0;
  }

  private void deliver(ContactState state, Contact3D event) {
    if (event.isTrigger()) {
      Collider3D trigger = state.trigger;
      deliverTo(trigger, trigger.getGameObject() == event.objectA() ? event : event.reversed());
      if (state.triggerPair) {
        Collider3D peer = event.colliderA() == trigger ? event.colliderB() : event.colliderA();
        deliverTo(peer, peer.getGameObject() == event.objectA() ? event : event.reversed());
      } else {
        Binding peer = state.a.object == trigger.getGameObject() ? state.b : state.a;
        Contact3D oriented = peer.object == event.objectA() ? event : event.reversed();
        for (Collider3D c : peer.solidColliders) deliverTo(c, oriented);
      }
    } else {
      for (Collider3D c : state.a.solidColliders) deliverTo(c, event);
      for (Collider3D c : state.b.solidColliders) deliverTo(c, event.reversed());
    }
  }

  private void deliverTo(Collider3D collider, Contact3D event) {
    if (!receiverLive(collider)) return;
    for (Consumer<Contact3D> callback : collider.listenersSnapshot()) {
      if (!receiverLive(collider)) return;
      try {
        callback.accept(event);
      } catch (RuntimeException | Error failure) {
        gameplayCallbackFailure = combine(gameplayCallbackFailure, failure);
      }
    }
  }

  private boolean receiverLive(Collider3D collider) {
    if (collider == null || closing || !collider.isInitialized() || !collider.isEnabled())
      return false;
    GameObject owner = collider.getGameObject();
    if (owner == null
        || owner.isDestroyed()
        || !owner.isActive()
        || !owner.containsComponent(collider)) return false;
    for (RigidBody3D rigid : owner.getComponents(RigidBody3D.class))
      if (rigid.isInitialized() && !rigid.isEnabled()) return false;
    return true;
  }

  private boolean characterLive(BulletCharacterController3D component) {
    if (component == null || closing || !component.isInitialized() || !component.isEnabled())
      return false;
    GameObject owner = component.getGameObject();
    return owner != null
        && !owner.isDestroyed()
        && owner.isActive()
        && owner.containsComponent(component);
  }

  private void insert(Binding b) {
    if (b.inserted) return;
    failureProbe.allocation("world-insert");
    try {
      if (b.body != null) {
        Collider3D solid = b.solidColliders.getFirst();
        world.addRigidBody(b.body, solid.getCollisionGroup(), solid.getCollisionMask());
        b.inserted = true;
        nativeEntries.put(
            btCollisionObject.getCPtr(b.body),
            new NativeEntry(
                b,
                null,
                b.object,
                null,
                false,
                solid.getCollisionGroup(),
                solid.getCollisionMask()));
      }
      for (TriggerBinding trigger : b.triggers) {
        failureProbe.allocation("trigger-world-insert");
        Collider3D c = trigger.collider;
        world.addCollisionObject(trigger.object, c.getCollisionGroup(), c.getCollisionMask());
        trigger.inserted = true;
        nativeEntries.put(
            btCollisionObject.getCPtr(trigger.object),
            new NativeEntry(
                b, null, b.object, c, true, c.getCollisionGroup(), c.getCollisionMask()));
      }
      b.inserted = true;
    } catch (RuntimeException | Error failure) {
      for (TriggerBinding trigger : b.triggers)
        if (trigger.inserted) {
          try {
            world.removeCollisionObject(trigger.object);
          } catch (RuntimeException | Error rollback) {
            failure.addSuppressed(rollback);
          }
          trigger.inserted = false;
          nativeEntries.remove(btCollisionObject.getCPtr(trigger.object));
        }
      if (b.body != null) {
        try {
          world.removeRigidBody(b.body);
          b.inserted = false;
          nativeEntries.remove(btCollisionObject.getCPtr(b.body));
        } catch (RuntimeException | Error rollback) {
          failure.addSuppressed(rollback);
          b.inserted = true;
        }
      }
      throw failure;
    }
  }

  private void removeParticipation(Binding b) {
    if (b.inserted && b.body != null) {
      world.removeRigidBody(b.body);
      b.inserted = false;
      nativeEntries.remove(btCollisionObject.getCPtr(b.body));
    }
    for (TriggerBinding trigger : b.triggers)
      if (trigger.inserted) {
        world.removeCollisionObject(trigger.object);
        trigger.inserted = false;
        nativeEntries.remove(btCollisionObject.getCPtr(trigger.object));
      }
    b.inserted = false;
  }

  private void removeBinding(GameObject object) {
    Binding b = bindings.remove(object);
    if (b != null) disposeBinding(b);
  }

  private void disposeBinding(Binding b) {
    Throwable failure = null;
    if (b.inserted && b.body != null) {
      try {
        world.removeRigidBody(b.body);
      } catch (RuntimeException | Error e) {
        failure = e;
      }
      b.inserted = false;
      nativeEntries.remove(btCollisionObject.getCPtr(b.body));
    }
    for (TriggerBinding trigger : b.triggers) {
      if (trigger.inserted) {
        try {
          world.removeCollisionObject(trigger.object);
        } catch (RuntimeException | Error e) {
          failure = combine(failure, e);
        }
        trigger.inserted = false;
      }
      nativeEntries.remove(btCollisionObject.getCPtr(trigger.object));
      failure = release(trigger.object, failure);
      if (trigger.shape.compound != null) failure = release(trigger.shape.compound, failure);
      for (btCollisionShape child : trigger.shape.children) failure = release(child, failure);
    }
    b.inserted = false;
    failure = release(b.body, failure);
    failure = release(b.motion, failure);
    if (b.compound != null) {
      failure = release(b.compound, failure);
      for (btCollisionShape child : b.children) failure = release(child, failure);
    } else failure = release(b.shape, failure);
    if (failure instanceof RuntimeException e) throw e;
    if (failure instanceof Error e) throw e;
  }

  private void disposeTrigger(TriggerBinding trigger, Throwable failure) {
    if (trigger.inserted && world != null) {
      try {
        world.removeCollisionObject(trigger.object);
      } catch (RuntimeException | Error e) {
        failure.addSuppressed(e);
      }
      trigger.inserted = false;
    }
    nativeEntries.remove(btCollisionObject.getCPtr(trigger.object));
    safeDisposeTracked(trigger.object, failure);
    if (trigger.shape.compound != null) safeDisposeTracked(trigger.shape.compound, failure);
    for (btCollisionShape child : trigger.shape.children) safeDisposeTracked(child, failure);
  }

  @Override
  public void onDestroy() {
    Throwable t = cleanupNative();
    if (t instanceof RuntimeException e) throw e;
    if (t instanceof Error e) throw e;
  }

  @Override
  protected void onRelease() {
    beforeFixedStep.clear();
    Throwable t = cleanupNative();
    if (t instanceof RuntimeException e) throw e;
    if (t instanceof Error e) throw e;
  }

  private Throwable cleanupNative() {
    if (closing) return null;
    closing = true;
    Throwable failure = null;
    if (debugView != null) {
      try {
        debugView.close();
      } catch (RuntimeException | Error e) {
        failure = combine(failure, e);
      }
    }
    for (GameObject owner : List.copyOf(characters.keySet())) {
      try {
        removeCharacter(owner);
      } catch (RuntimeException | Error e) {
        failure = combine(failure, e);
      }
    }
    if (ghostPairCallback != null) {
      try {
        broadphase.getOverlappingPairCache().setInternalGhostPairCallback(null);
      } catch (RuntimeException | Error e) {
        failure = combine(failure, e);
      }
      failure = release(ghostPairCallback, failure);
      ghostPairCallback = null;
    }
    for (Binding b : List.copyOf(bindings.values())) {
      try {
        disposeBinding(b);
      } catch (RuntimeException | Error e) {
        failure = combine(failure, e);
      }
    }
    bindings.clear();
    activeContacts.clear();
    nativeEntries.clear();
    failure = release(world, failure);
    world = null;
    failure = release(solver, failure);
    solver = null;
    failure = release(broadphase, failure);
    broadphase = null;
    failure = release(dispatcher, failure);
    dispatcher = null;
    failure = release(collisionConfig, failure);
    collisionConfig = null;
    accumulator = 0;
    return failure;
  }

  private void allocation(String stage) {
    nativeAllocations++;
    failureProbe.allocation(stage);
  }

  private Throwable release(com.badlogic.gdx.utils.Disposable value, Throwable failure) {
    if (value != null) {
      nativeDisposals++;
      try {
        value.dispose();
      } catch (RuntimeException | Error e) {
        return combine(failure, e);
      }
    }
    return failure;
  }

  private void safeDisposeTracked(com.badlogic.gdx.utils.Disposable value, Throwable failure) {
    nativeDisposals++;
    try {
      value.dispose();
    } catch (RuntimeException | Error e) {
      failure.addSuppressed(e);
    }
  }

  private static Throwable combine(Throwable prior, Throwable next) {
    if (prior == null) return next;
    prior.addSuppressed(next);
    return prior;
  }

  private void requireWorld() {
    if (world == null || isDisposed() || closing)
      throw new IllegalStateException("Physics3D requires a started live Game");
  }

  private static String signature(RigidBody3D r, List<Collider3D> colliders) {
    StringBuilder b =
        new StringBuilder(
            r == null
                ? "static"
                : r.getType()
                    + ":"
                    + r.massForNative()
                    + ":"
                    + r.revision()
                    + ":"
                    + r.linearDamping()
                    + ":"
                    + r.angularDamping()
                    + ":"
                    + r.ccdThreshold()
                    + ":"
                    + r.ccdRadius());
    for (Collider3D c : colliders)
      b.append('|')
          .append(System.identityHashCode(c))
          .append(':')
          .append(c.revision)
          .append(':')
          .append(c.isTrigger())
          .append(':')
          .append(c.getCollisionGroup())
          .append(':')
          .append(c.getCollisionMask())
          .append(':')
          .append(c.getFriction())
          .append(':')
          .append(c.getRestitution())
          .append(':')
          .append(c.descriptor());
    return b.toString();
  }

  private static void validateSolidConfiguration(GameObject object, List<Collider3D> solids) {
    if (solids.isEmpty()) return;
    Collider3D first = solids.getFirst();
    for (Collider3D collider : solids) {
      if (collider.getCollisionGroup() != first.getCollisionGroup()
          || collider.getCollisionMask() != first.getCollisionMask()
          || collider.getFriction() != first.getFriction()
          || collider.getRestitution() != first.getRestitution())
        throw new IllegalArgumentException(
            object.name + ": solid colliders on one rigid body must share filter and material");
    }
  }

  private static void validateCcd(
      GameObject object, RigidBody3D rigid, List<Collider3D> colliders) {
    if (rigid == null || rigid.ccdThreshold() == 0) return;
    boolean supported =
        colliders.size() == 1
            && !(colliders.getFirst() instanceof CompoundCollider3D)
            && colliders.getFirst().getOffset().isZero(1e-6f)
            && (colliders.getFirst() instanceof SphereCollider3D
                || colliders.getFirst().hasCenteredIdentityPose());
    if (!supported)
      throw new IllegalArgumentException(
          object.name + ": CCD currently requires one centered box, sphere or capsule collider");
  }

  private static void validateTransform(GameObject o, Transform3D t) {
    if (t.getParent() != null)
      throw new IllegalStateException(
          o.name + ": parented Transform3D is not supported by Physics3D");
    finite(t.position);
    finite(t.scale);
    validateQuaternion(t.rotation);
    if (Math.abs(t.rotation.len2() - 1f) > 1e-4f)
      throw new IllegalStateException(o.name + ": Transform3D rotation must be a unit quaternion");
    if (!t.scale.epsilonEquals(1, 1, 1, 1e-6f))
      throw new IllegalStateException(o.name + ": Physics3D requires unit Transform3D scale");
  }

  private static boolean samePose(Transform3D t, Vector3 p, Quaternion q) {
    return t.position.epsilonEquals(p, 1e-6f) && Math.abs(Math.abs(t.rotation.dot(q)) - 1) < 1e-6f;
  }

  static void positive(float v) {
    if (!Float.isFinite(v) || v <= 0)
      throw new IllegalArgumentException("Expected positive finite physics value");
  }

  static void nonnegative(float v) {
    if (!Float.isFinite(v) || v < 0)
      throw new IllegalArgumentException("Expected nonnegative finite physics value");
  }

  private static boolean finitePositive(Vector3 v) {
    return Float.isFinite(v.x)
        && Float.isFinite(v.y)
        && Float.isFinite(v.z)
        && v.x > 0
        && v.y > 0
        && v.z > 0;
  }

  static void finite(Vector3 v) {
    if (!Float.isFinite(v.x) || !Float.isFinite(v.y) || !Float.isFinite(v.z))
      throw new IllegalArgumentException("Expected finite vector");
  }

  static Vector3 copyFinite(Vector3 v) {
    Objects.requireNonNull(v);
    finite(v);
    return v.cpy();
  }

  static void validateQuaternion(Quaternion q) {
    Objects.requireNonNull(q);
    if (!Float.isFinite(q.x)
        || !Float.isFinite(q.y)
        || !Float.isFinite(q.z)
        || !Float.isFinite(q.w))
      throw new IllegalArgumentException("Expected finite nonzero quaternion");
    double norm = (double) q.x * q.x + (double) q.y * q.y + (double) q.z * q.z + (double) q.w * q.w;
    if (!Double.isFinite(norm) || norm < 1e-12)
      throw new IllegalArgumentException("Expected finite nonzero quaternion");
  }

  static Quaternion normalizedCopy(Quaternion q) {
    validateQuaternion(q);
    double norm =
        Math.sqrt(
            (double) q.x * q.x + (double) q.y * q.y + (double) q.z * q.z + (double) q.w * q.w);
    return new Quaternion(
        (float) (q.x / norm), (float) (q.y / norm), (float) (q.z / norm), (float) (q.w / norm));
  }
}
