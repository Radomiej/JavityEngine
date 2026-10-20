package org.javity.animation;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodeAnimation;
import com.badlogic.gdx.graphics.g3d.model.NodeKeyframe;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ArrayMap;
import java.util.ArrayList;
import java.util.List;
import org.javity.core.Component;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.GameSystem;
import org.javity.core.Transform3D;
import org.javity.render.ModelRenderer;
import org.javity.services.TimeManager;
import org.junit.Test;

public class ModelRootMotionTest {
  @Test
  public void extractsRealInstanceTranslationBeforeAfterWorldAndPreservesSourceAndTransform() {
    for (boolean cache : new boolean[] {false, true}) {
      try (Rig rig = new Rig(cache)) {
        List<String> phases = new ArrayList<>();
        rig.game.addSystem(
            new GameSystem() {
              @Override
              public void onAfterUpdate(float delta) {
                phases.add("physics:" + rig.motion.size());
              }
            });
        rig.object.addComponent(
            new Component() {
              @Override
              public void onLateUpdate(float delta) {
                phases.add("late:" + rig.motion.size());
              }
            });
        rig.start();
        rig.pose.setRootMotion(
            "root",
            delta -> {
              rig.motion.add(delta);
              assertEquals(3, rig.root().translation.x, .0001);
              phases.add("sink");
            });
        rig.pose.play("walk", PlayMode.ONCE);
        rig.game.step(.1f);
        assertEquals(1, rig.motion.size());
        assertEquals(.8, rig.motion.getFirst().x(), .0001);
        assertEquals(.2, rig.motion.getFirst().y(), .0001);
        assertEquals(-.4, rig.motion.getFirst().z(), .0001);
        assertEquals(
            new Vector3(3, 5, 5), rig.root().parts.first().bones[0].getTranslation(new Vector3()));
        assertEquals(List.of("sink", "physics:1", "late:1"), phases);
        assertEquals(new Vector3(40, 50, 60), rig.transform.position);
        assertEquals(3, rig.model.nodes.first().translation.x, 0);
        assertNotSame(rig.model.nodes.first(), rig.root());
        rig.pose.clearRootMotion();
        assertEquals(7.8, rig.root().translation.x, .0001);
        rig.game.step(.1f);
        assertEquals(1, rig.motion.size());
        assertEquals(8.6, rig.root().translation.x, .0001);
      }
    }
  }

  @Test
  public void loopsUseAuthoredEndpointDisplacementAcrossSeveralCycles() {
    try (Rig rig = new Rig(true)) {
      rig.start();
      rig.pose.setRootMotion("root", rig.motion::add);
      ModelPosePlayback playback = rig.pose.play("walk", PlayMode.LOOP).seek(.75);
      playback.setSpeed(25);
      rig.game.step(.1f);
      assertEquals(20, rig.motion.getFirst().x(), .0001);
      assertEquals(.25, playback.time(), .0001);
      assertEquals(3, rig.root().translation.x, 0);
      assertEquals(1, rig.motion.size());
      playback.setSpeed(10).seek(0);
      rig.game.step(.1f);
      assertEquals(8, rig.motion.getLast().x(), .0001);
    }
  }

  @Test
  public void seekRestartPauseStopAndDisableNeverEmitDiscontinuities() {
    try (Rig rig = new Rig(false)) {
      rig.start();
      rig.pose.setRootMotion("root", rig.motion::add);
      ModelPosePlayback playback = rig.pose.play("walk", PlayMode.ONCE).seek(.5);
      assertTrue(rig.motion.isEmpty());
      rig.game.step(.1f);
      playback.pause();
      rig.game.step(.1f);
      playback.resume().setSpeed(0);
      rig.game.step(.1f);
      assertEquals(1, rig.motion.size());
      playback.setSpeed(1).restart();
      assertEquals(1, rig.motion.size());
      rig.game.step(.1f);
      assertEquals(.8, rig.motion.getLast().x(), .0001);
      playback.seek(.95);
      rig.game.step(.1f);
      assertEquals(.4, rig.motion.getLast().x(), .0001);
      rig.game.step(.1f);
      assertEquals(3, rig.motion.size());
      rig.pose.setEnabled(false);
      rig.renderer.prepareForRender();
      rig.game.step(.1f);
      rig.pose.setEnabled(true);
      rig.pose.play("walk", PlayMode.ONCE);
      rig.game.step(.1f);
      assertEquals(.8, rig.motion.getLast().x(), .0001);
      rig.pose.stop();
      rig.game.step(.1f);
      assertEquals(4, rig.motion.size());
    }
  }

