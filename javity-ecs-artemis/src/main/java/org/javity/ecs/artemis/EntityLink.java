package org.javity.ecs.artemis;

import com.artemis.*;
import java.util.*;
import org.javity.core.Component;
import org.javity.core.GameObject;

/** Generation-checked handle for one GameObject and one Artemis entity. */
public final class EntityLink implements AutoCloseable {
  final ArtemisSystem owner;
  final GameObject object;
  final int id;
  final World identity;
  final long generation;
  final List<ProjectionSession> projections = new ArrayList<>();
  boolean valid = true, synchronizationEnabled = true, dirty = true;
  GameObjectRef reference;
  LinkLifetime lifetime;
  boolean factoryOwned;
  Runnable removeObjectListener;

  EntityLink(ArtemisSystem owner, GameObject object, int id, World identity, long generation) {
    this.owner = owner; this.object = object; this.id = id; this.identity = identity; this.generation = generation;
  }

  boolean hasValidReference() {
    if (!valid || owner.isWorldReleased()) return false;
    Entity entity = identity.getEntity(id);
    return entity != null && entity.isActive() && entity.getComponent(GameObjectRef.class) == reference
        && reference != null && reference.matches(object, this)
        && entity.getComponent(LinkLifetime.class) == lifetime && lifetime != null && lifetime.matches(this);
  }

  public boolean isValid() {
    owner.checkOwnerThread();
    return valid && hasValidReference() && !object.isDestroyed();
  }
  public GameObject object() { owner.ensureValid(this); return object; }
  public int entity() { owner.ensureValid(this); return id; }
  public long generation() { owner.ensureValid(this); return generation; }
  public void setSynchronizationEnabled(boolean enabled) {
    owner.ensureValid(this); synchronizationEnabled = enabled; if (enabled) dirty = true;
  }
  public boolean isSynchronizationEnabled() { owner.checkOwnerThread(); return synchronizationEnabled && isValid(); }

  public void addProjection(ComponentProjection<?, ?> projection) {
    owner.checkOwnerThread(); Objects.requireNonNull(projection);
    if (projection.ecsType() == InactiveGameObject.class || projection.ecsType() == GameObjectRef.class
        || projection.ecsType() == LinkLifetime.class)
      throw new IllegalArgumentException("Projection target is reserved bridge state");
    owner.ensureValid(this);
    // Selectors are user code. Revalidate after invocation before reserving a writer.
    projection.selected(object);
    owner.ensureValid(this);
    if (!synchronizationEnabled) throw new IllegalStateException("Cannot configure a projection while synchronization is disabled");
    for (ProjectionSession existing : projections) {
      if (existing.projection.ecsType() == projection.ecsType()
          || existing.projection.coreType() == projection.coreType())
        throw new IllegalArgumentException("Conflicting projection writer on link");
    }
    if (projection.direction() == ProjectionDirection.GO_TO_ECS
        && projection.ownership() == ProjectionOwnership.OWNED
        && identity.getMapper(projection.ecsType()).has(id))
      throw new IllegalStateException("Owned target component already exists");
    if (projection.direction() == ProjectionDirection.GO_TO_ECS
        && projection.ownership() == ProjectionOwnership.BORROWED
        && !identity.getMapper(projection.ecsType()).has(id))
      throw new IllegalStateException("Borrowed target component must exist");
    // Selector and native validation both precede claims; do not run copier callbacks here.
    projections.add(new ProjectionSession(projection));
    dirty = true;
  }

