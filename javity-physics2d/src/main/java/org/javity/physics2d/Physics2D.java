package org.javity.physics2d;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.GdxNativesLoader;
import java.util.*;
import org.javity.core.*;

/** Scene-owned native Box2D adapter, independent of Artemis. */
public final class Physics2D extends GameSystem {
  private static final float FIXED_STEP = 1f / 60;

  private static final class Binding {
    final Body body;
    final Vector2 lastPosition = new Vector2();
    final Map<Collider2D, FixtureState> fixtures = new LinkedHashMap<>();
    RigidBody2D rigidBody;
    long rigidRevision = -1;
    float lastRotation;

    Binding(Body body) {
      this.body = body;
    }
  }

  private record FixtureState(Fixture fixture, long revision) {}

  private record ContactEvent(Collider2D a, Collider2D b, boolean trigger, boolean entering) {}

  private final Vector2 gravity;
  private final float unitsPerMeter;
  private final Map<GameObject, Binding> bindings = new LinkedHashMap<>();
  private final Deque<ContactEvent> contacts = new ArrayDeque<>();
  private World world;
  private Box2DDebugRenderer debugRenderer;
  private double accumulator;
  private boolean dispatching;

  /** Gravity in world units/s²; e.g. (0,-980),100 gives pixel units and Earth gravity. */
  public Physics2D(Vector2 gravity, float unitsPerMeter) {
    Objects.requireNonNull(gravity);
    RigidBody2D.finite(gravity.x);
    RigidBody2D.finite(gravity.y);
    Collider2D.positive(unitsPerMeter);
    this.gravity = gravity.cpy();
    this.unitsPerMeter = unitsPerMeter;
    Collider2D.meters(gravity.x, unitsPerMeter);
    Collider2D.meters(gravity.y, unitsPerMeter);
  }

  public int getBodyCount() {
    return world == null ? 0 : world.getBodyCount();
  }

  public int getFixtureCount() {
    return bindings.values().stream().mapToInt(b -> b.fixtures.size()).sum();
  }

  public float getUnitsPerMeter() {
    return unitsPerMeter;
  }

  @Override
  public void onCreate() {
    GdxNativesLoader.load();
    Box2D.init();
    world =
        new World(
            new Vector2(
                Collider2D.meters(gravity.x, unitsPerMeter),
                Collider2D.meters(gravity.y, unitsPerMeter)),
            true);
    world.setContactListener(
        new ContactListener() {
          public void beginContact(Contact contact) {
            queue(contact, true);
          }

          public void endContact(Contact contact) {
            queue(contact, false);
          }

          public void preSolve(Contact contact, Manifold oldManifold) {}

          public void postSolve(Contact contact, ContactImpulse impulse) {}
        });
  }

  private void queue(Contact contact, boolean entering) {
    if (isDisposed()) return;
    if (contact.getFixtureA().getUserData() instanceof Collider2D a
        && contact.getFixtureB().getUserData() instanceof Collider2D b)
      contacts.add(
          new ContactEvent(
              a,
              b,
              contact.getFixtureA().isSensor() || contact.getFixtureB().isSensor(),
              entering));
  }

  @Override
  public void onBeforeUpdate(float delta) {
    synchronize();
  }

  @Override
  public void onAfterUpdate(float delta) {
    synchronize();
    accumulator += delta;
    while (accumulator + 1e-9 >= FIXED_STEP) {
      applyActions();
      world.step(FIXED_STEP, 6, 2);
      accumulator = Math.max(0, accumulator - FIXED_STEP);
    }
    publishTransforms();
    boolean hadContacts = !contacts.isEmpty();
    dispatchContacts();
    if (hadContacts && !isDisposed()) {
      synchronize();
      dispatchContacts();
    }
  }

  /** Explicit synchronization without advancing time; normally Game's phases do this. */
  public void refresh() {
    requireWorld();
    synchronize();
    if (!dispatching) dispatchContacts();
  }

  private void requireWorld() {
    if (world == null || isDisposed())
      throw new IllegalStateException("Physics requires a started, live Game");
  }

