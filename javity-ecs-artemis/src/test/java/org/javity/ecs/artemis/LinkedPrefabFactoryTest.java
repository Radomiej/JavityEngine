package org.javity.ecs.artemis;

import static org.junit.Assert.*;

import com.artemis.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.javity.core.*;
import org.javity.serialization.*;
import org.junit.Test;

public class LinkedPrefabFactoryTest {
  @Test public void nativeConfigurationFailureDestroysOnlyNewPrefabObjects() {
    Game game = new Game();
    GameObject existing = game.createObject("existing");
    World world = new World(new WorldConfigurationBuilder().build());
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
    game.start();
    SceneJson schema = new SceneJson();
    Prefab prefab = new Prefab("{\"version\":1,\"objects\":[{\"name\":\"new\",\"components\":[]}]}");
    AtomicInteger createdId = new AtomicInteger(-1);
    AtomicReference<GameObject> callbackOwned = new AtomicReference<>();
    assertThrows(RuntimeException.class, () -> LinkedPrefabFactory.instantiate(
        bridge, prefab, schema, edit -> { createdId.set(edit.getEntityId()); callbackOwned.set(game.createObject("callback owned")); throw new RuntimeException("configure"); }));
    game.step(.01f);
    assertFalse(existing.isDestroyed());
    assertEquals(2, game.getObjects().size());
    assertFalse(callbackOwned.get().isDestroyed());
    assertFalse(world.getMapper(GameObjectRef.class).has(createdId.get()));
    game.close();
  }

  @Test public void prefabReadFailureCleansOnlyObjectsCreatedDuringThatRead() {
    Game game = new Game();
    GameObject existing = game.createObject("existing");
    World world = new World(new WorldConfigurationBuilder().build());
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
    AtomicReference<GameObject> codecOwned = new AtomicReference<>();
    SceneJson schema = new SceneJson().register("broken", ProjectionTest.CoreValue.class,
        value -> SceneJson.object(), data -> { codecOwned.set(game.createObject("codec side effect")); throw new IllegalStateException("read"); });
    Prefab prefab = new Prefab("{\"version\":1,\"objects\":[{\"name\":\"new\",\"components\":[{\"type\":\"broken\",\"data\":{}}]}]}");
    assertThrows(IllegalStateException.class,
        () -> LinkedPrefabFactory.instantiate(bridge, prefab, schema, null));
    assertFalse(existing.isDestroyed());
    assertFalse(codecOwned.get().isDestroyed());
    assertEquals(List.of(existing, codecOwned.get()), game.getObjects());
    game.close();
  }

