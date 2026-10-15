package org.javity.effects;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.javity.core.*;
import org.javity.render.ShapeRenderer2D;
import org.junit.Test;

public class EffectsTest {
  @Test
  public void clocksPauseAndZeroDurationWritesOnNextEligibleUpdate() {
    try (Game game = new Game()) {
      game.addSystem(new EffectSystem());
      GameObject object = game.createObject("clock");
      object.transform.scale.x = 2;
      TweenPlayer world = object.addComponent(new TweenPlayer(Clock.WORLD));
      TweenPlayer ui = object.addComponent(new TweenPlayer(Clock.UI));
      TweenHandle zero = world.to(Targets.scaleX(object), 4, 0, Ease.LINEAR);
      ui.to(Targets.scaleY(object), 3, 0, Ease.LINEAR);
      game.start();
      game.pause();
      assertEquals(2, object.transform.scale.x, 0);
      game.step(.05f);
      assertEquals(2, object.transform.scale.x, 0);
      assertEquals(3, object.transform.scale.y, 0);
      assertTrue(zero.isRunning());
      game.resume();
      game.step(.05f);
      assertEquals(4, object.transform.scale.x, 0);
      assertTrue(zero.isCompleted());
    }
  }

  @Test
  public void validatesArgumentsAndRejectsForeignTargets() {
    try (Game game = new Game()) {
      EffectSystem effects = game.addSystem(new EffectSystem());
      GameObject owner = game.createObject("owner"), other = game.createObject("other");
      TweenPlayer player = owner.addComponent(new TweenPlayer(Clock.WORLD));
      owner.addComponent(new TweenPlayer(Clock.UI));
      FeedbackPlayer feedback = owner.addComponent(new FeedbackPlayer(Clock.WORLD));
      game.start();
      assertThrows(
          IllegalArgumentException.class,
          () -> player.to(Targets.positionX(owner), Float.NaN, 1, Ease.LINEAR));
      assertThrows(
          IllegalArgumentException.class,
          () -> player.to(Targets.positionX(owner), 1, Double.POSITIVE_INFINITY, Ease.LINEAR));
      assertThrows(
          IllegalArgumentException.class,
          () -> player.to(Targets.positionX(owner), 1, -1, Ease.LINEAR));
      assertThrows(
          IllegalArgumentException.class,
          () -> player.to(Targets.positionX(other), 1, 1, Ease.LINEAR));
      assertThrows(
          IllegalArgumentException.class,
          () -> feedback.play(Feedback.tween(Targets.positionX(other), 1, 1, Ease.LINEAR)));
      assertEquals(0, effects.leaseCount());
    }
  }

  @Test
  public void restoreCancelOnlyRestoresCurrentLeaseAndRemovedComponentInvalidatesTarget() {
    try (Game game = new Game()) {
      EffectSystem effects = game.addSystem(new EffectSystem());
      GameObject object = game.createObject("restore");
      object.transform.position.x = 7;
      TweenPlayer player = object.addComponent(new TweenPlayer(Clock.WORLD));
      ShapeRenderer2D shape = object.addComponent(ShapeRenderer2D.rectangle(2, 2));
      FloatTarget alpha = Targets.shapeAlpha(shape);
      assertTrue(alpha.isValid());
      game.start();
      TweenHandle handle = player.to(Targets.positionX(object), 12, 1, Ease.LINEAR);
      game.step(.1f);
      assertEquals(7.5f, object.transform.position.x, .001f);
      handle.cancel(CancelMode.RESTORE_START);
      assertEquals(7, object.transform.position.x, 0);
      assertEquals(0, effects.leaseCount());
      object.removeComponent(shape);
      assertFalse(alpha.isValid());
      assertThrows(IllegalArgumentException.class, () -> Targets.shapeAlpha(shape));
    }
  }

