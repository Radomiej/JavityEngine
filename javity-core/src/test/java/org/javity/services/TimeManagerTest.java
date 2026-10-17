package org.javity.services;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.javity.core.Component;
import org.javity.core.Game;
import org.junit.Test;

public class TimeManagerTest {
  @Test
  public void newestLeaseWinsAndRemovingAnyLeaseRevealsCorrectScale() {
    try (Game game = new Game()) {
      TimeManager time = game.addSystem(new TimeManager());
      time.setBaseScale(1.5);
      TimeLease first = time.push(0.5, 5);
      TimeLease second = time.push(2, 5);
      TimeLease third = time.push(3, 5);
      game.start();

      game.step(0);
      assertEquals(3, time.getScale(), 0);
      first.cancel();
      first.close();
      assertFalse(first.isActive());
      game.step(0);
      assertEquals(3, time.getScale(), 0);
      third.close();
      game.step(0);
      assertEquals(2, time.getScale(), 0);
      second.cancel();
      game.step(0);
      assertEquals(1.5, time.getScale(), 0);
    }
  }

  @Test
  public void expiryIsChosenBeforeAgingAndAdvancesDuringPauseAndFreeze() {
    AtomicInteger worldUpdates = new AtomicInteger();
    AtomicInteger timerCalls = new AtomicInteger();
    try (Game game = new Game()) {
      TimeManager time = game.addSystem(new TimeManager());
      game.createObject("world")
          .addComponent(
              new Component() {
                public void onUpdate(float delta) {
                  worldUpdates.incrementAndGet();
                }
              });
      SceneTimers timers = game.createObject("timers").addComponent(new SceneTimers());
      timers.after(0, false, timerCalls::incrementAndGet);
      TimeLease freeze = time.freeze(0.1);
      game.start();

      game.step(0.1f);
      assertEquals(0, worldUpdates.get());
      assertEquals(0, timerCalls.get());
      assertEquals(0, time.getScale(), 0);
      assertFalse(freeze.isActive());
      game.step(0.1f);
      assertEquals(1, worldUpdates.get());
      assertEquals(1, timerCalls.get());

      TimeLease whilePaused = time.push(0.25, 0.1);
      game.pause();
      game.step(0.1f);
      assertFalse(whilePaused.isActive());
      assertEquals(1, worldUpdates.get());
      game.resume();
      game.step(0.1f);
      assertEquals(2, worldUpdates.get());
      assertEquals(1, timerCalls.get());
    }
  }

  @Test
  public void scaleSnapshotScalesWorldOnlyAndLeasesCreatedMidStepStartNextFrame() {
    AtomicInteger worldUpdates = new AtomicInteger();
    float[] worldDelta = {0};
    float[] uiDelta = {0};
    try (Game game = new Game()) {
      TimeManager time = game.addSystem(new TimeManager());
      time.setBaseScale(2);
      game.createObject("world")
          .addComponent(
              new Component() {
                boolean pushed;

                public void onUpdate(float delta) {
                  worldUpdates.incrementAndGet();
                  worldDelta[0] = delta;
                  if (!pushed) {
                    time.freeze(0.1);
                    pushed = true;
                  }
                }
              });
      Component ui =
          game.createObject("ui")
              .addComponent(
                  new Component() {
                    public void onUpdate(float delta) {
                      uiDelta[0] = delta;
                    }
                  });
      ui.updateMode = Component.UpdateMode.UI;
      game.start();

      game.step(0.05f);
      assertEquals(1, worldUpdates.get());
      assertEquals(0.1f, worldDelta[0], 0.00001f);
      assertEquals(0.05f, uiDelta[0], 0.00001f);
      assertEquals(0.1, game.elapsed, 0.00001);
      assertEquals(0.05, game.unscaledElapsed, 0.00001);
      game.step(0.1f);
      assertEquals(1, worldUpdates.get());
      assertEquals(0, time.getScale(), 0);
      game.step(0.1f);
      assertEquals(2, worldUpdates.get());
      assertEquals(2, time.getScale(), 0);
    }
  }

  @Test
  public void zeroDurationIsInactiveAndInputsAreValidated() {
    try (Game game = new Game()) {
      TimeManager time = game.addSystem(new TimeManager());
      time.setBaseScale(1);
      TimeLease zero = time.push(0, 0);
      assertFalse(zero.isActive());
      game.start();
      game.step(0);
      assertEquals(1, time.getScale(), 0);
      assertThrows(IllegalArgumentException.class, () -> time.setBaseScale(-0.1));
      assertThrows(IllegalArgumentException.class, () -> time.setBaseScale(4.01));
      assertThrows(IllegalArgumentException.class, () -> time.setBaseScale(Double.NaN));
      assertThrows(IllegalArgumentException.class, () -> time.push(Double.POSITIVE_INFINITY, 1));
      assertThrows(IllegalArgumentException.class, () -> time.push(1, -1));
      assertThrows(IllegalArgumentException.class, () -> time.freeze(Double.NaN));
    }
  }

  @Test
  public void sceneManagersAndLeasesAreIsolatedAndCloseDeactivatesThem() {
    try (Game first = new Game();
        Game second = new Game()) {
      TimeManager a = first.addSystem(new TimeManager());
      TimeManager b = second.addSystem(new TimeManager());
      a.setBaseScale(2);
      TimeLease lease = a.freeze(10);
      first.start();
      second.start();
      first.step(0);
      second.step(0);
      assertEquals(0, a.getScale(), 0);
      assertEquals(1, b.getScale(), 0);
      first.close();
      assertFalse(lease.isActive());
      second.step(0);
      assertEquals(1, b.getScale(), 0);
    }
  }
}