  private void synchronize() {
    requireWorld();
    Set<GameObject> candidates = new LinkedHashSet<>(getGame().getObjectsWith(Collider2D.class));
    candidates.addAll(getGame().getObjectsWith(RigidBody2D.class));
    for (GameObject o : List.copyOf(bindings.keySet()))
      if (o.isDestroyed() || !candidates.contains(o)) removeBody(o);
    for (GameObject o :
        candidates.stream().sorted(Comparator.comparingInt(actor -> actor.id)).toList()) {
      List<RigidBody2D> rigidBodies =
          o.getComponents(RigidBody2D.class).stream().filter(Component::isInitialized).toList();
      if (rigidBodies.size() > 1)
        throw new IllegalStateException(o.name + ": only one RigidBody2D is allowed");
      RigidBody2D rigid = rigidBodies.isEmpty() ? null : rigidBodies.getFirst();
      List<Collider2D> colliders =
          o.getComponents(Collider2D.class).stream()
              .filter(c -> c.isInitialized() && c.isEnabled())
              .toList();
      if (rigid == null && colliders.isEmpty()) {
        removeBody(o);
        continue;
      }
      Vector2 position = o.transform.position;
      float x = Collider2D.meters(position.x, unitsPerMeter),
          y = Collider2D.meters(position.y, unitsPerMeter);
      RigidBody2D.finite(o.transform.rotation);
      Binding binding = bindings.get(o);
      if (binding == null) {
        BodyDef def = new BodyDef();
        def.position.set(x, y);
        def.angle = o.transform.rotation;
        binding = new Binding(world.createBody(def));
        binding.lastPosition.set(position);
        binding.lastRotation = o.transform.rotation;
        bindings.put(o, binding);
      }
      if (binding.rigidBody != rigid) {
        if (binding.rigidBody != null) binding.rigidBody.mass = 0;
        binding.rigidBody = rigid;
        binding.rigidRevision = -1;
        configureBody(binding);
      } else if (rigid != null && binding.rigidRevision != rigid.revision) configureBody(binding);
      boolean active = o.isActive() && (rigid == null || rigid.isEnabled());
      if (binding.body.isActive() != active) {
        binding.body.setActive(active);
        if (active) binding.body.setAwake(true);
      }
      if (!binding.lastPosition.equals(position) || binding.lastRotation != o.transform.rotation) {
        binding.body.setTransform(x, y, o.transform.rotation);
        binding.body.setAwake(true);
        binding.lastPosition.set(position);
        binding.lastRotation = o.transform.rotation;
      }
      syncFixtures(binding, colliders);
      if (rigid != null) {
        if (rigid.velocityRequested) {
          binding.body.setLinearVelocity(
              Collider2D.meters(rigid.velocity.x, unitsPerMeter),
              Collider2D.meters(rigid.velocity.y, unitsPerMeter));
          rigid.velocityRequested = false;
        }
        if (rigid.angularVelocityRequested) {
          binding.body.setAngularVelocity(rigid.angularVelocity);
          rigid.angularVelocityRequested = false;
        }
        rigid.mass = binding.body.getMass();
      }
    }
  }

  private void applyActions() {
    for (Binding b : bindings.values())
      if (b.rigidBody != null && b.body.isActive())
        while (!b.rigidBody.actions.isEmpty()) {
          var a = b.rigidBody.actions.removeFirst();
          float x = Collider2D.meters(a.x(), unitsPerMeter),
              y = Collider2D.meters(a.y(), unitsPerMeter);
          if (a.impulse())
            b.body.applyLinearImpulse(
                x, y, b.body.getWorldCenter().x, b.body.getWorldCenter().y, true);
          else b.body.applyForceToCenter(x, y, true);
        }
  }

  /** Sorted closest first. Box2D rays do not report fixtures containing the ray origin. */
  public List<RayHit2D> raycast(Vector2 start, Vector2 end, int mask, boolean includeTriggers) {
    requireWorld();
    if ((mask & ~65535) != 0) throw new IllegalArgumentException("Unsigned 16-bit mask required");
    Vector2 a =
        new Vector2(
            Collider2D.meters(start.x, unitsPerMeter), Collider2D.meters(start.y, unitsPerMeter));
    Vector2 b =
        new Vector2(
            Collider2D.meters(end.x, unitsPerMeter), Collider2D.meters(end.y, unitsPerMeter));
    if (a.equals(b)) return List.of();
    synchronize();
    List<RayHit2D> hits = new ArrayList<>();
    world.rayCast(
        (fixture, point, normal, fraction) -> {
          if (fixture.getUserData() instanceof Collider2D c
              && (c.getCategory() & mask) != 0
              && (includeTriggers || !c.isTrigger()))
            hits.add(
                new RayHit2D(
                    c.getGameObject(), c, point.cpy().scl(unitsPerMeter), normal, fraction));
          return 1;
        },
        a,
        b);
    hits.sort(Comparator.comparingDouble(RayHit2D::fraction).thenComparingInt(h -> h.object().id));
    return List.copyOf(hits);
  }