  @Test
  public void newerCrossPlayerWriterCancelsOldAndOldRestoreCannotClobberIt() {
    try (Game game = new Game()) {
      EffectSystem effects = game.addSystem(new EffectSystem());
      GameObject object = game.createObject("lease");
      TweenPlayer first = object.addComponent(new TweenPlayer(Clock.WORLD));
      FeedbackPlayer second = object.addComponent(new FeedbackPlayer(Clock.WORLD));
      game.start();
      TweenHandle old = first.to(Targets.positionX(object), 10, 1, Ease.LINEAR);
      game.step(.1f);
      assertEquals(1, object.transform.position.x, .001f);
      PlaybackHandle replacement =
          second.play(Feedback.tween(Targets.positionX(object), 20, 1, Ease.LINEAR));
      game.step(.1f);
      assertTrue(old.isCancelled());
      assertTrue(replacement.isRunning());
      float replacementValue = object.transform.position.x;
      old.cancel(CancelMode.RESTORE_START);
      assertEquals(replacementValue, object.transform.position.x, 0);
      object.destroy();
      game.step(.01f);
      assertEquals(0, effects.leaseCount());
    }
  }

  @Test
  public void sequenceCarriesRemainderAndParallelRejectsConflictingNestedProperties() {
    try (Game game = new Game()) {
      game.addSystem(new EffectSystem());
      GameObject object = game.createObject("timeline");
      FeedbackPlayer player = object.addComponent(new FeedbackPlayer(Clock.WORLD));
      game.start();
      Feedback sequence =
          Feedback.sequence(
              Feedback.waitFor(.04),
              Feedback.tween(Targets.positionX(object), 10, .04, Ease.LINEAR),
              Feedback.callback(() -> object.transform.position.y = 5));
      PlaybackHandle handle = player.play(sequence);
      game.step(.1f);
      assertTrue(handle.isCompleted());
      assertEquals(10, object.transform.position.x, 0);
      assertEquals(5, object.transform.position.y, 0);
      assertThrows(
          IllegalArgumentException.class,
          () ->
              Feedback.parallel(
                  Feedback.sequence(
                      Feedback.waitFor(1),
                      Feedback.tween(Targets.positionX(object), 1, 1, Ease.LINEAR)),
                  Feedback.parallel(Feedback.tween(Targets.positionX(object), 2, 1, Ease.LINEAR))));
    }
  }

  @Test
  public void callbacksCanCancelPeersAndNewPlayWaitsUntilNextUpdate() {
    try (Game game = new Game()) {
      game.addSystem(new EffectSystem());
      GameObject object = game.createObject("callbacks");
      FeedbackPlayer first = object.addComponent(new FeedbackPlayer(Clock.UI));
      FeedbackPlayer second = object.addComponent(new FeedbackPlayer(Clock.UI));
      AtomicInteger calls = new AtomicInteger();
      PlaybackHandle[] peer = new PlaybackHandle[1];
      PlaybackHandle[] deferred = new PlaybackHandle[1];
      game.start();
      peer[0] = second.play(Feedback.callback(calls::incrementAndGet));
      first.play(
          Feedback.callback(
              () -> {
                peer[0].cancel();
                deferred[0] = second.play(Feedback.callback(calls::incrementAndGet));
              }));
      game.step(.01f);
      assertTrue(peer[0].isCancelled());
      assertEquals(0, calls.get());
      assertTrue(deferred[0].isRunning());
      game.step(.01f);
      assertEquals(1, calls.get());
      assertTrue(deferred[0].isCompleted());
    }
  }

  @Test
  public void uiCallbackPlaybackDefersAcrossPausedZeroDeltaSteps() {
    try (Game game = new Game()) {
      game.addSystem(new EffectSystem());
      GameObject object = game.createObject("paused-ui");
      FeedbackPlayer first = object.addComponent(new FeedbackPlayer(Clock.UI));
      FeedbackPlayer second = object.addComponent(new FeedbackPlayer(Clock.UI));
      AtomicInteger calls = new AtomicInteger();
      PlaybackHandle[] deferred = new PlaybackHandle[1];
      game.start();
      game.pause();
      first.play(
          Feedback.callback(
              () -> deferred[0] = second.play(Feedback.callback(calls::incrementAndGet))));
      game.step(0);
      assertEquals(0, calls.get());
      assertTrue(deferred[0].isRunning());
      game.step(0);
      assertEquals(1, calls.get());
      assertTrue(deferred[0].isCompleted());
    }
  }