  @Test public void callbackCreatedObjectSurvivesPrefabRollbackAndSuccessfulInstantiationIsFresh() {
    SceneJson schema = SceneJson.standard();
    Game sourceGame = new Game();
    GameObject parent = sourceGame.createObject("parent").addTag("root");
    Transform3D parentTransform = parent.addComponent(new Transform3D());
    GameObject child = sourceGame.createObject("child").addTag("leaf");
    Transform3D childTransform = child.addComponent(new Transform3D()); childTransform.setParent(parentTransform);
    String json = schema.write(List.of(parent, child));
    sourceGame.close();

    Game game = new Game(); World world = new World(new WorldConfigurationBuilder().build());
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world)); game.start();
    GameObject unrelated = game.createObject("unrelated");
    Prefab prefab = new Prefab(json);
    List<EntityLink> links = LinkedPrefabFactory.instantiate(bridge, prefab, schema, null);
    assertEquals(2, links.size());
    GameObject copiedParent = links.get(0).object(); GameObject copiedChild = links.get(1).object();
    Transform3D copiedParentTransform = copiedParent.getComponent(Transform3D.class);
    Transform3D copiedChildTransform = copiedChild.getComponent(Transform3D.class);
    assertNotSame(parentTransform, copiedParentTransform);
    assertNotSame(childTransform, copiedChildTransform);
    assertNotSame(parent, copiedParent); assertNotSame(child, copiedChild);
    assertSame(copiedParentTransform, copiedChildTransform.getParent());
    assertTrue(copiedParent.hasTag("root")); assertTrue(copiedChild.hasTag("leaf"));
    assertFalse(unrelated.isDestroyed());
    game.close();
  }

  @Test public void wrongThreadFactoryCallRejectsBeforeDecoderOrGameMutation() throws Exception {
    Game game = new Game(); GameObject existing = game.createObject("existing");
    World world = new World(new WorldConfigurationBuilder().build());
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
    AtomicInteger reads = new AtomicInteger();
    SceneJson schema = new SceneJson().register("value", ProjectionTest.CoreValue.class,
        value -> SceneJson.object(), data -> { reads.incrementAndGet(); return new ProjectionTest.CoreValue(); });
    Prefab prefab = new Prefab("{\"version\":1,\"objects\":[{\"name\":\"new\",\"components\":[{\"type\":\"value\",\"data\":{}}]}]}");
    Throwable[] failure = new Throwable[1];
    Thread worker = new Thread(() -> {
      try { LinkedPrefabFactory.instantiate(bridge, prefab, schema, null); }
      catch (Throwable e) { failure[0] = e; }
    });
    worker.start(); worker.join();
    assertTrue(failure[0] instanceof IllegalStateException);
    assertEquals(0, reads.get());
    assertEquals(List.of(existing), game.getObjects());
    game.close();
  }

  @Test public void createLinkedRejectsConfiguratorDestroyingItsNewGameObjectAndRollsBack() {
    Game game = new Game(); World world = new World(new WorldConfigurationBuilder().build());
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world)); game.start();
    AtomicReference<GameObject> created = new AtomicReference<>(); AtomicInteger id = new AtomicInteger(-1);
    assertThrows(IllegalStateException.class, () -> bridge.createLinked("destroyed",
        created::set, edit -> { id.set(edit.getEntityId()); created.get().destroy(); }));
    assertTrue(created.get().isDestroyed());
    game.step(.01f);
    assertFalse(world.getMapper(GameObjectRef.class).has(id.get()));
    game.close();
  }

  @Test public void createLinkedConfiguratorFailureDeletesItsEntityAndObject() {
    Game game = new Game(); GameObject existing = game.createObject("existing");
    World world = new World(new WorldConfigurationBuilder().build());
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world)); game.start();
    AtomicInteger id = new AtomicInteger(-1);
    assertThrows(IllegalArgumentException.class, () -> bridge.createLinked("failed", null,
        edit -> { id.set(edit.getEntityId()); throw new IllegalArgumentException("configure"); }));
    game.step(.01f);
    assertEquals(List.of(existing), game.getObjects());
    assertFalse(world.getMapper(GameObjectRef.class).has(id.get()));
    game.close();
  }

  @Test public void failedPrefabConfigurationDeletesOwnedEntityWhenReservedRefWasRemovedOrReplaced() {
    for (boolean replace : new boolean[] { false, true }) {
      Game game = new Game(); World world = new World(new WorldConfigurationBuilder().build());
      ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world)); game.start();
      Prefab prefab = new Prefab("{\"version\":1,\"objects\":[{\"name\":\"new\",\"components\":[]}]}");
      AtomicInteger id = new AtomicInteger(-1);
      assertThrows(IllegalStateException.class, () -> LinkedPrefabFactory.instantiate(bridge, prefab,
          new SceneJson(), edit -> {
            id.set(edit.getEntityId());
            edit.remove(GameObjectRef.class);
            if (replace) edit.create(GameObjectRef.class);
            throw new IllegalStateException("fail after changing reserved ref");
          }));
      game.step(.01f);
      assertFalse("failed prefab entity must be deleted without another World.process", world.getEntity(id.get()).isActive());
      assertTrue(game.getObjects().isEmpty());
      game.close();
    }
  }

  @Test public void failedCreateLinkedConfigurationDeletesOwnedEntityWhenReservedRefWasRemoved() {
    Game game = new Game(); World world = new World(new WorldConfigurationBuilder().build());
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world)); game.start();
    AtomicInteger id = new AtomicInteger(-1);
    assertThrows(IllegalStateException.class, () -> bridge.createLinked("failed", null, edit -> {
      id.set(edit.getEntityId()); edit.remove(GameObjectRef.class);
      throw new IllegalStateException("fail after removing reserved ref");
    }));
    game.step(.01f);
    assertFalse("failed createLinked entity must be deleted without another World.process", world.getEntity(id.get()).isActive());
    assertTrue(game.getObjects().isEmpty());
    game.close();
  }

  @Test public void failedCreateLinkedDoesNotDeleteEntityRecycledDuringConfigurator() {
    Game game = new Game(); World world = new World(new WorldConfigurationBuilder().build());
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world)); game.start();
    AtomicInteger id = new AtomicInteger(-1); AtomicReference<Entity> replacement = new AtomicReference<>();
    assertThrows(IllegalStateException.class, () -> bridge.createLinked("failed", null, edit -> {
      id.set(edit.getEntityId());
      world.delete(id.get()); world.process(); replacement.set(world.createEntity());
      assertEquals(id.get(), replacement.get().getId());
      throw new IllegalStateException("fail after recycling native ID");
    }));
    game.step(.01f);
    assertTrue("unrelated entity reusing the ID must survive rollback", replacement.get().isActive());
    game.close();
  }

  @Test public void prefabKeepsRollbackOwnershipForEveryPairUntilTheWholeFactoryCommits() {
    Game game = new Game(); World world = new World(new WorldConfigurationBuilder().build());
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world)); game.start();
    Prefab prefab = new Prefab("{\"version\":1,\"objects\":[{\"name\":\"first\",\"components\":[]},{\"name\":\"second\",\"components\":[]}]}");
    java.util.List<Integer> ids = new java.util.ArrayList<>();
    assertThrows(IllegalStateException.class, () -> LinkedPrefabFactory.instantiate(bridge, prefab,
        new SceneJson(), edit -> {
          ids.add(edit.getEntityId());
          if (ids.size() == 2) {
            world.edit(ids.get(0)).remove(GameObjectRef.class);
            game.step(.01f);
            throw new IllegalStateException("later pair fails after earlier link invalidation");
          }
        }));
    game.step(.01f);
    assertEquals(2, ids.size());
    assertFalse(world.getEntity(ids.get(0)).isActive());
    assertFalse(world.getEntity(ids.get(1)).isActive());
    assertTrue(game.getObjects().isEmpty());
    game.close();
  }

  @Test public void oldPrefabRollbackCannotClearWitnessOfEntityReboundDuringConfiguration() {
    Game game = new Game(); World world = new World(new WorldConfigurationBuilder().build());
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world)); game.start();
    Prefab prefab = new Prefab("{\"version\":1,\"objects\":[{\"name\":\"factory\",\"components\":[]}]}");
    AtomicReference<EntityLink> replacementLink = new AtomicReference<>();
    AtomicReference<GameObject> unrelated = new AtomicReference<>();
    assertThrows(IllegalStateException.class, () -> LinkedPrefabFactory.instantiate(bridge, prefab,
        new SceneJson(), edit -> {
          int id = edit.getEntityId();
          world.edit(id).remove(GameObjectRef.class);
          game.step(.01f);
          unrelated.set(game.createObject("unrelated"));
          replacementLink.set(bridge.bind(unrelated.get(), world.getEntity(id)));
          throw new IllegalStateException("old factory transaction fails after rebind");
        }));
    assertTrue("unrelated binding must remain valid after old rollback", replacementLink.get().isValid());
    assertSame(unrelated.get(), world.getMapper(GameObjectRef.class).get(replacementLink.get().entity()).objectOrNull());
    game.step(.01f);
    assertTrue(replacementLink.get().isValid());
    assertFalse(unrelated.get().isDestroyed());
    game.close();
  }
}
