package org.javity.ecs.artemis;

import static org.junit.Assert.*;

import com.artemis.*;
import java.util.*;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.serialization.Prefab;
import org.javity.serialization.SceneJson;
import org.junit.Test;

/** Real Artemis regressions for the final combined ownership/reentrancy review. */
public class FinalIntegrationRegressionTest {
  public static class Value extends org.javity.core.Component {}
  public static class NativeValue extends com.artemis.Component {}

  @Test public void laterDestroyedPrefabObjectRollsBackEvenItsUnboundAllocation() {
    int[] ticks = {0};
    World world = new World(new WorldConfigurationBuilder().with(new BaseSystem() {
      @Override protected void processSystem() { ticks[0]++; }
    }).build());
    try (Game game = new Game()) {
      ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
      GameObject unrelated = game.createObject("existing");
      Entity existing = world.createEntity();
      List<Entity> callbackEntities = new ArrayList<>();
      List<GameObject> callbackObjects = new ArrayList<>(), prefabObjects = new ArrayList<>();
      List<Integer> configured = new ArrayList<>();
      game.start();
      Prefab prefab = new Prefab("{\"version\":1,\"objects\":[{\"name\":\"first\",\"components\":[]},{\"name\":\"second\",\"components\":[]}]}");
      assertThrows(IllegalArgumentException.class, () -> LinkedPrefabFactory.instantiate(bridge, prefab,
          new SceneJson(), edit -> {
            configured.add(edit.getEntityId());
            for (GameObject object : game.getObjects()) {
              if (object.name.equals("first") || object.name.equals("second")) prefabObjects.add(object);
              if (object.name.equals("second")) object.destroy();
            }
            callbackEntities.add(world.createEntity());
            callbackObjects.add(game.createObject("callback owned"));
          }));
      assertEquals("rollback must not process native systems", 0, ticks[0]);
      game.step(.01f);
      assertEquals(1, ticks[0]);
      assertEquals(1, configured.size());
      assertFalse(world.getEntity(configured.get(0)).isActive());
      assertEquals("no allocated prefab entity may remain", 2,
          world.getAspectSubscriptionManager().get(Aspect.all()).getEntities().size());
      assertTrue(existing.isActive());
      assertTrue(callbackEntities.get(0).isActive());
      assertEquals(List.of(unrelated, callbackObjects.get(0)), game.getObjects());
      for (GameObject object : prefabObjects) assertTrue(object.isDestroyed());
    }
  }

  @Test public void beforeWorldHookCannotReenterFromUiSelectorEvenWhilePaused() { recursiveSelector(EcsPhase.BEFORE_WORLD); }
  @Test public void afterWorldHookCannotReenterFromUiSelectorEvenWhilePaused() { recursiveSelector(EcsPhase.AFTER_WORLD); }
  @Test public void uiHookCannotReenterFromItsOwnSelectorEvenWhilePaused() { recursiveSelector(EcsPhase.UI); }