  @Test
  public void callbackReplacementSeekAndDisableDiscardThePlannedDelta() {
    for (String action :
        List.of(
            "replace",
            "seek",
            "restart",
            "pause",
            "speed",
            "disable",
            "clear",
            "rootReplace",
            "activeToggle",
            "rendererToggle")) {
      try (Rig rig = new Rig(false)) {
        rig.start();
        rig.pose.setRootMotion("root", rig.motion::add);
        ModelPosePlayback playback = rig.pose.play("walk", PlayMode.LOOP);
        playback.onMarker(
            "change",
            .05,
            () -> {
              switch (action) {
                case "replace" -> rig.pose.play("walk", PlayMode.LOOP);
                case "seek" -> playback.seek(.5);
                case "disable" -> rig.pose.setEnabled(false);
                case "clear" -> rig.pose.clearRootMotion();
                case "restart" -> playback.restart();
                case "pause" -> playback.pause();
                case "speed" -> playback.setSpeed(2);
                case "rootReplace" -> rig.pose.setRootMotion("root", rig.motion::add);
                case "activeToggle" -> {
                  rig.object.setActive(false);
                  rig.object.setActive(true);
                }
                case "rendererToggle" -> {
                  rig.renderer.setEnabled(false);
                  rig.renderer.setEnabled(true);
                }
              }
            });
        rig.game.step(.1f);
        assertTrue(action, rig.motion.isEmpty());
      }
    }
  }

  @Test
  public void throwingSinkCommitsClockAndDoesNotReplayItsDelta() {
    try (Rig rig = new Rig(false)) {
      rig.start();
      RuntimeException failure = new RuntimeException("sink");
      rig.pose.setRootMotion(
          "root",
          delta -> {
            rig.motion.add(delta);
            if (rig.motion.size() == 1) throw failure;
          });
      ModelPosePlayback playback = rig.pose.play("walk", PlayMode.ONCE);
      assertSame(failure, assertThrows(RuntimeException.class, () -> rig.game.step(.1f)));
      assertEquals(.1, playback.time(), .0001);
      rig.game.step(.1f);
      assertEquals(2, rig.motion.size());
      assertEquals(.8, rig.motion.getLast().x(), .0001);
      assertEquals(.2, playback.time(), .0001);
    }
  }

  @Test
  public void rejectsUnsupportedConfigurationBeforeReplacingValidState() {
    try (Rig rig = new Rig(false)) {
      rig.start();
      ModelPosePlayback playback = rig.pose.play("walk", PlayMode.LOOP);
      rig.pose.setRootMotion("root", rig.motion::add);
      assertThrows(
          IllegalArgumentException.class, () -> rig.pose.setRootMotion("root/arm", d -> fail()));
      assertThrows(
          IllegalArgumentException.class, () -> rig.pose.setRootMotion("missing", d -> fail()));
      assertThrows(
          IllegalStateException.class,
          () ->
              rig.pose.play(
                  LinearModelMixer.builder(1).add("walk", 0).add("idle", 1).build(),
                  PlayMode.LOOP));
      assertThrows(
          IllegalStateException.class,
          () -> rig.pose.play(AnimationTransition.clip("walk").fade(.3)));
      assertThrows(
          IllegalStateException.class, () -> rig.pose.addLayer("bad", NodeMask.paths("root")));
      assertSame(playback, rig.pose.current());
      assertTrue(rig.pose.layers().isEmpty());
      ModelAnimationLayer arm = rig.pose.addLayer("upper", NodeMask.paths("root/arm"));
      arm.setWeight(1).play("walk", PlayMode.LOOP);
      rig.game.step(.1f);
      assertEquals(.8, rig.motion.getFirst().x(), .0001);
      assertEquals(1, rig.renderer.instance.getNode("arm", true).translation.y, .0001);
      rig.pose.clearRootMotion();
      rig.pose.addLayer("root-layer", NodeMask.paths("root"));
      assertThrows(
          IllegalStateException.class, () -> rig.pose.setRootMotion("root", rig.motion::add));
      rig.game.step(.1f);
      assertEquals(8.6, rig.root().translation.x, .0001);
    }
  }