  @Test
  public void ownerDestroyAndCallbackFailureCancelAndReleasePlayback() {
    try (Game game = new Game()) {
      EffectSystem effects = game.addSystem(new EffectSystem());
      GameObject object = game.createObject("owner");
      FeedbackPlayer player = object.addComponent(new FeedbackPlayer(Clock.WORLD));
      game.start();
      PlaybackHandle destroys =
          player.play(
              Feedback.sequence(
                  Feedback.callback(object::destroy),
                  Feedback.tween(Targets.positionX(object), 2, 1, Ease.LINEAR),
                  Feedback.tween(Targets.positionX(object), 9, 1, Ease.LINEAR)));
      game.step(.1f);
      assertTrue(destroys.isCancelled());
      assertEquals(0, effects.leaseCount());

      GameObject failingObject = game.createObject("failure");
      FeedbackPlayer failing = failingObject.addComponent(new FeedbackPlayer(Clock.WORLD));
      game.step(.01f);
      PlaybackHandle failure =
          failing.play(
              Feedback.sequence(
                  Feedback.tween(Targets.positionX(failingObject), 5, .04, Ease.LINEAR),
                  Feedback.callback(
                      () -> {
                        throw new IllegalStateException("boom");
                      })));
      assertThrows(IllegalStateException.class, () -> game.step(.1f));
      assertTrue(failure.isCancelled());
      assertEquals(0, effects.leaseCount());
    }
  }

  @Test
  public void removingPlayerInsideFeedbackCallbackCancelsTheCurrentTimelinePass() {
    try (Game game = new Game()) {
      EffectSystem effects = game.addSystem(new EffectSystem());
      GameObject object = game.createObject("removed-player");
      FeedbackPlayer player = object.addComponent(new FeedbackPlayer(Clock.WORLD));
      game.start();
      PlaybackHandle playback =
          player.play(
              Feedback.sequence(
                  Feedback.callback(() -> object.removeComponent(player)),
                  Feedback.tween(Targets.positionX(object), 10, 0, Ease.LINEAR)));
      game.step(.01f);
      assertTrue(playback.isCancelled());
      assertEquals(0, object.transform.position.x, 0);
      assertEquals(0, effects.leaseCount());
    }
  }

  @Test
  public void registeringCompletedHandleCallbackDispatchesAndDefersAcrossPlayerOrder() {
    try (Game game = new Game()) {
      game.addSystem(new EffectSystem());
      GameObject object = game.createObject("completed-callback");
      TweenPlayer completing = object.addComponent(new TweenPlayer(Clock.WORLD));
      AtomicInteger calls = new AtomicInteger();
      TweenHandle[] completed = new TweenHandle[1];
      FeedbackPlayer[] later = new FeedbackPlayer[1];
      object.addComponent(
          new Component() {
            boolean registered;

            @Override
            public void onUpdate(float delta) {
              if (!registered && getGame().getFrameIndex() >= 2) {
                registered = true;
                completed[0].onComplete(
                    () -> later[0].play(Feedback.callback(calls::incrementAndGet)));
              }
            }
          });
      later[0] = object.addComponent(new FeedbackPlayer(Clock.WORLD));
      completed[0] = completing.to(Targets.positionX(object), 1, 0, Ease.LINEAR);
      game.start();
      game.step(.01f);
      assertTrue(completed[0].isCompleted());
      game.step(.01f);
      assertEquals(0, calls.get());
      game.step(.01f);
      assertEquals(1, calls.get());
    }
  }

  @Test
  public void throwingCompletionCallbackCancelsHandleAndSuppressesFollowingCallbacks() {
    try (Game game = new Game()) {
      EffectSystem effects = game.addSystem(new EffectSystem());
      GameObject object = game.createObject("throwing-completion");
      TweenPlayer player = object.addComponent(new TweenPlayer(Clock.WORLD));
      AtomicInteger following = new AtomicInteger();
      game.start();
      TweenHandle handle = player.to(Targets.positionX(object), 1, 0, Ease.LINEAR);
      handle.onComplete(
          () -> {
            throw new IllegalStateException("completion failure");
          });
      handle.onComplete(following::incrementAndGet);
      assertThrows(IllegalStateException.class, () -> game.step(.01f));
      assertTrue(handle.isCancelled());
      assertEquals(0, following.get());
      assertEquals(0, effects.leaseCount());
    }
  }

