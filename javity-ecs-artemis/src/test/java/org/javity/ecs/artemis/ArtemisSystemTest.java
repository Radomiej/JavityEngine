package org.javity.ecs.artemis;

import static org.junit.Assert.*;

import com.artemis.*;
import com.artemis.utils.IntBag;
import java.util.*;
import org.javity.core.Game;
import org.javity.services.TimeManager;
import org.junit.Test;

public class ArtemisSystemTest {
  public static class Position extends Component {
    int x;
  }

  private static class CountingSystem extends BaseSystem {
    int calls;
    float delta;
    RuntimeException failure;

    World nativeWorld() { return getWorld(); }

    @Override
    protected void processSystem() {
      calls++;
      delta = world.getDelta();
      if (failure != null) throw failure;
    }
  }

  private static class CountingWorld extends World {
    int disposeCalls;
    int disposeDuringProcess;
    RuntimeException disposeFailure;
    boolean processing;

    CountingWorld(WorldConfiguration configuration) { super(configuration); }

    @Override public void process() {
      processing = true;
      try { super.process(); } finally { processing = false; }
    }

    @Override public void dispose() {
      disposeCalls++;
      if (processing) disposeDuringProcess++;
      if (disposeFailure != null) throw disposeFailure;
      super.dispose();
    }
  }

  private World world(CountingSystem system) {
    return new World(new WorldConfigurationBuilder().with(system).build());
  }

  @Test
  public void nativeWorldRunsOnceInSelectedPhaseWithGameDeltaAndOrder() {
    for (EcsPhase phase : EcsPhase.values()) {
      List<String> order = new ArrayList<>();
      CountingSystem nativeSystem = new CountingSystem() {
        @Override protected void processSystem() { super.processSystem(); order.add("native"); }
      };
      try (Game game = new Game()) {
        ArtemisSystem adapter = game.addSystem(new ArtemisSystem(world(nativeSystem), phase));
        game.addSystem(new org.javity.core.GameSystem() {
          @Override public void onUiUpdate(float dt) { order.add("ui"); }
          @Override public void onBeforeUpdate(float dt) { order.add("before"); }
          @Override public void onAfterUpdate(float dt) { order.add("after"); }
        });
        assertSame(nativeSystem.nativeWorld(), adapter.world());
        game.start();
        game.step(.04f);
        assertEquals(1, nativeSystem.calls);
        assertEquals(.04f, nativeSystem.delta, .0001f);
        assertEquals(phase == EcsPhase.UI ? List.of("native", "ui", "before", "after")
            : phase == EcsPhase.BEFORE_WORLD ? List.of("ui", "native", "before", "after")
            : List.of("ui", "before", "native", "after"), order);
      }
    }
  }

  @Test
  public void worldPhaseHonorsPauseAndTimeScaleWhileUiRemainsUnscaled() {
    CountingSystem worldSystem = new CountingSystem();
    try (Game game = new Game()) {
      game.addSystem(new TimeManager());
      game.getSystem(TimeManager.class).setBaseScale(.5);
      game.addSystem(new ArtemisSystem(world(worldSystem), EcsPhase.BEFORE_WORLD));
      game.start();
      game.step(.08f);
      assertEquals(1, worldSystem.calls);
      assertEquals(.04f, worldSystem.delta, .0001f);
      game.pause();
      game.step(.08f);
      game.resume();
      game.getSystem(TimeManager.class).setBaseScale(0);
      game.step(.08f);
      assertEquals(1, worldSystem.calls);
    }
    CountingSystem uiSystem = new CountingSystem();
    try (Game game = new Game()) {
      game.addSystem(new TimeManager());
      game.getSystem(TimeManager.class).setBaseScale(.5);
      game.addSystem(new ArtemisSystem(world(uiSystem), EcsPhase.UI));
      game.start();
      game.step(.08f);
      assertEquals(1, uiSystem.calls);
      assertEquals(.08f, uiSystem.delta, .0001f);
      game.pause();
      game.step(.08f);
      game.resume();
      game.getSystem(TimeManager.class).setBaseScale(0);
      game.step(.08f);
      assertEquals(3, uiSystem.calls);
    }
  }

  @Test
  public void exposesNativeMapperSubscriptionBagAndArchetypeApi() {
    CountingSystem nativeSystem = new CountingSystem();
    World world = world(nativeSystem);
    try (Game game = new Game()) {
      ArtemisSystem adapter = game.addSystem(new ArtemisSystem(world));
      ComponentMapper<Position> positions = adapter.world().getMapper(Position.class);
      EntitySubscription subscription = adapter.world().getAspectSubscriptionManager()
          .get(Aspect.all(Position.class));
      Archetype archetype = new ArchetypeBuilder().add(Position.class).build(adapter.world());
      int id = adapter.world().createEntity(archetype).getId();
      game.start();
      game.step(.01f);
      IntBag entities = subscription.getEntities();
      assertEquals(1, entities.size());
      assertEquals(id, entities.get(0));
      positions.get(id).x = 17;
      assertEquals(17, positions.get(id).x);
      adapter.world().edit(id).remove(Position.class);
      game.step(.01f);
      assertEquals(0, subscription.getEntities().size());
    }
  }