  private void configureBody(Binding b) {
    RigidBody2D r = b.rigidBody;
    BodyDef.BodyType type =
        r == null
            ? BodyDef.BodyType.StaticBody
            : switch (r.getType()) {
              case STATIC -> BodyDef.BodyType.StaticBody;
              case DYNAMIC -> BodyDef.BodyType.DynamicBody;
              case KINEMATIC -> BodyDef.BodyType.KinematicBody;
            };
    if (b.body.getType() != type) b.body.setType(type);
    b.body.setFixedRotation(r != null && r.isFixedRotation());
    b.body.setBullet(r != null && r.isBullet());
    b.body.setGravityScale(r == null ? 1 : r.getGravityScale());
    b.body.setLinearDamping(r == null ? 0 : r.getLinearDamping());
    b.body.setAngularDamping(r == null ? 0 : r.getAngularDamping());
    b.rigidRevision = r == null ? -1 : r.revision;
    b.body.setAwake(true);
  }

  private void syncFixtures(Binding b, List<Collider2D> colliders) {
    for (Collider2D collider : List.copyOf(b.fixtures.keySet())) {
      FixtureState state = b.fixtures.get(collider);
      if (!colliders.contains(collider) || state.revision != collider.revision) {
        b.body.destroyFixture(state.fixture);
        b.fixtures.remove(collider);
        b.body.setAwake(true);
      }
    }
    for (Collider2D collider : colliders)
      if (!b.fixtures.containsKey(collider)) {
        Shape shape = collider.createShape(unitsPerMeter);
        try {
          FixtureDef def = new FixtureDef();
          def.shape = shape;
          def.density = collider.getDensity();
          def.friction = collider.getFriction();
          def.restitution = collider.getRestitution();
          def.isSensor = collider.isTrigger();
          def.filter.categoryBits = (short) collider.getCategory();
          def.filter.maskBits = (short) collider.getMask();
          Fixture fixture = b.body.createFixture(def);
          fixture.setUserData(collider);
          b.fixtures.put(collider, new FixtureState(fixture, collider.revision));
          b.body.setAwake(true);
        } finally {
          shape.dispose();
        }
      }
  }

  private void publishTransforms() {
    for (var entry : bindings.entrySet()) {
      Binding b = entry.getValue();
      if (!b.body.isActive()) continue;
      entry.getKey().transform.position.set(b.body.getPosition()).scl(unitsPerMeter);
      entry.getKey().transform.rotation = b.body.getAngle();
      b.lastPosition.set(entry.getKey().transform.position);
      b.lastRotation = b.body.getAngle();
      if (b.rigidBody != null) {
        b.rigidBody.velocity.set(b.body.getLinearVelocity()).scl(unitsPerMeter);
        b.rigidBody.angularVelocity = b.body.getAngularVelocity();
        b.rigidBody.mass = b.body.getMass();
      }
    }
  }

  private void removeBody(GameObject o) {
    Binding b = bindings.remove(o);
    if (b == null) return;
    world.destroyBody(b.body);
    if (b.rigidBody != null) b.rigidBody.mass = 0;
  }

  private void dispatchContacts() {
    dispatching = true;
    try {
      while (!contacts.isEmpty() && !isDisposed()) {
        ContactEvent event = contacts.removeFirst();
        deliver(event.a, event.b, event);
        if (!isDisposed()) deliver(event.b, event.a, event);
      }
    } finally {
      dispatching = false;
    }
  }

  private void deliver(Collider2D self, Collider2D other, ContactEvent event) {
    GameObject recipient = self.getGameObject(), target = other.getGameObject();
    if (!recipient.isActive()
        || recipient.isDestroyed()
        || !self.isInitialized()
        || !self.isEnabled()) return;
    if (event.entering
        && (!target.isActive()
            || target.isDestroyed()
            || !other.isInitialized()
            || !other.isEnabled())) return;
    if (event.entering) self.fireEnter(target);
    Contact2D contact = new Contact2D(target, self, other);
    for (Component c : recipient.getComponents()) {
      if (isDisposed() || recipient.isDestroyed() || !recipient.isActive()) break;
      if (!c.isInitialized() || !c.isEnabled() || !(c instanceof CollisionListener2D listener))
        continue;
      if (event.trigger) {
        if (event.entering) listener.onTriggerEnter(contact);
        else listener.onTriggerExit(contact);
      } else {
        if (event.entering) listener.onCollisionEnter(contact);
        else listener.onCollisionExit(contact);
      }
    }
  }

  /** Draw after sprites and before UI. Requires a live OpenGL context. */
  public void drawDebug(Camera camera) {
    requireWorld();
    if (debugRenderer == null) debugRenderer = new Box2DDebugRenderer();
    debugRenderer.render(world, camera.combined.cpy().scale(unitsPerMeter, unitsPerMeter, 1));
  }

  @Override
  public void onDestroy() {
    for (Binding b : bindings.values()) if (b.rigidBody != null) b.rigidBody.mass = 0;
    bindings.clear();
    contacts.clear();
    if (debugRenderer != null) debugRenderer.dispose();
    if (world != null) {
      world.dispose();
      world = null;
    }
  }
}
