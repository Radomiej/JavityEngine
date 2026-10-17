package org.javity.presentation;

import static org.junit.Assert.*;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.effects.CancelMode;
import org.javity.effects.Clock;
import org.javity.effects.Ease;
import org.javity.effects.EffectSystem;
import org.javity.effects.Feedback;
import org.javity.effects.FeedbackPlayer;
import org.javity.effects.FloatTarget;
import org.javity.effects.PlaybackHandle;
import org.javity.effects.Targets;
import org.javity.effects.TweenHandle;
import org.javity.effects.TweenPlayer;
import org.junit.Test;

public class Presentation3DTest {
  @Test
  public void persistentChannelsAndTransientLeasesComposeWithoutChangingCanonicalTransform() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("presented");
      Transform3D transform = object.addComponent(new Transform3D());
      transform.position.set(4, 5, 6);
      transform.scale.set(2, 3, 4);
      Presentation3D provider = object.addComponent(new Presentation3D());
      game.start();
      Presentation3D.Channel first = provider.openChannel();
      Presentation3D.Channel second = provider.openChannel();
      FloatTarget firstX = Targets.presentationX(first), secondX = Targets.presentationX(second);
      assertNotSame(firstX.identity(), secondX.identity());
      firstX.set(1.25f);
      secondX.set(-.25f);
      first.setRotationZ((float) (Math.PI / 2));
      first.setScaleX(.5f);
      Presentation3D.Lease transientLease = provider.contribute();
      transientLease.set(0, 2, 0, 0, 0, 0, 0, .25f, 0);

      Presentation3D.Pose pose = new Presentation3D.Pose();
      provider.snapshot(pose);
      assertEquals(1f, pose.translationX(), 0);
      assertEquals(2f, pose.translationY(), 0);
      assertEquals(1.5f, pose.scaleX(), 0);
      Matrix4 local = pose.localMatrix(new Matrix4());
      float[] values = local.val;
      assertEquals(
          1.5f,
          (float)
              Math.sqrt(
                  values[Matrix4.M00] * values[Matrix4.M00]
                      + values[Matrix4.M10] * values[Matrix4.M10]
                      + values[Matrix4.M20] * values[Matrix4.M20]),
          1e-5);
      assertEquals(
          1.25f,
          (float)
              Math.sqrt(
                  values[Matrix4.M01] * values[Matrix4.M01]
                      + values[Matrix4.M11] * values[Matrix4.M11]
                      + values[Matrix4.M21] * values[Matrix4.M21]),
          1e-5);
      assertEquals(
          1f,
          (float)
              Math.sqrt(
                  values[Matrix4.M02] * values[Matrix4.M02]
                      + values[Matrix4.M12] * values[Matrix4.M12]
                      + values[Matrix4.M22] * values[Matrix4.M22]),
          1e-5);
      Quaternion q = local.getRotation(new Quaternion(), true);
      assertEquals(1f, q.len(), 1e-6);
      assertEquals(4, transform.position.x, 0);
      assertEquals(5, transform.position.y, 0);
      assertEquals(6, transform.position.z, 0);
      assertEquals(2, transform.scale.x, 0);
      assertEquals(3, transform.scale.y, 0);
      assertEquals(4, transform.scale.z, 0);