  @Test
  public void rejectsWrongThreadWorldAccessAndDisposeBeforeChangingState() throws Exception {
    CountingSystem nativeSystem = new CountingSystem();
    ArtemisSystem adapter = new ArtemisSystem(world(nativeSystem));
    Throwable[] failures = new Throwable[3];
    Thread other = new Thread(() -> {
      try { adapter.world(); } catch (Throwable failure) { failures[0] = failure; }
      try { adapter.dispose(); } catch (Throwable failure) { failures[1] = failure; }
      try { adapter.onBeforeUpdate(.01f); } catch (Throwable failure) { failures[2] = failure; }
    });
    other.start();
    other.join();
    assertTrue(failures[0] instanceof IllegalStateException);
    assertTrue(failures[1] instanceof IllegalStateException);
    assertTrue(failures[2] instanceof IllegalStateException);
    assertFalse(adapter.isDisposed());
    assertSame(nativeSystem.nativeWorld(), adapter.world());
    adapter.dispose();
    assertTrue(adapter.isDisposed());
  }

  @Test
  public void closesBeforeStartRepeatedlyAndAfterProcessFailure() {
    CountingSystem beforeStartSystem = new CountingSystem();
    CountingWorld ownedWorld = new CountingWorld(
        new WorldConfigurationBuilder().with(beforeStartSystem).build());
    ArtemisSystem beforeStart = new ArtemisSystem(ownedWorld);
    beforeStart.dispose();
    beforeStart.dispose();
    assertEquals(1, ownedWorld.disposeCalls);
    assertThrows(IllegalStateException.class, beforeStart::world);

    CountingSystem failing = new CountingSystem();
    RuntimeException processFailure = new RuntimeException("native process");
    failing.failure = processFailure;
    CountingWorld failedWorld = new CountingWorld(
        new WorldConfigurationBuilder().with(failing).build());
    try (Game game = new Game()) {
      game.addSystem(new ArtemisSystem(failedWorld));
      game.start();
      assertSame(processFailure, assertThrows(RuntimeException.class, () -> game.step(.02f)));
    }
    assertEquals(1, failedWorld.disposeCalls);
  }

  @Test
  public void nativeDisposeFailureStillMarksAdapterDisposed() {
    CountingWorld world = new CountingWorld(new WorldConfigurationBuilder().build());
    world.disposeFailure = new RuntimeException("native dispose");
    ArtemisSystem adapter = new ArtemisSystem(world);
    assertThrows(RuntimeException.class, adapter::dispose);
    assertTrue(adapter.isDisposed());
    assertEquals(1, world.disposeCalls);
    adapter.dispose();
    assertEquals(1, world.disposeCalls);
    assertThrows(IllegalStateException.class, adapter::world);
  }

  @Test
  public void rejectsRecursiveSelectedHookFromNativeProcessCallback() {
    ArtemisSystem[] adapter = new ArtemisSystem[1];
    CountingSystem nativeSystem = new CountingSystem() {
      @Override protected void processSystem() {
        calls++;
        adapter[0].onBeforeUpdate(world.getDelta());
      }
    };
    try (Game game = new Game()) {
      adapter[0] = game.addSystem(new ArtemisSystem(world(nativeSystem)));
      game.start();
      assertThrows(IllegalStateException.class, () -> game.step(.02f));
      assertEquals(1, nativeSystem.calls);
    }
  }

  @Test
  public void defersOwnedWorldDisposeUntilNativeProcessCallbackReturns() {
    Game[] game = new Game[1];
    CountingWorld[] world = new CountingWorld[1];
    CountingSystem nativeSystem = new CountingSystem() {
      @Override protected void processSystem() {
        calls++;
        game[0].close();
      }
    };
    world[0] = new CountingWorld(new WorldConfigurationBuilder().with(nativeSystem).build());
    ArtemisSystem[] adapter = new ArtemisSystem[1];
    game[0] = new Game();
    adapter[0] = game[0].addSystem(new ArtemisSystem(world[0]));
    game[0].start();

    game[0].step(.02f);

    assertEquals(1, nativeSystem.calls);
    assertEquals(1, world[0].disposeCalls);
    assertEquals(0, world[0].disposeDuringProcess);
    assertTrue(adapter[0].isDisposed());
  }

  @Test
  public void duplicateAdapterTypeIsRejectedByGame() {
    try (Game game = new Game()) {
      World first = new World(new WorldConfigurationBuilder().build());
      World second = new World(new WorldConfigurationBuilder().build());
      game.addSystem(new ArtemisSystem(first));
      assertThrows(IllegalArgumentException.class, () -> game.addSystem(new ArtemisSystem(second)));
      second.dispose();
    }
  }
}
