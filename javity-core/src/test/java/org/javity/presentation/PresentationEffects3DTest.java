package org.javity.presentation;

import static org.junit.Assert.*;

import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.effects.Clock;
import org.javity.effects.EffectSystem;
import org.javity.effects.Feedback;
import org.javity.effects.FeedbackPlayer;
import org.junit.Test;

public class PresentationEffects3DTest {
  @Test
  public void punchMatchesKnownUnderdampedImpulsePointAndExpiresExactly() {
    try (Game game = new Game()) {
      GameObject o = game.createObject("punch");
      o.addComponent(new Transform3D());
      Presentation3D provider = o.addComponent(new Presentation3D());
      PresentationEffects3D effects = o.addComponent(new PresentationEffects3D(Clock.WORLD));
      game.start();
      var h = effects.play(PresentationEffects3D.punchPosition(2, 0, 0, 3, .25, .2));
      game.step(.1f);
      var pose = new Presentation3D.Pose();
      provider.snapshot(pose);
      double wn = 2 * Math.PI * 3, wd = wn * Math.sqrt(1 - .25 * .25);
      double expected = 2 * Math.exp(-.25 * wn * .1) * Math.sin(wd * .1) / wd;
      assertEquals(expected, pose.translationX(), 2e-6);
      game.step(.1f);
      assertFalse(h.isActive());
      provider.snapshot(pose);
      assertEquals(0, pose.translationX(), 0);
    }
  }

  @Test
  public void punchSupportsUnderCriticalAndOverDampingIncludingFiniteNearCriticalValues() {
    for (double zeta : new double[] {0, .4, 1, 2, .9999999, 1.0000001}) {
      try (Game game = new Game()) {
        GameObject o = game.createObject("damping-" + zeta);
        o.addComponent(new Transform3D());
        Presentation3D p = o.addComponent(new Presentation3D());
        PresentationEffects3D fx = o.addComponent(new PresentationEffects3D(Clock.WORLD));
        game.start();
        fx.play(PresentationEffects3D.punchPosition(1, 0, 0, 1, zeta, 1));
        game.step(.1f);
        var pose = new Presentation3D.Pose();
        p.snapshot(pose);
        assertTrue(Double.isFinite(pose.translationX()));
        assertEquals(impulse(.1, 1, zeta), pose.translationX(), 2e-6);
      }
    }
  }

  private static double impulse(double t, double hz, double zeta) {
    double wn = 2 * Math.PI * hz;
    if (zeta < 1) {
      double wd = wn * Math.sqrt(1 - zeta * zeta);
      return Math.exp(-zeta * wn * t) * Math.sin(wd * t) / wd;
    }
    if (zeta == 1) return t * Math.exp(-wn * t);
    double root = Math.sqrt(zeta * zeta - 1), r1 = -wn * (zeta - root), gap = 2 * wn * root;
    return Math.exp(r1 * t) * -Math.expm1(-gap * t) / gap;
  }

  @Test
  public void springSubdivisionAndGoalChangePreservePositionAndVelocity() {
    double whole = runSpring(.1f, 1), split = runSpring(.05f, 2);
    assertEquals(whole, split, 2e-6);
  }

  private double runSpring(float dt, int n) {
    try (Game game = new Game()) {
      GameObject o = game.createObject("spring");
      o.addComponent(new Transform3D());
      Presentation3D p = o.addComponent(new Presentation3D());
      PresentationEffects3D fx = o.addComponent(new PresentationEffects3D(Clock.WORLD));
      game.start();
      var h = fx.play(PresentationEffects3D.springToOffset(1, 0, 0, 2, .7, 1));
      game.step(.1f);
      var pose = new Presentation3D.Pose();
      p.snapshot(pose);
      double before = pose.translationX();
      h.setGoal(2, 0, 0);
      p.snapshot(pose);
      assertEquals(before, pose.translationX(), 0);
      for (int i = 0; i < n; i++) game.step(dt);
      p.snapshot(pose);
      return pose.translationX();
    }
  }

  @Test
  public void seededShakeAndTimeBobAreStableAtEqualTimeAndIntensityCanEaseToZero() {
    assertEquals(shakeAt(.09f, 9981), shakeAt(.09f, 9981), 1e-7);
    try (Game game = new Game()) {
      GameObject o = game.createObject("bob");
      o.addComponent(new Transform3D());
      Presentation3D provider = o.addComponent(new Presentation3D());
      PresentationEffects3D fx = o.addComponent(new PresentationEffects3D(Clock.WORLD));
      game.start();
      var bob = fx.play(PresentationEffects3D.timeBob(0, 1, 0, 2, 0, 1));
      game.step(.125f);
      var pose = new Presentation3D.Pose();
      provider.snapshot(pose);
      assertEquals(Math.sin(2 * Math.PI * 2 * .1), pose.translationY(), 1e-6);
      bob.setTimeBobIntensity(0);
      game.step(.1f);
      provider.snapshot(pose);
      assertTrue(pose.translationY() > 0);
      assertTrue(pose.translationY() < 1);
    }
  }

