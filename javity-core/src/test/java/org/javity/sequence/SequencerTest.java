package org.javity.sequence;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.effects.Clock;
import org.javity.services.TimeManager;
import org.junit.Test;

public class SequencerTest {
  @Test
  public void playRejectsUnattachedRemovedAndClosedOwnersWithoutReplacingCurrent() {
    Sequence sequence = Sequence.builder(1).build();
    Sequencer unattached = new Sequencer(Clock.UI);
    assertThrows(IllegalStateException.class, () -> unattached.play(sequence, 60, false));
    assertNull(unattached.current());

    Game removedGame = new Game();
    GameObject removedObject = removedGame.createObject("removed");
    Sequencer removed = removedObject.addComponent(new Sequencer(Clock.UI));
    removedGame.start();
    SequencePlayback old = removed.play(sequence, 60, false);
    removedObject.removeComponent(removed);
    removedGame.step(0);
    assertThrows(IllegalStateException.class, () -> removed.play(sequence, 120, false));
    assertSame(old, removed.current());
    assertFalse(old.isActive());
    removedGame.close();

    Game prestartRemovedGame = new Game();
    GameObject prestartRemovedObject = prestartRemovedGame.createObject("removed-before-start");
    Sequencer prestartRemoved = prestartRemovedObject.addComponent(new Sequencer(Clock.UI));
    prestartRemovedObject.removeComponent(prestartRemoved);
    prestartRemovedGame.start();
    assertThrows(IllegalStateException.class, () -> prestartRemoved.play(sequence, 60, false));
    assertNull(prestartRemoved.current());
    prestartRemovedGame.close();

    Game closedGame = new Game();
    Sequencer closed = closedGame.createObject("closed").addComponent(new Sequencer(Clock.UI));
    closedGame.start();
    SequencePlayback closedOld = closed.play(sequence, 60, false);
    closedGame.close();
    assertThrows(IllegalStateException.class, () -> closed.play(sequence, 120, false));
    assertSame(closedOld, closed.current());
    assertFalse(closedOld.isActive());
  }

  @Test
  public void attachedPrestartSequencerCanQueueBeatZero() {
    try (Game game = new Game()) {
      Sequencer sequencer = game.createObject("prestart").addComponent(new Sequencer(Clock.UI));
      AtomicInteger calls = new AtomicInteger();
      SequencePlayback playback =
          sequencer.play(Sequence.builder(1).cue(0, calls::incrementAndGet).build(), 60, false);
      assertSame(playback, sequencer.current());
      assertTrue(playback.isActive());
      game.start();
      game.step(0);
      assertEquals(1, calls.get());
    }
  }

