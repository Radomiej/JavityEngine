package org.javity.animation;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.model.*;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import org.javity.core.*;
import org.javity.render.ModelRenderer;
import org.junit.Test;

public class PoseBatchingTest {
  record Owner(GameObject object, ModelRenderer renderer, ModelPoseAnimator animator) {}

  static Owner owner(Game game, Model model) {
    GameObject object = game.createObject("owner");
    return new Owner(
        object,
        object.addComponent(new ModelRenderer(model)),
        object.addComponent(new ModelPoseAnimator()));
  }

  static Model model() {
    Model model = new Model();
    Node root = new Node();
    root.id = "root";
    Node socket = new Node();
    socket.id = "socket";
    socket.translation.y = 2;
    root.addChild(socket);
    model.nodes.add(root);
    for (int i = 0; i < 3; i++) {
      Animation animation = new Animation();
      animation.id = "clip" + i;
      animation.duration = 1;
      if (i != 2) {
        NodeAnimation track = new NodeAnimation();
        track.node = i == 0 ? root : socket;
        track.translation = new Array<>();
        track.translation.add(new NodeKeyframe<>(0, new Vector3()));
        track.translation.add(new NodeKeyframe<>(1, new Vector3(10 + i * 10, 0, 0)));
        animation.nodeAnimations.add(track);
      }
      if (i == 2) {
        NodeAnimation track = new NodeAnimation();
        track.node = root;
        track.scaling = new Array<>();
        track.scaling.add(new NodeKeyframe<>(0, new Vector3(1, 1, 1)));
        animation.nodeAnimations.add(track);
      }
      model.animations.add(animation);
    }
    return model;
  }

  static Node root(Owner owner) {
    return owner.renderer.instance.nodes.first();
  }

  @Test
  public void equal256OwnersSampleOnceButDispatch256IndependentEventsEveryFrame() {
    Model model = model();
    try (Game game = new Game()) {
      PoseBatchSystem batch = game.addSystem(new PoseBatchSystem(32));
      Owner[] owners = new Owner[256];
      int[] markers = {0}, loops = {0}, completions = {0};
      for (int i = 0; i < owners.length; i++) owners[i] = owner(game, model);
      game.start();
      for (Owner owner : owners)
        owner
            .animator
            .play(AnimationTransition.clip("clip0").loop().markers(new AnimationMarker("hit", .05)))
            .onMarker("hit", () -> markers[0]++)
            .onLoop(() -> loops[0]++);
      game.step(.1f);
      assertEquals(256, markers[0]);
      assertEquals(1, batch.stats().frameSamplerExecutions());
      assertEquals(255, batch.stats().frameHits());
      for (Owner owner : owners) {
        assertEquals(1, root(owner).translation.x, .00001);
        assertNotSame(model.nodes.first(), root(owner));
        assertArrayEquals(
            root(owners[0]).getChild(0).globalTransform.val,
            root(owner).getChild(0).globalTransform.val,
            0);
      }
      assertNotSame(root(owners[0]).globalTransform, root(owners[1]).globalTransform);
      for (int i = 0; i < 10; i++) {
        game.step(.1f);
        assertEquals(1, batch.stats().frameSamplerExecutions());
        assertEquals(255, batch.stats().frameHits());
      }
      assertEquals(256, loops[0]);
      for (Owner owner : owners)
        owner.animator.play("clip0", PlayMode.ONCE).onComplete(() -> completions[0]++);
      for (int i = 0; i < 11; i++) {
        game.step(.1f);
        assertEquals(1, batch.stats().frameSamplerExecutions());
        assertEquals(255, batch.stats().frameHits());
      }
      assertEquals(256, completions[0]);
      long samples = batch.stats().totalSamplerExecutions();
      owners[0].renderer.prepareForRender();
      owners[0].renderer.prepareForRender();
      assertEquals(samples, batch.stats().totalSamplerExecutions());
      assertEquals(1, batch.sourceStats().snapshotsCreated());
    } finally {
      model.dispose();
    }
  }