  @Test
  public void shakeEndsAtZeroAndStaleGenerationCannotCloseItsReplacement() {
    try (Game game = new Game()) {
      GameObject o = game.createObject("bounded-slots");
      o.addComponent(new Transform3D());
      Presentation3D p = o.addComponent(new Presentation3D());
      PresentationEffects3D fx = o.addComponent(new PresentationEffects3D(Clock.WORLD));
      game.start();
      var first =
          fx.play(
              PresentationEffects3D.shake(
                  PresentationEffects3D.Surface.MODEL, 1, 0, 0, 0, 0, 0, .2, 5, 3));
      game.step(.1f);
      game.step(.1f);
      assertFalse(first.isActive());
      var pose = new Presentation3D.Pose();
      p.snapshot(pose);
      assertEquals(0, pose.translationX(), 0);
      var old = fx.play(PresentationEffects3D.punchPosition(1, 0, 0, 2, .5, 1));
      old.cancel();
      var fresh = fx.play(PresentationEffects3D.punchPosition(1, 0, 0, 2, .5, 1));
      old.close();
      assertTrue(fresh.isActive());
      assertEquals(1, fx.activeCount());
    }
  }

  @Test
  public void callbackFailureDoesNotLeakItsSlotOrSkipLaterEffects() {
    try (Game game = new Game()) {
      game.addSystem(new EffectSystem());
      GameObject o = game.createObject("callback-cleanup");
      o.addComponent(new Transform3D());
      Presentation3D p = o.addComponent(new Presentation3D());
      PresentationEffects3D fx = o.addComponent(new PresentationEffects3D(Clock.WORLD));
      game.start();
      var failing =
          fx.play(PresentationEffects3D.punchPosition(1, 0, 0, 2, .5, .1))
              .onComplete(
                  () -> {
                    throw new IllegalStateException("callback");
                  });
      var survivor = fx.play(PresentationEffects3D.punchPosition(2, 0, 0, 2, .5, 1));
      assertThrows(IllegalStateException.class, () -> game.step(.1f));
      assertFalse(failing.isActive());
      assertTrue(survivor.isActive());
      var pose = new Presentation3D.Pose();
      p.snapshot(pose);
      assertNotEquals(0, pose.translationX(), 1e-7);
    }
  }

  @Test
  public void callbackFailureCannotCancelSuccessorAllocatedIntoReleasedSlot() {
    try (Game game = new Game()) {
      game.addSystem(new EffectSystem());
      GameObject o = game.createObject("callback-successor");
      o.addComponent(new Transform3D());
      Presentation3D provider = o.addComponent(new Presentation3D());
      PresentationEffects3D effects = o.addComponent(new PresentationEffects3D());
      game.start();
      PresentationEffects3D.Playback[] successor = {null};
      effects
          .play(PresentationEffects3D.punchPosition(1, 0, 0, 2, .5, .1))
          .onComplete(
              () -> {
                successor[0] = effects.play(PresentationEffects3D.punchPosition(4, 0, 0, 2, .5, 1));
                throw new IllegalStateException("after successor allocation");
              });
      assertThrows(IllegalStateException.class, () -> game.step(.1f));
      assertNotNull(successor[0]);
      assertTrue(successor[0].isActive());
      assertEquals(1, effects.activeCount());
      game.step(.1f);
      assertTrue(successor[0].isActive());
      Presentation3D.Pose pose = new Presentation3D.Pose();
      provider.snapshot(pose);
      assertNotEquals(0, pose.translationX(), 1e-7);
    }
  }

