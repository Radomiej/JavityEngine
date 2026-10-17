package org.javity.haptics;

import static org.junit.Assert.*;

import java.util.List;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.effects.Clock;
import org.javity.effects.EffectSystem;
import org.javity.effects.Feedback;
import org.javity.effects.FeedbackPlayer;
import org.javity.effects.PlaybackHandle;
import org.junit.Test;

public class HapticsTest {
  private static final HapticPulse PULSE = new HapticPulse(.7f, .2);

  @Test
  public void unsupportedBackendReportsNoCapabilitiesAndNeverPretendsToPlay() {
    UnsupportedHaptics haptics = new UnsupportedHaptics();
    assertEquals(HapticsCapabilities.unsupported(), haptics.capabilities());
    HapticHandle pulse = haptics.play(PULSE);
    HapticHandle pattern = haptics.play(new HapticPattern(List.of(PULSE)));
    assertEquals(HapticStatus.UNSUPPORTED, pulse.status());
    assertEquals(HapticStatus.UNSUPPORTED, pattern.status());
    assertFalse(pulse.isActive());
    pulse.cancel();
    pulse.close();
    pattern.close();
    assertFalse(pulse.isActive());
  }

  @Test
  public void validatesPulseAndPatternBoundsBeforeFallbackOrBackend() {
    UnsupportedHaptics unsupported = new UnsupportedHaptics();
    assertThrows(IllegalArgumentException.class, () -> new HapticPulse(Float.NaN, .1));
    assertThrows(IllegalArgumentException.class, () -> new HapticPulse(1.1f, .1));
    assertThrows(IllegalArgumentException.class, () -> new HapticPulse(.2f, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new HapticPulse(.2f, HapticsLimits.MAX_TOTAL_DURATION_SECONDS + .01));
    assertThrows(IllegalArgumentException.class, () -> new HapticPattern(List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new HapticPattern(List.of(new HapticPulse(1, 6), new HapticPulse(1, 5))));
    assertEquals(HapticStatus.UNSUPPORTED, unsupported.play(PULSE).status());
  }

  @Test
  public void exclusiveBackendRejectsStaleCancellationAndClosesCurrentOnce() {
    FakeBackend backend = new FakeBackend(new HapticsCapabilities(true, true, false, true));
    ExclusiveHaptics haptics = new ExclusiveHaptics(backend);
    HapticHandle old = haptics.play(PULSE);
    HapticHandle current = haptics.play(PULSE);
    assertTrue(old.status() == HapticStatus.STARTED);
    assertFalse(old.isActive());
    assertTrue(current.isActive());
    old.cancel();
    assertEquals(0, backend.cancelCount);
    assertTrue(current.isActive());
    current.close();
    current.cancel();
    assertEquals(1, backend.cancelCount);
    assertFalse(current.isActive());
    haptics.close();
    haptics.close();
    assertEquals(1, backend.cancelCount);
  }

  @Test
  public void closeDuringStartDefersCleanupUntilBackendReturns() {
    FakeBackend backend = new FakeBackend(new HapticsCapabilities(true, true, true, true));
    ExclusiveHaptics haptics = new ExclusiveHaptics(backend);
    backend.onPlay = haptics::close;

    HapticHandle handle = haptics.play(PULSE);

    assertEquals(HapticStatus.STARTED, handle.status());
    assertFalse(handle.isActive());
    assertFalse(backend.nativeRunning);
    assertEquals(1, backend.cancelCount);
    handle.close();
    haptics.close();
    assertEquals(1, backend.cancelCount);
    assertThrows(IllegalStateException.class, () -> haptics.play(PULSE));
  }

  @Test
  public void nestedPlayDuringBackendPlayIsRejectedWithoutReplacingTheRequest() {
    FakeBackend backend = new FakeBackend(new HapticsCapabilities(true, true, true, true));
    ExclusiveHaptics haptics = new ExclusiveHaptics(backend);
    boolean[] rejected = {false};
    backend.onPlay =
        () -> {
          try {
            haptics.play(PULSE);
          } catch (IllegalStateException expected) {
            rejected[0] = true;
          }
        };

    HapticHandle handle = haptics.play(PULSE);

    assertTrue(rejected[0]);
    assertEquals(1, backend.playCount);
    assertTrue(handle.isActive());
    assertTrue(backend.nativeRunning);
    handle.close();
    assertFalse(backend.nativeRunning);
    assertEquals(1, backend.cancelCount);
  }

  @Test
  public void nestedPlayDuringGlobalCancelIsRejectedBeforeCancelCompletes() {
    FakeBackend backend = new FakeBackend(new HapticsCapabilities(true, true, true, true));
    ExclusiveHaptics haptics = new ExclusiveHaptics(backend);
    HapticHandle handle = haptics.play(PULSE);
    boolean[] rejected = {false};
    backend.onCancel =
        () -> {
          try {
            haptics.play(PULSE);
          } catch (IllegalStateException expected) {
            rejected[0] = true;
          }
        };

    handle.cancel();

    assertTrue(rejected[0]);
    assertEquals(1, backend.playCount);
    assertEquals(1, backend.cancelCount);
    assertFalse(backend.nativeRunning);
    assertFalse(handle.isActive());
  }

  @Test
  public void failedStartsAttemptCleanupAndPreserveCleanupFailures() {
    FakeBackend falseStart = new FakeBackend(new HapticsCapabilities(true, true, true, true));
    falseStart.startResult = false;
    falseStart.nativeOnFailedStart = true;
    RuntimeException falseCleanup = new IllegalStateException("false-start cleanup");
    falseStart.cancelFailure = falseCleanup;
    ExclusiveHaptics falseHaptics = new ExclusiveHaptics(falseStart);

    assertSame(falseCleanup, assertThrows(RuntimeException.class, () -> falseHaptics.play(PULSE)));
    assertEquals(1, falseStart.cancelCount);
    assertFalse(falseStart.nativeRunning);
    falseHaptics.close();
    falseHaptics.close();
    assertEquals(1, falseStart.cancelCount);

    FakeBackend throwingStart = new FakeBackend(new HapticsCapabilities(true, true, true, true));
    RuntimeException startFailure = new IllegalArgumentException("backend start");
    RuntimeException throwingCleanup = new IllegalStateException("throwing cleanup");
    throwingStart.startFailure = startFailure;
    throwingStart.cancelFailure = throwingCleanup;
    ExclusiveHaptics throwingHaptics = new ExclusiveHaptics(throwingStart);

    assertSame(
        startFailure, assertThrows(RuntimeException.class, () -> throwingHaptics.play(PULSE)));
    assertArrayEquals(new Throwable[] {throwingCleanup}, startFailure.getSuppressed());
    assertEquals(1, throwingStart.cancelCount);
    assertFalse(throwingStart.nativeRunning);
    throwingHaptics.close();
    throwingHaptics.close();
    assertEquals(1, throwingStart.cancelCount);
  }

  @Test
  public void unsupportedPatternAndBackendStartFailureReturnInactiveResults() {
    FakeBackend noPatterns = new FakeBackend(new HapticsCapabilities(true, true, false, false));
    HapticHandle pattern = new ExclusiveHaptics(noPatterns).play(new HapticPattern(List.of(PULSE)));
    assertEquals(HapticStatus.UNSUPPORTED, pattern.status());
    assertEquals(0, noPatterns.playCount);

    FakeBackend failing = new FakeBackend(new HapticsCapabilities(true, true, true, true));
    failing.startResult = false;
    HapticHandle failed = new ExclusiveHaptics(failing).play(PULSE);
    assertEquals(HapticStatus.FAILED, failed.status());
    assertFalse(failed.isActive());
    assertEquals(1, failing.cancelCount);
  }

  @Test
  public void feedbackClosesOwnedPlaybackOnOwnerRemovalAndCallbackFailure() {
    FakeBackend ownerBackend = new FakeBackend(new HapticsCapabilities(true, true, false, false));
    ExclusiveHaptics ownerHaptics = new ExclusiveHaptics(ownerBackend);
    try (Game game = new Game()) {
      game.addSystem(new EffectSystem());
      GameObject owner = game.createObject("owner-removal");
      FeedbackPlayer player = owner.addComponent(new FeedbackPlayer(Clock.WORLD));
      game.start();
      PlaybackHandle playback = player.play(HapticFeedback.pulse(ownerHaptics, PULSE));
      game.step(.01f);
      assertEquals(1, ownerBackend.playCount);
      assertEquals(0, ownerBackend.cancelCount);
      owner.removeComponent(player);
      game.step(.01f);
      assertTrue(playback.isCancelled());
      assertEquals(1, ownerBackend.cancelCount);
      ownerHaptics.close();
      assertEquals(1, ownerBackend.cancelCount);
    }

    FakeBackend failureBackend = new FakeBackend(new HapticsCapabilities(true, true, false, false));
    ExclusiveHaptics failureHaptics = new ExclusiveHaptics(failureBackend);
    try (Game game = new Game()) {
      game.addSystem(new EffectSystem());
      GameObject owner = game.createObject("callback-failure");
      FeedbackPlayer player = owner.addComponent(new FeedbackPlayer(Clock.WORLD));
      game.start();
      PlaybackHandle playback =
          player.play(
              Feedback.parallel(
                  HapticFeedback.pulse(failureHaptics, new HapticPulse(.7f, 1)),
                  Feedback.callback(
                      () -> {
                        assertTrue(failureBackend.nativeRunning);
                        throw new IllegalStateException("expected callback failure");
                      })));
      assertThrows(IllegalStateException.class, () -> game.step(.01f));
      assertTrue(playback.isCancelled());
      assertEquals(1, failureBackend.playCount);
      assertEquals(1, failureBackend.cancelCount);
      failureHaptics.close();
      assertEquals(1, failureBackend.cancelCount);
    }
  }

  @Test
  public void feedbackNaturalCompletionAndExplicitCancellationCloseOnlyTheirHandle() {
    FakeBackend naturalBackend = new FakeBackend(new HapticsCapabilities(true, true, false, false));
    ExclusiveHaptics naturalHaptics = new ExclusiveHaptics(naturalBackend);
    try (Game game = new Game()) {
      game.addSystem(new EffectSystem());
      GameObject owner = game.createObject("natural-completion");
      FeedbackPlayer player = owner.addComponent(new FeedbackPlayer(Clock.WORLD));
      game.start();
      PlaybackHandle playback =
          player.play(HapticFeedback.pulse(naturalHaptics, new HapticPulse(.5f, .05)));
      game.step(.01f);
      assertTrue(naturalBackend.nativeRunning);
      game.step(.1f);
      assertTrue(playback.isCompleted());
      assertFalse(naturalBackend.nativeRunning);
      assertEquals(1, naturalBackend.cancelCount);
    }

    FakeBackend cancelBackend = new FakeBackend(new HapticsCapabilities(true, true, false, false));
    ExclusiveHaptics cancelHaptics = new ExclusiveHaptics(cancelBackend);
    try (Game game = new Game()) {
      game.addSystem(new EffectSystem());
      GameObject owner = game.createObject("explicit-cancel");
      FeedbackPlayer player = owner.addComponent(new FeedbackPlayer(Clock.WORLD));
      game.start();
      PlaybackHandle playback =
          player.play(HapticFeedback.pulse(cancelHaptics, new HapticPulse(.5f, .5)));
      game.step(.01f);
      assertTrue(cancelBackend.nativeRunning);
      playback.cancel();
      assertTrue(playback.isCancelled());
      assertFalse(cancelBackend.nativeRunning);
      assertEquals(1, cancelBackend.cancelCount);
    }
  }

  private static final class FakeBackend implements ExclusiveHaptics.Backend {
    private final HapticsCapabilities capabilities;
    private int playCount;
    private int cancelCount;
    private boolean startResult = true;
    private boolean nativeRunning;
    private boolean nativeOnFailedStart;
    private Runnable onPlay;
    private Runnable onCancel;
    private RuntimeException startFailure;
    private RuntimeException cancelFailure;

    private FakeBackend(HapticsCapabilities capabilities) {
      this.capabilities = capabilities;
    }

    @Override
    public HapticsCapabilities capabilities() {
      return capabilities;
    }

    @Override
    public boolean play(ExclusiveHaptics.HapticRequest request) {
      playCount++;
      Runnable callback = onPlay;
      onPlay = null;
      if (callback != null) callback.run();
      if (startFailure != null) {
        nativeRunning = true;
        throw startFailure;
      }
      nativeRunning = startResult || nativeOnFailedStart;
      return startResult;
    }

    @Override
    public void cancel() {
      cancelCount++;
      Runnable callback = onCancel;
      onCancel = null;
      if (callback != null) callback.run();
      nativeRunning = false;
      if (cancelFailure != null) throw cancelFailure;
    }
  }
}