  private void recursiveSelector(EcsPhase phase) {
    for (boolean paused : new boolean[] {false, true}) {
      int[] ticks = {0}, rejected = {0}; boolean[] armed = {false};
      World world = new World(new WorldConfigurationBuilder().with(new BaseSystem() {
        @Override protected void processSystem() { ticks[0]++; }
      }).build());
      try (Game game = new Game()) {
        ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world, phase));
        GameObject object = game.createObject("recursive selector"); Value source = object.addComponent(new Value());
        EntityLink link = bridge.bind(object, world.createEntity());
        link.addProjection(new ComponentProjection<Value, NativeValue>(Value.class, NativeValue.class,
            ProjectionDirection.GO_TO_ECS, ProjectionOwnership.OWNED) {
          @Override protected Value select(GameObject go) {
            if (armed[0]) {
              armed[0] = false;
              assertThrows(IllegalStateException.class, () -> selectedHook(bridge, phase));
              rejected[0]++;
            }
            return source;
          }
          @Override protected void copyToEntity(Value from, NativeValue to) {}
        });
        game.start();
        if (paused) game.pause();
        armed[0] = true; game.step(.01f);
        assertEquals(1, rejected[0]);
        assertEquals(paused && phase != EcsPhase.UI ? 0 : 1, ticks[0]);
        game.resume(); game.step(.01f);
        assertEquals(paused && phase != EcsPhase.UI ? 1 : 2, ticks[0]);
      }
    }
  }

  private void selectedHook(ArtemisSystem bridge, EcsPhase phase) {
    switch (phase) {
      case BEFORE_WORLD -> bridge.onBeforeUpdate(.01f);
      case AFTER_WORLD -> bridge.onAfterUpdate(.01f);
      case UI -> bridge.onUiUpdate(.01f);
    }
  }

  @Test public void selectorFailureResetsWholeHookGuardAndDisposeIsDeferredWithSuppressedFailure() {
    for (boolean dispose : new boolean[] {false, true}) {
      boolean[] inSelector = {false}, armed = {false}; int[] disposals = {0}, ticks = {0};
      RuntimeException selectorFailure = new IllegalStateException("selector"), disposeFailure = new IllegalStateException("dispose");
      World world = new World(new WorldConfigurationBuilder().with(new BaseSystem() {
        @Override protected void processSystem() { ticks[0]++; }
      }).build()) {
        @Override public void dispose() {
          assertFalse("World disposal must wait for selector return", inSelector[0]);
          disposals[0]++; super.dispose();
          if (dispose) throw disposeFailure;
        }
      };
      try (Game game = new Game()) {
        ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
        GameObject object = game.createObject("selector failure"); Value value = object.addComponent(new Value());
        EntityLink link = bridge.bind(object, world.createEntity());
        link.addProjection(new ComponentProjection<Value, NativeValue>(Value.class, NativeValue.class,
            ProjectionDirection.GO_TO_ECS, ProjectionOwnership.OWNED) {
          @Override protected Value select(GameObject go) {
            if (armed[0]) {
              armed[0] = false; inSelector[0] = true;
              try { if (dispose) bridge.dispose(); throw selectorFailure; }
              finally { inSelector[0] = false; }
            }
            return value;
          }
          @Override protected void copyToEntity(Value from, NativeValue to) {}
        });
        game.start(); armed[0] = true;
        assertSame(selectorFailure, assertThrows(RuntimeException.class, () -> game.step(.01f)));
        assertEquals(0, ticks[0]);
        if (dispose) {
          assertEquals(1, disposals[0]); assertFalse(link.isValid());
          assertArrayEquals(new Throwable[] {disposeFailure}, selectorFailure.getSuppressed());
        } else { game.step(.01f); assertEquals(1, ticks[0]); }
      }
      assertEquals(1, disposals[0]);
    }
  }

  @Test public void reservedProjectionClaimsAreRejectedBeforeSelectorsOrNativeMutation() {
    List<Class<? extends com.artemis.Component>> reserved = List.of(InactiveGameObject.class, GameObjectRef.class, LinkLifetime.class);
    try (Game game = new Game()) {
      World world = new World(); ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
      GameObject object = game.createObject("reserved"); Value value = object.addComponent(new Value());
      EntityLink link = bridge.bind(object, world.createEntity()); int id = link.entity();
      GameObjectRef reference = world.getMapper(GameObjectRef.class).get(id);
      LinkLifetime lifetime = world.getMapper(LinkLifetime.class).get(id);
      for (Class<? extends com.artemis.Component> type : reserved) {
        for (ProjectionDirection direction : ProjectionDirection.values()) {
          for (ProjectionOwnership ownership : ProjectionOwnership.values()) {
            if (direction == ProjectionDirection.ECS_TO_GO && ownership == ProjectionOwnership.OWNED) continue;
            int[] callbacks = {0};
            ComponentProjection<Value, ?> projection = reservedProjection(type, direction, ownership, callbacks, value);
            assertThrows(IllegalArgumentException.class, () -> link.addProjection(projection));
            assertEquals(0, callbacks[0]);
            assertTrue(link.projections.isEmpty());
            assertSame(reference, world.getMapper(GameObjectRef.class).get(id));
            assertSame(lifetime, world.getMapper(LinkLifetime.class).get(id));
            assertFalse(world.getMapper(InactiveGameObject.class).has(id));
          }
        }
      }
    }
  }

  @Test public void reservedProjectionRejectionPrecedesInvalidLinkCleanup() {
    try (Game game = new Game()) {
      World world = new World(); ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
      GameObject object = game.createObject("invalid reserved claim"); Value value = object.addComponent(new Value());
      Entity entity = world.createEntity(); EntityLink link = bridge.bind(object, entity);
      LinkLifetime lifetime = entity.getComponent(LinkLifetime.class);
      entity.edit().remove(GameObjectRef.class);
      int[] callbacks = {0};
      assertThrows(IllegalArgumentException.class, () -> link.addProjection(reservedProjection(InactiveGameObject.class,
          ProjectionDirection.GO_TO_ECS, ProjectionOwnership.OWNED, callbacks, value)));
      assertEquals(0, callbacks[0]);
      assertSame(lifetime, entity.getComponent(LinkLifetime.class));
      assertTrue("reserved rejection must not invalidate or mutate the link", link.valid);
    }
  }

  private <E extends com.artemis.Component> ComponentProjection<Value, E> reservedProjection(Class<E> type,
      ProjectionDirection direction, ProjectionOwnership ownership, int[] callbacks, Value value) {
    return new ComponentProjection<>(Value.class, type, direction, ownership) {
      @Override protected Value select(GameObject go) { callbacks[0]++; return value; }
    };
  }

  @Test public void bindRejectsApplicationSuppliedReservedSlotsWithoutTakingOwnership() {
    try (Game game = new Game()) {
      World world = new World(); ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
      for (Class<? extends com.artemis.Component> type : List.of(InactiveGameObject.class, GameObjectRef.class, LinkLifetime.class)) {
        Entity entity = world.createEntity(); com.artemis.Component supplied = entity.edit().create(type);
        assertThrows(IllegalArgumentException.class, () -> bridge.bind(game.createObject("reserved slot"), entity));
        assertSame(supplied, entity.getComponent(type));
        assertTrue(entity.isActive());
      }
    }
  }

  @Test public void unlinkAndReferenceInvalidationReleaseInactiveMarkerAndRestoreNativeAspect() {
    for (int action = 0; action < 3; action++) {
      try (Game game = new Game()) {
        World world = new World(); ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
        GameObject object = game.createObject("inactive"); object.setActive(false);
        Entity entity = world.createEntity(); entity.edit().create(NativeValue.class);
        EntityLink link = bridge.bind(object, entity);
        EntitySubscription active = world.getAspectSubscriptionManager().get(Aspect.all(NativeValue.class).exclude(InactiveGameObject.class));
        game.start(); game.step(.01f);
        assertTrue(entity.getComponent(InactiveGameObject.class) != null);
        assertEquals(0, active.getEntities().size());
        if (action == 0) link.close();
        else {
          entity.edit().remove(GameObjectRef.class);
          if (action == 2) entity.edit().create(GameObjectRef.class);
        }
        game.pause(); game.step(.01f);
        assertFalse(link.isValid());
        assertNull(entity.getComponent(InactiveGameObject.class));
        game.resume(); game.step(.01f);
        assertTrue(entity.isActive()); assertFalse(object.isDestroyed());
        assertEquals(1, active.getEntities().size());
        assertEquals(entity.getId(), active.getEntities().get(0));
      }
    }
  }

  @Test public void oldFactoryRollbackCannotRemoveReboundLifetimeOrInactiveMarker() {
    try (Game game = new Game()) {
      World world = new World(); ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
      game.start();
      Prefab prefab = new Prefab("{\"version\":1,\"objects\":[{\"name\":\"factory\",\"components\":[]}]}");
      EntityLink[] rebound = {null}; InactiveGameObject[] marker = {null};
      assertThrows(IllegalStateException.class, () -> LinkedPrefabFactory.instantiate(bridge, prefab,
          new SceneJson(), edit -> {
            int id = edit.getEntityId();
            edit.remove(GameObjectRef.class); game.step(.01f);
            GameObject replacement = game.createObject("rebound inactive"); replacement.setActive(false);
            rebound[0] = bridge.bind(replacement, world.getEntity(id)); game.step(.01f);
            marker[0] = world.getMapper(InactiveGameObject.class).get(id);
            throw new IllegalStateException("later factory failure");
          }));
      assertNotNull(marker[0]); assertTrue(rebound[0].isValid());
      game.step(.01f);
      assertSame(marker[0], world.getMapper(InactiveGameObject.class).get(rebound[0].entity()));
      assertFalse(rebound[0].object().isDestroyed());
    }
  }

  @Test public void staleHandleCannotRemoveMarkerFromRecycledOrReboundEntity() {
    try (Game game = new Game()) {
      World world = new World(); ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
      GameObject object = game.createObject("old"); object.setActive(false);
      EntityLink old = bridge.bind(object, world.createEntity()); int id = old.entity();
      game.start(); game.step(.01f); world.delete(id); game.step(.01f);
      Entity recycled = world.createEntity(); assertEquals(id, recycled.getId());
      InactiveGameObject supplied = recycled.edit().create(InactiveGameObject.class);
      old.close(); assertSame(supplied, recycled.getComponent(InactiveGameObject.class));
      recycled.edit().remove(InactiveGameObject.class);
      GameObject replacement = game.createObject("replacement"); replacement.setActive(false);
      EntityLink current = bridge.bind(replacement, recycled); game.step(.01f);
      InactiveGameObject marker = recycled.getComponent(InactiveGameObject.class);
      old.close(); game.step(.01f);
      assertSame(marker, recycled.getComponent(InactiveGameObject.class));
      assertTrue(current.isValid());
    }
  }
}