  @Test
  public void providerCyclesInvalidateModelCameraAndPausedHandlesOnFirstRead() {
    try (Game game = new Game()) {
      GameObject owner = game.createObject("provider-cycle");
      owner.addComponent(new Transform3D());
      Presentation3D model = owner.addComponent(new Presentation3D());
      CameraPresentation3D camera =
          owner.addComponent(new CameraPresentation3D(CameraPresentation3D.Space.WORLD));
      PresentationEffects3D effects = owner.addComponent(new PresentationEffects3D());
      game.start();
      var modelHandle = effects.play(PresentationEffects3D.punchPosition(1, 0, 0, 2, .5, 1));
      modelHandle.pause();
      model.setEnabled(false);
      model.setEnabled(true);
      assertFalse(modelHandle.isActive());
      assertEquals(0, effects.activeCount());

      var cameraHandle =
          effects.play(
              PresentationEffects3D.shake(
                  PresentationEffects3D.Surface.WORLD_CAMERA, 1, 0, 0, 0, 0, 0, 1, 2, 1));
      cameraHandle.pause();
      camera.setEnabled(false);
      camera.setEnabled(true);
      assertFalse(cameraHandle.isActive());
      assertEquals(0, effects.activeCount());

      var ownerHandle = effects.play(PresentationEffects3D.punchPosition(1, 0, 0, 2, .5, 1));
      owner.setActive(false);
      owner.setActive(true);
      assertFalse(ownerHandle.isActive());
      assertEquals(0, effects.activeCount());

      var replacedProviderHandle =
          effects.play(PresentationEffects3D.punchPosition(1, 0, 0, 2, .5, 1));
      owner.removeComponent(model);
      Presentation3D replacement = owner.addComponent(new Presentation3D());
      game.initializePendingComponents();
      assertFalse(replacedProviderHandle.isActive());
      var replacementHandle = effects.play(PresentationEffects3D.punchPosition(1, 0, 0, 2, .5, 1));
      assertTrue(replacementHandle.isActive());
      assertNotNull(replacement);
    }
  }

  @Test
  public void completionCallbacksShareEffectSystemBudgetAndNextFrameScope() {
    try (Game game = new Game()) {
      game.addSystem(new EffectSystem());
      GameObject owner = game.createObject("callback-scope");
      owner.addComponent(new Transform3D());
      owner.addComponent(new Presentation3D());
      PresentationEffects3D effects = owner.addComponent(new PresentationEffects3D());
      FeedbackPlayer feedback = owner.addComponent(new FeedbackPlayer(Clock.WORLD));
      game.start();
      int[] ran = {0};
      effects
          .play(PresentationEffects3D.punchPosition(1, 0, 0, 2, .5, .1))
          .onComplete(() -> feedback.play(Feedback.callback(() -> ran[0]++)));
      game.step(.1f);
      assertEquals(0, ran[0]);
      game.step(.1f);
      assertEquals(1, ran[0]);

      // Reentrant late callbacks consume the same per-frame budget and unwind without a live slot.
      var completed = effects.play(PresentationEffects3D.punchPosition(1, 0, 0, 2, .5, .1));
      game.step(.1f);
      int[] callbacks = {0};
      assertThrows(
          IllegalStateException.class,
          () ->
              completed.onComplete(
                  new Runnable() {
                    @Override
                    public void run() {
                      callbacks[0]++;
                      completed.onComplete(this);
                    }
                  }));
      assertTrue(callbacks[0] > 0);
      assertEquals(0, effects.activeCount());
    }
  }

  @Test
  public void lateCompletionRegistrationAfterOwnerRemovalIsSuppressed() {
    try (Game game = new Game()) {
      game.addSystem(new EffectSystem());
      GameObject owner = game.createObject("late-callback-owner");
      owner.addComponent(new Transform3D());
      owner.addComponent(new Presentation3D());
      PresentationEffects3D effects = owner.addComponent(new PresentationEffects3D());
      game.start();
      var completed = effects.play(PresentationEffects3D.punchPosition(1, 0, 0, 2, .5, .1));
      game.step(.1f);
      owner.removeComponent(effects);
      int[] calls = {0};
      completed.onComplete(() -> calls[0]++);
      assertEquals(0, calls[0]);
    }
  }

  private double shakeAt(float dt, long seed) {
    try (Game game = new Game()) {
      GameObject o = game.createObject("shake");
      o.addComponent(new Transform3D());
      Presentation3D p = o.addComponent(new Presentation3D());
      PresentationEffects3D fx = o.addComponent(new PresentationEffects3D(Clock.WORLD));
      game.start();
      fx.play(
          PresentationEffects3D.shake(
              PresentationEffects3D.Surface.MODEL, 1, 2, 3, .1, .2, .3, 1, 7, seed));
      game.step(dt);
      var pose = new Presentation3D.Pose();
      p.snapshot(pose);
      return pose.translationX();
    }
  }

  @Test
  public void rapidRecoilContributionsOverlapAndOldCancelDoesNotRemoveNewOne() {
    try (Game game = new Game()) {
      GameObject o = game.createObject("recoil");
      o.addComponent(new Transform3D());
      CameraPresentation3D camera =
          o.addComponent(new CameraPresentation3D(CameraPresentation3D.Space.VIEWMODEL));
      PresentationEffects3D fx = o.addComponent(new PresentationEffects3D(Clock.WORLD));
      game.start();
      var preset =
          PresentationEffects3D.recoil(
              PresentationEffects3D.Surface.VIEWMODEL_CAMERA, 0, 0, 0, .5, 0, 0, 4, .8, 1);
      var first = fx.play(preset);
      game.step(.05f);
      var second = fx.play(preset);
      game.step(.05f);
      var snap = new CameraPresentation3D.Snapshot();
      camera.snapshot(snap);
      double both = snap.rotationX();
      first.cancel();
      camera.snapshot(snap);
      assertNotEquals(0, both, 1e-8);
      assertNotEquals(both, snap.rotationX(), 1e-8);
      assertTrue(second.isActive());
    }
  }

