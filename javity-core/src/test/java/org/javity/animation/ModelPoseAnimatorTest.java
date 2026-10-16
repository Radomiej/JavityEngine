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

@org.junit.runner.RunWith(org.junit.runners.Parameterized.class)
public class ModelPoseAnimatorTest {
  @org.junit.runners.Parameterized.Parameters(name = "poseCache={0}")
  public static Object[] modes() {
    return new Object[] {false, true};
  }

  private final boolean poseCache;

  public ModelPoseAnimatorTest(boolean poseCache) {
    this.poseCache = poseCache;
  }

  private Game newGame() {
    Game game = new Game();
    if (poseCache) game.addSystem(new PoseBatchSystem(64));
    return game;
  }

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
    try (Game game = newGame()) {
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
    try (Game game = newGame()) {
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
    try (Game game = newGame()) {
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
    try (Game game = newGame()) {
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
      assertThrows(IllegalArgumentException.class, () -> pose.play("missing", PlayMode.LOOP));
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
  public void namedBaseClipCrossfadeUsesFrozenSourceAndSurvivesInterruptionWithoutPop() {
    Model model = animatedModel();
    try (Game game = newGame()) {
      GameObject object = game.createObject("base clip fade");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();

      ModelPosePlayback outgoing =
          pose.play(AnimationTransition.clip("slow").loop().startAt(0).speed(0));
      float source = node(renderer, "root").translation.x;
      ModelPosePlayback incoming =
          pose.play(AnimationTransition.clip("fast").loop().startAt(.5).speed(0).fade(1));
      assertFalse(outgoing.isActive());
      assertEquals(source, node(renderer, "root").translation.x, .0001);
      assertEquals(0, incoming.fadeProgress(), 0);
      assertTrue(incoming.isFading());

      advance(game, .5f);
      assertEquals(10, node(renderer, "root").translation.x, .0001);
      assertEquals(1.5, node(renderer, "root").scale.x, .0001);
      assertEquals(
          45,
          Math.min(
              node(renderer, "root").rotation.getAngleAround(Vector3.Z),
              360 - node(renderer, "root").rotation.getAngleAround(Vector3.Z)),
          .03);
      assertEquals(.5, incoming.fadeProgress(), .0001);
      double beforeInterrupt = node(renderer, "root").translation.x;
      ModelPosePlayback third =
          pose.play(AnimationTransition.clip("slow").loop().startAt(.5).speed(0).fade(1));
      assertEquals(beforeInterrupt, node(renderer, "root").translation.x, .0001);
      assertFalse(incoming.isActive());
      advance(game, .5f);
      assertEquals(7.5, node(renderer, "root").translation.x, .0001);
      advance(game, .5f);
      assertEquals(5, node(renderer, "root").translation.x, .0001);
      assertEquals(1.5, node(renderer, "root").scale.x, .0001);
      assertFalse(third.isFading());
      assertEquals(1, third.fadeProgress(), 0);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void firstBaseFadeUsesBindPoseAndEmptyLayerFadeStartsWithNoContribution() {
    Model model = animatedModel();
    node(model, "root").translation.set(2, 0, 0);
    node(model, "root/arm").translation.set(1, 0, 0);
    try (Game game = newGame()) {
      GameObject object = game.createObject("first transition bind source");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();

      ModelPosePlayback firstBase =
          pose.play(AnimationTransition.clip("fast").loop().startAt(.5).speed(0).fade(1));
      assertEquals(2, node(renderer, "root").translation.x, .0001);
      assertEquals(0, firstBase.fadeProgress(), 0);
      advance(game, .5f);
      assertEquals(11, node(renderer, "root").translation.x, .0001);

      ModelAnimationLayer emptyLayer = pose.addLayer("empty", NodeMask.paths("root/arm"));
      emptyLayer.setWeight(1);
      ModelPosePlayback firstLayer =
          emptyLayer.play(AnimationTransition.clip("attack").loop().startAt(0).speed(0).fade(1));
      assertEquals(1, node(renderer, "root/arm").translation.x, .0001);
      assertEquals(0, firstLayer.fadeProgress(), 0);
      advance(game, .5f);
      assertEquals(.5, node(renderer, "root/arm").translation.x, .0001);
      assertEquals(.5, firstLayer.fadeProgress(), .0001);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void maskedLayerClipFadePreservesSparseChannelsAndIndependentWeight() {
    Model model = animatedModel();
    Node arm = node(model, "root/arm");
    Animation turn = new Animation();
    turn.id = "turn";
    turn.duration = 1;
    turn.nodeAnimations = new Array<>();
    NodeAnimation rotation = new NodeAnimation();
    rotation.node = arm;
    rotation.rotation = new Array<>();
    rotation.rotation.add(new NodeKeyframe<>(0, new Quaternion().idt()));
    rotation.rotation.add(new NodeKeyframe<>(1, new Quaternion().setFromAxis(Vector3.Z, 120)));
    turn.nodeAnimations.add(rotation);
    model.animations.add(turn);
    Animation lower = new Animation();
    lower.id = "lower";
    lower.duration = 1;
    lower.nodeAnimations = new Array<>();
    lower.nodeAnimations.add(
        track(
            arm,
            vector(new Vector3(1, 0, 0), new Vector3(9, 0, 0), 1),
            rotations(0, 90, 1),
            vector(new Vector3(1, 1, 1), new Vector3(2, 1, 1), 1)));
    model.animations.add(lower);
    try (Game game = newGame()) {
      GameObject object = game.createObject("sparse clip fade");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();
      ModelPosePlayback base =
          pose.play(AnimationTransition.clip("lower").loop().startAt(.5).speed(0));
      ModelAnimationLayer layer = pose.addLayer("masked", NodeMask.paths("root/arm"));
      layer.setWeight(1);
      layer.play(AnimationTransition.clip("attack").loop().startAt(.25).speed(0));
      assertEquals(2.5, node(renderer, "root/arm").translation.x, .0001);
      ModelAnimationLayer probe = pose.addLayer("rotation-probe", NodeMask.paths("root/arm"));
      probe.setWeight(1).play(AnimationTransition.clip("turn").loop().startAt(.5).speed(0));
      assertEquals(
          60,
          Math.min(
              node(renderer, "root/arm").rotation.getAngleAround(Vector3.Z),
              360 - node(renderer, "root/arm").rotation.getAngleAround(Vector3.Z)),
          .03);
      probe.stop();
      ModelPosePlayback turnPlayback =
          layer.play(AnimationTransition.clip("turn").loop().startAt(.5).speed(0).fade(1));
      assertEquals(2.5, node(renderer, "root/arm").translation.x, .0001);
      assertEquals(1, layer.weight(), 0);
      assertTrue(turnPlayback.isFading());

      advance(game, .5f);
      assertEquals(3.75, node(renderer, "root/arm").translation.x, .0001);
      assertEquals(1.5, node(renderer, "root/arm").scale.x, .0001);
      assertEquals(
          52.5,
          Math.min(
              node(renderer, "root/arm").rotation.getAngleAround(Vector3.Z),
              360 - node(renderer, "root/arm").rotation.getAngleAround(Vector3.Z)),
          .03);
      base.seek(.75);
      assertEquals(4.75, node(renderer, "root/arm").translation.x, .0001);
      assertEquals(1.75, node(renderer, "root/arm").scale.x, .0001);
      advance(game, .5f);
      assertEquals(7, node(renderer, "root/arm").translation.x, .0001);
      assertEquals(
          60,
          Math.min(
              node(renderer, "root/arm").rotation.getAngleAround(Vector3.Z),
              360 - node(renderer, "root/arm").rotation.getAngleAround(Vector3.Z)),
          .03);
      assertEquals(1.75, node(renderer, "root/arm").scale.x, .0001);

      layer.setWeight(0).fadeTo(.5, 1);
      ModelPosePlayback attack =
          layer.play(AnimationTransition.clip("attack").loop().startAt(.5).speed(0).fade(1));
      assertEquals(7, node(renderer, "root/arm").translation.x, .0001);
      advance(game, .5f);
      assertEquals(6.75, node(renderer, "root/arm").translation.x, .0001);
      assertEquals(.25, layer.weight(), .0001);
      assertTrue(attack.isFading());
      advance(game, .5f);
      assertEquals(6, node(renderer, "root/arm").translation.x, .0001);
      assertEquals(.5, layer.weight(), .0001);
      assertFalse(attack.isFading());
    } finally {
      model.dispose();
    }
  }

  @Test
  public void maskedFadeBlendsOutgoingChannelsWhenIncomingHasNoTrackForThatNode() {
    Model model = animatedModel();
    Node arm = node(model, "root/arm");
    Node hand = new Node();
    hand.id = "hand";
    arm.addChild(hand);
    Node leg = node(model, "root/leg");
    Animation legOnly = new Animation();
    legOnly.id = "leg-only";
    legOnly.duration = 1;
    legOnly.nodeAnimations = new Array<>();
    legOnly.nodeAnimations.add(
        track(leg, vector(new Vector3(8, 0, 0), new Vector3(8, 0, 0), 1), null, null));
    model.animations.add(legOnly);
    try (Game game = newGame()) {
      GameObject object = game.createObject("sparse missing track fade");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();
      pose.play(AnimationTransition.clip("slow").loop().speed(0));
      ModelAnimationLayer layer = pose.addLayer("whole-rig", NodeMask.subtreePaths("root"));
      layer.setWeight(1);
      layer.play(AnimationTransition.clip("attack").loop().startAt(.5).speed(0));
      ModelPosePlayback incoming =
          layer.play(AnimationTransition.clip("leg-only").loop().speed(0).fade(1));
      assertEquals(5, node(renderer, "root/arm").translation.x, .0001);
      assertEquals(0, node(renderer, "root/arm/hand").translation.x, .0001);

      advance(game, .5f);
      assertEquals(3, node(renderer, "root/arm").translation.x, .0001);
      assertEquals(0, node(renderer, "root/arm/hand").translation.x, .0001);
      assertEquals(4, node(renderer, "root/leg").translation.x, .0001);
      advance(game, .5f);
      assertEquals(1, node(renderer, "root/arm").translation.x, .0001);
      assertEquals(0, node(renderer, "root/arm/hand").translation.x, .0001);
      assertEquals(8, node(renderer, "root/leg").translation.x, .0001);
      assertFalse(incoming.isFading());
    } finally {
      model.dispose();
    }
  }

  @Test
  public void clipFadeHonorsPlaybackPauseWorldPauseAndStartCallbackRevision() {
    Model model = animatedModel();
    try (Game game = newGame()) {
      TimeManager time = game.addSystem(new TimeManager());
      GameObject object = game.createObject("fade clocks");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();
      pose.play(AnimationTransition.clip("slow").loop().speed(0));
      int[] starts = {0};
      ModelPosePlayback target =
          pose.play(AnimationTransition.clip("fast").loop().startAt(.5).fade(1));
      target.onStart(
          () -> {
            starts[0]++;
            target.seekNormalized(.25);
          });

      game.step(.1f);
      assertEquals(1, starts[0]);
      assertEquals(0, target.fadeProgress(), 0);
      target.pause();
      advance(game, .3);
      assertEquals(0, target.fadeProgress(), 0);
      assertEquals(1, target.time(), .0001);
      target.resume();
      game.step(.1f);
      assertEquals(.1, target.fadeProgress(), .0001);
      double pausedPose = node(renderer, "root").translation.x;
      game.pause();
      advance(game, .3);
      assertEquals(.1, target.fadeProgress(), .0001);
      assertEquals(pausedPose, node(renderer, "root").translation.x, .0001);
      game.resume();
      var frozen = time.freeze(1);
      advance(game, .3);
      assertEquals(.1, target.fadeProgress(), .0001);
      frozen.cancel();
      target.setSpeed(0);
      advance(game, .3);
      assertEquals(.4, target.fadeProgress(), .0001);
      assertEquals(0, target.speed(), 0);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void laterLayerPreflightFailureLeavesEarlierFadeClocksWeightsAndPoseUntouched() {
    Model model = animatedModel();
    try (Game game = newGame()) {
      GameObject object = game.createObject("atomic fade frame");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();
      ModelPosePlayback first = pose.play(AnimationTransition.clip("slow").loop().speed(0));
      ModelPosePlayback incoming =
          pose.play(AnimationTransition.clip("fast").loop().startAt(.5).speed(0).fade(1));
      int[] starts = {0};
      incoming.onStart(() -> starts[0]++);
      ModelAnimationLayer layer = pose.addLayer("late", NodeMask.paths("root/arm"));
      ModelPosePlayback tooFast = layer.play("attack", PlayMode.LOOP).setSpeed(30000);
      layer.fadeTo(1, 1);
      double beforePose = node(renderer, "root").translation.x;
      assertThrows(IllegalArgumentException.class, () -> game.step(.6f));
      assertEquals(0, first.time(), .0001);
      assertEquals(0, incoming.fadeProgress(), 0);
      assertEquals(0, starts[0]);
      assertEquals(0, layer.weight(), 0);
      assertEquals(beforePose, node(renderer, "root").translation.x, .0001);
      assertTrue(tooFast.isActive());
    } finally {
      model.dispose();
    }
  }

  @Test
  public void interruptedFractionalLayerCoverageExcludesHigherLayerAndUsesLiveLowerPose() {
    Model model = animatedModel();
    Node arm = node(model, "root/arm");
    Animation lower = new Animation();
    lower.id = "lower-static";
    lower.duration = 1;
    lower.nodeAnimations = new Array<>();
    lower.nodeAnimations.add(
        track(arm, vector(new Vector3(2, 0, 0), new Vector3(6, 0, 0), 1), null, null));
    model.animations.add(lower);
    Animation attack20 = new Animation();
    attack20.id = "attack-20";
    attack20.duration = 1;
    attack20.nodeAnimations = new Array<>();
    attack20.nodeAnimations.add(
        track(arm, vector(new Vector3(20, 0, 0), new Vector3(20, 0, 0), 1), null, null));
    model.animations.add(attack20);
    try (Game game = newGame()) {
      GameObject object = game.createObject("fractional coverage");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();
      ModelPosePlayback base =
          pose.play(AnimationTransition.clip("lower-static").loop().startAt(0).speed(0));
      ModelAnimationLayer lowerLayer = pose.addLayer("lower-layer", NodeMask.paths("root/arm"));
      lowerLayer.setWeight(.5);
      lowerLayer.play(AnimationTransition.clip("attack").mode(PlayMode.ONCE).startAt(1).speed(0));
      ModelPosePlayback firstFade =
          lowerLayer.play(AnimationTransition.clip("slow").loop().speed(0).fade(1));
      ModelAnimationLayer later = pose.addLayer("later", NodeMask.paths("root/arm"));
      later.setWeight(.25);
      later.play(AnimationTransition.clip("attack").loop().startAt(.25).speed(0));
      advance(game, .5f);
      double beforeInterrupt = node(renderer, "root/arm").translation.x;
      assertEquals(3.625, beforeInterrupt, .0001);

      ModelPosePlayback secondFade =
          lowerLayer.play(AnimationTransition.clip("attack-20").loop().speed(0).fade(1));
      assertEquals(beforeInterrupt, node(renderer, "root/arm").translation.x, .0001);
      assertFalse(firstFade.isActive());
      base.seek(.5);
      double afterLowerMoves = node(renderer, "root/arm").translation.x;
      assertNotEquals(beforeInterrupt, afterLowerMoves, .0001);
      advance(game, .5f);
      assertEquals(7.1875, node(renderer, "root/arm").translation.x, .0001);
      assertTrue(secondFade.isFading());
    } finally {
      model.dispose();
    }
  }

  @Test
  public void movingOutgoingClockStopsCallbacksAndItsSampleRemainsFrozen() {
    Model model = animatedModel();
    try (Game game = newGame()) {
      GameObject object = game.createObject("frozen outgoing");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();
      int[] outgoingEvents = {0};
      ModelPosePlayback outgoing =
          pose.play(
              AnimationTransition.clip("slow").loop().markers(new AnimationMarker("late", .75)));
      outgoing.onMarker("late", () -> outgoingEvents[0]++);
      advance(game, .5);
      assertEquals(5, node(renderer, "root").translation.x, .0001);
      ModelPosePlayback incoming =
          pose.play(AnimationTransition.clip("fast").loop().startAt(0).speed(0).fade(1));
      assertFalse(outgoing.isActive());
      advance(game, .5);
      assertEquals(2.5, node(renderer, "root").translation.x, .0001);
      assertEquals(0, outgoingEvents[0]);
      advance(game, 1);
      assertEquals(0, node(renderer, "root").translation.x, .0001);
      assertEquals(0, outgoingEvents[0]);
      assertFalse(incoming.isFading());
    } finally {
      model.dispose();
    }
  }

  @Test
  public void repeatedBaseFadeInterruptionsKeepPoseContinuousAndRetireEveryOldHandle() {
    Model model = animatedModel();
    try (Game game = newGame()) {
      GameObject object = game.createObject("bounded repeated interruptions");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();
      ModelPosePlayback current = pose.play("slow", PlayMode.LOOP);
      current.setSpeed(0);
      for (int i = 0; i < 12; i++) {
        advance(game, .04f);
        double before = node(renderer, "root").translation.x;
        String nextName = (i & 1) == 0 ? "fast" : "slow";
        ModelPosePlayback next =
            pose.play(
                AnimationTransition.clip(nextName).loop().startAt((i % 4) * .2).speed(0).fade(.2));
        assertEquals(before, node(renderer, "root").translation.x, .0001);
        assertFalse(current.isActive());
        assertEquals(0, next.fadeProgress(), 0);
        current = next;
      }
      advance(game, .2f);
      assertFalse(current.isFading());
      assertTrue(Float.isFinite(node(renderer, "root").translation.x));
    } finally {
      model.dispose();
    }
  }

  @Test
  public void onceCompletionCanPrecedeFadeEndAndZeroDeltaStillDispatchesEligibleStart() {
    Model model = animatedModel();
    try (Game game = newGame()) {
      GameObject object = game.createObject("once fade endpoint");
      object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();
      pose.play(AnimationTransition.clip("fast").loop().speed(0));
      int[] starts = {0}, zeroMarkers = {0}, completions = {0};
      ModelPosePlayback target =
          pose.play(
                  AnimationTransition.clip("slow")
                      .mode(PlayMode.ONCE)
                      .fade(2)
                      .markers(new AnimationMarker("zero", 0)))
              .onStart(() -> starts[0]++)
              .onMarker("zero", () -> zeroMarkers[0]++)
              .onComplete(() -> completions[0]++);
      game.step(0);
      assertEquals(0, target.fadeProgress(), 0);
      assertEquals(1, starts[0]);
      assertEquals(1, zeroMarkers[0]);
      advance(game, 1.1);
      assertTrue(target.isCompleted());
      assertEquals(1, completions[0]);
      assertEquals(.55, target.fadeProgress(), .002);
      advance(game, .9);
      assertEquals(1, completions[0]);
      assertFalse(target.isFading());
      assertEquals(1, target.fadeProgress(), 0);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void completedOnceBaseCanPauseItsFadeFromOnCompleteWithoutPausingOtherSlots() {
    Model model = animatedModel();
    try (Game game = newGame()) {
      GameObject object = game.createObject("completed base pause");
      object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();
      pose.play(AnimationTransition.clip("fast").loop().speed(0));
      int[] completions = {0};
      ModelPosePlayback onceBase =
          pose.play(AnimationTransition.clip("slow").mode(PlayMode.ONCE).fade(2));
      onceBase.onComplete(
          () -> {
            completions[0]++;
            onceBase.pause();
          });
      ModelAnimationLayer other = pose.addLayer("other", NodeMask.paths("root/arm"));
      other.setWeight(0);
      other.play(AnimationTransition.clip("attack").loop().speed(0));
      ModelPosePlayback otherFade =
          other.play(AnimationTransition.clip("fast").loop().speed(0).fade(2));
      other.fadeTo(1, 2);

      advance(game, 1.1);
      assertTrue(onceBase.isCompleted());
      assertTrue(onceBase.isPaused());
      assertEquals(.5, onceBase.fadeProgress(), .002);
      assertEquals(1, completions[0]);
      assertEquals(.55, otherFade.fadeProgress(), .002);
      assertEquals(.55, other.weight(), .002);

      advance(game, .2);
      assertEquals(.5, onceBase.fadeProgress(), .002);
      assertEquals(.65, otherFade.fadeProgress(), .002);
      assertEquals(.65, other.weight(), .002);
      onceBase.resume();
      advance(game, .2);
      assertFalse(onceBase.isPaused());
      assertEquals(.6, onceBase.fadeProgress(), .002);
      assertEquals(1, completions[0]);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void completedOnceLayerCanPauseItsFadeAndResumeBesideContinuingBase() {
    Model model = animatedModel();
    try (Game game = newGame()) {
      GameObject object = game.createObject("completed layer pause");
      object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();
      pose.play(AnimationTransition.clip("slow").loop().speed(0));
      ModelPosePlayback baseFade =
          pose.play(AnimationTransition.clip("fast").loop().speed(0).fade(3));
      ModelAnimationLayer layer = pose.addLayer("once-layer", NodeMask.paths("root/arm"));
      layer.setWeight(0);
      layer.play(AnimationTransition.clip("attack").loop().speed(0));
      int[] completions = {0};
      ModelPosePlayback onceLayer =
          layer.play(AnimationTransition.clip("attack").mode(PlayMode.ONCE).fade(4));
      onceLayer.onComplete(
          () -> {
            completions[0]++;
            onceLayer.pause();
          });
      layer.fadeTo(1, 3);

      advance(game, 2.1);
      assertTrue(onceLayer.isCompleted());
      assertTrue(onceLayer.isPaused());
      assertEquals(.5, onceLayer.fadeProgress(), .003);
      assertEquals(1, completions[0]);
      assertEquals(.7, baseFade.fadeProgress(), .003);
      assertEquals(.7, layer.weight(), .003);

      advance(game, .2);
      assertEquals(.5, onceLayer.fadeProgress(), .003);
      assertEquals(.7667, baseFade.fadeProgress(), .004);
      assertEquals(.7667, layer.weight(), .004);
      onceLayer.resume();
      advance(game, .1);
      assertEquals(.525, onceLayer.fadeProgress(), .003);
      assertEquals(1, completions[0]);
      assertEquals(.8, layer.weight(), .003);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void seekRestartAndMixerBoundaryPreserveOnlyTheDocumentedTransitionState() {
    Model model = animatedModel();
    try (Game game = newGame()) {
      GameObject object = game.createObject("transition manipulation");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();
      LinearModelMixer mixer = LinearModelMixer.builder(1).add("slow", 0).add("fast", 1).build();
      ModelPosePlayback mixed = pose.play(mixer, PlayMode.LOOP, .5).setSpeed(0).seekNormalized(.5);
      double beforeClip = node(renderer, "root").translation.x;
      int[] starts = {0};
      ModelPosePlayback clip =
          pose.play(AnimationTransition.clip("fast").loop().startAt(.5).speed(0).fade(1));
      clip.onStart(() -> starts[0]++);
      assertEquals(beforeClip, node(renderer, "root").translation.x, .0001);
      assertFalse(mixed.isActive());
      assertThrows(IllegalStateException.class, () -> mixed.setParameter(.9));
      advance(game, .3);
      assertEquals(.3, clip.fadeProgress(), .001);
      clip.seekNormalized(.75);
      assertEquals(.3, clip.fadeProgress(), .001);
      clip.restart();
      assertEquals(.3, clip.fadeProgress(), .001);
      assertEquals(0, clip.normalizedTime(), 0);
      game.step(.1f);
      assertEquals(2, starts[0]);
      assertEquals(.4, clip.fadeProgress(), .001);
      LinearModelMixer invalid =
          LinearModelMixer.builder(1).add("slow", 0).add("missing", 1).build();
      ModelPosePlayback beforeInvalid = pose.current();
      assertThrows(IllegalArgumentException.class, () -> pose.play(invalid, PlayMode.LOOP));
      assertSame(beforeInvalid, pose.current());
      LinearModelMixer destination =
          LinearModelMixer.builder(1).add("slow", 0).add("fast", 1).build();
      ModelPosePlayback remixed = pose.play(destination, PlayMode.LOOP, .5);
      assertFalse(clip.isActive());
      assertFalse(remixed.isFading());
      assertEquals(1, remixed.fadeProgress(), 0);
      assertThrows(IllegalStateException.class, () -> clip.seekNormalized(.5));
    } finally {
      model.dispose();
    }
  }

  @Test
  public void quaternionFadeTreatsQAndNegativeQAsTheSameRotationWithoutMutatingKeys() {
    Model model = animatedModel();
    Animation fast = model.animations.get(1);
    Quaternion first = new Quaternion().setFromAxis(Vector3.Z, 179.9f);
    Quaternion opposite = first.cpy().mul(-1);
    fast.nodeAnimations.first().rotation.first().value.set(first);
    fast.nodeAnimations.first().rotation.peek().value.set(opposite);
    Quaternion authoredFirst = first.cpy();
    Quaternion authoredOpposite = opposite.cpy();
    try (Game game = newGame()) {
      GameObject object = game.createObject("equivalent quaternion keys");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();
      pose.play(AnimationTransition.clip("slow").loop().speed(0));
      ModelPosePlayback target =
          pose.play(AnimationTransition.clip("fast").loop().startAt(.5).speed(0).fade(1));
      advance(game, .5f);
      Quaternion sampled = node(renderer, "root").rotation;
      assertEquals(
          89.95,
          Math.min(sampled.getAngleAround(Vector3.Z), 360 - sampled.getAngleAround(Vector3.Z)),
          .08);
      assertTrue(fast.nodeAnimations.first().rotation.first().value.equals(authoredFirst));
      assertTrue(fast.nodeAnimations.first().rotation.peek().value.equals(authoredOpposite));
      target.stop();
    } finally {
      model.dispose();
    }
  }

  @Test
  public void fadeNormalizesLargeFiniteQuaternionsAndInterpolatesSignedScaleInTrs() {
    Model model = animatedModel();
    Node root = node(model, "root");
    Quaternion huge = new Quaternion(0, 0, 1e30f, 1e30f);
    Quaternion hugeOpposite = huge.cpy().mul(-1);
    Array<NodeKeyframe<Quaternion>> hugeRotationKeys = new Array<>();
    hugeRotationKeys.add(new NodeKeyframe<>(0, huge));
    hugeRotationKeys.add(new NodeKeyframe<>(1, hugeOpposite));
    Animation extreme = new Animation();
    extreme.id = "extreme-trs";
    extreme.duration = 1;
    extreme.nodeAnimations = new Array<>();
    extreme.nodeAnimations.add(
        track(
            root, null, hugeRotationKeys, vector(new Vector3(-4, 2, 3), new Vector3(-4, 2, 3), 1)));
    model.animations.add(extreme);
    Quaternion authoredFirst = huge.cpy();
    Quaternion authoredSecond = hugeOpposite.cpy();
    try (Game game = newGame()) {
      GameObject object = game.createObject("finite TRS fade");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();
      pose.play(AnimationTransition.clip("slow").loop().speed(0));
      ModelPosePlayback target =
          pose.play(AnimationTransition.clip("extreme-trs").loop().startAt(.5).speed(0).fade(1));
      advance(game, .5f);

      assertEquals(-1.5, node(renderer, "root").scale.x, .0001);
      assertEquals(1.5, node(renderer, "root").scale.y, .0001);
      assertEquals(2, node(renderer, "root").scale.z, .0001);
      Quaternion result = node(renderer, "root").rotation;
      assertTrue(Float.isFinite(result.x) && Float.isFinite(result.y));
      assertTrue(Float.isFinite(result.z) && Float.isFinite(result.w));
      assertEquals(1, result.len(), .0001);
      assertEquals(45, result.getAngleAround(Vector3.Z), .03);
      assertEquals(authoredFirst, extreme.nodeAnimations.first().rotation.first().value);
      assertEquals(authoredSecond, extreme.nodeAnimations.first().rotation.peek().value);
      assertTrue(target.isFading());
    } finally {
      model.dispose();
    }
  }

  @Test
  public void markerReplacementCapturesCommittedMidstepFadeAndSuppressesOldEvents() {
    Model model = animatedModel();
    try (Game game = newGame()) {
      GameObject object = game.createObject("marker fade replacement");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();
      pose.play(AnimationTransition.clip("slow").loop().speed(0));
      int[] oldLateMarkers = {0};
      ModelPosePlayback[] replacement = {null};
      ModelPosePlayback incoming =
          pose.play(
              AnimationTransition.clip("fast")
                  .loop()
                  .fade(1)
                  .markers(new AnimationMarker("replace", .025), new AnimationMarker("late", .05)));
      incoming.onMarker(
          "replace",
          () ->
              replacement[0] =
                  pose.play(AnimationTransition.clip("slow").loop().startAt(.5).speed(0).fade(1)));
      incoming.onMarker("late", () -> oldLateMarkers[0]++);
      game.step(.1f);
      assertNotNull(replacement[0]);
      assertFalse(incoming.isActive());
      assertEquals(.1, node(renderer, "root").translation.x, .002);
      advance(game, .3);
      assertEquals(0, oldLateMarkers[0]);
      assertTrue(replacement[0].isFading());
    } finally {
      model.dispose();
    }
  }

  @Test
  public void baseMarkerMutationCancelsLaterLayerFadeFromTheSameFramePlan() {
    Model model = animatedModel();
    try (Game game = newGame()) {
      GameObject object = game.createObject("cross-slot callback cancellation");
      object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();

      ModelAnimationLayer later = pose.addLayer("later", NodeMask.paths("root/arm"));
      ModelPosePlayback laterSource = later.play("slow", PlayMode.LOOP).setSpeed(0);
      ModelPosePlayback laterFade =
          later.play(AnimationTransition.clip("fast").loop().speed(0).fade(1));
      ModelPosePlayback base = pose.play(AnimationTransition.clip("slow").loop());
      int[] markerCalls = {0};
      base.onMarker(
          "pause-later",
          .1,
          () -> {
            markerCalls[0]++;
            laterFade.pause();
          });

      game.step(.5f);
      assertEquals(1, markerCalls[0]);
      assertTrue(laterFade.isPaused());
      assertEquals(0, laterFade.fadeProgress(), 0);
      assertEquals(0, laterFade.time(), 0);
      assertFalse(laterSource.isActive());
    } finally {
      model.dispose();
    }
  }

  @Test
  public void earlierStartCallbackRestartDefersLaterSlotStartToItsNextFramePlan() {
    Model model = animatedModel();
    try (Game game = newGame()) {
      GameObject object = game.createObject("later layer restart preflight");
      object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();

      ModelAnimationLayer later = pose.addLayer("later", NodeMask.paths("root/arm"));
      later.play(AnimationTransition.clip("slow").loop().speed(0));
      int[] laterStarts = {0};
      ModelPosePlayback laterTarget =
          later.play(AnimationTransition.clip("fast").loop().startAt(.25).speed(0).fade(1));
      laterTarget.onStart(() -> laterStarts[0]++);
      game.step(.1f);
      assertEquals(1, laterStarts[0]);
      assertEquals(.1, laterTarget.fadeProgress(), .001);

      ModelPosePlayback base = pose.play(AnimationTransition.clip("slow").loop().speed(0));
      base.onStart(laterTarget::restart);
      game.step(.1f);
      assertEquals("the restarted start remains queued", 1, laterStarts[0]);
      assertEquals(0, laterTarget.time(), 0);
      assertEquals(.1, laterTarget.fadeProgress(), .001);

      game.step(.1f);
      assertEquals("the requeued start fires once on the next update", 2, laterStarts[0]);
      assertEquals(0, laterTarget.time(), 0);
      assertEquals(.2, laterTarget.fadeProgress(), .001);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void componentMembershipUsesIdentityBeforeStartAndAfterRemoval() {
    try (Game game = newGame()) {
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
    try (Game game = newGame()) {
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
    try (Game game = newGame()) {
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
    try (Game game = newGame()) {
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
    try (Game game = newGame()) {
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
    try (Game game = newGame()) {
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
    try (Game game = newGame()) {
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
    try (Game game = newGame()) {
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
    try (Game game = newGame()) {
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
  public void subtreeUnionAndDifferenceMasksResolveOnceAndChangeSampledPose() {
    Model model = animatedModel();
    Animation limbs = new Animation();
    limbs.id = "limbs";
    limbs.duration = 1;
    limbs.nodeAnimations = new Array<>();
    limbs.nodeAnimations.add(
        track(
            node(model, "root/arm"), vector(new Vector3(), new Vector3(10, 0, 0), 1), null, null));
    limbs.nodeAnimations.add(
        track(
            node(model, "root/leg"), vector(new Vector3(), new Vector3(20, 0, 0), 1), null, null));
    Node armHand = new Node();
    armHand.id = "hand";
    node(model, "root/arm").addChild(armHand);
    Node armature = new Node();
    armature.id = "armature";
    Node finger = new Node();
    finger.id = "finger";
    armature.addChild(finger);
    model.nodes.first().addChild(armature);
    limbs.nodeAnimations.add(
        track(armHand, vector(new Vector3(), new Vector3(8, 0, 0), 1), null, null));
    limbs.nodeAnimations.add(
        track(finger, vector(new Vector3(), new Vector3(12, 0, 0), 1), null, null));
    model.animations.add(limbs);
    Node duplicateParent = new Node();
    duplicateParent.id = "other";
    Node duplicateArm = new Node();
    duplicateArm.id = "arm";
    duplicateParent.addChild(duplicateArm);
    model.nodes.add(duplicateParent);
    try (Game game = newGame()) {
      GameObject object = game.createObject("composed masks");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
      game.start();

      ModelAnimationLayer exact = pose.addLayer("exact", NodeMask.paths("root/arm"));
      exact.setWeight(1).play("limbs", PlayMode.LOOP).seekNormalized(.5).setSpeed(0);
      assertEquals(5, node(renderer, "root/arm").translation.x, .0001);
      assertEquals(0, node(renderer, "root/leg").translation.x, .0001);
      assertEquals(0, node(renderer, "root/arm/hand").translation.x, .0001);
      assertEquals(0, node(renderer, "root/armature/finger").translation.x, .0001);

      exact.setWeight(0);
      ModelAnimationLayer subtree = pose.addLayer("subtree", NodeMask.subtreePaths("root"));
      subtree.setWeight(1).play("limbs", PlayMode.LOOP).seekNormalized(.5).setSpeed(0);
      assertEquals(5, node(renderer, "root/arm").translation.x, .0001);
      assertEquals(10, node(renderer, "root/leg").translation.x, .0001);
      assertEquals(4, node(renderer, "root/arm/hand").translation.x, .0001);
      assertEquals(6, node(renderer, "root/armature/finger").translation.x, .0001);

      subtree.setWeight(0);
      ModelAnimationLayer union =
          pose.addLayer("union", NodeMask.union(NodeMask.paths("root/arm"), NodeMask.ids("leg")));
      union.setWeight(1).play("limbs", PlayMode.LOOP).seekNormalized(.5).setSpeed(0);
      assertEquals(5, node(renderer, "root/arm").translation.x, .0001);
      assertEquals(10, node(renderer, "root/leg").translation.x, .0001);
      assertEquals(0, node(renderer, "root/arm/hand").translation.x, .0001);
      assertEquals(0, node(renderer, "root/armature/finger").translation.x, .0001);

      union.setWeight(0);
      ModelAnimationLayer difference =
          pose.addLayer(
              "difference",
              NodeMask.difference(
                  NodeMask.subtreePaths("root"), NodeMask.subtreePaths("root/arm")));
      difference.setWeight(1).play("limbs", PlayMode.LOOP).seekNormalized(.5).setSpeed(0);
      assertEquals(1, node(renderer, "root/arm").translation.x, .0001);
      assertEquals(10, node(renderer, "root/leg").translation.x, .0001);
      assertEquals(0, node(renderer, "root/arm/hand").translation.x, .0001);
      assertEquals(6, node(renderer, "root/armature/finger").translation.x, .0001);

      difference.setWeight(0);
      ModelAnimationLayer empty =
          pose.addLayer(
              "empty-difference",
              NodeMask.difference(NodeMask.subtreePaths("root"), NodeMask.subtreePaths("root")));
      empty.setWeight(1).play("limbs", PlayMode.LOOP).seekNormalized(.5).setSpeed(0);
      assertEquals(1, node(renderer, "root/arm").translation.x, .0001);
      assertEquals(0, node(renderer, "root/leg").translation.x, .0001);
      assertEquals(0, node(renderer, "root/armature/finger").translation.x, .0001);
      assertThrows(
          IllegalArgumentException.class,
          () -> pose.addLayer("unknown", NodeMask.subtreePaths("root/missing")));

      // Exact path selectors remain usable even when the same ID occurs at another path.
      assertThrows(
          IllegalArgumentException.class,
          () -> pose.addLayer("duplicate-id", NodeMask.subtreeIds("arm")));
      ModelAnimationLayer qualified = pose.addLayer("qualified", NodeMask.subtreePaths("root/arm"));
      qualified.setWeight(1).play("limbs", PlayMode.LOOP).seekNormalized(.5);
      assertEquals(5, node(renderer, "root/arm").translation.x, .0001);
      assertEquals(4, node(renderer, "root/arm/hand").translation.x, .0001);
      assertEquals(0, node(renderer, "other/arm").translation.x, .0001);
      assertEquals(0, node(renderer, "root/armature/finger").translation.x, .0001);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void twoInstancesShareOnlyImmutableModelAnimationData() {
    Model model = animatedModel();
    try (Game game = newGame()) {
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
    try (Game game = newGame()) {
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
    try (Game game = newGame()) {
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
    try (Game game = newGame()) {
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
    try (Game game = newGame()) {
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
    for (int i = 1; i < parts.length; i++) result = result.getChild(parts[i], false, false);
    return result;
  }

  private static Node node(Model model, String path) {
    String[] parts = path.split("/");
    Node result = model.nodes.first();
    for (int i = 1; i < parts.length; i++) result = result.getChild(parts[i], false, false);
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
