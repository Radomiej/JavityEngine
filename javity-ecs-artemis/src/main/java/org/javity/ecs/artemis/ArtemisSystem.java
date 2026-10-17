package org.javity.ecs.artemis;

import com.artemis.*;
import java.util.*;
import java.util.function.Consumer;
import org.javity.core.*;

/** Owns one native Artemis world and explicitly linked GameObjects. */
public class ArtemisSystem extends GameSystem {
  private final World world;
  private final EcsPhase phase;
  private final Thread ownerThread;
  private boolean processing, releasePending, worldReleased;
  private final LinkRegistry registry = new LinkRegistry();
  private EntitySubscription allEntities;
  private final EntitySubscription.SubscriptionListener deletionListener = new EntitySubscription.SubscriptionListener() {
    @Override public void inserted(com.artemis.utils.IntBag entities) {}
    @Override public void removed(com.artemis.utils.IntBag entities) {
      for (int i = 0; i < entities.size(); i++) {
        EntityLink link = registry.forEntity(entities.get(i));
        if (link != null && !link.hasValidReference()) invalidate(link, false);
      }
    }
  };

  public ArtemisSystem(World world) { this(world, EcsPhase.BEFORE_WORLD); }
  public ArtemisSystem(World world, EcsPhase phase) {
    this.world = Objects.requireNonNull(world, "world");
    this.phase = Objects.requireNonNull(phase, "phase");
    ownerThread = Thread.currentThread();
  }

  /** Returns the owned native world while live. */
  public World world() {
    checkOwnerThread();
    if (isDisposed() || worldReleased) throw new IllegalStateException("Artemis world is closed");
    return world;
  }
  public EcsPhase phase() { return phase; }

  public EntityLink bind(GameObject object, Entity entity) {
    checkOwnerThread(); Objects.requireNonNull(object); Objects.requireNonNull(entity);
    Game game = getGame();
    if (game == null || game.isDisposed() || isDisposed()) throw new IllegalStateException("Adapter must be attached to a live Game");
    if (object.game != game) throw new IllegalArgumentException("GameObject belongs to another Game");
    if (object.isDestroyed()) throw new IllegalArgumentException("GameObject is destroyed");
    if (entity.getWorld() != world) throw new IllegalArgumentException("Entity belongs to another World");
    if (!entity.isActive()) throw new IllegalArgumentException("Entity is not active");
    if (registry.contains(object) || registry.contains(entity.getId())) throw new IllegalArgumentException("Object or entity already linked");
    if (entity.getComponent(GameObjectRef.class) != null) throw new IllegalArgumentException("Reserved GameObjectRef already present");
    ensureDeletionListener();
    EntityLink link = new EntityLink(this, object, entity.getId(), world, registry.nextGeneration());
    GameObjectRef ref = world.edit(entity.getId()).create(GameObjectRef.class);
    ref.bind(object, link);
    link.reference = ref;
    link.removeObjectListener = object.onComponentChange(change -> { if (link.valid) link.dirty = true; });
    registry.add(link);
    return link;
  }

  public EntityLink createLinked(String name, Consumer<GameObject> configureObject, Consumer<EntityEdit> configureEntity) {
    checkOwnerThread();
    Game game = getGame();
    if (game == null || game.isDisposed()) throw new IllegalStateException("Adapter must be attached to a live Game");
    GameObject object = game.createObject(Objects.requireNonNull(name));
    Entity entity = world.createEntity();
    EntityLink link = null;
    try {
      if (configureObject != null) configureObject.accept(object);
      link = bind(object, entity);
      if (configureEntity != null) configureEntity.accept(world.edit(entity.getId()));
      if (!link.hasValidReference()) throw new IllegalStateException("Reserved GameObjectRef was removed");
      return link;
    } catch (RuntimeException | Error failure) {
      if (link != null) invalidate(link, true); else if (entity.isActive()) world.delete(entity.getId());
      object.destroy();
      throw failure;
    }
  }