  @Test
  public void disabledOrInactiveAttachedSequencerMayQueueAndResumePlayback() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("deferred");
      Sequencer sequencer = object.addComponent(new Sequencer(Clock.UI));
      game.start();
      AtomicInteger calls = new AtomicInteger();
      sequencer.setEnabled(false);
      object.setActive(false);
      SequencePlayback playback =
          sequencer.play(Sequence.builder(1).cue(0, calls::incrementAndGet).build(), 60, false);
      assertTrue(playback.isActive());
      game.step(0);
      assertEquals(0, calls.get());
      sequencer.setEnabled(true);
      object.setActive(true);
      game.step(0);
      assertEquals(1, calls.get());
    }
  }

  @Test
  public void dataIsImmutableAndValidatesCueBoundsAndTempo() {
    List<Cue> cues = new ArrayList<>();
    cues.add(new Cue(0, () -> {}));
    Track track = new Track(cues);
    cues.clear();
    assertEquals(1, track.cues().size());
    assertThrows(UnsupportedOperationException.class, () -> track.cues().clear());
    assertThrows(IllegalArgumentException.class, () -> new Sequence(0, List.of()));
    assertThrows(IllegalArgumentException.class, () -> new Sequence(Double.NaN, List.of()));
    assertThrows(IllegalArgumentException.class, () -> new Cue(-.01, () -> {}));
    assertThrows(NullPointerException.class, () -> new Cue(0, null));
    assertThrows(NullPointerException.class, () -> new Track(java.util.Arrays.asList((Cue) null)));

    try (Game game = new Game()) {
      Sequencer sequencer = game.createObject("sequence").addComponent(new Sequencer(Clock.UI));
      game.start();
      assertThrows(
          IllegalArgumentException.class,
          () ->
              sequencer.play(
                  new Sequence(1, List.of(new Track(List.of(new Cue(1, () -> {}))))), 120, false));
      Sequence valid = Sequence.builder(1).cue(.5, () -> {}).build();
      assertThrows(IllegalArgumentException.class, () -> sequencer.play(valid, 0, false));
      assertThrows(
          IllegalArgumentException.class,
          () -> sequencer.play(valid, Double.POSITIVE_INFINITY, false));
    }
  }

  @Test
  public void zeroBeatRunsOnFirstEligibleZeroDeltaStepAndTieOrderIsStable() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("sequence");
      Sequencer sequencer = object.addComponent(new Sequencer(Clock.UI));
      game.start();
      List<String> calls = new ArrayList<>();
      Sequence sequence =
          new Sequence(
              1,
              List.of(
                  new Track(
                      List.of(
                          new Cue(.5, () -> calls.add("track0-late")),
                          new Cue(0, () -> calls.add("track0-zero")))),
                  new Track(
                      List.of(
                          new Cue(.5, () -> calls.add("track1-late")),
                          new Cue(0, () -> calls.add("track1-zero"))))));
      SequencePlayback playback = sequencer.play(sequence, 300, false);
      game.step(0);
      assertEquals(List.of("track0-zero", "track1-zero"), calls);
      game.step(.25f);
      assertEquals(List.of("track0-zero", "track1-zero", "track0-late", "track1-late"), calls);
      assertTrue(playback.isPlaying());
    }
  }

  @Test
  public void loopCarriesOvershootAndExactBoundaryDispatchesLoopThenNextZeroCue() {
    try (Game game = new Game()) {
      Sequencer sequencer = game.createObject("sequence").addComponent(new Sequencer(Clock.UI));
      game.start();
      List<String> calls = new ArrayList<>();
      SequencePlayback playback =
          sequencer
              .play(
                  new Sequence(
                      1,
                      List.of(
                          new Track(
                              List.of(
                                  new Cue(0, () -> calls.add("cue")),
                                  new Cue(.5, () -> calls.add("half")))))),
                  60,
                  true)
              .onLoop(() -> calls.add("loop"));
      game.step(0);
      game.step(.1f);
      game.step(.1f);
      game.step(.1f);
      game.step(.1f);
      game.step(.1f);
      game.step(.1f);
      game.step(.1f);
      game.step(.1f);
      game.step(.1f);
      assertEquals(List.of("cue", "half"), calls);
      game.step(.1f);
      assertEquals(List.of("cue", "half", "loop", "cue"), calls);
      assertEquals(0, playback.playheadBeats(), .000001);
      game.step(.1f);
      assertEquals(.1, playback.playheadBeats(), .000001);
    }
  }

  @Test
  public void oneUpdateCarriesOvershootAcrossMultipleLoops() {
    try (Game game = new Game()) {
      Sequencer sequencer = game.createObject("sequence").addComponent(new Sequencer(Clock.UI));
      game.start();
      AtomicInteger cues = new AtomicInteger();
      AtomicInteger loops = new AtomicInteger();
      SequencePlayback playback =
          sequencer
              .play(
                  new Sequence(.3, List.of(new Track(List.of(new Cue(0, cues::incrementAndGet))))),
                  600,
                  true)
              .onLoop(loops::incrementAndGet);
      game.step(0);
      game.step(.1f);
      assertEquals(4, cues.get());
      assertEquals(3, loops.get());
      assertEquals(.1, playback.playheadBeats(), .000001);
    }
  }

  @Test
  public void pauseResumeAndClockChoiceRespectWorldFreezeAndUiTime() {
    try (Game game = new Game()) {
      game.addSystem(new TimeManager());
      GameObject object = game.createObject("sequence");
      Sequencer world = object.addComponent(new Sequencer(Clock.WORLD));
      Sequencer ui = object.addComponent(new Sequencer(Clock.UI));
      game.start();
      Sequence sequence = new Sequence(2, List.of());
      SequencePlayback worldPlayback = world.play(sequence, 60, false);
      SequencePlayback uiPlayback = ui.play(sequence, 60, false);
      game.getSystem(TimeManager.class).setBaseScale(0);
      game.step(.1f);
      assertEquals(0, worldPlayback.playheadBeats(), 0);
      assertEquals(.1, uiPlayback.playheadBeats(), .000001);
      game.getSystem(TimeManager.class).setBaseScale(2);
      game.step(.1f);
      assertEquals(.2, worldPlayback.playheadBeats(), .000001);
      assertEquals(.2, uiPlayback.playheadBeats(), .000001);
      worldPlayback.pause();
      game.step(.1f);
      assertEquals(.2, worldPlayback.playheadBeats(), .000001);
      worldPlayback.resume();
      game.pause();
      game.step(.1f);
      assertEquals(.2, worldPlayback.playheadBeats(), .000001);
      assertEquals(.4, uiPlayback.playheadBeats(), .000001);
    }
  }

  @Test
  public void callbackReplacementDefersNewPlaybackAndStalesOldHandle() {
    try (Game game = new Game()) {
      Sequencer sequencer = game.createObject("sequence").addComponent(new Sequencer(Clock.UI));
      game.start();
      AtomicInteger newCue = new AtomicInteger();
      SequencePlayback[] next = new SequencePlayback[1];
      SequencePlayback old =
          sequencer.play(
              Sequence.builder(1)
                  .cue(
                      0,
                      () ->
                          next[0] =
                              sequencer.play(
                                  Sequence.builder(1).cue(0, newCue::incrementAndGet).build(),
                                  60,
                                  false))
                  .build(),
              60,
              false);
      game.step(0);
      assertFalse(old.isActive());
      assertEquals(0, newCue.get());
      game.step(0);
      assertEquals(1, newCue.get());
      assertSame(next[0], sequencer.current());
      old.resume();
      old.stop();
      assertSame(next[0], sequencer.current());
    }
  }

  @Test
  public void removedOwnerAndCallbackFailureCancelRemainingWork() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("sequence");
      Sequencer sequencer = object.addComponent(new Sequencer(Clock.UI));
      game.start();
      AtomicInteger calls = new AtomicInteger();
      SequencePlayback playback =
          sequencer.play(
              new Sequence(
                  1,
                  List.of(
                      new Track(
                          List.of(
                              new Cue(
                                  0,
                                  () -> {
                                    throw new IllegalStateException("cue");
                                  }),
                              new Cue(.1, calls::incrementAndGet))))),
              60,
              false);
      assertThrows(IllegalStateException.class, () -> game.step(.1f));
      assertFalse(playback.isActive());
      assertEquals(0, calls.get());

      SequencePlayback removed =
          sequencer.play(Sequence.builder(1).cue(0, calls::incrementAndGet).build(), 60, false);
      object.removeComponent(sequencer);
      game.step(0);
      assertFalse(removed.isActive());
      assertEquals(0, calls.get());

      Sequencer replacement = game.createObject("completion").addComponent(new Sequencer(Clock.UI));
      SequencePlayback completionFailure =
          replacement.play(new Sequence(0.05, List.of()), 60, false);
      completionFailure.onComplete(
          () -> {
            throw new IllegalStateException("complete");
          });
      assertThrows(IllegalStateException.class, () -> game.step(.1f));
      assertFalse(completionFailure.isActive());
      assertFalse(completionFailure.isCompleted());
    }
  }

  @Test
  public void preflightRejectsExcessiveCallbacksWithoutAdvancingOrDispatching() {
    try (Game game = new Game()) {
      Sequencer sequencer = game.createObject("sequence").addComponent(new Sequencer(Clock.UI));
      game.start();
      AtomicInteger calls = new AtomicInteger();
      List<Cue> cues = new ArrayList<>();
      for (int i = 0; i < 1025; i++) cues.add(new Cue(0, calls::incrementAndGet));
      SequencePlayback playback =
          sequencer.play(new Sequence(1, List.of(new Track(cues))), 60, false);
      assertThrows(IllegalArgumentException.class, () -> game.step(0));
      assertEquals(0, calls.get());
      assertEquals(0, playback.playheadBeats(), 0);
      assertTrue(playback.isPlaying());

      SequencePlayback cycles = sequencer.play(new Sequence(.00005, List.of()), 120_000, true);
      assertThrows(IllegalArgumentException.class, () -> game.step(.1f));
      assertEquals(0, cycles.playheadBeats(), 0);
      assertTrue(cycles.isPlaying());
    }
  }

  @Test
  public void callbackPauseStopsRemainingSameFrameCuesAndCompletionFiresOnce() {
    try (Game game = new Game()) {
      Sequencer sequencer = game.createObject("sequence").addComponent(new Sequencer(Clock.UI));
      game.start();
      List<String> calls = new ArrayList<>();
      Sequence sequence =
          new Sequence(
              1,
              List.of(
                  new Track(
                      List.of(
                          new Cue(
                              .1,
                              () -> {
                                calls.add("pause");
                                sequencer.current().pause();
                              }),
                          new Cue(.2, () -> calls.add("later"))))));
      SequencePlayback playback = sequencer.play(sequence, 600, false);
      game.step(.1f);
      assertEquals(List.of("pause"), calls);
      assertEquals(.1, playback.playheadBeats(), .000001);
      playback.resume();
      game.step(.1f);
      assertEquals(List.of("pause", "later"), calls);
      AtomicInteger completed = new AtomicInteger();
      SequencePlayback ending =
          sequencer
              .play(new Sequence(.05, List.of()), 60, false)
              .onComplete(completed::incrementAndGet);
      game.step(.1f);
      game.step(.1f);
      assertEquals(1, completed.get());
      assertTrue(ending.isCompleted());
    }
  }

  @Test
  public void stopInCueOrLoopCallbackSuppressesRemainingPlannedEvents() {
    try (Game game = new Game()) {
      Sequencer sequencer = game.createObject("cue-stop").addComponent(new Sequencer(Clock.UI));
      game.start();
      List<String> calls = new ArrayList<>();
      SequencePlayback stopped =
          sequencer.play(
              new Sequence(
                  1,
                  List.of(
                      new Track(
                          List.of(
                              new Cue(
                                  .1,
                                  () -> {
                                    calls.add("stop");
                                    sequencer.current().stop();
                                  }),
                              new Cue(.2, () -> calls.add("stale")))))),
              600,
              false);
      game.step(.1f);
      assertEquals(List.of("stop"), calls);
      assertFalse(stopped.isActive());
    }

    try (Game game = new Game()) {
      Sequencer sequencer = game.createObject("loop-stop").addComponent(new Sequencer(Clock.UI));
      game.start();
      List<String> calls = new ArrayList<>();
      SequencePlayback[] playback = new SequencePlayback[1];
      playback[0] =
          sequencer
              .play(
                  new Sequence(.1, List.of(new Track(List.of(new Cue(0, () -> calls.add("cue")))))),
                  600,
                  true)
              .onLoop(
                  () -> {
                    calls.add("loop");
                    playback[0].stop();
                  });
      game.step(.1f);
      assertEquals(List.of("cue", "loop"), calls);
      assertFalse(playback[0].isActive());
    }
  }

  @Test
  public void pauseAndResumeInsideCallbackInvalidatesOldPlan() {
    try (Game game = new Game()) {
      Sequencer sequencer = game.createObject("pause-resume").addComponent(new Sequencer(Clock.UI));
      game.start();
      List<String> calls = new ArrayList<>();
      SequencePlayback[] playback = new SequencePlayback[1];
      playback[0] =
          sequencer.play(
              new Sequence(
                  1,
                  List.of(
                      new Track(
                          List.of(
                              new Cue(
                                  .1,
                                  () -> {
                                    calls.add("first");
                                    playback[0].pause().resume();
                                  }),
                              new Cue(.2, () -> calls.add("stale")))))),
              600,
              false);
      game.step(.1f);
      assertEquals(List.of("first"), calls);
      assertTrue(playback[0].isPlaying());
      game.step(.1f);
      assertEquals(List.of("first", "stale"), calls);
    }
  }

  @Test
  public void disablingDeactivatingOrPausingWorldInsideCueDefersRemainingWork() {
    try (Game game = new Game()) {
      Sequencer sequencer = game.createObject("disabled").addComponent(new Sequencer(Clock.UI));
      game.start();
      List<String> calls = new ArrayList<>();
      SequencePlayback playback =
          sequencer.play(
              new Sequence(
                  1,
                  List.of(
                      new Track(
                          List.of(
                              new Cue(
                                  .1,
                                  () -> {
                                    calls.add("disable");
                                    sequencer.setEnabled(false);
                                  }),
                              new Cue(.2, () -> calls.add("later")))))),
              600,
              false);
      game.step(.1f);
      assertEquals(List.of("disable"), calls);
      assertTrue(playback.isActive());
      sequencer.setEnabled(true);
      game.step(.1f);
      assertEquals(List.of("disable", "later"), calls);
    }

    try (Game game = new Game()) {
      game.addSystem(new TimeManager());
      GameObject object = game.createObject("inactive");
      Sequencer sequencer = object.addComponent(new Sequencer(Clock.UI));
      game.start();
      List<String> calls = new ArrayList<>();
      SequencePlayback playback =
          sequencer.play(
              new Sequence(
                  1,
                  List.of(
                      new Track(
                          List.of(
                              new Cue(
                                  .1,
                                  () -> {
                                    calls.add("inactive");
                                    object.setActive(false);
                                  }),
                              new Cue(.2, () -> calls.add("later")))))),
              600,
              false);
      game.step(.1f);
      assertEquals(List.of("inactive"), calls);
      assertTrue(playback.isActive());
      object.setActive(true);
      game.step(.1f);
      assertEquals(List.of("inactive", "later"), calls);
    }

    try (Game game = new Game()) {
      Sequencer sequencer =
          game.createObject("world-pause").addComponent(new Sequencer(Clock.WORLD));
      game.start();
      List<String> calls = new ArrayList<>();
      SequencePlayback playback =
          sequencer.play(
              new Sequence(
                  1,
                  List.of(
                      new Track(
                          List.of(
                              new Cue(
                                  .1,
                                  () -> {
                                    calls.add("pause-world");
                                    game.pause();
                                  }),
                              new Cue(.2, () -> calls.add("later")))))),
              600,
              false);
      game.step(.1f);
      assertEquals(List.of("pause-world"), calls);
      assertTrue(playback.isActive());
      game.resume();
      game.step(.1f);
      assertEquals(List.of("pause-world", "later"), calls);
    }

    try (Game game = new Game()) {
      GameObject object = game.createObject("removed-during-cue");
      Sequencer sequencer = object.addComponent(new Sequencer(Clock.UI));
      game.start();
      List<String> calls = new ArrayList<>();
      SequencePlayback playback =
          sequencer.play(
              new Sequence(
                  1,
                  List.of(
                      new Track(
                          List.of(
                              new Cue(
                                  .1,
                                  () -> {
                                    calls.add("remove");
                                    object.removeComponent(sequencer);
                                  }),
                              new Cue(.2, () -> calls.add("stale")))))),
              600,
              false);
      game.step(.1f);
      assertEquals(List.of("remove"), calls);
      assertFalse(playback.isActive());
    }
  }

  @Test
  public void splitAndCombinedStepsDispatchSameCues() {
    List<Double> split = runPartitioned(.05f, 4);
    List<Double> combined = runPartitioned(.1f, 2);
    assertEquals(split.size(), combined.size());
    for (int i = 0; i < split.size(); i++) assertEquals(split.get(i), combined.get(i), .000001);
  }

  @Test
  public void tempoChangesPreserveBeatAndCallbackTempoChangeInvalidatesOldStepPlan() {
    try (Game game = new Game()) {
      Sequencer sequencer = game.createObject("sequence").addComponent(new Sequencer(Clock.UI));
      game.start();
      SequencePlayback playback = sequencer.play(new Sequence(4, List.of()), 60, false);
      game.step(.1f);
      assertEquals(.1, playback.playheadBeats(), .000001);
      assertThrows(IllegalArgumentException.class, () -> playback.setBpm(Double.NaN));
      assertEquals(60, playback.bpm(), 0);
      playback.setBpm(120);
      assertEquals(.1, playback.playheadBeats(), .000001);
      game.step(.1f);
      assertEquals(.3, playback.playheadBeats(), .000001);
    }

    try (Game game = new Game()) {
      Sequencer sequencer = game.createObject("sequence").addComponent(new Sequencer(Clock.UI));
      game.start();
      List<String> calls = new ArrayList<>();
      Sequence sequence =
          new Sequence(
              2,
              List.of(
                  new Track(
                      List.of(
                          new Cue(
                              .25,
                              () -> {
                                calls.add("tempo");
                                sequencer.current().setBpm(60);
                              }),
                          new Cue(.5, () -> calls.add("old-plan"))))));
      SequencePlayback changing = sequencer.play(sequence, 600, false);
      game.step(.1f);
      assertEquals(List.of("tempo"), calls);
      assertEquals(.25, changing.playheadBeats(), 0);
      game.step(.1f);
      assertEquals(List.of("tempo"), calls);
      game.step(.2f);
      game.step(.1f);
      assertEquals(List.of("tempo", "old-plan"), calls);
    }
  }

  private List<Double> runPartitioned(float step, int count) {
    try (Game game = new Game()) {
      Sequencer sequencer = game.createObject("sequence").addComponent(new Sequencer(Clock.UI));
      game.start();
      List<Double> seen = new ArrayList<>();
      Sequence sequence =
          new Sequence(
              1,
              List.of(
                  new Track(
                      List.of(new Cue(.1, () -> seen.add(.1)), new Cue(.3, () -> seen.add(.3))))));
      sequencer.play(sequence, 120, false);
      for (int i = 0; i < count; i++) game.step(step);
      return seen;
    }
  }
}
