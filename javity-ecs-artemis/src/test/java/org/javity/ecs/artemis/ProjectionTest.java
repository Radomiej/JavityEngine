package org.javity.ecs.artemis;

import static org.junit.Assert.*;

import com.artemis.*;
import java.util.*;
import org.javity.core.*;
import org.junit.Test;

public class ProjectionTest {
  public static class CoreValue extends org.javity.core.Component { int value; }
  public static class NativeValue extends com.artemis.Component { int value; }
  public static class UserOwned extends com.artemis.Component { int value; }
  public static class OtherNative extends com.artemis.Component { int value; }
  public static class CopyCounts { int calls; List<String> order = new ArrayList<>(); }
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

  @Test public void goToEcsCopyRunsOnceImmediatelyBeforeProcessAndNotWhilePausedOrDisabled() {
    CopyCounts counts = new CopyCounts();
    BaseSystem nativeWriter = new BaseSystem() {
      @Override protected void processSystem() {
        counts.order.add("process");
        getWorld().getMapper(NativeValue.class).get(0).value = 77;
      }
    };
    World world = new World(new WorldConfigurationBuilder().with(nativeWriter).build());
    Game game = new Game();
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
    GameObject object = game.createObject("phase");
    CoreValue source = object.addComponent(new CoreValue()); source.value = 5;
    Entity entity = world.createEntity(); int id = entity.getId();
    EntityLink link = bridge.bind(object, entity);
    link.addProjection(new ComponentProjection<CoreValue, NativeValue>(CoreValue.class, NativeValue.class,
        ProjectionDirection.GO_TO_ECS, ProjectionOwnership.OWNED) {
      @Override protected void copyToEntity(CoreValue from, NativeValue to) {
        counts.calls++; counts.order.add("copy"); to.value = from.value;
      }
    });
    game.start();
    assertEquals(0, counts.calls);
    game.step(.01f);
    assertEquals(1, counts.calls);
    assertEquals(List.of("copy", "process"), counts.order);
    assertEquals(77, world.getMapper(NativeValue.class).get(id).value);
    counts.order.clear(); game.pause(); game.step(.01f);
    assertEquals(1, counts.calls);
    game.resume(); link.setSynchronizationEnabled(false); game.step(.01f);
    assertEquals(1, counts.calls);
    game.close();
  }

  @Test public void selectorUnlinkDuringProjectionRegistrationCannotInstallOrCopySession() {
    Game game = new Game(); World world = new World(new WorldConfigurationBuilder().build());
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
    GameObject object = game.createObject("registration unlink"); CoreValue source = object.addComponent(new CoreValue());
    EntityLink link = bridge.bind(object, world.createEntity());
    int[] copies = {0};
    ComponentProjection<CoreValue, NativeValue> projection = new ComponentProjection<>(CoreValue.class,
        NativeValue.class, ProjectionDirection.GO_TO_ECS, ProjectionOwnership.OWNED) {
      @Override protected CoreValue select(GameObject go) { link.close(); return source; }
      @Override protected void copyToEntity(CoreValue from, NativeValue to) { copies[0]++; }
    };
    assertThrows(IllegalStateException.class, () -> link.addProjection(projection));
    game.start(); game.step(.01f);
    assertEquals(0, copies[0]);
    assertFalse(world.getMapper(NativeValue.class).has(link.id));
    game.close();
  }

  @Test public void selectorDestroyingObjectOrClosingAdapterStopsCurrentProjectionCallback() {
    for (int action = 0; action < 2; action++) {
      Game game = new Game(); World world = new World(new WorldConfigurationBuilder().build());
      ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
      GameObject object = game.createObject("selector action"); CoreValue source = object.addComponent(new CoreValue());
      EntityLink link = bridge.bind(object, world.createEntity());
      int selectedAction = action; int[] copies = {0}; boolean[] armed = {false};
      link.addProjection(new ComponentProjection<CoreValue, NativeValue>(CoreValue.class, NativeValue.class,
          ProjectionDirection.GO_TO_ECS, ProjectionOwnership.OWNED) {
        @Override protected CoreValue select(GameObject go) {
          if (!armed[0]) return source;
          if (selectedAction == 0) go.destroy(); else bridge.dispose();
          return source;
        }
        @Override protected void copyToEntity(CoreValue from, NativeValue to) { copies[0]++; }
      });
      game.start(); armed[0] = true;
      try { game.step(.01f); } catch (IllegalStateException expected) { }
      assertEquals(0, copies[0]);
      assertFalse(link.isValid());
      if (!game.isDisposed()) game.close();
    }
  }

  @Test public void copierClosingAdapterBeforeNativeProcessSkipsThatProcess() {
    int[] ticks = {0};
    BaseSystem counter = new BaseSystem() { @Override protected void processSystem() { ticks[0]++; } };
    World world = new World(new WorldConfigurationBuilder().with(counter).build());
    Game game = new Game(); ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
    GameObject object = game.createObject("close from copier"); object.addComponent(new CoreValue());
    EntityLink link = bridge.bind(object, world.createEntity());
    boolean[] armed = {false};
    link.addProjection(new ComponentProjection<CoreValue, NativeValue>(CoreValue.class, NativeValue.class,
        ProjectionDirection.GO_TO_ECS, ProjectionOwnership.OWNED) {
      @Override protected void copyToEntity(CoreValue from, NativeValue to) { if (armed[0]) bridge.dispose(); }
    });
    game.start(); armed[0] = true; game.step(.01f);
    assertEquals(0, ticks[0]);
    assertTrue(bridge.isDisposed());
    game.close();
  }

  @Test public void selectorDisablingSynchronizationStopsTheCurrentCopy() {
    Game game = new Game(); World world = new World(new WorldConfigurationBuilder().build());
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
    GameObject object = game.createObject("disable during selection"); CoreValue source = object.addComponent(new CoreValue());
    EntityLink link = bridge.bind(object, world.createEntity()); int[] copies = {0}; boolean[] armed = {false};
    link.addProjection(new ComponentProjection<CoreValue, NativeValue>(CoreValue.class, NativeValue.class,
        ProjectionDirection.GO_TO_ECS, ProjectionOwnership.OWNED) {
      @Override protected CoreValue select(GameObject go) {
        if (armed[0]) link.setSynchronizationEnabled(false);
        return source;
      }
      @Override protected void copyToEntity(CoreValue from, NativeValue to) { copies[0]++; }
    });
    game.start(); armed[0] = true; game.step(.01f);
    assertEquals(0, copies[0]);
    assertTrue(link.isValid()); assertFalse(link.isSynchronizationEnabled());
    game.close();
  }

  @Test public void unlinkRemovesReplacedProjectionOwnedComponentButPreservesBorrowedSlot() {
    Game game = new Game(); World world = new World(new WorldConfigurationBuilder().build());
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
    GameObject object = game.createObject("replace owned slot"); CoreValue source = object.addComponent(new CoreValue());
    Entity entity = world.createEntity(); entity.edit().create(UserOwned.class);
    EntityLink link = bridge.bind(object, entity);
    link.addProjection(new ValueProjection(ProjectionDirection.GO_TO_ECS, ProjectionOwnership.OWNED));
    game.start(); game.step(.01f); int id = link.entity();
    world.edit(id).add(new NativeValue());
    link.close(); game.step(.01f);
    assertFalse(world.getMapper(NativeValue.class).has(id));
    assertTrue(world.getMapper(UserOwned.class).has(id));
    game.close();
  }
}
