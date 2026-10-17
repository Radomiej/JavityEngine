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
        if (link != null) invalidate(link, false);
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
    LinkLifetime lifetime = new LinkLifetime();
    lifetime.bind(link);
    world.edit(entity.getId()).add(lifetime);
    link.lifetime = lifetime;
    link.removeObjectListener = object.onComponentChange(change -> { if (link.valid) link.dirty = true; });
    registry.add(link);
    return link;
  }

  public EntityLink createLinked(String name, Consumer<GameObject> configureObject, Consumer<EntityEdit> configureEntity) {
    checkOwnerForMutation();
    Game game = getGame();
    if (game == null || game.isDisposed()) throw new IllegalStateException("Adapter must be attached to a live Game");
    GameObject object = game.createObject(Objects.requireNonNull(name));
    Entity entity = null;
    EntityLink link = null;
    try {
      if (configureObject != null) configureObject.accept(object);
      if (object.isDestroyed()) throw new IllegalStateException("GameObject configurator destroyed its object");
      checkOwnerForMutation();
      entity = world.createEntity();
      link = bind(object, entity);
      link.factoryOwned = true;
      if (configureEntity != null) configureEntity.accept(world.edit(entity.getId()));
      ensureValid(link);
      link.factoryOwned = false;
      return link;
    } catch (RuntimeException | Error failure) {
      if (link != null) rollbackCreatedLink(link);
      else if (entity != null && entity.isActive()) world.delete(entity.getId());
      object.destroy();
      throw failure;
    }
  }

  private void ensureDeletionListener() {
    if (allEntities == null) {
      allEntities = world.getAspectSubscriptionManager().get(Aspect.all());
      allEntities.addSubscriptionListener(deletionListener);
    }
  }

  void ensureValid(EntityLink link) {
    checkOwnerThread();
    if (!link.valid || link.owner != this || link.identity != world || worldReleased || isDisposed()) {
      throw new IllegalStateException("Entity link is no longer valid");
    }
    if (!link.hasValidReference()) { invalidate(link, false); throw new IllegalStateException("Native link reference is missing or replaced"); }
    if (link.object.isDestroyed()) { invalidate(link, true); throw new IllegalStateException("GameObject is destroyed"); }
  }

  boolean isOperational(EntityLink link, ProjectionSession session) {
    if (!link.valid || isDisposed() || worldReleased || !link.synchronizationEnabled
        || (session != null && !link.projections.contains(session))) return false;
    if (!link.hasValidReference()) { invalidate(link, false); return false; }
    if (link.object.isDestroyed()) { invalidate(link, true); return false; }
    return true;
  }

  void checkOwnerForMutation() {
    checkOwnerThread();
    if (isDisposed() || worldReleased) throw new IllegalStateException("ArtemisSystem is closed");
    Game game = getGame();
    if (game == null || game.isDisposed()) throw new IllegalStateException("Adapter must be attached to a live Game");
  }

  void invalidate(EntityLink link, boolean deleteEntity) {
    if (!link.valid) return;
    Entity current = worldReleased ? null : world.getEntity(link.id);
    boolean currentCarriesLifetime = current != null && current.isActive()
        && current.getComponent(LinkLifetime.class) == link.lifetime;
    boolean ownsCurrentEntity = currentCarriesLifetime
        && link.lifetime != null && link.lifetime.matches(link);
    link.valid = false;
    registry.remove(link);
    if (link.removeObjectListener != null) { link.removeObjectListener.run(); link.removeObjectListener = null; }
    GameObjectRef ref = link.reference; link.reference = null;
    if (ref != null) ref.clear();
    link.clearOwnedCoreReferences(ownsCurrentEntity);
    if (ownsCurrentEntity) {
      if (deleteEntity) {
        world.delete(link.id);
        link.lifetime.clear();
        link.lifetime = null;
      }
      else {
        current.edit().remove(GameObjectRef.class);
        if (!link.factoryOwned) {
          current.edit().remove(LinkLifetime.class);
          link.lifetime.clear();
          link.lifetime = null;
        }
      }
    } else if (!currentCarriesLifetime && link.lifetime != null) {
      link.lifetime.clear();
      link.lifetime = null;
    } else {
      // If the component is attached to a different current link, only drop
      // this stale link's pointer; never clear the other link's witness.
      link.lifetime = null;
    }
  }
  void rollbackCreatedLink(EntityLink link) {
    if (link.valid) {
      link.factoryOwned = false;
      invalidate(link, true);
      return;
    }
    // A public-reference failure may already have invalidated the handle. The
    // private witness still authorizes rollback of this factory's entity only.
    Entity current = worldReleased ? null : world.getEntity(link.id);
    boolean currentCarriesLifetime = current != null && current.isActive()
        && current.getComponent(LinkLifetime.class) == link.lifetime;
    boolean ownsCurrentEntity = currentCarriesLifetime
        && link.lifetime != null && link.lifetime.matches(link);
    link.factoryOwned = false;
    if (ownsCurrentEntity) {
      world.delete(link.id);
      link.lifetime.clear();
    } else if (!currentCarriesLifetime && link.lifetime != null) {
      link.lifetime.clear();
    }
    link.lifetime = null;
  }

  private void housekeeping() {
    checkOwnerThread();
    for (EntityLink link : registry.snapshot()) {
      if (!link.valid) continue;
      if (!link.hasValidReference()) { invalidate(link, false); continue; }
      if (link.object.isDestroyed()) { invalidate(link, true); continue; }
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
      for (EntityLink link : registry.snapshot()) {
        if (!link.valid || !link.synchronizationEnabled) continue;
        link.copyToEcs();
        if (isDisposed() || worldReleased || releasePending) break;
      }
      if (!isDisposed() && !worldReleased && !releasePending) {
        world.setDelta(delta);
        world.process();
        if (!isDisposed() && !worldReleased && !releasePending) {
          housekeeping();
          for (EntityLink link : registry.snapshot()) {
            if (!link.valid || !link.synchronizationEnabled) continue;
            link.copyToObject();
            if (isDisposed() || worldReleased || releasePending) break;
          }
        }
      }
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