  @Test
  public void worldTimePauseFreezeAndLateSeekHaveExplicitOrdering() {
    try (Rig rig = new Rig(false)) {
      TimeManager time = rig.game.addSystem(new TimeManager());
      rig.start();
      rig.pose.setRootMotion("root", rig.motion::add);
      ModelPosePlayback playback = rig.pose.play("walk", PlayMode.LOOP);
      rig.object.addComponent(
          new Component() {
            @Override
            public void onLateUpdate(float delta) {
              playback.seek(.5);
            }
          });
      time.setBaseScale(.5);
      rig.game.step(.1f);
      assertEquals(.4, rig.motion.getFirst().x(), .0001);
      assertEquals(.5, playback.time(), .0001);
      rig.game.pause();
      rig.game.step(.1f);
      rig.game.resume();
      time.setBaseScale(0);
      rig.game.step(.1f);
      assertEquals(1, rig.motion.size());
      time.setBaseScale(1);
      rig.game.step(.1f);
      assertEquals(.8, rig.motion.getLast().x(), .0001);
    }
  }

  @Test
  public void cachedPoseRemainsCanonicalForAnotherInstanceWithoutExtraction() {
    try (Rig rig = new Rig(true)) {
      GameObject ordinary = rig.game.createObject("ordinary");
      ModelRenderer otherRenderer = ordinary.addComponent(new ModelRenderer(rig.model));
      ModelPoseAnimator otherPose = ordinary.addComponent(new ModelPoseAnimator());
      rig.start();
      rig.pose.setRootMotion("root", rig.motion::add);
      rig.pose.play("walk", PlayMode.LOOP);
      otherPose.play("walk", PlayMode.LOOP);
      rig.game.step(.1f);
      assertEquals(3, rig.root().translation.x, 0);
      assertEquals(7.8, otherRenderer.instance.nodes.first().translation.x, .0001);
      assertEquals(.8, rig.motion.getFirst().x(), .0001);
    }
  }

  @Test
  public void excessiveLoopsAndDisplacementOverflowFailBeforeClockOrCallbacksAdvance() {
    try (Rig rig = new Rig(false)) {
      rig.start();
      rig.pose.setRootMotion("root", rig.motion::add);
      int[] starts = {0};
      ModelPosePlayback playback =
          rig.pose.play("walk", PlayMode.LOOP).setSpeed(10250).onStart(() -> starts[0]++);
      assertThrows(IllegalArgumentException.class, () -> rig.game.step(.1f));
      assertEquals(0, playback.time(), 0);
      assertEquals(0, starts[0]);
      assertTrue(rig.motion.isEmpty());
      NodeAnimation track = rig.renderer.instance.animations.first().nodeAnimations.first();
      track.translation.first().value.x = -Float.MAX_VALUE;
      track.translation.peek().value.x = Float.MAX_VALUE;
      playback.setSpeed(10);
      assertThrows(IllegalArgumentException.class, () -> rig.game.step(.1f));
      assertEquals(0, playback.time(), 0);
      assertEquals(0, starts[0]);
      assertTrue(rig.motion.isEmpty());
      assertThrows(IllegalArgumentException.class, () -> new RootMotionDelta(Float.NaN, 0, 0));
    }
  }

  @Test
  public void inactiveAndRemovedOwnersEmitNothingAndUiModeIsRejected() {
    try (Rig rig = new Rig(false)) {
      rig.start();
      rig.pose.updateMode = Component.UpdateMode.UI;
      assertThrows(
          IllegalStateException.class, () -> rig.pose.setRootMotion("root", rig.motion::add));
      rig.pose.updateMode = Component.UpdateMode.WORLD;
      rig.pose.setRootMotion("root", rig.motion::add);
      rig.pose.play("walk", PlayMode.LOOP);
      rig.object.setActive(false);
      rig.game.step(.1f);
      assertTrue(rig.motion.isEmpty());
      rig.object.setActive(true);
      rig.game.step(.1f);
      assertEquals(.8, rig.motion.getFirst().x(), .0001);
      rig.object.removeComponent(rig.pose);
      rig.renderer.prepareForRender();
      rig.game.step(.1f);
      assertEquals(1, rig.motion.size());
      assertEquals(3, rig.root().translation.x, 0);
    }
  }

