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
  Runnable removeObjectListener;
  EntityLink(ArtemisSystem owner, GameObject object, int id, World identity, long generation) {
    this.owner = owner; this.object = object; this.id = id; this.identity = identity; this.generation = generation;
  }
  boolean hasValidReference() {
    if (!valid || owner.isWorldReleased()) return false;
    Entity e = identity.getEntity(id);
    return e != null && e.isActive() && e.getComponent(GameObjectRef.class) == reference && reference != null && reference.matches(object, this);
  }
  public boolean isValid() { owner.checkOwnerThread(); return valid && hasValidReference() && !object.isDestroyed(); }
  public GameObject object() { owner.ensureValid(this); return object; }
  public int entity() { owner.ensureValid(this); return id; }
  public long generation() { owner.ensureValid(this); return generation; }
  public void setSynchronizationEnabled(boolean enabled) { owner.ensureValid(this); synchronizationEnabled = enabled; if (enabled) dirty = true; }
  public boolean isSynchronizationEnabled() { owner.checkOwnerThread(); return synchronizationEnabled && isValid(); }
  public void addProjection(ComponentProjection<?, ?> projection) {
    owner.ensureValid(this); Objects.requireNonNull(projection);
    // Run the user selector before claiming either side so ambiguity cannot leave a partial writer.
    projection.selected(object);
    for (ProjectionSession existing : projections) {
      if (existing.projection.ecsType() == projection.ecsType() || existing.projection.coreType() == projection.coreType())
        throw new IllegalArgumentException("Conflicting projection writer on link");
    }
    if (projection.direction() == ProjectionDirection.GO_TO_ECS && projection.ownership() == ProjectionOwnership.OWNED
        && identity.getMapper(projection.ecsType()).has(id)) throw new IllegalStateException("Owned target component already exists");
    if (projection.direction() == ProjectionDirection.GO_TO_ECS && projection.ownership() == ProjectionOwnership.BORROWED
        && !identity.getMapper(projection.ecsType()).has(id)) throw new IllegalStateException("Borrowed target component must exist");
    projections.add(new ProjectionSession(projection));
    if (synchronizationEnabled) reconcileSession(projections.get(projections.size() - 1));
  }
  void reconcile() {
    owner.ensureValid(this);
    if (object.isActive()) { ensureInactiveMarker(false); } else ensureInactiveMarker(true);
    for (ProjectionSession session : List.copyOf(projections)) {
      if (!valid) return;
      if (projections.contains(session)) reconcileSession(session);
    }
    dirty = false;
  }
  private void ensureInactiveMarker(boolean inactive) {
    boolean has = identity.getMapper(InactiveGameObject.class).has(id);
    if (inactive && !has) identity.edit(id).create(InactiveGameObject.class);
    else if (!inactive && has) identity.edit(id).remove(InactiveGameObject.class);
  }
  private void reconcileSession(ProjectionSession session) {
    ComponentProjection<?, ?> p = session.projection;
    Component source = p.selected(object);
    if (source != null && (!source.isInitialized() || !source.isEnabled() || !object.isActive())) source = null;
    ComponentMapper<? extends com.artemis.Component> mapper = identity.getMapper(p.ecsType());
    com.artemis.Component target = mapper.get(id);
    if (p.direction() == ProjectionDirection.GO_TO_ECS) {
      if (p.ownership() == ProjectionOwnership.OWNED) {
        if (source == null) {
          if (target != null) identity.edit(id).remove(p.ecsType());
          session.ownedEcs = null; session.ownedCore = null; return;
        }
        if (target == null) target = identity.edit(id).create(p.ecsType());
        session.ownedCore = source; session.ownedEcs = target;
      } else if (source == null || target == null) return;
      if (source != null && target != null) p.toEntity(source, target);
    }
  }
  void copyToEcs() {
    for (ProjectionSession s : List.copyOf(projections)) {
      if (!valid) return;
      if (s.projection.direction() == ProjectionDirection.GO_TO_ECS && projections.contains(s)) reconcileSession(s);
    }
  }
  void copyToObject() {
    for (ProjectionSession s : List.copyOf(projections)) {
      if (!valid) return;
      if (!projections.contains(s) || s.projection.direction() != ProjectionDirection.ECS_TO_GO) continue;
      com.artemis.Component source = identity.getMapper(s.projection.ecsType()).get(id);
      Component target = s.projection.selected(object);
      if (source != null && target != null && target.isInitialized() && target.isEnabled() && object.isActive()) s.projection.toObject(source, target);
    }
  }
  void clearOwnedCoreReferences() {
    if (!owner.isWorldReleased()) {
      Entity current = identity.getEntity(id);
      if (current != null && current.isActive()) for (ProjectionSession session : projections) {
        ComponentProjection<?, ?> p = session.projection;
        if (p.direction() == ProjectionDirection.GO_TO_ECS && p.ownership() == ProjectionOwnership.OWNED
            && session.ownedEcs != null && current.getComponent(p.ecsType()) == session.ownedEcs)
          current.edit().remove(p.ecsType());
      }
    }
    for (ProjectionSession session : projections) session.clear();
    projections.clear();
  }
  @Override public void close() { owner.checkOwnerThread(); owner.invalidate(this, false); }
}
