package org.javity.animation;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodeAnimation;
import com.badlogic.gdx.graphics.g3d.model.NodeKeyframe;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import java.util.ArrayList;
import java.util.List;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.render.ModelRenderer;
import org.javity.render.SpriteAnimator;
import org.javity.render.SpriteRenderer2D;
import org.junit.Test;

public class AnimationTest {
  @Test
  public void spritePlaybackUsesWorldClockAndClampsOnceAtFinalFrame() {
    try (Game game = new Game()) {
      TextureRegion first = new TextureRegion(), second = new TextureRegion();
      GameObject object = game.createObject("sprite");
      SpriteRenderer2D sprite = object.addComponent(new SpriteRenderer2D(first));
      SpriteAnimationPlayer player = object.addComponent(new SpriteAnimationPlayer());
      player.register(new SpriteClip("walk", List.of(first, second), .1f));
      game.start();
      SpritePlayback playback = player.play("walk", PlayMode.ONCE);
      int[] completed = {0};
      playback.onComplete(() -> completed[0]++);
      game.step(.1f);
      assertSame(second, sprite.region);
      game.step(.1f);
      game.step(.1f);
      assertSame(second, sprite.region);
      assertTrue(playback.isCompleted());
      assertEquals(1, playback.normalizedTime(), 0);
      assertEquals(.2, playback.time(), .00001);
      assertEquals(1, completed[0]);
      assertThrows(IllegalArgumentException.class, () -> playback.setSpeed(Double.NaN));
      assertThrows(IllegalArgumentException.class, () -> playback.setSpeed(-1));
      SpritePlayback excessiveLoops = player.play("walk", PlayMode.LOOP).setSpeed(11_000);
      assertThrows(IllegalArgumentException.class, () -> game.step(.1f));
      assertEquals(0, excessiveLoops.time(), 0);
    }
  }

  @Test
  public void spriteLoopCallbacksCanReplacePlaybackSafely() {
    try (Game game = new Game()) {
      TextureRegion first = new TextureRegion(), second = new TextureRegion();
      GameObject object = game.createObject("sprite");
      SpriteRenderer2D sprite = object.addComponent(new SpriteRenderer2D(first));
      SpriteAnimationPlayer player = object.addComponent(new SpriteAnimationPlayer());
      player.register(new SpriteClip("walk", List.of(first, second), .1f));
      player.register(new SpriteClip("idle", List.of(second), .2f));
      game.start();
      List<String> events = new ArrayList<>();
      SpritePlayback walk = player.play("walk", PlayMode.LOOP);
      walk.onLoop(
          () -> {
            events.add("loop");
            player.play("idle", PlayMode.ONCE);
          });
      game.step(.1f);
      game.step(.1f);
      assertEquals(List.of("loop"), events);
      assertEquals("idle", player.current().clipName());
      assertSame(second, sprite.region);
      walk.setSpeed(0);
      assertEquals("idle", player.current().clipName());
    }
  }

  @Test
  public void spriteStartCallbackWaitsForEligibilityAndRunsAgainAfterRestart() {
    try (Game game = new Game()) {
      TextureRegion frame = new TextureRegion();
      GameObject object = game.createObject("sprite");
      object.addComponent(new SpriteRenderer2D(frame));
      SpriteAnimationPlayer player = object.addComponent(new SpriteAnimationPlayer());
      player.register(new SpriteClip("idle", List.of(frame), .1f));
      game.start();
      int[] starts = {0};
      SpritePlayback playback =
          player.play("idle", PlayMode.LOOP).setSpeed(0).onStart(() -> starts[0]++);
      playback.pause();
      game.step(0);
      assertEquals(0, starts[0]);
      playback.resume();
      game.step(0);
      assertEquals(1, starts[0]);
      playback.onStart(() -> starts[0] += 100);
      game.step(.1f);
      assertEquals("late onStart registration must not fire retroactively", 1, starts[0]);
      playback.restart();
      game.step(0);
      assertEquals(101, starts[0]);
    }
  }

