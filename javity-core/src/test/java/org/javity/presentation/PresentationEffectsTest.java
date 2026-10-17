package org.javity.presentation;

import static org.junit.Assert.*;

import com.badlogic.gdx.math.Vector2;
import org.javity.core.Game;
import org.javity.effects.Clock;
import org.javity.input.Pickable2D;
import org.javity.input.PointerPicker;
import org.javity.render.Camera2D;
import org.javity.render.FollowTarget2D;
import org.javity.services.TimeManager;
import org.junit.Test;

public class PresentationEffectsTest {
  @Test
  public void impulseSpringIsAnalyticalAndPartitionIndependent() {
    double whole = runPunch(0.1f, 2);
    double split = runPunch(0.05f, 4);
    double omega = 2 * Math.PI * 3;
    double damped = omega * Math.sqrt(1 - .25 * .25);
    double expected = 2 * Math.exp(-.25 * omega * .2) * Math.sin(damped * .2) / damped;
    assertEquals(expected, whole, 1e-6);
    assertEquals(whole, split, 2e-6);
  }

  @Test
  public void impulseSpringRemainsFiniteAtSmallFrequencyAndNearCriticalDamping() {
    try (Game game = new Game()) {
      var object = game.createObject("stable-springs");
      PresentationEffects effects = object.addComponent(new PresentationEffects(Clock.WORLD));
      effects.punchPosition(1, 0, .001, 0, 1);
      effects.punchPosition(1, 0, 1, .9999999, 1);
      effects.punchPosition(1, 0, 1, 1.0000001, 1);
      game.start();
      double acceptedDelta = .1f;
      game.step((float) acceptedDelta);
      Presentation2D pose = presentation(object);
      double omega = 2 * Math.PI;
      double smallFrequencyResponse =
          Math.sin(2 * Math.PI * .001 * acceptedDelta) / (2 * Math.PI * .001);
      assertTrue(Double.isFinite(pose.presentedX()));
      assertEquals(
          smallFrequencyResponse + 2 * acceptedDelta * Math.exp(-omega * acceptedDelta),
          pose.presentedX(),
          1e-5);
    }
  }

  @Test
  public void shakeUsesRepeatableContinuousSeededSamples() {
    Vector2 a = runShake(987654321L);
    Vector2 b = runShake(987654321L);
    assertEquals(a.x, b.x, 1e-6);
    assertEquals(a.y, b.y, 1e-6);
  }

  @Test
  public void shakeChangesAcrossNoiseCellsAndFrequencyAtSameAge() {
    double beforeCell = runShake(1234L, 5, .1f, .09f).x;
    double afterCell = runShake(1234L, 5, .1f, .101f).x;
    double otherFrequency = runShake(1234L, 13, .1f, .1f).x;
    assertNotEquals(beforeCell, afterCell, 1e-4);
    assertNotEquals(runShake(1234L, 5, .1f, .1f).x, otherFrequency, 1e-4);
  }

  @Test
  public void naturalShakeExpiryRemovesOnlyItsContributionAndCallsCompletionOnce() {
    try (Game game = new Game()) {
      var object = game.createObject("shake-expiry").setPosition(12, -7);
      object.transform.rotation = .3f;
      object.transform.scale.set(2, 3);
      object.transform.visualOffset.set(4, -5);
      PresentationEffects effects = object.addComponent(new PresentationEffects(Clock.WORLD));
      int[] completed = {0};
      PresentationHandle handle =
          effects.shake(5, 3, .4f, .2, 7, 45).onComplete(() -> completed[0]++);
      game.start();
      game.step(.1f);
      Presentation2D pose = presentation(object);
      assertNotEquals(12, pose.presentedX(), 1e-5);
      assertNotEquals(.3f, pose.presentedRotation(), 1e-5);
      game.step(.1f);
      game.step(.1f);
      assertFalse(handle.isActive());
      assertEquals(1, completed[0]);
      assertEquals(12, pose.presentedX(), 0);
      assertEquals(-7, pose.presentedY(), 0);
      assertEquals(.3f, pose.presentedRotation(), 0);
      assertEquals(2, pose.presentedScaleX(), 0);
      assertEquals(3, pose.presentedScaleY(), 0);
      assertEquals(16, pose.renderX(), 0);
      assertEquals(-12, pose.renderY(), 0);
      assertEquals(12, object.transform.position.x, 0);
      assertEquals(-7, object.transform.position.y, 0);
      assertEquals(2, object.transform.scale.x, 0);
      assertEquals(3, object.transform.scale.y, 0);
      game.step(.1f);
      assertEquals(1, completed[0]);
    }
  }