  @Test
  public void removingOwnerInFirstCompletionCallbackSuppressesOthersAndLaterRegistrationIsNoOp() {
    try (Game game = new Game()) {
      game.addSystem(new EffectSystem());
      GameObject object = game.createObject("removed-completion-owner");
      TweenPlayer player = object.addComponent(new TweenPlayer(Clock.WORLD));
      AtomicInteger calls = new AtomicInteger();
      game.start();
      TweenHandle handle = player.to(Targets.positionX(object), 1, 0, Ease.LINEAR);
      handle.onComplete(() -> object.removeComponent(player));
      handle.onComplete(calls::incrementAndGet);
      game.step(.01f);
      assertTrue(handle.isCompleted());
      assertEquals(0, calls.get());
      handle.onComplete(calls::incrementAndGet);
      assertEquals(0, calls.get());
    }
  }

  @Test
  public void customEffectsAreFreshLazyAndCloseOnceOnNormalCompletion() {
    try (Game game = new Game()) {
      game.addSystem(new EffectSystem());
      GameObject object = game.createObject("custom-lifecycle");
      FeedbackPlayer player = object.addComponent(new FeedbackPlayer(Clock.WORLD));
      AtomicInteger factories = new AtomicInteger();
      AtomicInteger closes = new AtomicInteger();
      StringBuilder lifecycle = new StringBuilder();
      Feedback delayed =
          Feedback.sequence(
              Feedback.waitFor(.2),
              Feedback.custom(
                  0,
                  () -> {
                    factories.incrementAndGet();
                    return new FeedbackEffect() {
                      @Override
                      public void start() {
                        lifecycle.append("start ");
                      }

                      @Override
                      public void update(double elapsed, double delta) {
                        lifecycle.append("update ");
                      }

                      @Override
                      public void complete() {
                        lifecycle.append("complete ");
                      }

                      @Override
                      public void close() {
                        closes.incrementAndGet();
                        lifecycle.append("close ");
                      }
                    };
                  }));
      game.start();
      PlaybackHandle first = player.play(delayed);
      PlaybackHandle second = player.play(delayed);
      assertEquals(0, factories.get());
      game.step(.1f);
      assertEquals(0, factories.get());
      game.step(.1f);
      assertEquals(2, factories.get());
      assertEquals(2, closes.get());
      assertTrue(first.isCompleted());
      assertTrue(second.isCompleted());
      assertEquals(
          "start update complete close start update complete close ", lifecycle.toString());
    }
  }

  @Test
  public void resourceFactoryIsPerPlayAndCloseFailureDoesNotSkipParallelPeers() {
    try (Game game = new Game()) {
      game.addSystem(new EffectSystem());
      GameObject object = game.createObject("resource-cleanup");
      FeedbackPlayer player = object.addComponent(new FeedbackPlayer(Clock.WORLD));
      AtomicInteger acquired = new AtomicInteger();
      AtomicInteger closed = new AtomicInteger();
      Feedback feedback =
          Feedback.parallel(
              Feedback.resource(
                  5,
                  () -> {
                    acquired.incrementAndGet();
                    return () -> {
                      closed.incrementAndGet();
                      throw new IllegalStateException("first close");
                    };
                  }),
              Feedback.resource(
                  5,
                  () -> {
                    acquired.incrementAndGet();
                    return () -> {
                      closed.incrementAndGet();
                      throw new IllegalArgumentException("second close");
                    };
                  }));
      game.start();
      PlaybackHandle handle = player.play(feedback);
      assertEquals(0, acquired.get());
      game.step(.01f);
      assertEquals(2, acquired.get());
      IllegalStateException failure = assertThrows(IllegalStateException.class, handle::cancel);
      assertEquals("first close", failure.getMessage());
      assertEquals(2, closed.get());
      assertTrue(handle.isCancelled());
      assertEquals(1, failure.getSuppressed().length);
      assertEquals("second close", failure.getSuppressed()[0].getMessage());
    }
  }

  @Test
  public void resourceCleanupFailureDoesNotPreventOwnerRemovalFromCleaningOtherPlaybacks() {
    try (Game game = new Game()) {
      game.addSystem(new EffectSystem());
      GameObject object = game.createObject("owner-resource-cleanup");
      FeedbackPlayer player = object.addComponent(new FeedbackPlayer(Clock.WORLD));
      AtomicInteger closed = new AtomicInteger();
      game.start();
      PlaybackHandle first =
          player.play(
              Feedback.resource(
                  5,
                  () ->
                      () -> {
                        closed.incrementAndGet();
                        throw new IllegalStateException("first close");
                      }));
      PlaybackHandle second =
          player.play(
              Feedback.resource(
                  5,
                  () -> {
                    closed.incrementAndGet();
                    return () -> {};
                  }));
      game.step(.01f);
      object.removeComponent(player);
      assertThrows(IllegalStateException.class, () -> game.step(.01f));
      assertEquals(2, closed.get());
      assertTrue(first.isCancelled());
      assertTrue(second.isCancelled());
    }
  }