  @Test
  public void spriteStartCallbackReplacementDoesNotWriteOldPlaybackFrame() {
    try (Game game = new Game()) {
      TextureRegion walkFrame = new TextureRegion(), idleFrame = new TextureRegion();
      GameObject object = game.createObject("sprite");
      SpriteRenderer2D sprite = object.addComponent(new SpriteRenderer2D(walkFrame));
      SpriteAnimationPlayer player = object.addComponent(new SpriteAnimationPlayer());
      player.register(new SpriteClip("walk", List.of(walkFrame), .1f));
      player.register(new SpriteClip("idle", List.of(idleFrame), .2f));
      game.start();
      SpritePlayback walk = player.play("walk", PlayMode.LOOP);
      walk.onStart(() -> player.play("idle", PlayMode.ONCE));
      game.step(.1f);
      assertFalse(walk.isActive());
      assertEquals("idle", player.current().clipName());
      assertEquals(0, player.current().time(), 0);
      assertSame(idleFrame, sprite.region);
    }
  }

  @Test
  public void restartFromStartCallbackQueuesTheNextStartForNextUpdate() {
    try (Game game = new Game()) {
      TextureRegion frame = new TextureRegion();
      GameObject object = game.createObject("sprite");
      object.addComponent(new SpriteRenderer2D(frame));
      SpriteAnimationPlayer player = object.addComponent(new SpriteAnimationPlayer());
      player.register(new SpriteClip("idle", List.of(frame), .1f));
      game.start();
      SpritePlayback[] playback = new SpritePlayback[1];
      int[] starts = {0};
      playback[0] =
          player
              .play("idle", PlayMode.LOOP)
              .onStart(
                  () -> {
                    if (++starts[0] == 1) playback[0].restart();
                  });
      game.step(0);
      assertEquals(1, starts[0]);
      game.step(0);
      assertEquals(2, starts[0]);
    }
  }

