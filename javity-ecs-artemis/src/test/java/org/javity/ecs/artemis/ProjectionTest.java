package org.javity.ecs.artemis;

import static org.junit.Assert.*;

import com.artemis.*;
import org.javity.core.*;
import org.junit.Test;

public class ProjectionTest {
  public static class CoreValue extends org.javity.core.Component { int value; }
  public static class NativeValue extends com.artemis.Component { int value; }
  public static class UserOwned extends com.artemis.Component { int value; }
  public static class OtherNative extends com.artemis.Component { int value; }
  private static class ValueProjection extends ComponentProjection<CoreValue, NativeValue> {
    ValueProjection(ProjectionDirection direction, ProjectionOwnership ownership) {
      super(CoreValue.class, NativeValue.class, direction, ownership);
    }
    @Override protected void copyToEntity(CoreValue source, NativeValue target) { target.value = source.value; }
    @Override protected void copyToObject(NativeValue source, CoreValue target) { target.value = source.value; }
  }
  private static final class ExactValueProjection extends ValueProjection {
    private final CoreValue chosen;
    ExactValueProjection(CoreValue chosen) { super(ProjectionDirection.GO_TO_ECS, ProjectionOwnership.OWNED); this.chosen = chosen; }
    @Override protected CoreValue select(GameObject object) { return chosen; }
  }

  @Test public void ownedGoToEcsCreatesCopiesAndRemovesItsNativeSlot() {
    Game game = new Game();
    World world = new World(new WorldConfigurationBuilder().build());
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
    GameObject object = game.createObject("projected");
    CoreValue source = object.addComponent(new CoreValue());
    source.value = 13;
    EntityLink link = bridge.bind(object, world.createEntity());
    link.addProjection(new ValueProjection(ProjectionDirection.GO_TO_ECS, ProjectionOwnership.OWNED));
    game.start();
    game.step(.01f);
    assertEquals(13, world.getMapper(NativeValue.class).get(link.entity()).value);
    object.removeComponent(source);
    game.step(.01f);
    assertFalse(world.getMapper(NativeValue.class).has(link.entity()));
    game.close();
  }

  @Test public void duplicateDefaultSourceIsRejectedBeforeProjectionRegistration() {
    Game game = new Game();
    World world = new World(new WorldConfigurationBuilder().build());
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
    GameObject object = game.createObject("ambiguous");
    object.addComponent(new CoreValue());
    CoreValue chosen = object.addComponent(new CoreValue());
    EntityLink link = bridge.bind(object, world.createEntity());
    assertThrows(IllegalStateException.class, () -> link.addProjection(
        new ValueProjection(ProjectionDirection.GO_TO_ECS, ProjectionOwnership.OWNED)));
    link.addProjection(new ExactValueProjection(chosen));
    game.close();
  }

  @Test public void syncOffSuspendsCopiesAndUnlinkReleasesOnlyOwnedProjectionSlots() {
    Game game = new Game();
    World world = new World(new WorldConfigurationBuilder().build());
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
    GameObject object = game.createObject("projected");
    CoreValue source = object.addComponent(new CoreValue()); source.value = 3;
    Entity entity = world.createEntity();
    entity.edit().create(UserOwned.class).value = 99;
    EntityLink link = bridge.bind(object, entity);
    link.addProjection(new ValueProjection(ProjectionDirection.GO_TO_ECS, ProjectionOwnership.OWNED));
    game.start(); game.step(.01f);
    int id = link.entity();
    link.setSynchronizationEnabled(false);
    source.value = 21; game.step(.01f);
    assertEquals(3, world.getMapper(NativeValue.class).get(id).value);
    link.setSynchronizationEnabled(true); game.step(.01f);
    assertEquals(21, world.getMapper(NativeValue.class).get(id).value);
    link.close(); game.step(.01f);
    assertFalse(world.getMapper(NativeValue.class).has(id));
    assertEquals(99, world.getMapper(UserOwned.class).get(id).value);
    game.close();
  }

  @Test public void projectionMayUnlinkItsOwnHandleReentrantly() {
    Game game = new Game();
    World world = new World(new WorldConfigurationBuilder().build());
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
    GameObject object = game.createObject("reentrant"); object.addComponent(new CoreValue());
    EntityLink link = bridge.bind(object, world.createEntity());
    ComponentProjection<CoreValue, NativeValue> projection = new ComponentProjection<>(
        CoreValue.class, NativeValue.class, ProjectionDirection.GO_TO_ECS, ProjectionOwnership.OWNED) {
      @Override protected void copyToEntity(CoreValue source, NativeValue target) { link.close(); }
    };
    link.addProjection(projection);
    game.start();
    game.step(.01f);
    assertFalse(link.isValid());
    game.close();
  }