  @Test
  public void absentRootTranslationTrackProducesNoMovement() {
    try (Rig rig = new Rig(false)) {
      rig.renderer.instance.animations.first().nodeAnimations.removeIndex(0);
      rig.start();
      rig.pose.setRootMotion("root", rig.motion::add);
      rig.pose.play("walk", PlayMode.LOOP).setSpeed(25);
      rig.game.step(.1f);
      assertTrue(rig.motion.isEmpty());
      assertEquals(new Vector3(3, 4, 5), rig.root().translation);
    }
  }

  @Test
  public void enablingDuringMixerOrFadePreservesOrdinaryPlaybackOnRejection() {
    try (Rig rig = new Rig(false)) {
      rig.start();
      ModelPosePlayback mixer =
          rig.pose.play(
              LinearModelMixer.builder(1).add("walk", 0).add("idle", 1).build(), PlayMode.LOOP);
      assertThrows(
          IllegalStateException.class, () -> rig.pose.setRootMotion("root", rig.motion::add));
      assertSame(mixer, rig.pose.current());
      ModelPosePlayback fade = rig.pose.play(AnimationTransition.clip("walk").fade(.5));
      assertThrows(
          IllegalStateException.class, () -> rig.pose.setRootMotion("root", rig.motion::add));
      assertSame(fade, rig.pose.current());
      assertTrue(fade.isFading());
      rig.game.step(.1f);
      assertTrue(rig.motion.isEmpty());
      assertTrue(rig.root().translation.x > 3);
    }
  }

  @Test
  public void completionCallbackClockChangeDiscardsItsFinalInterval() {
    try (Rig rig = new Rig(false)) {
      rig.start();
      rig.pose.setRootMotion("root", rig.motion::add);
      ModelPosePlayback playback = rig.pose.play(AnimationTransition.clip("walk").startAt(.95));
      playback.onComplete(playback::pause);
      rig.game.step(.1f);
      assertTrue(playback.isCompleted());
      assertTrue(playback.isPaused());
      assertTrue(rig.motion.isEmpty());
      assertEquals(1, playback.time(), 0);
      playback.seek(.5).resume();
      rig.game.step(.1f);
      assertEquals(.8, rig.motion.getFirst().x(), .0001);
    }
  }

  @Test
  public void callbackRootCurveEditsCancelTheTickAndNextTickUsesTheLiveCurve() {
    for (boolean cache : new boolean[] {false, true}) {
      for (String edit : List.of("endpoint", "replaceKeys", "keyTime", "addKey")) {
        try (Rig rig = new Rig(cache)) {
          rig.start();
          NodeAnimation rootTrack = rig.renderer.instance.animations.first().nodeAnimations.first();
          rig.pose.setRootMotion("root", rig.motion::add);
          ModelPosePlayback playback = rig.pose.play("walk", PlayMode.LOOP);
          playback.onMarker(
              "edit",
              .05,
              () -> {
                if (edit.equals("replaceKeys")) {
                  rootTrack.translation.clear();
                  rootTrack.translation.add(new NodeKeyframe<>(0, new Vector3(7, 8, 9)));
                  rootTrack.translation.add(new NodeKeyframe<>(1, new Vector3(23, 10, 5)));
                } else if (edit.equals("keyTime")) {
                  rootTrack.translation.set(1, new NodeKeyframe<>(.5f, new Vector3(15, 10, 5)));
                } else if (edit.equals("addKey")) {
                  rootTrack.translation.insert(1, new NodeKeyframe<>(.5f, new Vector3(19, 9, 7)));
                } else {
                  rootTrack.translation.peek().value.x = 23;
                }
              });
          rig.game.step(.1f);
          assertTrue("cache=" + cache + ", edit=" + edit, rig.motion.isEmpty());
          assertEquals(.1, playback.time(), .0001);
          rig.game.step(.1f);
          assertEquals(1, rig.motion.size());
          // Independent curve slopes over [.1,.2]: (23-7)/1, (15-7)/.5 or (19-7)/.5.
          double slope = edit.equals("addKey") ? 24 : 16;
          assertEquals(slope * .1, rig.motion.getFirst().x(), .0001);
          assertEquals(edit.equals("addKey") ? 3 : 2, rootTrack.translation.size);
          assertEquals(edit.equals("keyTime") ? .5 : 1, rootTrack.translation.peek().keytime, 0);
          rig.pose.clearRootMotion();
          assertEquals(7 + slope * .2, rig.root().translation.x, .0001);
        }
      }
    }
  }