  @Test
  public void failingCustomStartAndReentrantCleanupStillCloseTheOwnedEffectOnce() {
    try (Game game = new Game()) {
      game.addSystem(new EffectSystem());
      GameObject object = game.createObject("custom-start-error");
      FeedbackPlayer player = object.addComponent(new FeedbackPlayer(Clock.WORLD));
      AtomicInteger closes = new AtomicInteger();
      AtomicReference<PlaybackHandle> handle = new AtomicReference<>();
      Feedback feedback =
          Feedback.custom(
              1,
              () ->
                  new FeedbackEffect() {
                    @Override
                    public void start() {
                      throw new IllegalStateException("start failed");
                    }

                    @Override
                    public void close() {
                      closes.incrementAndGet();
                      handle.get().cancel();
                    }
                  });
      game.start();
      handle.set(player.play(feedback));
      assertThrows(IllegalStateException.class, () -> game.step(.01f));
      assertEquals(1, closes.get());
      assertTrue(handle.get().isCancelled());
    }
  }

  @Test
  public void customUpdateAndCompleteFailuresCloseOwnedEffectsExactlyOnce() {
    try (Game game = new Game()) {
      game.addSystem(new EffectSystem());
      GameObject object = game.createObject("custom-hook-errors");
      FeedbackPlayer player = object.addComponent(new FeedbackPlayer(Clock.WORLD));
      AtomicInteger closes = new AtomicInteger();
      game.start();
      PlaybackHandle updateFailure =
          player.play(
              Feedback.custom(
                  1,
                  () ->
                      new FeedbackEffect() {
                        @Override
                        public void update(double elapsed, double delta) {
                          throw new IllegalArgumentException("update failed");
                        }

                        @Override
                        public void close() {
                          closes.incrementAndGet();
                        }
                      }));
      assertThrows(IllegalArgumentException.class, () -> game.step(.01f));
      assertTrue(updateFailure.isCancelled());
      assertEquals(1, closes.get());

      PlaybackHandle completeFailure =
          player.play(
              Feedback.custom(
                  0,
                  () ->
                      new FeedbackEffect() {
                        @Override
                        public void complete() {
                          throw new IllegalArgumentException("complete failed");
                        }

                        @Override
                        public void close() {
                          closes.incrementAndGet();
                        }
                      }));
      assertThrows(IllegalArgumentException.class, () -> game.step(.01f));
      assertTrue(completeFailure.isCancelled());
      assertEquals(2, closes.get());
    }
  }

  @Test
  public void callbackBudgetOverflowAfterFactoryStillClosesAcquiredResource() {
    try (Game game = new Game()) {
      game.addSystem(new EffectSystem());
      GameObject object = game.createObject("factory-budget-cleanup");
      FeedbackPlayer player = object.addComponent(new FeedbackPlayer(Clock.WORLD));
      Feedback[] beforeFactory = new Feedback[1023];
      for (int i = 0; i < beforeFactory.length; i++) beforeFactory[i] = Feedback.callback(() -> {});
      AtomicInteger factories = new AtomicInteger();
      AtomicInteger closes = new AtomicInteger();
      Feedback overBudget =
          Feedback.sequence(
              Feedback.sequence(beforeFactory),
              Feedback.custom(
                  1,
                  () -> {
                    factories.incrementAndGet();
                    return new FeedbackEffect() {
                      @Override
                      public void close() {
                        closes.incrementAndGet();
                      }
                    };
                  }));
      game.start();
      PlaybackHandle handle = player.play(overBudget);
      assertThrows(IllegalStateException.class, () -> game.step(.01f));
      assertTrue(handle.isCancelled());
      assertEquals(1, factories.get());
      assertEquals(1, closes.get());
    }
  }