  @Test
  public void contributionsCancelIndependentlyAndFollowBaseChanges() {
    try (Game game = new Game()) {
      var object = game.createObject("target").setPosition(10, 20);
      object.transform.visualOffset.set(3, -2);
      PresentationEffects effects = object.addComponent(new PresentationEffects(Clock.WORLD));
      PresentationHandle first = effects.punchPosition(4, 0, 2, .3, 1);
      PresentationHandle second = effects.punchPosition(0, 5, 2, .3, 1);
      game.start();
      game.step(.1f);
      assertNotEquals(10, object.getComponent(Presentation2D.class).presentedX(), 1e-5);
      first.cancel();
      object.transform.position.set(50, 60);
      object.transform.scale.set(2, 3);
      game.step(.1f);
      Presentation2D pose = object.getComponent(Presentation2D.class);
      assertEquals(50, object.transform.position.x, 0);
      assertEquals(60, object.transform.position.y, 0);
      assertEquals(53, pose.renderX(), 1e-5);
      double omega = 2 * Math.PI * 2;
      double damped = omega * Math.sqrt(1 - .3 * .3);
      double expectedOffset = 5 * Math.exp(-.3 * omega * .2) * Math.sin(damped * .2) / damped;
      assertEquals(60 + expectedOffset, pose.presentedY(), 1e-5);
      assertEquals(58 + expectedOffset, pose.renderY(), 1e-5);
      assertTrue(second.isActive());
      first.cancel();
      assertTrue(second.isActive());
    }
  }

  @Test
  public void renderedPoseDrivesCameraConversionsAndPicking() {
    try (Game game = new Game()) {
      var cameraObject = game.createObject("camera").setPosition(10, 20);
      Camera2D camera = cameraObject.addComponent(new Camera2D());
      camera.resize(200, 100);
      PresentationEffects cameraEffects =
          cameraObject.addComponent(new PresentationEffects(Clock.WORLD));
      PresentationHandle cameraShake = cameraEffects.shake(3, 2, .2f, 2, 5, 7L);
      var target = game.createObject("pickable").setPosition(25, 30);
      target.transform.rotation = .25f;
      PresentationHandle targetShake =
          target.addComponent(new PresentationEffects(Clock.WORLD)).shake(4, 0, 0, 2, 5, 12L);
      Pickable2D pickable = target.addComponent(new Pickable2D(20, 20, p -> {}));
      game.start();
      game.step(.05f);
      assertTrue(cameraShake.isActive());
      assertTrue(targetShake.isActive());
      Vector2 center = camera.screenToWorld(100, 50);
      assertEquals(10 + presentation(cameraObject).presentedX() - 10, center.x, 1e-5);
      Vector2 screen = camera.worldToScreen(center.x, center.y);
      assertEquals(100, screen.x, 1e-4);
      assertEquals(50, screen.y, 1e-4);
      assertTrue(
          pickable.contains(
              new Vector2(presentation(target).renderX(), presentation(target).renderY())));
      PointerPicker picker = new PointerPicker(game, camera);
      Vector2 targetScreen =
          camera.worldToScreen(presentation(target).renderX(), presentation(target).renderY());
      assertSame(pickable, picker.pick(camera.screenToWorld(targetScreen.x, targetScreen.y)));
      cameraShake.cancel();
      targetShake.cancel();
    }
  }