  @Test
  public void rootSourceSnapshotReusesHighWaterStorageAndNestedLeasesStayIndependent()
      throws Exception {
    ModelRootMotion extraction = new ModelRootMotion(0, delta -> {});
    Array<NodeKeyframe<Vector3>> keys = new Array<>();
    for (int i = 0; i < 1000; i++) keys.add(new NodeKeyframe<>(i / 999f, new Vector3(i, 0, 0)));
    ModelRootMotion.TranslationSnapshot initial = extraction.captureTranslation(keys);
    Object storage = snapshotStorage(initial);
    initial.close();
    for (int i = 0; i < 100; i++) {
      try (ModelRootMotion.TranslationSnapshot lease = extraction.captureTranslation(keys)) {
        assertSame(initial, lease);
        assertSame(storage, snapshotStorage(lease));
        assertTrue(lease.unchanged());
      }
    }
    keys.truncate(2);
    try (ModelRootMotion.TranslationSnapshot outer = extraction.captureTranslation(keys)) {
      assertSame(storage, snapshotStorage(outer));
      assertTrue(outer.unchanged());
      keys.peek().value.x = 23;
      try (ModelRootMotion.TranslationSnapshot inner = extraction.captureTranslation(keys)) {
        assertNotSame(outer, inner);
        assertNotSame(snapshotStorage(outer), snapshotStorage(inner));
        assertTrue(inner.unchanged());
        assertFalse(outer.unchanged());
      }
      assertFalse(outer.unchanged());
    }
    keys.add(new NodeKeyframe<>(1, new Vector3(24, 0, 0)));
    try (ModelRootMotion.TranslationSnapshot lease = extraction.captureTranslation(keys)) {
      assertSame(initial, lease);
      assertSame(storage, snapshotStorage(lease));
      assertTrue(lease.unchanged());
    }
    while (keys.size < 1001) keys.add(new NodeKeyframe<>(keys.size, new Vector3(keys.size, 0, 0)));
    Object grownStorage;
    try (ModelRootMotion.TranslationSnapshot lease = extraction.captureTranslation(keys)) {
      assertSame(initial, lease);
      grownStorage = snapshotStorage(lease);
      assertNotSame(storage, grownStorage);
      assertEquals(1001 * 4, ((int[]) grownStorage).length);
      assertTrue(lease.unchanged());
    }
    keys.truncate(3);
    try (ModelRootMotion.TranslationSnapshot lease = extraction.captureTranslation(keys)) {
      assertSame(grownStorage, snapshotStorage(lease));
      assertTrue(lease.unchanged());
    }
  }

  @Test
  public void animatorReleasesSnapshotOnSuccessEarlyReturnAndCallbackOrSinkFailure()
      throws Exception {
    for (String outcome : List.of("success", "disable", "callbackFailure", "sinkFailure")) {
      try (Rig rig = new Rig(false)) {
        rig.start();
        RuntimeException failure = new RuntimeException(outcome);
        rig.pose.setRootMotion(
            "root",
            delta -> {
              if (outcome.equals("sinkFailure")) throw failure;
            });
        var field = ModelPoseAnimator.class.getDeclaredField("rootMotion");
        field.setAccessible(true);
        ModelRootMotion extraction = (ModelRootMotion) field.get(rig.pose);
        Array<NodeKeyframe<Vector3>> channel =
            rig.renderer.instance.animations.first().nodeAnimations.first().translation;
        ModelRootMotion.TranslationSnapshot initial = extraction.captureTranslation(channel);
        Object storage = snapshotStorage(initial);
        initial.close();
        rig.pose
            .play("walk", PlayMode.LOOP)
            .onMarker(
                "change",
                .05,
                () -> {
                  if (outcome.equals("disable")) rig.pose.setEnabled(false);
                  if (outcome.equals("callbackFailure")) throw failure;
                });
        if (outcome.endsWith("Failure"))
          assertSame(failure, assertThrows(RuntimeException.class, () -> rig.game.step(.1f)));
        else rig.game.step(.1f);
        try (ModelRootMotion.TranslationSnapshot lease = extraction.captureTranslation(channel)) {
          assertSame(outcome, initial, lease);
          assertSame(outcome, storage, snapshotStorage(lease));
          assertTrue(lease.unchanged());
        }
      }
    }
  }