  @Test
  public void distanceBobUsesCallerPhaseAndFovSpringKeepsStateWhenItsGoalChanges() {
    try (Game game = new Game()) {
      GameObject o = game.createObject("camera-feel");
      o.addComponent(new Transform3D());
      Presentation3D model = o.addComponent(new Presentation3D());
      CameraPresentation3D camera =
          o.addComponent(new CameraPresentation3D(CameraPresentation3D.Space.WORLD));
      PresentationEffects3D fx = o.addComponent(new PresentationEffects3D(Clock.WORLD));
      game.start();
      var fov =
          fx.play(
              PresentationEffects3D.fovSpring(
                  PresentationEffects3D.Surface.WORLD_CAMERA, 12, 2, .8, 1));
      var bob = fx.play(PresentationEffects3D.distanceBob(0, 1, 0, 1));
      bob.setDistanceBobInput(1, Math.PI / 2);
      game.step(.1f);
      var snapshot = new CameraPresentation3D.Snapshot();
      camera.snapshot(snapshot);
      float current = snapshot.fovDelta();
      assertTrue(current > 0 && current < 12);
      fov.setFovGoal(-6);
      camera.snapshot(snapshot);
      assertEquals(current, snapshot.fovDelta(), 0);
      var modelPose = new Presentation3D.Pose();
      model.snapshot(modelPose);
      assertTrue(bob.isActive());
      assertEquals(1, modelPose.translationY(), 1e-6);
      bob.cancel();
      model.snapshot(modelPose);
      assertEquals(0, modelPose.translationY(), 0);
    }
  }

  @Test
  public void uiClockRunsWhileWorldIsPausedAndProviderLossInvalidatesWithoutCompletion() {
    try (Game game = new Game()) {
      game.addSystem(new EffectSystem());
      GameObject o = game.createObject("ui");
      o.addComponent(new Transform3D());
      Presentation3D p = o.addComponent(new Presentation3D());
      PresentationEffects3D fx = o.addComponent(new PresentationEffects3D(Clock.UI));
      game.start();
      int[] done = {0};
      var h =
          fx.play(PresentationEffects3D.punchPosition(1, 0, 0, 2, .5, .5))
              .onComplete(() -> done[0]++);
      GameObject worldObject = game.createObject("world-paused");
      worldObject.addComponent(new Transform3D());
      Presentation3D worldPose = worldObject.addComponent(new Presentation3D());
      PresentationEffects3D worldFx =
          worldObject.addComponent(new PresentationEffects3D(Clock.WORLD));
      game.initializePendingComponents();
      var worldHandle = worldFx.play(PresentationEffects3D.punchPosition(1, 0, 0, 2, .5, .5));
      game.pause();
      for (int i = 0; i < 5; i++) game.step(.1f);
      assertEquals(1, done[0]);
      assertFalse(h.isActive());
      var worldSnapshot = new Presentation3D.Pose();
      worldPose.snapshot(worldSnapshot);
      assertTrue(worldHandle.isActive());
      assertEquals(0, worldSnapshot.translationX(), 0);
      var stale = fx.play(PresentationEffects3D.punchPosition(1, 0, 0, 2, .5, 1));
      game.step(.1f);
      o.removeComponent(p);
      game.step(0);
      assertFalse(stale.isActive());
      assertEquals(1, done[0]);
    }
  }

  @Test
  public void feedbackResourceReleasesContributionAtNaturalEndAndOnCancellation() {
    try (Game game = new Game()) {
      game.addSystem(new EffectSystem());
      GameObject o = game.createObject("feedback");
      o.addComponent(new Transform3D());
      Presentation3D p = o.addComponent(new Presentation3D());
      o.addComponent(new PresentationEffects3D(Clock.WORLD));
      FeedbackPlayer player = o.addComponent(new FeedbackPlayer(Clock.WORLD));
      game.start();
      var playback =
          player.play(
              PresentationEffects3D.feedback(
                  o, PresentationEffects3D.punchPosition(1, 0, 0, 2, .5, .5)));
      game.step(.1f);
      game.step(.1f);
      game.step(.1f);
      var pose = new Presentation3D.Pose();
      p.snapshot(pose);
      assertNotEquals(0, pose.translationX(), 1e-7);
      playback.cancel();
      p.snapshot(pose);
      assertEquals(0, pose.translationX(), 0);
    }
  }
}
