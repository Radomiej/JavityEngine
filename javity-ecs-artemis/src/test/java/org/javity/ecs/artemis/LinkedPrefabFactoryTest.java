package org.javity.ecs.artemis;

import static org.junit.Assert.*;

import com.artemis.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
    assertThrows(RuntimeException.class, () -> LinkedPrefabFactory.instantiate(
        bridge, prefab, schema, edit -> { createdId.set(edit.getEntityId()); throw new RuntimeException("configure"); }));
    game.step(.01f);
    assertFalse(existing.isDestroyed());
    assertEquals(1, game.getObjects().size());
    assertFalse(world.getMapper(GameObjectRef.class).has(createdId.get()));
    game.close();
  }

  @Test public void prefabReadFailureCleansOnlyObjectsCreatedDuringThatRead() {
    Game game = new Game();
    GameObject existing = game.createObject("existing");
    World world = new World(new WorldConfigurationBuilder().build());
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
    SceneJson schema = new SceneJson().register("broken", ProjectionTest.CoreValue.class,
        value -> SceneJson.object(), data -> { throw new IllegalStateException("read"); });
    Prefab prefab = new Prefab("{\"version\":1,\"objects\":[{\"name\":\"new\",\"components\":[{\"type\":\"broken\",\"data\":{}}]}]}");
    assertThrows(IllegalStateException.class,
        () -> LinkedPrefabFactory.instantiate(bridge, prefab, schema, null));
    assertFalse(existing.isDestroyed());
    assertEquals(List.of(existing), game.getObjects());
    game.close();
  }
}