  private void ensureDeletionListener() {
    if (allEntities == null) {
      allEntities = world.getAspectSubscriptionManager().get(Aspect.all(GameObjectRef.class));
      allEntities.addSubscriptionListener(deletionListener);
    }
  }

  void ensureValid(EntityLink link) {
    checkOwnerThread();
    if (!link.valid || link.owner != this || link.identity != world || worldReleased || isDisposed() || link.object.isDestroyed()) {
      if (link.valid) invalidate(link, true);
      throw new IllegalStateException("Entity link is no longer valid");
    }
    if (!link.hasValidReference()) { invalidate(link, false); throw new IllegalStateException("Native link reference is missing or replaced"); }
  }

  void invalidate(EntityLink link, boolean deleteEntity) {
    if (!link.valid) return;
    link.valid = false;
    registry.remove(link);
    if (link.removeObjectListener != null) { link.removeObjectListener.run(); link.removeObjectListener = null; }
    link.clearOwnedCoreReferences();
    GameObjectRef ref = link.reference; link.reference = null;
    if (!worldReleased) {
      Entity current = world.getEntity(link.id);
      if (current != null && current.isActive()) {
        if (current.getComponent(GameObjectRef.class) == ref && ref != null) {
          ref.clear();
          if (deleteEntity) world.delete(link.id); else current.edit().remove(GameObjectRef.class);
        } else if (deleteEntity) world.delete(link.id);
      }
    }
  }
  void rollbackCreatedLink(EntityLink link) { invalidate(link, true); }

  private void housekeeping() {
    checkOwnerThread();
    for (EntityLink link : registry.snapshot()) {
      if (!link.valid) continue;
      if (link.object.isDestroyed()) { invalidate(link, true); continue; }
      if (!link.hasValidReference()) { invalidate(link, false); continue; }
      if (link.synchronizationEnabled) link.reconcile();
    }
  }

  @Override public void onBeforeUpdate(float delta) { if (phase == EcsPhase.BEFORE_WORLD) process(delta); }
  @Override public void onAfterUpdate(float delta) { if (phase == EcsPhase.AFTER_WORLD) process(delta); }
  @Override public void onUiUpdate(float delta) {
    housekeeping();
    if (phase == EcsPhase.UI) process(delta);
  }

  private void process(float delta) {
    checkOwnerThread();
    if (isDisposed() || worldReleased) return;
    if (processing) throw new IllegalStateException("Recursive Artemis world process");
    processing = true;
    Throwable failure = null;
    try {
      housekeeping();
      for (EntityLink link : registry.snapshot()) if (link.valid && link.synchronizationEnabled) link.copyToEcs();
      world.setDelta(delta); world.process();
      housekeeping();
      for (EntityLink link : registry.snapshot()) if (link.valid && link.synchronizationEnabled) link.copyToObject();
    } catch (RuntimeException | Error e) { failure = e; }
    finally {
      processing = false;
      if (releasePending) try { releaseWorld(); } catch (RuntimeException | Error e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
    }
    if (failure instanceof RuntimeException e) throw e;
    if (failure instanceof Error e) throw e;
  }

  @Override protected void validateDispose() { checkOwnerThread(); }
  @Override protected void onRelease() {
    for (EntityLink link : registry.snapshot()) invalidate(link, false);
    if (allEntities != null) allEntities.removeSubscriptionListener(deletionListener);
    if (worldReleased) return;
    if (processing) { releasePending = true; return; }
    releaseWorld();
  }
  private void releaseWorld() { if (worldReleased) return; worldReleased = true; releasePending = false; world.dispose(); }
  boolean isWorldReleased() { return worldReleased; }
  void checkOwnerThread() { if (Thread.currentThread() != ownerThread) throw new IllegalStateException("ArtemisSystem is confined to its owner thread"); }
}