  @Test
  public void reentrantCallbackUpdateCannotOverwriteOuterSourceSnapshot() {
    try (Rig rig = new Rig(false)) {
      rig.start();
      NodeAnimation track = rig.renderer.instance.animations.first().nodeAnimations.first();
      rig.pose.setRootMotion("root", rig.motion::add);
      rig.pose
          .play("walk", PlayMode.LOOP)
          .onMarker(
              "edit",
              .05,
              () -> {
                track.translation.peek().value.x = 23;
                rig.pose.onUpdate(.01f);
              });
      rig.game.step(.1f);
      assertEquals(1, rig.motion.size());
      assertEquals(.16, rig.motion.getFirst().x(), .0001);
      rig.game.step(.1f);
      assertEquals(2, rig.motion.size());
      assertEquals(1.6, rig.motion.getLast().x(), .0001);
    }
  }

  private static Object snapshotStorage(ModelRootMotion.TranslationSnapshot snapshot)
      throws ReflectiveOperationException {
    var field = ModelRootMotion.TranslationSnapshot.class.getDeclaredField("bits");
    field.setAccessible(true);
    return field.get(snapshot);
  }

  private static final class Rig implements AutoCloseable {
    final Model model = new Model();
    final Game game = new Game();
    final GameObject object = game.createObject("character");
    final Transform3D transform = object.addComponent(new Transform3D());
    final ModelRenderer renderer;
    final ModelPoseAnimator pose;
    final List<RootMotionDelta> motion = new ArrayList<>();

    Rig(boolean cache) {
      if (cache) game.addSystem(new PoseBatchSystem(64));
      Node root = new Node();
      root.id = "root";
      root.translation.set(3, 4, 5);
      Node arm = new Node();
      arm.id = "arm";
      root.addChild(arm);
      model.nodes.add(root);
      Animation animation = new Animation();
      animation.id = "walk";
      animation.duration = 1;
      animation.nodeAnimations.add(track(root, new Vector3(7, 8, 9), new Vector3(15, 10, 5)));
      animation.nodeAnimations.add(track(arm, new Vector3(), new Vector3(0, 10, 0)));
      model.animations.add(animation);
      Animation idle = new Animation();
      idle.id = "idle";
      idle.duration = 1;
      idle.nodeAnimations.add(track(arm, new Vector3(), new Vector3()));
      model.animations.add(idle);
      renderer = object.addComponent(new ModelRenderer(new ModelInstance(model)));
      NodePart skinnedPart = new NodePart();
      skinnedPart.enabled = false; // CPU skin matrices only; this fixture has no GPU mesh.
      skinnedPart.bones = new Matrix4[] {new Matrix4()};
      skinnedPart.invBoneBindTransforms = new ArrayMap<>(true, 1, Node[]::new, Matrix4[]::new);
      skinnedPart.invBoneBindTransforms.put(renderer.instance.getNode("arm", true), new Matrix4());
      renderer.instance.nodes.first().parts.add(skinnedPart);
      pose = object.addComponent(new ModelPoseAnimator());
      transform.position.set(40, 50, 60);
    }

    void start() {
      game.start();
    }

    Node root() {
      return renderer.instance.nodes.first();
    }

    @Override
    public void close() {
      game.close();
      model.dispose();
    }
  }

  private static NodeAnimation track(Node node, Vector3 from, Vector3 to) {
    NodeAnimation track = new NodeAnimation();
    track.node = node;
    track.translation = new Array<>();
    track.translation.add(new NodeKeyframe<>(0, from));
    track.translation.add(new NodeKeyframe<>(1, to));
    return track;
  }
}