  /** Reconciles markers and projection-owned slots without copying user data. */
  void reconcile() {
    if (!owner.isOperational(this, null)) return;
    ensureInactiveMarker(!object.isActive());
    for (ProjectionSession session : List.copyOf(projections)) {
      if (!owner.isOperational(this, session)) return;
      Component source = session.projection.selected(object);
      if (!owner.isOperational(this, session)) return;
      if (session.projection.direction() != ProjectionDirection.GO_TO_ECS
          || session.projection.ownership() != ProjectionOwnership.OWNED) continue;
      boolean sourceReady = ready(source);
      ComponentMapper<? extends com.artemis.Component> mapper = identity.getMapper(session.projection.ecsType());
      com.artemis.Component target = mapper.get(id);
      if (!sourceReady) {
        if (target != null) identity.edit(id).remove(session.projection.ecsType());
        session.ownedCore = null; session.ownedEcs = null;
      } else {
        if (target == null) target = identity.edit(id).create(session.projection.ecsType());
        session.ownedCore = source; session.ownedEcs = target;
      }
    }
    dirty = false;
  }

  private boolean ready(Component source) {
    return source != null && object.isActive() && object.containsComponent(source)
        && source.isInitialized() && source.isEnabled();
  }

  private void ensureInactiveMarker(boolean inactive) {
    boolean has = identity.getMapper(InactiveGameObject.class).has(id);
    if (inactive && !has) identity.edit(id).create(InactiveGameObject.class);
    else if (!inactive && has) identity.edit(id).remove(InactiveGameObject.class);
  }

  /** GO_TO_ECS writer boundary: immediately before World.process(). */
  void copyToEcs() {
    for (ProjectionSession session : List.copyOf(projections)) {
      if (!owner.isOperational(this, session)) return;
      ComponentProjection<?, ?> projection = session.projection;
      if (projection.direction() != ProjectionDirection.GO_TO_ECS) continue;
      Component source = projection.selected(object);
      if (!owner.isOperational(this, session)) return;
      if (!ready(source)) {
        if (projection.ownership() == ProjectionOwnership.OWNED) {
          com.artemis.Component current = identity.getMapper(projection.ecsType()).get(id);
          if (current != null) identity.edit(id).remove(projection.ecsType());
          session.ownedCore = null; session.ownedEcs = null;
        }
        continue;
      }
      com.artemis.Component target = identity.getMapper(projection.ecsType()).get(id);
      if (projection.ownership() == ProjectionOwnership.OWNED && target == null)
        target = identity.edit(id).create(projection.ecsType());
      if (target == null) continue; // Borrowed target is absent.
      session.ownedCore = source;
      if (projection.ownership() == ProjectionOwnership.OWNED) session.ownedEcs = target;
      projection.toEntity(source, target);
      // The copier may close the link, dispose the adapter or disable synchronization.
      if (!owner.isOperational(this, session)) return;
    }
  }

  /** ECS_TO_GO writer boundary: immediately after World.process(). */
  void copyToObject() {
    for (ProjectionSession session : List.copyOf(projections)) {
      if (!owner.isOperational(this, session)) return;
      ComponentProjection<?, ?> projection = session.projection;
      if (projection.direction() != ProjectionDirection.ECS_TO_GO) continue;
      Component target = projection.selected(object);
      if (!owner.isOperational(this, session)) return;
      if (!ready(target)) continue;
      com.artemis.Component source = identity.getMapper(projection.ecsType()).get(id);
      if (source == null) continue;
      projection.toObject(source, target);
      if (!owner.isOperational(this, session)) return;
    }
  }

  void clearOwnedCoreReferences(boolean ownsCurrentEntity) {
    if (ownsCurrentEntity && !owner.isWorldReleased()) {
      Entity current = identity.getEntity(id);
      if (current != null && current.isActive()) for (ProjectionSession session : projections) {
        ComponentProjection<?, ?> projection = session.projection;
        if (projection.direction() == ProjectionDirection.GO_TO_ECS
            && projection.ownership() == ProjectionOwnership.OWNED)
          current.edit().remove(projection.ecsType());
        session.clear();
      }
    }
    for (ProjectionSession session : projections) session.clear();
    projections.clear();
  }

  @Override public void close() { owner.checkOwnerThread(); owner.invalidate(this, false); }
}