  @Test public void ecsToGoCopiesAfterNativeProcessingIntoBorrowedCoreTarget() {
    int[] nativeId = {-1};
    BaseSystem writer = new BaseSystem() {
      @Override protected void processSystem() { getWorld().getMapper(NativeValue.class).get(nativeId[0]).value = 42; }
    };
    World world = new World(new WorldConfigurationBuilder().with(writer).build());
    Game game = new Game();
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
    GameObject object = game.createObject("readback");
    CoreValue target = object.addComponent(new CoreValue());
    Entity entity = world.createEntity();
    int id = entity.getId();
    nativeId[0] = id;
    entity.edit().create(NativeValue.class);
    EntityLink link = bridge.bind(object, entity);
    link.addProjection(new ValueProjection(ProjectionDirection.ECS_TO_GO, ProjectionOwnership.BORROWED));
    game.start(); game.step(.01f);
    assertEquals(42, target.value);
    assertTrue(link.isValid());
    game.close();
  }

  @Test public void borrowedNativeTargetIsNeverCreatedOrRemovedByProjection() {
    Game game = new Game();
    World world = new World(new WorldConfigurationBuilder().build());
    Entity entity = world.createEntity();
    NativeValue target = entity.edit().create(NativeValue.class); target.value = -1;
    world.process(); // materialize the borrowed component before transferring World ownership
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
    GameObject object = game.createObject("borrowed");
    CoreValue source = object.addComponent(new CoreValue()); source.value = 8;
    EntityLink link = bridge.bind(object, entity);
    link.addProjection(new ValueProjection(ProjectionDirection.GO_TO_ECS, ProjectionOwnership.BORROWED));
    game.start(); game.step(.01f);
    assertEquals(8, world.getMapper(NativeValue.class).get(link.entity()).value);
    object.removeComponent(source); game.step(.01f);
    assertTrue(world.getMapper(NativeValue.class).has(link.entity()));
    world.edit(link.entity()).remove(NativeValue.class); game.step(.01f);
    assertFalse(world.getMapper(NativeValue.class).has(link.entity()));
    game.close();
  }

  @Test public void ownedProjectionTracksLateAndDisabledSourcesAndInactiveMarker() {
    Game game = new Game();
    World world = new World(new WorldConfigurationBuilder().build());
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
    GameObject object = game.createObject("late");
    EntityLink link = bridge.bind(object, world.createEntity());
    link.addProjection(new ValueProjection(ProjectionDirection.GO_TO_ECS, ProjectionOwnership.OWNED));
    game.start(); game.step(.01f);
    int id = link.entity();
    assertFalse(world.getMapper(NativeValue.class).has(id));
    CoreValue source = object.addComponent(new CoreValue()); source.value = 5;
    game.step(.01f);
    assertEquals(5, world.getMapper(NativeValue.class).get(id).value);
    source.setEnabled(false); game.step(.01f);
    assertFalse(world.getMapper(NativeValue.class).has(id));
    object.setActive(false); game.step(.01f);
    assertTrue(world.getMapper(InactiveGameObject.class).has(id));
    game.close();
  }

  @Test public void nativeBaseSystemFailureStillReleasesLinkedReferenceOnClose() {
    BaseSystem failing = new BaseSystem() {
      @Override protected void processSystem() { throw new IllegalStateException("native failure"); }
    };
    World world = new World(new WorldConfigurationBuilder().with(failing).build());
    Game game = new Game();
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
    GameObject object = game.createObject("failure");
    EntityLink link = bridge.bind(object, world.createEntity());
    game.start();
    assertThrows(RuntimeException.class, () -> game.step(.01f));
    GameObjectRef ref = world.getMapper(GameObjectRef.class).get(link.entity());
    assertNotNull(ref);
    game.close();
    assertFalse(link.isValid());
    assertNull(ref.objectOrNull());
  }

  @Test public void linkedReferenceIsClearedBeforeNativeDisposeFailureEscapes() {
    World world = new World(new WorldConfigurationBuilder().build()) {
      @Override public void dispose() { super.dispose(); throw new IllegalStateException("dispose failure"); }
    };
    Game game = new Game();
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
    GameObject object = game.createObject("dispose failure");
    EntityLink link = bridge.bind(object, world.createEntity());
    game.start(); game.step(.01f);
    GameObjectRef ref = world.getMapper(GameObjectRef.class).get(link.entity());
    assertThrows(IllegalStateException.class, game::close);
    assertFalse(link.isValid());
    assertNull(ref.objectOrNull());
  }
}