  @Test
  public void checkedResourceCloseFailureKeepsCauseAndFactoryFailureOwnsNothing() {
    try (Game game = new Game()) {
      game.addSystem(new EffectSystem());
      GameObject object = game.createObject("checked-resource-close");
      FeedbackPlayer player = object.addComponent(new FeedbackPlayer(Clock.WORLD));
      AtomicInteger closes = new AtomicInteger();
      AtomicInteger factories = new AtomicInteger();
      game.start();
      PlaybackHandle checkedClose =
          player.play(
              Feedback.resource(
                  0,
                  () ->
                      () -> {
                        closes.incrementAndGet();
                        throw new IOException("checked close");
                      }));
      IllegalStateException closeFailure =
          assertThrows(IllegalStateException.class, () -> game.step(.01f));
      assertTrue(checkedClose.isCancelled());
      assertEquals("Could not close feedback resource", closeFailure.getMessage());
      assertTrue(closeFailure.getCause() instanceof IOException);
      assertEquals(1, closes.get());

      PlaybackHandle factoryFailure =
          player.play(
              Feedback.custom(
                  0,
                  () -> {
                    factories.incrementAndGet();
                    throw new IllegalArgumentException("factory failed");
                  }));
      assertThrows(IllegalArgumentException.class, () -> game.step(.01f));
      assertTrue(factoryFailure.isCancelled());
      assertEquals(1, factories.get());
      assertEquals(1, closes.get());
    }
  }

  @Test
  public void factoryReentrantCancelOrGameCloseClosesTheJustReturnedResource() {
    try (Game game = new Game()) {
      game.addSystem(new EffectSystem());
      GameObject object = game.createObject("factory-reentrancy");
      FeedbackPlayer player = object.addComponent(new FeedbackPlayer(Clock.WORLD));
      AtomicReference<PlaybackHandle> current = new AtomicReference<>();
      AtomicInteger closes = new AtomicInteger();
      AtomicInteger starts = new AtomicInteger();
      AtomicInteger updates = new AtomicInteger();
      game.start();

      Feedback cancelingFactory =
          Feedback.resource(
              1,
              () -> {
                current.get().cancel();
                return closes::incrementAndGet;
              });
      current.set(player.play(cancelingFactory));
      game.step(.01f);
      assertTrue(current.get().isCancelled());
      assertEquals(1, closes.get());

      Feedback destroyingFactory =
          Feedback.custom(
              1,
              () -> {
                game.close();
                return new FeedbackEffect() {
                  @Override
                  public void start() {
                    starts.incrementAndGet();
                  }

                  @Override
                  public void update(double elapsed, double delta) {
                    updates.incrementAndGet();
                  }

                  @Override
                  public void close() {
                    closes.incrementAndGet();
                  }
                };
              });
      current.set(player.play(destroyingFactory));
      game.step(.01f);
      assertTrue(current.get().isCancelled());
      assertEquals(2, closes.get());
      assertEquals(0, starts.get());
      assertEquals(0, updates.get());
    }
  }

  @Test
  public void callbackBudgetIsSharedAcrossPlayersAndResetsOnNextFrame() {
    try (Game game = new Game()) {
      game.addSystem(new EffectSystem());
      GameObject object = game.createObject("callback-budget");
      FeedbackPlayer first = object.addComponent(new FeedbackPlayer(Clock.WORLD));
      FeedbackPlayer second = object.addComponent(new FeedbackPlayer(Clock.WORLD));
      Feedback[] callbacksA = new Feedback[600];
      Feedback[] callbacksB = new Feedback[600];
      for (int i = 0; i < callbacksA.length; i++) callbacksA[i] = Feedback.callback(() -> {});
      for (int i = 0; i < callbacksB.length; i++) callbacksB[i] = Feedback.callback(() -> {});
      game.start();
      PlaybackHandle a = first.play(Feedback.sequence(callbacksA));
      PlaybackHandle b = second.play(Feedback.sequence(callbacksB));
      IllegalStateException budget =
          assertThrows(IllegalStateException.class, () -> game.step(.01f));
      assertTrue(budget.getMessage().contains("callback budget"));
      assertTrue(a.isCompleted());
      assertTrue(b.isCancelled());
      AtomicInteger nextFrame = new AtomicInteger();
      PlaybackHandle next = first.play(Feedback.callback(nextFrame::incrementAndGet));
      game.step(.01f);
      assertTrue(next.isCompleted());
      assertEquals(1, nextFrame.get());
    }
  }
}
