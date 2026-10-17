package org.javity.state;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.javity.signals.Subscription;
import org.junit.Test;

public class StateMachineTest {
  private enum Mode {
    OFF,
    READY,
    RUNNING,
    CLOSED
  }

  private enum OtherMode {
    ONLY
  }

  @Test
  public void startsOnceAndTransitionsExitCommitEnterThenNotify() {
    StateMachine<Mode> machine = new StateMachine<>(Mode.OFF);
    List<String> calls = new ArrayList<>();
    machine.onEnter(Mode.OFF, () -> calls.add("enter off"));
    machine.onExit(Mode.OFF, () -> calls.add("exit off"));
    machine.onEnter(Mode.READY, () -> calls.add("enter ready"));
    machine.onChange(t -> calls.add(t.previous() + "->" + t.current()));
    assertFalse(machine.isStarted());
    machine.start();
    machine.start();
    machine.transitionTo(Mode.READY);
    machine.transitionTo(Mode.READY);
    assertEquals(List.of("enter off", "exit off", "enter ready", "OFF->READY"), calls);
    assertEquals(Mode.READY, machine.current());
  }

  @Test
  public void queuesReentrantTransitionsAndCloseStopsPeers() {
    StateMachine<Mode> machine = new StateMachine<>(Mode.OFF);
    List<String> calls = new ArrayList<>();
    machine.onEnter(Mode.READY, () -> machine.transitionTo(Mode.RUNNING));
    machine.onChange(t -> calls.add(t.previous() + "->" + t.current()));
    Subscription closer =
        machine.onChange(
            t -> {
              if (t.current() == Mode.READY) machine.close();
            });
    machine.start();
    machine.transitionTo(Mode.READY);
    assertEquals(List.of("OFF->READY"), calls);
    assertTrue(machine.isClosed());
    assertThrows(IllegalStateException.class, () -> machine.transitionTo(Mode.RUNNING));
    assertTrue(closer.isClosed());
  }

  @Test
  public void failureClearsQueuedTransitionsAndKeepsCommittedState() {
    StateMachine<Mode> machine = new StateMachine<>(Mode.OFF);
    boolean[] first = {true};
    machine.onEnter(
        Mode.READY,
        () -> {
          if (first[0]) {
            first[0] = false;
            machine.transitionTo(Mode.RUNNING);
            throw new IllegalStateException("enter");
          }
        });
    machine.start();
    assertEquals(
        "enter",
        assertThrows(IllegalStateException.class, () -> machine.transitionTo(Mode.READY))
            .getMessage());
    assertEquals(Mode.READY, machine.current());
    machine.transitionTo(Mode.CLOSED);
    assertEquals(Mode.CLOSED, machine.current());
  }

  @Test
  public void exitFailureLeavesOldStateAndMachineCanBeRetried() {
    StateMachine<Mode> machine = new StateMachine<>(Mode.OFF);
    boolean[] first = {true};
    machine.onExit(
        Mode.OFF,
        () -> {
          if (first[0]) {
            first[0] = false;
            throw new IllegalStateException("exit");
          }
        });
    machine.start();
    assertEquals(
        "exit",
        assertThrows(IllegalStateException.class, () -> machine.transitionTo(Mode.READY))
            .getMessage());
    assertEquals(Mode.OFF, machine.current());
    machine.transitionTo(Mode.READY);
    assertEquals(Mode.READY, machine.current());
  }

  @Test
  public void handlersCanBeConfiguredBetweenTransitionsAndWrongEnumIsRejected() {
    StateMachine<Mode> machine = new StateMachine<>(Mode.OFF);
    machine.start();
    List<String> calls = new ArrayList<>();
    assertSame(machine, machine.onEnter(Mode.READY, () -> calls.add("ready")));
    machine.transitionTo(Mode.READY);
    assertEquals(List.of("ready"), calls);
    assertThrows(IllegalArgumentException.class, () -> transitionRaw(machine, OtherMode.ONLY));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void transitionRaw(StateMachine machine, Enum state) {
    machine.transitionTo(state);
  }

  @Test
  public void reentrantTransitionCyclesAreCappedAndClosedMachineRejectsFurtherWork() {
    StateMachine<Mode> machine = new StateMachine<>(Mode.OFF);
    int[] calls = {0};
    machine.onEnter(
        Mode.READY,
        () -> {
          calls[0]++;
          machine.transitionTo(Mode.RUNNING);
        });
    machine.onEnter(
        Mode.RUNNING,
        () -> {
          calls[0]++;
          machine.transitionTo(Mode.READY);
        });
    machine.start();
    assertThrows(IllegalStateException.class, () -> machine.transitionTo(Mode.READY));
    assertTrue(calls[0] <= 1025);
    assertEquals(Mode.RUNNING, machine.current());
    machine.close();
    assertThrows(IllegalStateException.class, () -> machine.transitionTo(Mode.OFF));
  }

  @Test
  public void rejectsNullsAndRequiresStartBeforeTransitions() {
    assertThrows(NullPointerException.class, () -> new StateMachine<Mode>(null));
    StateMachine<Mode> machine = new StateMachine<>(Mode.OFF);
    assertThrows(IllegalStateException.class, () -> machine.transitionTo(Mode.READY));
    assertThrows(NullPointerException.class, () -> machine.transitionTo(null));
    machine.start();
    assertThrows(NullPointerException.class, () -> machine.onEnter(null, () -> {}));
    machine.close();
    assertTrue(machine.isClosed());
  }
}