      transientLease.pause();
      provider.snapshot(pose);
      assertEquals(2, pose.translationY(), 0);
      assertThrows(
          IllegalStateException.class, () -> transientLease.set(0, 3, 0, 0, 0, 0, 0, 0, 0));
      first.close();
      provider.snapshot(pose);
      assertEquals(-.25f, pose.translationX(), 0);
      assertEquals(2, pose.translationY(), 0);
      assertEquals(1.25f, pose.scaleY(), 0);
      transientLease.close();
      second.close();
      assertEquals(0, provider.activeCount());
    }
  }

  @Test
  public void localPoseFollowsCanonicalParentRotationAndNonuniformScaleWithoutMutation() {
    try (Game game = new Game()) {
      GameObject parentObject = game.createObject("parent");
      Transform3D parent = parentObject.addComponent(new Transform3D());
      parent.position.set(3, 4, 5);
      parent.scale.set(2, 3, 4);
      parent.rotation.setFromAxis(Vector3.Z, 90);
      GameObject childObject = game.createObject("child");
      Transform3D child = childObject.addComponent(new Transform3D());
      child.position.set(1, 0, 0);
      child.setParent(parent);
      Presentation3D provider = childObject.addComponent(new Presentation3D());
      game.start();
      Presentation3D.Lease lease = provider.contribute();
      lease.set(1, 0, 0, 0, 0, (float) Math.PI / 2, 0, 0, 0);
      Presentation3D.Pose pose = new Presentation3D.Pose();
      provider.snapshot(pose);
      Matrix4 canonical = new Matrix4(child.worldMatrix());
      Matrix4 presented = new Matrix4(canonical).mul(pose.localMatrix(new Matrix4()));
      Vector3 canonicalOrigin = new Vector3();
      canonical.getTranslation(canonicalOrigin);
      Vector3 presentedOrigin = presented.getTranslation(new Vector3());
      assertEquals(3, canonicalOrigin.x, .0001f);
      assertEquals(6, canonicalOrigin.y, .0001f);
      assertEquals(5, canonicalOrigin.z, .0001f);
      assertEquals(1, child.position.x, 0); // canonical local translation remains unchanged
      assertTrue(presentedOrigin.dst(canonicalOrigin) > 1f);
      assertEquals(1, child.rotation.len(), 1e-6);
      assertEquals(new Vector3(2, 3, 4), parent.scale);
    }
  }

  @Test
  public void aggregateOverflowRejectsAtomicallyAndStaleHandlesCannotReleaseReusedSlots() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("bounded");
      object.addComponent(new Transform3D());
      Presentation3D provider = object.addComponent(new Presentation3D());
      game.start();
      Presentation3D.Lease a = provider.contribute(), b = provider.contribute();
      assertThrows(
          IllegalArgumentException.class, () -> a.set(Float.MAX_VALUE, 0, 0, 0, 0, 0, 0, 0, 0));
      float bounded = Float.MAX_VALUE / 128f;
      a.set(bounded, 0, 0, 0, 0, 0, 0, 0, 0);
      b.set(-bounded, 2, 0, 0, 0, 0, 0, 0, 0);
      Presentation3D.Pose pose = new Presentation3D.Pose();
      provider.snapshot(pose);
      assertEquals(0, pose.translationX(), 0);
      assertEquals(2, pose.translationY(), 0);
      a.close();
      provider.snapshot(pose);
      assertEquals(-bounded, pose.translationX(), 0);
      assertEquals(2, pose.translationY(), 0);
      Presentation3D.Lease successor = provider.contribute();
      a.close();
      b.close();
      assertTrue(successor.isActive());
      assertEquals(1, provider.activeCount());
    }
  }

  @Test
  public void fixedProviderCapacityNeverEvictsAnotherLease() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("capacity");
      object.addComponent(new Transform3D());
      Presentation3D provider = object.addComponent(new Presentation3D());
      game.start();
      Presentation3D.Lease[] leases = new Presentation3D.Lease[Presentation3D.CAPACITY];
      for (int i = 0; i < leases.length; i++) leases[i] = provider.contribute();
      assertEquals(Presentation3D.CAPACITY, provider.activeCount());
      assertThrows(IllegalStateException.class, provider::contribute);
      for (int i = 0; i < leases.length; i++) {
        assertTrue(leases[i].isActive());
        leases[i].close();
      }
      assertEquals(0, provider.activeCount());
    }
  }

  @Test
  public void removalAndOwnerDeactivationInvalidateTargetsAndLeases() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("lifetime");
      object.addComponent(new Transform3D());
      Presentation3D provider = object.addComponent(new Presentation3D());
      game.start();
      Presentation3D.Channel channel = provider.openChannel();
      Presentation3D.Lease lease = provider.contribute();
      FloatTarget target = Targets.presentationX(channel);
      object.setActive(false);
      assertFalse(target.isValid());
      assertFalse(lease.isActive());
      object.setActive(true);
      assertFalse(target.isValid());
      assertFalse(lease.isActive());
      assertEquals(0, provider.activeCount());
      Presentation3D.Lease fresh = provider.contribute();
      object.removeComponent(provider);
      assertFalse(fresh.isActive());
      assertEquals(0, provider.activeCount());
      game.step(0);
      assertFalse(channel.isValid());
    }
  }

  @Test
  public void unobservedActiveAndEnabledTransitionsInvalidateEveryOldHandle() {
    try (Game game = new Game()) {
      GameObject modelObject = game.createObject("unobserved active transition");
      modelObject.addComponent(new Transform3D());
      Presentation3D model = modelObject.addComponent(new Presentation3D());
      GameObject cameraObject = game.createObject("unobserved enabled transition");
      CameraPresentation3D camera =
          cameraObject.addComponent(new CameraPresentation3D(CameraPresentation3D.Space.WORLD));
      game.start();
      Presentation3D.Channel modelChannel = model.openChannel();
      CameraPresentation3D.Channel cameraChannel = camera.openChannel();
      modelChannel.setX(1);
      cameraChannel.setX(2);
      modelObject.setActive(false);
      modelObject.setActive(true);
      camera.setEnabled(false);
      camera.setEnabled(true);
      Presentation3D.Pose modelPose = new Presentation3D.Pose();
      model.snapshot(modelPose); // First presentation access after the active transition.
      assertEquals(0, modelPose.translationX(), 0);
      Presentation3D.Pose secondModelPose = new Presentation3D.Pose();
      model.snapshot(secondModelPose);
      assertEquals(0, secondModelPose.translationX(), 0);
      CameraPresentation3D.Snapshot cameraPose = new CameraPresentation3D.Snapshot();
      camera.snapshot(cameraPose); // First presentation access after the enabled transition.
      assertEquals(0, cameraPose.translationX(), 0);
      CameraPresentation3D.Snapshot cameraSecondCapture = new CameraPresentation3D.Snapshot();
      camera.snapshot(cameraSecondCapture);
      assertEquals(0, cameraSecondCapture.translationX(), 0);
      assertFalse(modelChannel.isValid());
      assertFalse(cameraChannel.isValid());
      assertEquals(0, model.activeCount());
      assertEquals(0, camera.activeCount());
    }
  }

  @Test
  public void providerRemovalBeforeInitializationAndCanonicalTransformReplacementAreSafe() {
    try (Game game = new Game()) {
      GameObject early = game.createObject("removed before init");
      early.addComponent(new Transform3D());
      Presentation3D neverInitialized = early.addComponent(new Presentation3D());
      early.removeComponent(neverInitialized);
      GameObject object = game.createObject("transform replacement");
      Transform3D canonical = object.addComponent(new Transform3D());
      Presentation3D provider = object.addComponent(new Presentation3D());
      game.start();
      Presentation3D.Lease lease = provider.contribute();
      object.removeComponent(canonical);
      assertFalse(lease.isActive());
      assertEquals(0, provider.activeCount());
      object.addComponent(new Transform3D());
      assertThrows(IllegalStateException.class, provider::contribute);
      game.step(0);
      assertEquals(0, neverInitialized.activeCount());
    }
  }

  @Test
  public void replacingProviderWithSameValuesInvalidatesOnlyTheOldGeneration() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("provider replacement");
      object.addComponent(new Transform3D());
      Presentation3D oldProvider = object.addComponent(new Presentation3D());
      game.start();
      Presentation3D.Channel oldChannel = oldProvider.openChannel();
      oldChannel.setX(2);
      Presentation3D.Lease oldLease = oldProvider.contribute();
      Presentation3D replacement = object.addComponent(new Presentation3D());
      object.removeComponent(oldProvider);
      assertFalse(oldChannel.isValid());
      assertFalse(oldLease.isActive());
      assertEquals(0, oldProvider.activeCount());
      game.step(0);
      Presentation3D.Channel newChannel = replacement.openChannel();
      newChannel.setX(3);
      oldChannel.close();
      Presentation3D.Pose pose = new Presentation3D.Pose();
      replacement.snapshot(pose);
      assertEquals(3, pose.translationX(), 0);
      newChannel.close();
    }
  }

  @Test
  public void feedbackUsesExclusiveChannelLeaseAndStaleRestoreCannotClobberSuccessor() {
    try (Game game = new Game()) {
      game.addSystem(new EffectSystem());
      GameObject object = game.createObject("feedback-channel");
      Transform3D canonical = object.addComponent(new Transform3D());
      Presentation3D provider = object.addComponent(new Presentation3D());
      TweenPlayer first = object.addComponent(new TweenPlayer(Clock.WORLD));
      FeedbackPlayer second = object.addComponent(new FeedbackPlayer(Clock.WORLD));
      game.start();
      Presentation3D.Channel channel = provider.openChannel();
      FloatTarget target = Targets.presentationX(channel);
      TweenHandle old = first.to(target, 10, 1, Ease.LINEAR);
      game.step(.1f);
      assertEquals(1f, channel.getX(), .001f);
      PlaybackHandle successor = second.play(Feedback.tween(target, 20, 1, Ease.LINEAR));
      game.step(.1f);
      assertTrue(old.isCancelled());
      assertTrue(successor.isRunning());
      float successorValue = channel.getX();
      old.cancel(CancelMode.RESTORE_START);
      assertEquals(successorValue, channel.getX(), 0);
      canonical.position.x = 12;
      successor.cancel(CancelMode.RESTORE_START);
      assertEquals(2f, channel.getX(), 0);
      assertEquals(12f, canonical.position.x, 0);
      Presentation3D.Pose pose = new Presentation3D.Pose();
      provider.snapshot(pose);
      assertEquals(2f, pose.translationX(), 0);
      channel.close();
    }
  }
}