  @Test
  public void modelStartCallbackWaitsForEligibleUpdateAndReplacementStopsOldControllerTick() {
    Model model = animatedModel();
    try (Game game = new Game()) {
      GameObject object = game.createObject("model");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelAnimator animator = object.addComponent(new ModelAnimator());
      game.start();
      int[] starts = {0};
      ModelPlayback playback =
          animator.play("move", PlayMode.LOOP).setSpeed(0).onStart(() -> starts[0]++);
      playback.pause();
      game.step(0);
      assertEquals(0, starts[0]);
      playback.resume();
      game.step(0);
      assertEquals(1, starts[0]);
      playback.onStart(() -> starts[0] += 100);
      playback.restart();
      playback.setSpeed(1);
      playback.onStart(
          () -> {
            starts[0]++;
            animator.play("jump", PlayMode.ONCE);
          });
      game.step(.1f);
      assertEquals(2, starts[0]);
      assertEquals("jump", animator.current().animationName());
      assertEquals(0, animator.current().time(), 0);
      assertEquals(0, root(renderer).translation.x, .001f);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void modelStartCallbackCanRemoveRendererWithoutStalePoseWrite() {
    Model model = animatedModel();
    try (Game game = new Game()) {
      GameObject object = game.createObject("model");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelAnimator animator = object.addComponent(new ModelAnimator());
      game.start();
      ModelPlayback playback = animator.play("move", PlayMode.LOOP);
      playback.onStart(
          () -> {
            object.removeComponent(renderer);
            renderer.instance.getNode("root").localTransform.idt().translate(77, 0, 0);
          });
      game.step(.1f);
      assertEquals(77, root(renderer).translation.x, .001f);
      assertFalse(playback.isActive());
    } finally {
      model.dispose();
    }
  }

  @Test
  public void legacyAndNamedSpriteAnimationCannotOwnSameRenderer() {
    try (Game game = new Game()) {
      TextureRegion frame = new TextureRegion();
      GameObject object = game.createObject("sprite");
      object.addComponent(new SpriteRenderer2D(frame));
      object.addComponent(new SpriteAnimator(List.of(frame), .1f, true));
      object.addComponent(new SpriteAnimationPlayer());
      assertThrows(IllegalStateException.class, game::start);
    }
  }

  @Test
  public void modelAnimatorDrivesRealPoseAndWorldPauseAndSpeed() {
    Model model = animatedModel();
    try (Game game = new Game()) {
      GameObject object = game.createObject("model");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelAnimator animator = object.addComponent(new ModelAnimator());
      game.start();
      ModelPlayback playback = animator.play("move", PlayMode.ONCE);
      playback.setSpeed(2);
      game.step(.1f);
      game.step(.1f);
      assertEquals(4, root(renderer).translation.x, .001f);
      game.pause();
      game.step(.1f);
      assertEquals(4, root(renderer).translation.x, .001f);
      game.resume();
      for (int i = 0; i < 3; i++) game.step(.1f);
      assertEquals(10, root(renderer).translation.x, .001f);
      assertTrue(playback.isCompleted());
      assertEquals(1, playback.normalizedTime(), 0);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void modelAnimatorReusesRendererControllerAndKeepsInstancesIndependent() {
    Model model = animatedModel();
    try (Game game = new Game()) {
      GameObject a = game.createObject("a"), b = game.createObject("b");
      ModelRenderer ra = a.addComponent(new ModelRenderer(model));
      ModelAnimator aa = a.addComponent(new ModelAnimator());
      ModelRenderer rb = b.addComponent(new ModelRenderer(model));
      ModelAnimator ab = b.addComponent(new ModelAnimator());
      game.start();
      assertSame(ra.animation, aa.controller());
      assertSame(rb.animation, ab.controller());
      assertNotSame(aa.controller(), ab.controller());
      ModelPlayback playbackA = aa.play("move", PlayMode.LOOP);
      ab.play("move", PlayMode.LOOP);
      game.step(.25f);
      assertEquals(1, root(ra).translation.x, .001f);
      assertEquals(1, root(rb).translation.x, .001f);
      playbackA.pause();
      game.step(.25f);
      assertEquals(1, root(ra).translation.x, .001f);
      assertEquals(2, root(rb).translation.x, .001f);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void legacyRendererPlayDelegatesToAnimatorBeforeGameStart() {
    Model model = animatedModel();
    try (Game game = new Game()) {
      GameObject object = game.createObject("model");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelAnimator animator = object.addComponent(new ModelAnimator());
      renderer.play("move", false);
      assertNotNull(animator.current());
      game.start();
      game.step(.1f);
      assertEquals(.1, animator.current().time(), .00001);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void legacyModelRendererPlayBeforeAttachmentKeepsLegacyTicker() {
    Model model = animatedModel();
    try (Game game = new Game()) {
      ModelRenderer renderer = new ModelRenderer(model);
      renderer.play("move", false);
      game.createObject("model").addComponent(renderer);
      game.start();
      game.step(.1f);
      assertEquals(1, root(renderer).translation.x, .001f);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void removingRendererInLoopCallbackStopsManagedPlayback() {
    Model model = animatedModel();
    try (Game game = new Game()) {
      GameObject object = game.createObject("model");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelAnimator animator = object.addComponent(new ModelAnimator());
      game.start();
      ModelPlayback playback = animator.play("move", PlayMode.LOOP);
      playback.onLoop(
          () -> {
            object.removeComponent(renderer);
            renderer.instance.getNode("root").localTransform.idt().translate(77, 0, 0);
          });
      for (int i = 0; i < 10; i++) game.step(.1f);
      assertFalse(playback.isActive());
      game.step(.1f);
      assertEquals(77, root(renderer).translation.x, .001f);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void rendererRemovedInGameCallbackIsNotUpdatedByAnimator() {
    Model model = animatedModel();
    GameObject[] targetObject = new GameObject[1];
    ModelRenderer[] targetRenderer = new ModelRenderer[1];
    Game game =
        new Game() {
          @Override
          public void onUpdate(float delta) {
            targetObject[0].removeComponent(targetRenderer[0]);
          }
        };
    try (game) {
      targetObject[0] = game.createObject("model");
      targetRenderer[0] = targetObject[0].addComponent(new ModelRenderer(model));
      ModelAnimator animator = targetObject[0].addComponent(new ModelAnimator());
      game.start();
      ModelPlayback playback = animator.play("move", PlayMode.LOOP);
      game.step(.1f);
      assertFalse(playback.isActive());
      assertEquals(0, playback.time(), 0);
      assertEquals(0, root(targetRenderer[0]).translation.x, .001f);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void spriteLoopCallbackRemovalCannotOverwriteCallbackRegion() {
    try (Game game = new Game()) {
      TextureRegion first = new TextureRegion(), second = new TextureRegion();
      TextureRegion sentinel = new TextureRegion();
      GameObject object = game.createObject("sprite");
      SpriteRenderer2D sprite = object.addComponent(new SpriteRenderer2D(first));
      SpriteAnimationPlayer player = object.addComponent(new SpriteAnimationPlayer());
      player.register(new SpriteClip("walk", List.of(first, second), .1f));
      game.start();
      player
          .play("walk", PlayMode.LOOP)
          .onLoop(
              () -> {
                sprite.region = sentinel;
                object.removeComponent(player);
              });
      game.step(.1f);
      game.step(.1f);
      assertSame(sentinel, sprite.region);
    }
  }

  @Test
  public void spriteTargetRemovedInCallbackCannotBeMutatedAfterward() {
    try (Game game = new Game()) {
      TextureRegion frame = new TextureRegion(), sentinel = new TextureRegion();
      GameObject object = game.createObject("sprite");
      SpriteRenderer2D sprite = object.addComponent(new SpriteRenderer2D(frame));
      SpriteAnimationPlayer player = object.addComponent(new SpriteAnimationPlayer());
      player.register(new SpriteClip("idle", List.of(frame), .1f));
      game.start();
      player
          .play("idle", PlayMode.ONCE)
          .onComplete(
              () -> {
                sprite.region = sentinel;
                object.removeComponent(sprite);
              });
      game.step(.1f);
      assertSame(sentinel, sprite.region);
    }
  }

  @Test
  public void modelCallbacksAndValidationAreSynchronousAndReentrantSafe() {
    Model model = animatedModel();
    try (Game game = new Game()) {
      GameObject object = game.createObject("model");
      object.addComponent(new ModelRenderer(model));
      ModelAnimator animator = object.addComponent(new ModelAnimator());
      game.start();
      assertThrows(IllegalArgumentException.class, () -> animator.play("missing", PlayMode.ONCE));
      assertThrows(
          IllegalArgumentException.class,
          () -> animator.crossFade("move", Float.NaN, PlayMode.ONCE));
      assertThrows(
          IllegalArgumentException.class,
          () -> animator.crossFade("move", Double.MAX_VALUE, PlayMode.ONCE));
      List<String> events = new ArrayList<>();
      ModelPlayback loop = animator.play("move", PlayMode.LOOP);
      loop.onLoop(
          () -> {
            events.add("loop");
            animator.stop();
          });
      for (int i = 0; i < 11; i++) game.step(.1f);
      assertEquals(List.of("loop"), events);
      assertNull(animator.current());
      assertFalse(loop.isActive());

      ModelPlayback guarded = animator.play("move", PlayMode.LOOP).setSpeed(Double.MAX_VALUE);
      assertThrows(IllegalArgumentException.class, () -> game.step(.1f));
      assertEquals(0, guarded.time(), 0);
      guarded.setSpeed(1);
      assertThrows(
          IllegalArgumentException.class, () -> guarded.setSpeed(Double.POSITIVE_INFINITY));
      ModelPlayback excessiveLoops = animator.play("move", PlayMode.LOOP).setSpeed(11_000);
      assertThrows(IllegalArgumentException.class, () -> game.step(.1f));
      assertEquals(0, excessiveLoops.time(), 0);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void modelCrossFadeReachesTargetPoseAtFadeEndpoint() {
    Model model = animatedModel();
    try (Game game = new Game()) {
      GameObject object = game.createObject("model");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelAnimator animator = object.addComponent(new ModelAnimator());
      game.start();
      animator.play("move", PlayMode.LOOP);
      game.step(.1f);
      ModelPlayback fade = animator.crossFade("jump", .2, PlayMode.ONCE);
      for (int i = 0; i < 2; i++) game.step(.1f);
      assertTrue(fade.isCompleted());
      assertEquals(20, root(renderer).translation.x, .01f);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void modelAnimatorRejectsMultipleOwnersAndRemovalRestoresLegacyTicker() {
    Model model = animatedModel();
    try (Game game = new Game()) {
      GameObject object = game.createObject("model");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      object.addComponent(new ModelAnimator());
      object.addComponent(new ModelAnimator());
      assertThrows(IllegalStateException.class, game::start);
    } finally {
      model.dispose();
    }
    Model model2 = animatedModel();
    try (Game game = new Game()) {
      GameObject object = game.createObject("model");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model2));
      ModelAnimator animator = object.addComponent(new ModelAnimator());
      game.start();
      animator.play("move", PlayMode.LOOP);
      object.removeComponent(animator);
      game.step(0);
      renderer.play("move", false);
      game.step(.1f);
      assertEquals(1, root(renderer).translation.x, .001f);
    } finally {
      model2.dispose();
    }
  }

  private static Model animatedModel() {
    Model model = new Model();
    Node node = new Node();
    node.id = "root";
    model.nodes.add(node);
    Animation animation = new Animation();
    animation.id = "move";
    animation.duration = 1;
    NodeAnimation nodeAnimation = new NodeAnimation();
    nodeAnimation.node = node;
    nodeAnimation.translation = new Array<>();
    nodeAnimation.translation.add(new NodeKeyframe<>(0, new Vector3()));
    nodeAnimation.translation.add(new NodeKeyframe<>(1, new Vector3(10, 0, 0)));
    animation.nodeAnimations = new Array<>();
    animation.nodeAnimations.add(nodeAnimation);
    model.animations.add(animation);
    Animation jump = new Animation();
    jump.id = "jump";
    jump.duration = .2f;
    NodeAnimation jumpNodeAnimation = new NodeAnimation();
    jumpNodeAnimation.node = node;
    jumpNodeAnimation.translation = new Array<>();
    jumpNodeAnimation.translation.add(new NodeKeyframe<>(0, new Vector3()));
    jumpNodeAnimation.translation.add(new NodeKeyframe<>(.2f, new Vector3(20, 0, 0)));
    jump.nodeAnimations = new Array<>();
    jump.nodeAnimations.add(jumpNodeAnimation);
    model.animations.add(jump);
    return model;
  }

  private static Node root(ModelRenderer renderer) {
    Node node = renderer.instance.getNode("root");
    node.localTransform.getTranslation(node.translation);
    return node;
  }

  @Test
  public void transitionTemplatesAreImmutableAndValidateMarkerNames() {
    AnimationMarker hit = new AnimationMarker("hit", .4);
    AnimationTransition transition =
        AnimationTransition.clip("move").loop().fade(.25).speed(1.5).startAt(.2).markers(hit);
    assertEquals("move", transition.clipName());
    assertEquals(PlayMode.LOOP, transition.mode());
    assertEquals(.25, transition.fadeSeconds(), 0);
    assertEquals(1.5, transition.speed(), 0);
    assertEquals(.2, transition.normalizedStart(), 0);
    assertEquals(List.of(hit), transition.markers());
    assertThrows(UnsupportedOperationException.class, () -> transition.markers().clear());
    assertThrows(
        IllegalArgumentException.class,
        () -> AnimationTransition.clip("move").markers(hit, new AnimationMarker("hit", .8)));
    assertThrows(IllegalArgumentException.class, () -> new AnimationMarker(" ", .2));
    assertThrows(IllegalArgumentException.class, () -> new AnimationMarker("bad", Double.NaN));
  }

  @Test
  public void spriteSeekAppliesFrameImmediatelyAndOnceEndpointCanReactivateSilently() {
    try (Game game = new Game()) {
      TextureRegion first = new TextureRegion(),
          middle = new TextureRegion(),
          last = new TextureRegion();
      GameObject object = game.createObject("sprite");
      SpriteRenderer2D sprite = object.addComponent(new SpriteRenderer2D(first));
      SpriteAnimationPlayer player = object.addComponent(new SpriteAnimationPlayer());
      player.register(new SpriteClip("walk", List.of(first, middle, last), .1f));
      game.start();
      int[] callbacks = {0};
      SpritePlayback playback =
          player
              .play(AnimationTransition.clip("walk").markers(new AnimationMarker("hit", .5)))
              .onStart(() -> callbacks[0]++)
              .onMarker("hit", () -> callbacks[0] += 10)
              .onComplete(() -> callbacks[0] += 100);
      playback.seekNormalized(.5);
      assertEquals(.5, playback.normalizedTime(), 0);
      assertSame(middle, sprite.region);
      playback.seekNormalized(1);
      assertTrue(playback.isCompleted());
      assertSame(last, sprite.region);
      playback.seek(.1);
      assertFalse(playback.isCompleted());
      assertSame(middle, sprite.region);
      assertEquals(0, callbacks[0]);
      assertThrows(IllegalArgumentException.class, () -> playback.seekNormalized(Double.NaN));
      assertEquals(.1, playback.time(), 0);
    }
  }

  @Test
  public void spriteMarkersOrderTiesAndLoopBoundariesWithoutDuplicateStart() {
    try (Game game = new Game()) {
      TextureRegion a = new TextureRegion(), b = new TextureRegion();
      GameObject object = game.createObject("sprite");
      object.addComponent(new SpriteRenderer2D(a));
      SpriteAnimationPlayer player = object.addComponent(new SpriteAnimationPlayer());
      player.register(new SpriteClip("walk", List.of(a, b), .1f));
      game.start();
      List<String> events = new ArrayList<>();
      SpritePlayback playback =
          player.play(
              AnimationTransition.clip("walk")
                  .loop()
                  .markers(
                      new AnimationMarker("open", 0),
                      new AnimationMarker("hit-a", .5),
                      new AnimationMarker("hit-b", .5),
                      new AnimationMarker("end", 1)));
      playback.onMarker("open", () -> events.add("open"));
      playback.onMarker("hit-a", () -> events.add("hit-a"));
      playback.onMarker("hit-b", () -> events.add("hit-b"));
      playback.onMarker("end", () -> events.add("end"));
      playback.onLoop(() -> events.add("loop"));
      game.step(0);
      assertEquals(List.of("open"), events);
      game.step(.1f);
      assertEquals(List.of("open", "hit-a", "hit-b"), events);
      game.step(.1f);
      assertEquals(List.of("open", "hit-a", "hit-b", "end", "loop", "open"), events);
      game.step(0);
      assertEquals(6, events.size());
    }
  }

  @Test
  public void spriteMarkerMutationAndThrowStopTheOldDispatch() {
    try (Game game = new Game()) {
      TextureRegion a = new TextureRegion(), b = new TextureRegion();
      GameObject object = game.createObject("sprite");
      object.addComponent(new SpriteRenderer2D(a));
      SpriteAnimationPlayer player = object.addComponent(new SpriteAnimationPlayer());
      player.register(new SpriteClip("walk", List.of(a, b), .1f));
      game.start();
      List<String> events = new ArrayList<>();
      SpritePlayback playback =
          player.play(
              AnimationTransition.clip("walk")
                  .markers(new AnimationMarker("seek", .25), new AnimationMarker("later", .5)));
      playback.onMarker("seek", () -> playback.seekNormalized(.75));
      playback.onMarker("later", () -> events.add("stale"));
      game.step(.1f);
      assertEquals(List.of(), events);
      assertEquals(.75, playback.normalizedTime(), 0);
      SpritePlayback throwing =
          player.play(
              AnimationTransition.clip("walk")
                  .markers(new AnimationMarker("throw", .25), new AnimationMarker("later", .5)));
      throwing.onMarker(
          "throw",
          () -> {
            throw new IllegalStateException("marker failed");
          });
      throwing.onMarker("later", () -> events.add("after throw"));
      assertThrows(IllegalStateException.class, () -> game.step(.1f));
      assertFalse(throwing.isActive());
      assertEquals(List.of(), events);
    }
  }

  @Test
  public void modelTransitionSeekChangesActualNodePoseAndReactivatesOncePlayback() {
    Model model = animatedModel();
    try (Game game = new Game()) {
      GameObject object = game.createObject("model");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelAnimator animator = object.addComponent(new ModelAnimator());
      game.start();
      ModelPlayback playback = animator.play(AnimationTransition.clip("move"));
      playback.seekNormalized(.5);
      assertEquals(.5, playback.normalizedTime(), 0);
      assertEquals(5, root(renderer).translation.x, .01f);
      playback.seekNormalized(1);
      assertTrue(playback.isCompleted());
      assertEquals(10, root(renderer).translation.x, .01f);
      playback.seekNormalized(.2);
      assertFalse(playback.isCompleted());
      assertEquals(2, root(renderer).translation.x, .01f);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void reusableTransitionCreatesIndependentSpriteClocksAndFadeRejectsBeforeReplacement() {
    try (Game game = new Game()) {
      TextureRegion a0 = new TextureRegion(), a1 = new TextureRegion();
      TextureRegion b0 = new TextureRegion(), b1 = new TextureRegion();
      GameObject objectA = game.createObject("a"), objectB = game.createObject("b");
      objectA.addComponent(new SpriteRenderer2D(a0));
      objectB.addComponent(new SpriteRenderer2D(b0));
      SpriteAnimationPlayer a = objectA.addComponent(new SpriteAnimationPlayer());
      SpriteAnimationPlayer b = objectB.addComponent(new SpriteAnimationPlayer());
      a.register(new SpriteClip("walk", List.of(a0, a1), .1f));
      b.register(new SpriteClip("walk", List.of(b0, b1), .1f));
      game.start();
      AnimationTransition shared = AnimationTransition.clip("walk").loop().speed(2);
      SpritePlayback first = a.play(shared), second = b.play(shared);
      game.step(.05f);
      assertEquals(.1, first.time(), .00001);
      assertEquals(.1, second.time(), .00001);
      first.seek(.1);
      assertEquals(.1, first.time(), .00001);
      assertEquals(.1, second.time(), .00001);
      assertThrows(IllegalArgumentException.class, () -> a.play(shared.fade(.1)));
      assertSame(first, a.current());
      assertEquals(.1, first.time(), .00001);
    }
  }

  @Test
  public void loopEndpointSeekMapsToStartAndBackwardSeekDoesNotFireCallbacks() {
    try (Game game = new Game()) {
      TextureRegion a = new TextureRegion(), b = new TextureRegion();
      GameObject object = game.createObject("sprite");
      SpriteRenderer2D sprite = object.addComponent(new SpriteRenderer2D(a));
      SpriteAnimationPlayer player = object.addComponent(new SpriteAnimationPlayer());
      player.register(new SpriteClip("walk", List.of(a, b), .1f));
      game.start();
      int[] hits = {0};
      SpritePlayback playback =
          player.play(
              AnimationTransition.clip("walk").loop().markers(new AnimationMarker("hit", .5)));
      playback.onMarker("hit", () -> hits[0]++);
      playback.seekNormalized(1);
      assertEquals(0, playback.time(), 0);
      assertSame(a, sprite.region);
      assertEquals(0, hits[0]);
      game.step(.1f);
      assertEquals(1, hits[0]);
      playback.seekNormalized(.25);
      game.step(.1f);
      assertEquals(2, hits[0]);
    }
  }

  @Test
  public void markerPauseAndRestartInvalidateRemainingLoopDispatch() {
    try (Game game = new Game()) {
      TextureRegion a = new TextureRegion();
      GameObject object = game.createObject("sprite");
      object.addComponent(new SpriteRenderer2D(a));
      SpriteAnimationPlayer player = object.addComponent(new SpriteAnimationPlayer());
      player.register(new SpriteClip("walk", List.of(a), .1f));
      game.start();
      List<String> events = new ArrayList<>();
      SpritePlayback pause =
          player.play(
              AnimationTransition.clip("walk")
                  .loop()
                  .markers(new AnimationMarker("first", .2), new AnimationMarker("second", .4)));
      pause.onMarker(
          "first",
          () -> {
            events.add("first");
            pause.pause();
          });
      pause.onMarker("second", () -> events.add("second"));
      pause.onLoop(() -> events.add("loop"));
      game.step(.35f);
      assertEquals(List.of("first"), events);
      assertTrue(pause.isPaused());

      events.clear();
      SpritePlayback[] restarting = new SpritePlayback[1];
      restarting[0] =
          player
              .play(AnimationTransition.clip("walk").loop())
              .onLoop(
                  () -> {
                    events.add("loop");
                    restarting[0].restart();
                  });
      game.step(.35f);
      assertEquals(List.of("loop"), events);
      assertEquals(0, restarting[0].time(), 0);
    }
  }

  @Test
  public void markerAddedDuringDispatchIsNotRunRetroactively() {
    try (Game game = new Game()) {
      TextureRegion a = new TextureRegion();
      GameObject object = game.createObject("sprite");
      object.addComponent(new SpriteRenderer2D(a));
      SpriteAnimationPlayer player = object.addComponent(new SpriteAnimationPlayer());
      player.register(new SpriteClip("walk", List.of(a), .1f));
      game.start();
      List<String> events = new ArrayList<>();
      SpritePlayback playback =
          player.play(
              AnimationTransition.clip("walk").loop().markers(new AnimationMarker("add", .2)));
      playback.onMarker("add", () -> playback.onMarker("late", .8, () -> events.add("late")));
      game.step(.1f);
      assertEquals(List.of(), events);
      game.step(.1f);
      assertEquals(List.of("late"), events);
    }
  }

  @Test
  public void excessiveMarkerStepIsRejectedBeforeStartPoseOrClockMutation() {
    try (Game game = new Game()) {
      TextureRegion first = new TextureRegion(), last = new TextureRegion();
      GameObject object = game.createObject("sprite");
      SpriteRenderer2D sprite = object.addComponent(new SpriteRenderer2D(first));
      SpriteAnimationPlayer player = object.addComponent(new SpriteAnimationPlayer());
      player.register(new SpriteClip("walk", List.of(first), .1f));
      game.start();
      List<AnimationMarker> markers = new ArrayList<>();
      for (int i = 1; i <= 1025; i++) markers.add(new AnimationMarker("m" + i, i / 1026.0));
      int[] starts = {0};
      SpritePlayback playback =
          player
              .play(
                  AnimationTransition.clip("walk").markers(markers.toArray(AnimationMarker[]::new)))
              .onStart(() -> starts[0]++);
      assertThrows(IllegalArgumentException.class, () -> game.step(.2f));
      assertEquals(0, starts[0]);
      assertEquals(0, playback.time(), 0);
      assertSame(first, sprite.region);
    }
  }

  @Test
  public void removedSpritePlayerCannotStartPlaybackOrWriteItsCachedRenderer() {
    try (Game game = new Game()) {
      TextureRegion first = new TextureRegion(),
          later = new TextureRegion(),
          sentinel = new TextureRegion();
      GameObject object = game.createObject("sprite");
      SpriteRenderer2D sprite = object.addComponent(new SpriteRenderer2D(first));
      SpriteAnimationPlayer player = object.addComponent(new SpriteAnimationPlayer());
      player.register(new SpriteClip("walk", List.of(first, later), .1f));
      game.start();
      SpritePlayback old = player.play("walk", PlayMode.LOOP);
      object.removeComponent(player);
      sprite.region = sentinel;
      assertThrows(IllegalStateException.class, () -> player.play("walk", PlayMode.ONCE));
      old.seek(.1);
      assertSame(sentinel, sprite.region);
    }
  }

  @Test
  public void removedModelAnimatorCannotStartPlaybackOrWriteItsCachedController() {
    Model model = animatedModel();
    try (Game game = new Game()) {
      GameObject object = game.createObject("model");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelAnimator animator = object.addComponent(new ModelAnimator());
      game.start();
      ModelPlayback old = animator.play("move", PlayMode.LOOP);
      object.removeComponent(animator);
      root(renderer).localTransform.idt().translate(77, 0, 0);
      assertThrows(IllegalStateException.class, () -> animator.play("move", PlayMode.ONCE));
      old.seek(.5);
      assertEquals(77, root(renderer).translation.x, .001f);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void finiteControllerOverflowIsRejectedBeforeStartOrMarkerCallbacks() {
    Model model = animatedModel();
    try (Game game = new Game()) {
      GameObject object = game.createObject("model");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelAnimator animator = object.addComponent(new ModelAnimator());
      game.start();
      int[] starts = {0}, markers = {0};
      ModelPlayback playback =
          animator.play(AnimationTransition.clip("move").markers(new AnimationMarker("zero", 0)));
      playback.onStart(() -> starts[0]++).onMarker("zero", () -> markers[0]++);
      playback.setSpeed(Double.MAX_VALUE);
      var originalDescriptor = animator.controller().current;
      float originalControllerTime = originalDescriptor.time;
      float originalPose = root(renderer).translation.x;

      assertThrows(IllegalArgumentException.class, () -> game.step(.1f));
      assertEquals(0, starts[0]);
      assertEquals(0, markers[0]);
      assertEquals(0, playback.time(), 0);
      assertSame(originalDescriptor, animator.controller().current);
      assertEquals(originalControllerTime, originalDescriptor.time, 0);
      assertEquals(originalPose, root(renderer).translation.x, .001f);

      playback.setSpeed(2);
      game.step(.1f);
      assertEquals(1, starts[0]);
      assertEquals(1, markers[0]);
      assertEquals(.2, playback.time(), .00001);
      assertEquals(2, root(renderer).translation.x, .001f);
    } finally {
      model.dispose();
    }
  }
}