  @Test
  public void worldFreezeUiClockPauseCapCompletionAndOwnerLifetime() {
    try (Game game = new Game()) {
      TimeManager time = game.addSystem(new TimeManager());
      var worldObject = game.createObject("world");
      PresentationEffects world = worldObject.addComponent(new PresentationEffects(Clock.WORLD));
      var uiObject = game.createObject("ui");
      PresentationEffects ui = uiObject.addComponent(new PresentationEffects(Clock.UI));
      PresentationHandle worldHandle = world.punchPosition(20, 0, 2, .2, 1);
      PresentationHandle uiHandle = ui.punchPosition(20, 0, 2, .2, 1);
      int[] completed = {0};
      uiHandle.onComplete(() -> completed[0]++);
      game.start();
      time.freeze(.25);
      game.step(.1f);
      double frozenWorld = presentation(worldObject).presentedX();
      double movingUi = presentation(uiObject).presentedX();
      assertEquals(0, frozenWorld, 0);
      assertNotEquals(0, movingUi, 1e-5);
      uiHandle.pause();
      double paused = presentation(uiObject).presentedX();
      game.step(.1f);
      assertEquals(paused, presentation(uiObject).presentedX(), 0);
      uiHandle.resume();
      for (int i = 0; i < 10; i++) world.shake(1, 1, .1f, 2, 20, i);
      for (int i = 0; i < 64; i++) world.shake(1, 0, 0, 2, 20, i);
      assertEquals(64, world.getActiveCount());
      assertFalse(worldHandle.isActive());
      for (int i = 0; i < 10; i++) game.step(.1f);
      assertEquals(1, completed[0]);
      assertFalse(uiHandle.isActive());
      PresentationHandle late = world.punchScale(0, 0, 1, .5, 2);
      worldObject.destroy();
      game.step(.01f);
      assertFalse(late.isActive());
    }
  }

  @Test
  public void callbackCreatedEffectWaitsForNextFrameAndInputsAreBounded() {
    try (Game game = new Game()) {
      var object = game.createObject("owner");
      PresentationEffects effects = object.addComponent(new PresentationEffects(Clock.WORLD));
      PresentationHandle initial = effects.punchPosition(0, 0, 1, .5, .05);
      PresentationHandle[] spawned = {null};
      initial.onComplete(() -> spawned[0] = effects.punchPosition(100, 0, 1, .5, .5));
      game.start();
      game.step(.05f);
      assertNotNull(spawned[0]);
      assertEquals(0, presentation(object).presentedX(), 1e-5);
      game.step(.01f);
      assertNotEquals(0, presentation(object).presentedX(), 1e-5);
      assertThrows(IllegalArgumentException.class, () -> effects.shake(Float.NaN, 0, 0, 1, 2, 0));
      assertThrows(
          IllegalArgumentException.class, () -> effects.punchScale(1, 1, 2, Double.NaN, 1));
      assertThrows(IllegalArgumentException.class, () -> effects.shake(1, 1, 1, 1, 1001, 0));
    }
  }

  @Test
  public void followWritesAuthoritativePoseWhilePresentationRemainsSeparate() {
    try (Game game = new Game()) {
      var target = game.createObject("target").setPosition(40, 50);
      target.addComponent(new PresentationEffects(Clock.WORLD)).shake(8, 0, 0, 2, 5, 2);
      var camera = game.createObject("camera");
      camera.addComponent(new Camera2D());
      camera.addComponent(new FollowTarget2D(target));
      game.start();
      game.step(.1f);
      assertEquals(40, camera.transform.position.x, 1e-5);
      assertEquals(40, target.transform.position.x, 1e-5);
      assertNotEquals(40, presentation(target).presentedX(), 1e-4);
    }
  }

