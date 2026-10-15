package org.javity.services;

import static org.junit.Assert.*;

import org.javity.core.*;
import org.javity.input.*;
import org.junit.Test;

public class RuntimeServicesTest {
  @Test
  public void timersRespectPauseRealtimeCancellationAndMutation() {
    try (Game g = new Game()) {
      SceneTimers t = g.createObject("timers").addComponent(new SceneTimers());
      int[] n = {0};
      t.after(
          .1f,
          false,
          () -> {
            n[0]++;
            t.after(0, false, () -> n[0]++);
          });
      t.after(.1f, true, () -> n[0] += 10);
      var cancelled = t.after(0, true, () -> n[0] += 100);
      cancelled.cancel();
      g.start();
      g.pause();
      g.step(.1f);
      assertEquals(10, n[0]);
      g.resume();
      g.step(.1f);
      assertEquals(11, n[0]);
      g.step(0);
      assertEquals(12, n[0]);
      assertEquals(0, t.pendingCount());
    }
  }

  @Test
  public void sceneChangesOnBoundaryAndClosesOnce() {
    int[] destroyed = {0};
    try (SceneManager scenes = new SceneManager()) {
      Game next = new Game();
      Game first =
          new Game() {
            public void onUpdate(float dt) {
              scenes.request(() -> next);
            }

            public void onDestroy() {
              destroyed[0]++;
            }
          };
      scenes.request(() -> first);
      scenes.step(.01f);
      assertSame(next, scenes.current());
      assertTrue(first.isDisposed());
      assertEquals(1, destroyed[0]);
    }
    assertEquals(1, destroyed[0]);
  }

  @Test
  public void sceneStartFailureReleasesNativeOwner() {
    SceneManager scenes = new SceneManager();
    Game bad =
        new Game() {
          public void onCreate() {
            throw new IllegalStateException("bad");
          }
        };
    scenes.request(() -> bad);
    assertThrows(IllegalStateException.class, () -> scenes.step(0));
    assertTrue(bad.isDisposed());
    scenes.close();
  }

  @Test
  public void actionsHandleSharedBindingsRepeatAndFocusLoss() {
    InputActions input = new InputActions();
    input.bind("jump", 32, 13);
    input.keyDown(32);
    input.keyDown(32);
    assertTrue(input.justPressed("jump"));
    input.finishFrame();
    assertTrue(input.isDown("jump"));
    assertFalse(input.justPressed("jump"));
    input.keyDown(13);
    input.keyUp(32);
    assertTrue(input.isDown("jump"));
    input.releaseAll();
    assertFalse(input.isDown("jump"));
    assertTrue(input.justReleased("jump"));
  }

  @Test
  public void assetSystemReleasesBeforeStart() {
    int[] closed = {0};
    var manager =
        new com.badlogic.gdx.assets.AssetManager(null, false) {
          public void dispose() {
            closed[0]++;
            super.dispose();
          }
        };
    Game g = new Game();
    g.addSystem(new SceneAssets(manager));
    g.dispose();
    g.dispose();
    assertEquals(1, closed[0]);
  }

  @Test
  public void timerHandleCancelledWhenRemovedBeforeStart() {
    Game g = new Game();
    var o = g.createObject("timers");
    SceneTimers timers = o.addComponent(new SceneTimers());
    var handle = timers.after(1, false, () -> fail("Should not run"));
    g.dispose();
    assertTrue(handle.isCancelled());
  }

  @Test
  public void zeroDelayWorldTimerWaitsForResume() {
    try (Game game = new Game()) {
      SceneTimers timers = game.createObject("timer").addComponent(new SceneTimers());
      int[] called = {0};
      timers.after(0, false, () -> called[0]++);
      game.start();
      game.pause();
      game.step(0);
      assertEquals(0, called[0]);
      game.resume();
      game.step(0);
      assertEquals(1, called[0]);
    }
  }
}
