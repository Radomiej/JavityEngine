package org.javity.animation;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodeAnimation;
import com.badlogic.gdx.graphics.g3d.model.NodeKeyframe;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import org.javity.core.Component;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.render.ModelRenderer;
import org.javity.services.TimeManager;
import org.junit.Test;

public class ModelPoseAnimatorTest {
  @Test
  public void mixerBlendsRealTrsAtEndpointsAndSynchronousMidpoint() {
    Model model = animatedModel();
    Animation fast = model.animations.get(1);
    Quaternion firstRotation = fast.nodeAnimations.first().rotation.first().value;
    Quaternion secondRotation = fast.nodeAnimations.first().rotation.peek().value;
    firstRotation.set(
        firstRotation.x * 1.0e20f,
        firstRotation.y * 1.0e20f,
        firstRotation.z * 1.0e20f,
        firstRotation.w * 1.0e20f);
    secondRotation.set(
        secondRotation.x * 1.0e20f,
        secondRotation.y * 1.0e20f,
        secondRotation.z * 1.0e20f,
        secondRotation.w * 1.0e20f);
    Quaternion sourceFirst = firstRotation.cpy(), sourceSecond = secondRotation.cpy();
    try (Game game = new Game()) {
      GameObject object = game.createObject("pose");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();
      LinearModelMixer mixer = LinearModelMixer.builder(2).add("slow", 0).add("fast", 1).build();

      ModelPosePlayback playback = pose.play(mixer, PlayMode.ONCE, 0).seekNormalized(.5);
      assertEquals(5, node(renderer, "root").translation.x, .0001);
      playback.setParameter(1);
      assertEquals(20, node(renderer, "root").translation.x, .0001);
      assertEquals(2, node(renderer, "root").scale.x, .0001);
      playback.setParameter(.5);
      assertEquals(12.5, node(renderer, "root").translation.x, .0001);
      assertEquals(1.75, node(renderer, "root").scale.x, .0001);
      assertEquals(6, node(renderer, "root/leg").translation.x, .0001);
      float angle = node(renderer, "root").rotation.getAngleAround(Vector3.Z);
      assertEquals(45, Math.min(angle, 360 - angle), .02);

      playback.seekNormalized(.25);
      assertEquals(6.25, node(renderer, "root").translation.x, .0001);
      assertEquals(.25, playback.normalizedTime(), .0001);
      assertEquals(0, model.nodes.first().translation.x, .0001);
      assertEquals(sourceFirst, firstRotation);
      assertEquals(sourceSecond, secondRotation);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void sparseMaskedOverridePreservesLowerPoseAndWeightFadeChangesPose() {
    Model model = animatedModel();
    try (Game game = new Game()) {
      GameObject object = game.createObject("layers");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();
      LinearModelMixer mixer = LinearModelMixer.builder(2).add("slow", 0).add("fast", 1).build();
      pose.play(mixer, PlayMode.LOOP, 0).seekNormalized(.5);
      ModelAnimationLayer upper = pose.addLayer("upper", NodeMask.paths("root/arm"));
      ModelPosePlayback attack = upper.play("attack", PlayMode.LOOP).seekNormalized(.5);
      attack.setSpeed(0);

      assertEquals(1, node(renderer, "root/arm").translation.x, .0001);
      assertEquals(2, node(renderer, "root/leg").translation.x, .0001);
      upper.fadeTo(1, 1);
      advance(game, .5f);
      assertEquals(.5, upper.weight(), .0001);
      assertEquals(3, node(renderer, "root/arm").translation.x, .0001);
      assertEquals(3, node(renderer, "root/leg").translation.x, .0001);
      game.pause();
      advance(game, .5f);
      assertEquals(.5, upper.weight(), .0001);
      game.resume();
      advance(game, .5f);
      assertEquals(1, upper.weight(), .0001);
      upper.setWeight(1);
      assertEquals(5, node(renderer, "root/arm").translation.x, .0001);
      upper.setWeight(0);
      assertEquals(1, node(renderer, "root/arm").translation.x, .0001);
      assertTrue(attack.isActive());

      upper.fadeTo(1, 1);
      advance(game, .25f);
      double interrupted = upper.weight();
      upper.fadeTo(0, 1);
      advance(game, .5f);
      assertEquals(interrupted * .5, upper.weight(), .0001);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void fadesStartedByEarlierComponentsWaitOneFrameAndUseWorldTime() {
    Model model = animatedModel();
    try (Game game = new Game()) {
      TimeManager time = game.addSystem(new TimeManager());
      ModelAnimationLayer[] layerRef = {null};
      GameObject object = game.createObject("deferred fade");
      object.addComponent(
          new Component() {
            boolean started;

            @Override
            public void onUpdate(float delta) {
              if (!started) {
                layerRef[0].fadeTo(1, 1);
                started = true;
              }
            }
          });
      object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      layerRef[0] = pose.addLayer("upper", NodeMask.paths("root/arm"));
      game.start();

      game.step(.25f);
      assertEquals(0, layerRef[0].weight(), 0);

      time.freeze(.2);
      game.step(.1f);
      game.step(.1f);
      assertEquals(0, layerRef[0].weight(), 0);

      for (int i = 0; i < 5; i++) game.step(.1f);
      assertEquals(.5, layerRef[0].weight(), .0001);
      for (int i = 0; i < 5; i++) game.step(.1f);
      assertEquals(1, layerRef[0].weight(), .0001);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void invalidInputsAndStaleOwnershipRejectBeforePoseMutation() {
    Model model = animatedModel();
    try (Game game = new Game()) {
      GameObject object = game.createObject("lifecycle");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();
      assertThrows(
          IllegalArgumentException.class,
          () -> pose.addLayer("bad", NodeMask.paths("root/missing")));
      assertThrows(
          IllegalArgumentException.class,
          () -> LinearModelMixer.builder(1).add("slow", 1).add("fast", 1));
      assertThrows(
          IllegalArgumentException.class,
          () -> pose.play(AnimationTransition.clip("attack").fade(.2)));
      assertNull(pose.current());

      ModelAnimationLayer layer = pose.addLayer("upper", NodeMask.paths("root/arm"));
      layer.setWeight(1);
      ModelPosePlayback stale = layer.play("attack", PlayMode.LOOP);
      stale.seekNormalized(.5);
      assertEquals(5, node(renderer, "root/arm").translation.x, .0001);
      object.removeComponent(pose);
      renderer.prepareForRender();
      assertEquals(1, node(renderer, "root/arm").translation.x, .0001);
      assertFalse(stale.isActive());
      assertThrows(IllegalStateException.class, () -> stale.seekNormalized(0));
      assertThrows(IllegalStateException.class, () -> layer.setWeight(.5));
    } finally {
      model.dispose();
    }
  }

  @Test
  public void componentMembershipUsesIdentityBeforeStartAndAfterRemoval() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("membership");
      Component component = new Component() {};
      assertFalse(object.containsComponent(null));
      assertFalse(object.containsComponent(component));
      object.addComponent(component);
      assertTrue(object.containsComponent(component));
      assertFalse(object.containsComponent(new Component() {}));
      assertTrue(object.removeComponent(component));
      assertFalse(object.containsComponent(component));
    }
  }

  @Test
  public void removingFailedConflictingSiblingDoesNotBreakPoseOwnerCleanup() {
    Model model = animatedModel();
    try (Game game = new Game()) {
      GameObject object = game.createObject("conflict cleanup");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();

      ModelPosePlayback playback = pose.play("slow", PlayMode.LOOP).seekNormalized(.5);
      ModelAnimator conflict = new ModelAnimator();
      assertThrows(IllegalStateException.class, () -> object.addComponent(conflict));
      assertTrue(object.containsComponent(conflict));
      assertTrue(object.removeComponent(conflict));
      assertFalse(object.containsComponent(conflict));
      assertSame(playback, pose.current());
      assertEquals(5, node(renderer, "root").translation.x, .0001);
      game.step(.1f);
      assertTrue(playback.isActive());
    } finally {
      model.dispose();
    }
  }

  @Test
  public void extremeFiniteMixerThresholdsKeepStableMidpointAndEndpoints() {
    Model model = animatedModel();
    try (Game game = new Game()) {
      GameObject object = game.createObject("extreme mixer thresholds");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();
      LinearModelMixer mixer =
          LinearModelMixer.builder(2)
              .add("slow", -Double.MAX_VALUE)
              .add("fast", Double.MAX_VALUE)
              .build();

      ModelPosePlayback playback = pose.play(mixer, PlayMode.LOOP, 0).seekNormalized(.5);
      assertEquals(12.5, node(renderer, "root").translation.x, .0001);
      playback.setParameter(-Double.MAX_VALUE);
      assertEquals(5, node(renderer, "root").translation.x, .0001);
      playback.setParameter(Double.MAX_VALUE);
      assertEquals(20, node(renderer, "root").translation.x, .0001);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void secondaryRendererKeepsLegacyControllerAndPrimaryRebindsAfterRemoval() {
    Model model = animatedModel();
    try (Game game = new Game()) {
      GameObject object = game.createObject("multiple model renderers");
      ModelRenderer primary = object.addComponent(new ModelRenderer(model));
      ModelRenderer secondary = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();

      ModelPosePlayback playback = pose.play("slow", PlayMode.LOOP).seekNormalized(.5);
      playback.setSpeed(0);
      primary.prepareForRender();
      secondary.prepareForRender();
      assertTrue(playback.isActive());
      assertEquals(5, node(primary, "root").translation.x, .0001);

      secondary.play("fast", true);
      assertNotNull(secondary.animation.current);
      assertSame(
          node(secondary, "root"),
          secondary.animation.current.animation.nodeAnimations.first().node);
      game.step(.1f);
      assertEquals(5, node(primary, "root").translation.x, .0001);
      assertEquals(.1, secondary.animation.current.time, .001);
      assertEquals(1, node(secondary, "root").localTransform.val[12], .001);
      primary.prepareForRender();
      secondary.prepareForRender();
      assertSame(playback, pose.current());
      assertTrue(playback.isActive());

      assertTrue(object.removeComponent(primary));
      assertFalse(playback.isActive());
      assertEquals(0, node(primary, "root").translation.x, .0001);
      secondary.prepareForRender();
      assertTrue(pose.owns(secondary));
      ModelPosePlayback replacement = pose.play("slow", PlayMode.LOOP).seekNormalized(0);
      assertTrue(replacement.isActive());
      assertEquals(0, node(secondary, "root").translation.x, .0001);
      assertEquals(0, model.nodes.first().translation.x, .0001);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void malformedChannelTypesAreRejectedBeforeReplacingCurrentPose() {
    Model model = animatedModel();
    try (Game game = new Game()) {
      GameObject object = game.createObject("malformed model clip");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();
      ModelPosePlayback current = pose.play("slow", PlayMode.LOOP).seekNormalized(.5);
      float before = node(renderer, "root").translation.x;
      Animation malformed = new Animation();
      malformed.id = "bad-rotation-type";
      malformed.duration = 1;
      malformed.nodeAnimations = new Array<>();
      NodeAnimation track = new NodeAnimation();
      track.node = renderer.instance.getNode("root");
      track.rotation = rawVectorChannel();
      malformed.nodeAnimations.add(track);
      renderer.instance.animations.add(malformed);

      assertThrows(
          IllegalArgumentException.class,
          () -> pose.play(AnimationTransition.clip("bad-rotation-type").loop()));
      assertSame(current, pose.current());
      assertEquals(before, node(renderer, "root").translation.x, .0001);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void vectorSamplingUsesFiniteIntermediateForExtremeOppositeEndpoints() {
    Model model = animatedModel();
    try (Game game = new Game()) {
      GameObject object = game.createObject("extreme vector keys");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      Animation extreme = new Animation();
      extreme.id = "extreme";
      extreme.duration = 1;
      extreme.nodeAnimations = new Array<>();
      NodeAnimation track = new NodeAnimation();
      track.node = renderer.instance.getNode("root");
      track.translation = new Array<>();
      track.translation.add(new NodeKeyframe<>(0, new Vector3(Float.MAX_VALUE, 0, 0)));
      track.translation.add(new NodeKeyframe<>(1, new Vector3(-Float.MAX_VALUE, 0, 0)));
      extreme.nodeAnimations.add(track);
      renderer.instance.animations.add(extreme);
      game.start();

      pose.play(AnimationTransition.clip("extreme").loop().startAt(.5));
      assertEquals(0, renderer.instance.getNode("root").translation.x, 0);
      assertTrue(Float.isFinite(renderer.instance.getNode("root").localTransform.val[12]));
    } finally {
      model.dispose();
    }
  }

  @Test
  public void threePointMixerClampsEndpointsAndInterpolatesOnlyTheNearestPair() {
    Model model = animatedModel();
    Animation middle = new Animation();
    middle.id = "middle";
    middle.duration = 2;
    middle.nodeAnimations = new Array<>();
    middle.nodeAnimations.add(
        track(model.nodes.first(), vector(new Vector3(), new Vector3(30, 0, 0), 2), null, null));
    model.animations.add(middle);
    try (Game game = new Game()) {
      GameObject object = game.createObject("three point mixer");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();
      LinearModelMixer mixer =
          LinearModelMixer.builder(2).add("slow", 0).add("middle", 1).add("fast", 4).build();
      ModelPosePlayback playback = pose.play(mixer, PlayMode.LOOP, -100).seekNormalized(.5);
      assertEquals(5, node(renderer, "root").translation.x, .0001);
      playback.setParameter(2.5);
      assertEquals(17.5, node(renderer, "root").translation.x, .0001);
      assertEquals(.5, playback.normalizedTime(), .0001);
      playback.setParameter(100);
      assertEquals(20, node(renderer, "root").translation.x, .0001);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void orderedSparseLayersBlendOnlyAuthoredChannels() {
    Model model = animatedModel();
    Animation rotationOnly = new Animation();
    rotationOnly.id = "arm-rotation";
    rotationOnly.duration = 2;
    rotationOnly.nodeAnimations = new Array<>();
    rotationOnly.nodeAnimations.add(
        track(model.nodes.first().getChild(0), null, rotations(0, 90, 2), null));
    model.animations.add(rotationOnly);
    try (Game game = new Game()) {
      GameObject object = game.createObject("ordered sparse layers");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();
      ModelAnimationLayer translation = pose.addLayer("translation", NodeMask.paths("root/arm"));
      ModelAnimationLayer rotation = pose.addLayer("rotation", NodeMask.paths("root/arm"));
      translation.setWeight(.5).play("attack", PlayMode.LOOP).seekNormalized(.5).setSpeed(0);
      rotation.setWeight(.5).play("arm-rotation", PlayMode.LOOP).seekNormalized(.5).setSpeed(0);
      Node arm = node(renderer, "root/arm");
      assertEquals(3, arm.translation.x, .0001);
      float angle = arm.rotation.getAngleAround(Vector3.Z);
      assertEquals(22.5, Math.min(angle, 360 - angle), .02);
      assertEquals(1, arm.scale.x, .0001);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void masksRejectAmbiguousIdsAndOwnerLimitsAreEnforced() {
    Model model = animatedModel();
    Node duplicateParent = new Node();
    duplicateParent.id = "other";
    Node duplicateArm = new Node();
    duplicateArm.id = "arm";
    duplicateParent.addChild(duplicateArm);
    model.nodes.add(duplicateParent);
    try (Game game = new Game()) {
      GameObject object = game.createObject("mask and owner limits");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();
      assertThrows(
          IllegalArgumentException.class, () -> pose.addLayer("ambiguous", NodeMask.ids("arm")));
      assertNotNull(pose.addLayer("qualified", NodeMask.paths("other/arm")));
      for (int i = 2; i <= 8; i++) pose.addLayer("layer-" + i, NodeMask.paths("root/arm"));
      assertThrows(
          IllegalStateException.class, () -> pose.addLayer("ninth", NodeMask.paths("root/arm")));
      renderer.play("attack", false);
      assertEquals("attack", pose.current().animationName());
      assertThrows(IllegalStateException.class, () -> object.addComponent(new ModelPoseAnimator()));
      assertThrows(IllegalStateException.class, () -> object.addComponent(new ModelAnimator()));
    } finally {
      model.dispose();
    }
  }

  @Test
  public void twoInstancesShareOnlyImmutableModelAnimationData() {
    Model model = animatedModel();
    try (Game game = new Game()) {
      GameObject a = game.createObject("a"), b = game.createObject("b");
      ModelRenderer ra = a.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pa = a.addComponent(new ModelPoseAnimator());
      ModelRenderer rb = b.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pb = b.addComponent(new ModelPoseAnimator());
      game.start();
      LinearModelMixer mixer = LinearModelMixer.builder(2).add("slow", 0).add("fast", 1).build();
      pa.play(mixer, PlayMode.LOOP, 0).seekNormalized(.5);
      pb.play(mixer, PlayMode.LOOP, 1).seekNormalized(.5);
      assertEquals(5, node(ra, "root").translation.x, .0001);
      assertEquals(20, node(rb, "root").translation.x, .0001);
      assertEquals(0, model.nodes.first().translation.x, .0001);
      assertNotSame(ra.instance.getNode("root"), rb.instance.getNode("root"));
    } finally {
      model.dispose();
    }
  }

  @Test
  public void callbacksCannotAdvanceOrStartReplacedOrNewLayersFromAnOldFramePlan() {
    Model model = animatedModel();
    try (Game game = new Game()) {
      GameObject object = game.createObject("callback layer plan");
      object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      ModelAnimationLayer trigger = pose.addLayer("trigger", NodeMask.paths("root/arm"));
      ModelAnimationLayer later = pose.addLayer("later", NodeMask.paths("root/leg"));
      game.start();

      ModelPosePlayback oldLater = later.play("attack", PlayMode.LOOP);
      int[] newStarts = {0};
      ModelPosePlayback[] replacement = {null};
      trigger
          .play(AnimationTransition.clip("attack").loop())
          .onStart(
              () ->
                  replacement[0] =
                      later.play("attack", PlayMode.LOOP).onStart(() -> newStarts[0]++));
      game.step(.1f);
      assertEquals(0, newStarts[0]);
      assertEquals(0, replacement[0].time(), .0001);
      assertFalse(oldLater.isActive());

      game.step(.1f);
      assertEquals(1, newStarts[0]);
      assertEquals(.1, replacement[0].time(), .0001);

      ModelAnimationLayer addFromCallback =
          pose.addLayer("add-trigger", NodeMask.paths("root/arm"));
      ModelPosePlayback[] addedPlayback = {null};
      addFromCallback
          .play(AnimationTransition.clip("attack").loop())
          .onStart(
              () -> {
                ModelAnimationLayer added = pose.addLayer("added", NodeMask.paths("root/leg"));
                addedPlayback[0] = added.play("attack", PlayMode.LOOP);
              });
      game.step(.1f);
      assertNotNull(addedPlayback[0]);
      assertEquals(0, addedPlayback[0].time(), .0001);
      game.step(.1f);
      assertEquals(.1, addedPlayback[0].time(), .0001);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void removingOwnerFromLayerCallbackStopsRemainingFrameCallbacks() {
    Model model = animatedModel();
    try (Game game = new Game()) {
      GameObject object = game.createObject("removed callback owner");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      ModelAnimationLayer first = pose.addLayer("first", NodeMask.paths("root/arm"));
      ModelAnimationLayer second = pose.addLayer("second", NodeMask.paths("root/leg"));
      game.start();
      int[] laterStarts = {0};
      ModelPosePlayback secondPlayback =
          second.play("attack", PlayMode.LOOP).onStart(() -> laterStarts[0]++);
      first
          .play(AnimationTransition.clip("attack").loop())
          .onStart(() -> object.removeComponent(pose));

      game.step(.1f);
      assertEquals(0, laterStarts[0]);
      renderer.prepareForRender();
      assertFalse(secondPlayback.isActive());
      assertFalse(renderer.instance.getNode("root", false).isAnimated);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void disabledOwnerOrRendererRestoresBindPoseAndStopsCurrentHandle() {
    Model model = animatedModel();
    try (Game game = new Game()) {
      GameObject object = game.createObject("disabled pose owner");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();
      ModelPosePlayback ownerPlayback = pose.play("slow", PlayMode.LOOP).seekNormalized(.5);
      assertEquals(5, node(renderer, "root").translation.x, .0001);
      pose.setEnabled(false);
      renderer.prepareForRender();
      assertFalse(ownerPlayback.isActive());
      assertEquals(0, node(renderer, "root").translation.x, .0001);
      pose.setEnabled(true);

      ModelPosePlayback rendererPlayback = pose.play("fast", PlayMode.LOOP).seekNormalized(.5);
      assertEquals(20, node(renderer, "root").translation.x, .0001);
      renderer.setEnabled(false);
      game.step(.1f);
      assertFalse(rendererPlayback.isActive());
      assertNull(pose.current());
      assertEquals(0, node(renderer, "root").translation.x, .0001);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void singleClipPlaybackSeekMarkersCompletionRestartAndStopUseSharedClock() {
    Model model = animatedModel();
    try (Game game = new Game()) {
      GameObject object = game.createObject("pose clock contract");
      object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();
      int[] starts = {0}, hits = {0}, completes = {0};
      ModelPosePlayback playback =
          pose.play(
                  AnimationTransition.clip("attack")
                      .mode(PlayMode.ONCE)
                      .markers(new AnimationMarker("hit", .5)))
              .onStart(() -> starts[0]++)
              .onMarker("hit", () -> hits[0]++)
              .onComplete(() -> completes[0]++);
      playback.seekNormalized(.25);
      advance(game, .6);
      assertEquals(1, starts[0]);
      assertEquals(1, hits[0]);
      advance(game, 1);
      assertEquals(1, completes[0]);
      playback.restart();
      assertEquals(0, playback.normalizedTime(), .0001);
      game.step(.1f);
      assertEquals(2, starts[0]);
      playback.stop();
      assertFalse(playback.isActive());
      assertNull(pose.current());
    } finally {
      model.dispose();
    }
  }

  private static Node node(ModelRenderer renderer, String path) {
    String[] parts = path.split("/");
    Node result = renderer.instance.getNode(parts[0], false);
    for (int i = 1; i < parts.length; i++) result = renderer.instance.getNode(parts[i], true);
    return result;
  }

  private static void advance(Game game, double seconds) {
    while (seconds > 0) {
      float delta = (float) Math.min(.1, seconds);
      game.step(delta);
      seconds -= delta;
    }
  }

  private static Model animatedModel() {
    Model model = new Model();
    Node root = new Node();
    root.id = "root";
    Node arm = new Node();
    arm.id = "arm";
    Node leg = new Node();
    leg.id = "leg";
    arm.translation.set(1, 0, 0);
    root.addChild(arm);
    root.addChild(leg);
    model.nodes.add(root);
    Animation slow = new Animation();
    slow.id = "slow";
    slow.duration = 1;
    slow.nodeAnimations = new Array<>();
    slow.nodeAnimations.add(
        track(
            root,
            vector(new Vector3(0, 0, 0), new Vector3(10, 0, 0), 1),
            rotations(0, 0, 1),
            vector(new Vector3(1, 1, 1), new Vector3(2, 2, 2), 1)));
    slow.nodeAnimations.add(track(leg, vector(new Vector3(), new Vector3(4, 0, 0), 1), null, null));
    model.animations.add(slow);
    Animation fast = new Animation();
    fast.id = "fast";
    fast.duration = 4;
    fast.nodeAnimations = new Array<>();
    fast.nodeAnimations.add(
        track(
            root,
            vector(new Vector3(), new Vector3(40, 0, 0), 4),
            rotations(0, 180, 4),
            vector(new Vector3(1, 1, 1), new Vector3(3, 3, 3), 4)));
    fast.nodeAnimations.add(
        track(leg, vector(new Vector3(), new Vector3(20, 0, 0), 4), null, null));
    model.animations.add(fast);
    Animation attack = new Animation();
    attack.id = "attack";
    attack.duration = 2;
    attack.nodeAnimations = new Array<>();
    attack.nodeAnimations.add(
        track(arm, vector(new Vector3(0, 0, 0), new Vector3(10, 0, 0), 2), null, null));
    model.animations.add(attack);
    return model;
  }

  private static NodeAnimation track(
      Node node,
      Array<NodeKeyframe<Vector3>> translation,
      Array<NodeKeyframe<Quaternion>> rotation,
      Array<NodeKeyframe<Vector3>> scaling) {
    NodeAnimation result = new NodeAnimation();
    result.node = node;
    result.translation = translation;
    result.rotation = rotation;
    result.scaling = scaling;
    return result;
  }

  private static Array<NodeKeyframe<Vector3>> vector(Vector3 from, Vector3 to, float duration) {
    Array<NodeKeyframe<Vector3>> result = new Array<>();
    result.add(new NodeKeyframe<>(0, from));
    result.add(new NodeKeyframe<>(duration, to));
    return result;
  }

  private static Array<NodeKeyframe<Quaternion>> rotations(float from, float to, float duration) {
    Array<NodeKeyframe<Quaternion>> result = new Array<>();
    result.add(new NodeKeyframe<>(0, new Quaternion().setFromAxis(Vector3.Z, from)));
    result.add(new NodeKeyframe<>(duration, new Quaternion().setFromAxis(Vector3.Z, to)));
    return result;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Array<NodeKeyframe<Quaternion>> rawVectorChannel() {
    Array result = new Array();
    result.add(new NodeKeyframe<>(0, new Vector3(1, 0, 0)));
    return result;
  }
}