  @Test
  public void twoPlayersOnOneObjectUseIndependentPoseSlotsAndDisabledEffectsSuspend() {
    try (Game game = new Game()) {
      var object = game.createObject("two-players");
      PresentationEffects world = object.addComponent(new PresentationEffects(Clock.WORLD));
      PresentationEffects ui = object.addComponent(new PresentationEffects(Clock.UI));
      PresentationHandle worldHit = world.punchPosition(3, 0, 2, .4, 2);
      PresentationHandle uiHit = ui.punchPosition(0, 4, 2, .4, 2);
      game.start();
      game.step(.1f);
      Presentation2D pose = presentation(object);
      double bothX = pose.presentedX();
      double bothY = pose.presentedY();
      assertNotEquals(0, bothX, 1e-5);
      assertNotEquals(0, bothY, 1e-5);
      worldHit.cancel();
      assertEquals(0, pose.presentedX(), 1e-6);
      assertEquals(bothY, pose.presentedY(), 1e-6);
      ui.setEnabled(false);
      assertEquals(0, pose.presentedY(), 1e-6);
      ui.setEnabled(true);
      assertEquals(bothY, pose.presentedY(), 1e-6);
      assertTrue(uiHit.isActive());
    }
  }

  @Test
  public void removedProviderInvalidatesOldWorldAndUiHandlesAndNewPlayUsesReplacement() {
    try (Game game = new Game()) {
      var object = game.createObject("provider-replacement").setPosition(10, 20);
      PresentationEffects world = object.addComponent(new PresentationEffects(Clock.WORLD));
      PresentationEffects ui = object.addComponent(new PresentationEffects(Clock.UI));
      PresentationHandle oldWorld = world.punchPosition(5, 0, 2, .3, 2);
      PresentationHandle oldUi = ui.punchPosition(0, 5, 2, .3, 2);
      int[] oldCompletions = {0};
      oldWorld.onComplete(() -> oldCompletions[0]++);
      oldUi.onComplete(() -> oldCompletions[0]++);
      game.start();
      game.step(.1f);
      Presentation2D oldProvider = object.getComponent(Presentation2D.class);
      assertNotEquals(10, oldProvider.presentedX(), 1e-5);
      assertNotEquals(20, oldProvider.presentedY(), 1e-5);

      assertTrue(object.removeComponent(oldProvider));
      assertFalse(oldWorld.isActive());
      assertFalse(oldUi.isActive());
      assertEquals(0, world.getActiveCount());
      assertEquals(0, ui.getActiveCount());
      assertEquals(0, oldCompletions[0]);
      assertEquals(0, oldProvider.activeContributionCount());
      assertEquals(10, oldProvider.presentedX(), 0);
      assertEquals(20, oldProvider.presentedY(), 0);

      Presentation2D replacement = object.addComponent(new Presentation2D());
      PresentationHandle newWorld = world.punchPosition(0, 5, 2, .3, 2);
      PresentationHandle newUi = ui.punchPosition(5, 0, 2, .3, 2);
      game.step(.1f);
      assertTrue(newWorld.isActive());
      assertTrue(newUi.isActive());
      assertNotEquals(10, replacement.presentedX(), 1e-5);
      assertNotEquals(20, replacement.presentedY(), 1e-5);
      oldWorld.cancel();
      oldUi.cancel();
      assertTrue(newWorld.isActive());
      assertTrue(newUi.isActive());
    }
  }