  @Test
  public void sameFrameSeekLayerMixerAndLiveChannelsMatchUncachedOwner() {
    Model model = model();
    try (Game cached = new Game();
        Game plain = new Game()) {
      PoseBatchSystem batch = cached.addSystem(new PoseBatchSystem(16));
      Owner a = owner(cached, model), b = owner(cached, model), oracle = owner(plain, model);
      cached.start();
      plain.start();
      ModelPosePlayback p = a.animator.play("clip0", PlayMode.LOOP),
          q = b.animator.play("clip0", PlayMode.LOOP),
          r = oracle.animator.play("clip0", PlayMode.LOOP);
      p.seek(.25);
      q.seek(.25);
      r.seek(.25);
      equal(a, oracle);
      equal(b, oracle);
      long before = batch.stats().totalSamplerExecutions();
      p.seek(Math.nextUp(.25));
      assertEquals(before + 1, batch.stats().totalSamplerExecutions());
      p.seek(.5);
      r.seek(.5);
      equal(a, oracle);
      assertEquals(2.5, root(b).translation.x, 0);
      for (Owner o : new Owner[] {a, oracle}) {
        ModelAnimationLayer layer = o.animator.addLayer("socket", NodeMask.ids("socket"));
        layer.play("clip1", PlayMode.LOOP).seek(.5);
        layer.setWeight(.3);
      }
      equal(a, oracle);
      for (Owner o : new Owner[] {a, oracle}) {
        o.animator
            .play(
                LinearModelMixer.builder(1).add("clip0", 0).add("clip1", 1).build(),
                PlayMode.LOOP,
                .4)
            .seek(.2);
      }
      equal(a, oracle);
      // Mutate retained key values after caching. Replacing instance.animations must not
      // hot-reload.
      a.renderer.instance.animations.first().nodeAnimations.first().translation.peek().value.x = 37;
      a.animator.current().seek(.2);
      oracle.animator.current().seek(.2);
      equal(a, oracle);
      Animation replacement = new Animation();
      replacement.id = "clip0";
      replacement.duration = 1;
      a.renderer.instance.animations.set(0, replacement);
      a.animator.current().seek(.3);
      oracle.animator.current().seek(.3);
      equal(a, oracle);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void pausedAndFrozenFramesResetEvenWithImmediateUiSeekBeforeWorld() {
    Model model = model();
    try (Game game = new Game()) {
      org.javity.services.TimeManager time = game.addSystem(new org.javity.services.TimeManager());
      Owner a = owner(game, model), b = owner(game, model);
      ModelPosePlayback[] playback = new ModelPosePlayback[2];
      // System UI callbacks occur before component/world updates and remain active during pause.
      game.addSystem(
          new GameSystem() {
            @Override
            public void onUiUpdate(float delta) {
              if (playback[0] != null) {
                playback[0].seek(.25);
                playback[1].seek(.25);
              }
            }
          });
      PoseBatchSystem batch = game.addSystem(new PoseBatchSystem(8));
      game.start();
      playback[0] = a.animator.play("clip0", PlayMode.LOOP);
      playback[1] = b.animator.play("clip0", PlayMode.LOOP);
      game.pause();
      for (int i = 0; i < 2; i++) {
        game.step(.1f);
        assertEquals(1, batch.stats().frameSamplerExecutions());
        assertEquals(1, batch.stats().frameHits());
        equal(a, b);
      }
      game.resume();
      time.freeze(.5);
      game.step(.1f);
      assertEquals(1, batch.stats().frameSamplerExecutions());
      assertEquals(1, batch.stats().frameHits());
      playback[0].pause();
      playback[1].setSpeed(2);
      assertEquals(.25, playback[0].time(), 0);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void fractionalFrozenLayerCoverageDistinguishesOtherwiseEqualFadeInputs() {
    Model model = model();
    try (Game game = new Game();
        Game plain = new Game()) {
      PoseBatchSystem batch = game.addSystem(new PoseBatchSystem(32));
      Owner a = owner(game, model),
          b = owner(game, model),
          ar = owner(plain, model),
          br = owner(plain, model);
      game.start();
      plain.start();
      ModelAnimationLayer[] layers = new ModelAnimationLayer[4];
      Owner[] owners = {a, b, ar, br};
      for (int i = 0; i < 4; i++) {
        layers[i] = owners[i].animator.addLayer("sparse", NodeMask.ids("socket")).setWeight(1);
        layers[i].play("clip1", PlayMode.LOOP).setSpeed(0);
        layers[i]
            .play(AnimationTransition.clip("clip2").loop().fade(i % 2 == 0 ? .4 : 1))
            .setSpeed(0);
      }
      game.step(.1f);
      plain.step(.1f);
      for (ModelAnimationLayer layer : layers)
        layer.play(AnimationTransition.clip("clip2").loop().fade(1)).setSpeed(0);
      equal(a, ar);
      equal(b, br);
      assertNotEquals(root(a).getChild(0).translation.y, root(b).getChild(0).translation.y, .001);
      assertEquals(.5, root(a).getChild(0).translation.y, .0001);
      assertEquals(.2, root(b).getChild(0).translation.y, .0001);
      game.step(.1f);
      plain.step(.1f);
      equal(a, ar);
      equal(b, br);
      assertEquals(2, batch.stats().frameSamplerExecutions());
    } finally {
      model.dispose();
    }
  }

  @Test
  public void callbacksSeekAndReplaceSynchronouslyWithUnchangedOwnerEventOrder() {
    Model model = model();
    try (Game game = new Game();
        Game plain = new Game()) {
      game.addSystem(new PoseBatchSystem(16));
      Owner a = owner(game, model), b = owner(plain, model);
      game.start();
      plain.start();
      java.util.List<String> cachedEvents = new java.util.ArrayList<>(),
          plainEvents = new java.util.ArrayList<>();
      for (int i = 0; i < 2; i++) {
        Owner o = i == 0 ? a : b;
        java.util.List<String> events = i == 0 ? cachedEvents : plainEvents;
        ModelPosePlayback p =
            o.animator.play(
                AnimationTransition.clip("clip0")
                    .loop()
                    .markers(
                        new AnimationMarker("replace", .025), new AnimationMarker("late", .05)));
        p.onStart(() -> events.add("start"));
        p.onMarker(
            "replace",
            () -> {
              events.add("replace");
              o.animator.play(AnimationTransition.clip("clip1").loop().fade(.3)).seek(.4);
              events.add("pose:" + root(o).translation.x);
            });
        p.onMarker("late", () -> events.add("stale"));
      }
      game.step(.1f);
      plain.step(.1f);
      equal(a, b);
      assertEquals(plainEvents, cachedEvents);
      assertEquals(3, cachedEvents.size());
      a.animator.play(AnimationTransition.clip("clip0").loop().fade(.5));
      b.animator.play(AnimationTransition.clip("clip0").loop().fade(.5));
      game.step(.1f);
      plain.step(.1f);
      equal(a, b);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void actualInstanceBindAndTracksDifferAndRendererRebindDropsOldData() {
    Model model = model();
    try (Game game = new Game()) {
      PoseBatchSystem batch = game.addSystem(new PoseBatchSystem(16));
      Owner a = owner(game, model), b = owner(game, model);
      root(b).getChild(0).translation.y = 7;
      NodeAnimation unique = new NodeAnimation();
      unique.node = root(b);
      unique.translation = new Array<>();
      unique.translation.add(new NodeKeyframe<>(0, new Vector3()));
      unique.translation.add(new NodeKeyframe<>(1, new Vector3(30, 0, 0)));
      b.renderer.instance.animations.first().nodeAnimations.set(0, unique);
      game.start();
      a.animator.play("clip0", PlayMode.LOOP).seek(.5);
      ModelPosePlayback stale = b.animator.play("clip0", PlayMode.LOOP).seek(.5);
      assertEquals(5, root(a).translation.x, 0);
      assertEquals(15, root(b).translation.x, 0);
      assertEquals(2, root(a).getChild(0).translation.y, 0);
      assertEquals(7, root(b).getChild(0).translation.y, 0);
      b.object.removeComponent(b.renderer);
      assertFalse(stale.isActive());
      assertThrows(IllegalStateException.class, () -> stale.seek(.2));
      ModelRenderer replacement = b.object.addComponent(new ModelRenderer(model));
      game.step(0);
      b.animator.play("clip0", PlayMode.LOOP).seek(.5);
      assertArrayEquals(
          root(a).localTransform.val, replacement.instance.nodes.first().localTransform.val, 0);
      assertEquals(2, replacement.instance.nodes.first().getChild(0).translation.y, 0);
      assertTrue(batch.sourceStats().snapshotsCreated() >= 2);
    } finally {
      model.dispose();
    }
  }

  @Test
  public void sourceAndPoseCapacityAndSceneLifetimesAreBounded() {
    Model model = model();
    PoseBatchSystem closed;
    try {
      try (Game game = new Game()) {
        closed = game.addSystem(new PoseBatchSystem(1));
        Owner a = owner(game, model), b = owner(game, model);
        game.start();
        a.animator.play("clip0", PlayMode.LOOP).seek(.2);
        b.animator.play("clip1", PlayMode.LOOP).seek(.3);
        assertEquals(1, closed.stats().retainedEntries());
        assertEquals(1, closed.sourceStats().retainedSnapshots());
      }
      assertTrue(closed.isDisposed());
      assertEquals(0, closed.stats().retainedEntries());
      assertEquals(0, closed.sourceStats().retainedSnapshots());
      try (Game next = new Game()) {
        PoseBatchSystem batch = next.addSystem(new PoseBatchSystem(1));
        Owner owner = owner(next, model);
        next.start();
        owner.animator.play("clip0", PlayMode.LOOP).seek(.2);
        assertTrue(batch.stats().totalSamplerExecutions() > 0);
      }
      try (Game neverStarted = new Game()) {
        closed = neverStarted.addSystem(new PoseBatchSystem(1));
      }
      assertTrue(closed.isDisposed());
    } finally {
      model.dispose();
    }
  }

  @Test
  public void equivalentMixersShareButIndependentSpeedPauseAndSeekSplitImmediately() {
    Model model = model();
    try (Game game = new Game()) {
      PoseBatchSystem batch = game.addSystem(new PoseBatchSystem(16));
      Owner a = owner(game, model), b = owner(game, model);
      game.start();
      ModelPosePlayback p =
          a.animator.play(
              LinearModelMixer.builder(1).add("clip0", 0).add("clip1", 1).build(),
              PlayMode.LOOP,
              .3);
      ModelPosePlayback q =
          b.animator.play(
              LinearModelMixer.builder(1).add("clip0", 0).add("clip1", 1).build(),
              PlayMode.LOOP,
              .3);
      game.step(.1f);
      assertEquals(1, batch.stats().frameSamplerExecutions());
      assertEquals(1, batch.stats().frameHits());
      equal(a, b);
      p.pause();
      q.setSpeed(2);
      game.step(.1f);
      assertEquals(.1, p.time(), .00001);
      assertEquals(.3, q.time(), .00001);
      assertEquals(2, batch.stats().frameSamplerExecutions());
      p.seek(q.time());
      equal(a, b); // pause state cannot prevent sharing identical current pose inputs
      long executions = batch.stats().totalSamplerExecutions();
      q.setParameter(.3);
      assertEquals(executions, batch.stats().totalSamplerExecutions());
      batch.dispose();
      p.seek(.4);
      assertEquals(.4, p.time(), 0);
      assertEquals(0, batch.stats().retainedEntries());
    } finally {
      model.dispose();
    }
  }

  @Test
  public void previouslyResolvedTrackEditsRetainOriginalSamplerMutationSemantics() {
    Model model = model();
    try (Game game = new Game();
        Game plain = new Game()) {
      game.addSystem(new PoseBatchSystem(8));
      Owner a = owner(game, model), b = owner(plain, model);
      game.start();
      plain.start();
      ModelPosePlayback p = a.animator.play("clip0", PlayMode.LOOP),
          q = b.animator.play("clip0", PlayMode.LOOP);
      // Initial clip validation is lazy and one-time. Existing retained arrays see later time
      // edits,
      // even a time beyond the original duration; sharing must not introduce a second validation.
      a.renderer.instance.animations.first().nodeAnimations.first().translation.peek().keytime = 2;
      p.seek(.5);
      q.seek(.5);
      equal(a, b);
      assertEquals(2.5, root(a).translation.x, 0);
    } finally {
      model.dispose();
    }
  }

  static void equal(Owner a, Owner b) {
    assertArrayEquals(root(a).localTransform.val, root(b).localTransform.val, 0);
    assertArrayEquals(
        root(a).getChild(0).globalTransform.val, root(b).getChild(0).globalTransform.val, 0);
  }
}