  @Test
  public void providerRemovalBeforeStartInvalidatesQueriesAndReacquiresOnNextPlay() {
    try (Game game = new Game()) {
      var object = game.createObject("prestart-provider").setPosition(8, 9);
      PresentationEffects effects = object.addComponent(new PresentationEffects(Clock.WORLD));
      PresentationHandle old = effects.shake(4, 0, .2f, 2, 5, 11);
      Presentation2D oldProvider = object.getComponent(Presentation2D.class);
      assertNotNull(oldProvider);
      assertTrue(object.removeComponent(oldProvider));
      assertFalse(old.isActive());
      assertEquals(0, effects.getActiveCount());
      assertEquals(0, oldProvider.activeContributionCount());

      PresentationHandle cancelledBeforeStart = effects.shake(0, 4, .2f, 2, 5, 12);
      Presentation2D secondProvider = object.getComponent(Presentation2D.class);
      assertNotSame(oldProvider, secondProvider);
      assertTrue(object.removeComponent(secondProvider));
      cancelledBeforeStart.cancel();
      assertFalse(cancelledBeforeStart.isActive());
      assertEquals(0, effects.getActiveCount());
      assertEquals(0, secondProvider.activeContributionCount());

      PresentationHandle current = effects.punchPosition(4, 0, 2, .3, 2);
      Presentation2D replacement = object.getComponent(Presentation2D.class);
      assertNotNull(replacement);
      assertNotSame(secondProvider, replacement);
      game.start();
      game.step(.1f);
      assertTrue(current.isActive());
      assertNotEquals(8, replacement.presentedX(), 1e-5);
      old.cancel();
      assertTrue(current.isActive());
    }
  }

  @Test
  public void sharedObjectCapacityRejectsUnownedPlayerAndEvictsOnlyRequestersOldest() {
    try (Game game = new Game()) {
      var object = game.createObject("shared-capacity");
      PresentationEffects first = object.addComponent(new PresentationEffects(Clock.WORLD));
      PresentationEffects second = object.addComponent(new PresentationEffects(Clock.UI));
      PresentationEffects newcomer = object.addComponent(new PresentationEffects(Clock.WORLD));
      PresentationHandle[] firstHandles = new PresentationHandle[40];
      PresentationHandle[] secondHandles = new PresentationHandle[24];
      for (int i = 0; i < firstHandles.length; i++) firstHandles[i] = first.shake(1, 0, 0, 2, 8, i);
      for (int i = 0; i < secondHandles.length; i++)
        secondHandles[i] = second.shake(0, 1, 0, 2, 8, i + 100);
      game.start();
      assertEquals(64, first.getActiveCount() + second.getActiveCount());
      assertThrows(IllegalStateException.class, () -> newcomer.shake(1, 1, 0, 2, 8, 999));
      assertEquals(0, newcomer.getActiveCount());
      assertTrue(firstHandles[0].isActive());
      assertTrue(secondHandles[0].isActive());

      PresentationHandle replacement = first.shake(2, 0, 0, 2, 8, 1000);
      assertFalse(firstHandles[0].isActive());
      assertTrue(firstHandles[1].isActive());
      assertTrue(secondHandles[0].isActive());
      assertTrue(replacement.isActive());
      assertEquals(40, first.getActiveCount());
      assertEquals(24, second.getActiveCount());
      assertEquals(64, presentation(object).activeContributionCount());
    }
  }

  private double runPunch(float delta, int count) {
    try (Game game = new Game()) {
      var object = game.createObject("punch");
      PresentationEffects effects = object.addComponent(new PresentationEffects(Clock.WORLD));
      effects.punchPosition(2, 0, 3, .25, 1);
      game.start();
      for (int i = 0; i < count; i++) game.step(delta);
      return presentation(object).presentedX();
    }
  }

  private Vector2 runShake(long seed) {
    return runShake(seed, 17, .07f, .11f);
  }

  private Vector2 runShake(long seed, double frequency, float firstDelta, float secondDelta) {
    try (Game game = new Game()) {
      var object = game.createObject("shake");
      PresentationEffects effects = object.addComponent(new PresentationEffects(Clock.WORLD));
      effects.shake(5, 3, .1f, 2, frequency, seed);
      game.start();
      game.step(firstDelta);
      game.step(secondDelta);
      Presentation2D pose = presentation(object);
      return new Vector2(pose.presentedX(), pose.presentedY());
    }
  }

  private static Presentation2D presentation(org.javity.core.GameObject object) {
    return object.getComponent(Presentation2D.class);
  }
}
