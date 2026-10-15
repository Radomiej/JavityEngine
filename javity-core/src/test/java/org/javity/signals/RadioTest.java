package org.javity.signals;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.javity.core.Game;
import org.junit.Test;

public class RadioTest {
  @Test
  public void channelsAreTypedSuppressEqualValuesAndEmitCurrentOnRequest() {
    Radio radio = new Radio();
    Signal<Integer> score = radio.channel("score", Integer.class, 2);
    List<Integer> calls = new ArrayList<>();
    score.subscribe(calls::add, true);
    score.set(2);
    score.set(3);
    assertEquals(List.of(2, 3), calls);
    assertEquals(Integer.valueOf(3), score.get());
    assertSame(score, radio.channel("score", Integer.class, 9));
    assertThrows(IllegalArgumentException.class, () -> radio.channel("score", String.class, "x"));
    assertThrows(IllegalArgumentException.class, () -> radio.channel(" ", Integer.class, 0));
    assertThrows(NullPointerException.class, () -> radio.channel("null", String.class, null));
    assertThrows(
        IllegalArgumentException.class, () -> radio.channel("float", Float.class, Float.NaN));
    assertThrows(
        IllegalArgumentException.class,
        () -> radio.channel("double", Double.class, Double.POSITIVE_INFINITY));
  }

  @Test
  public void failedInitialEmissionUnregistersListenerAndLeavesOtherListenersUsable() {
    Radio radio = new Radio();
    Signal<String> signal = radio.channel("state", String.class, "initial");
    int[] failingCalls = {0};
    assertEquals(
        "initial",
        assertThrows(
                IllegalStateException.class,
                () ->
                    signal.subscribe(
                        value -> {
                          failingCalls[0]++;
                          throw new IllegalStateException(value);
                        },
                        true))
            .getMessage());

    List<String> otherCalls = new ArrayList<>();
    signal.subscribe(otherCalls::add, false);
    signal.set("next");

    assertEquals(1, failingCalls[0]);
    assertEquals(List.of("next"), otherCalls);
  }

  @Test
  public void crossChannelReentryIsQueuedAndCyclesAreBounded() {
    Radio radio = new Radio();
    Signal<Integer> a = radio.channel("a", Integer.class, 0);
    Signal<Integer> b = radio.channel("b", Integer.class, 0);
    List<String> calls = new ArrayList<>();
    a.subscribe(
        value -> {
          calls.add("a" + value);
          b.set(value + 1);
        },
        false);
    b.subscribe(
        value -> {
          calls.add("b" + value);
          a.set(value + 1);
        },
        false);
    assertThrows(IllegalStateException.class, () -> a.set(1));
    assertEquals(1024, calls.size());
    assertTrue(a.get() > 1);
    assertTrue(b.get() > 1);
  }

  @Test
  public void listenerMutationsAndCloseDuringDeliveryAreSafe() {
    Radio radio = new Radio();
    Signal<String> signal = radio.channel("state", String.class, "idle");
    List<String> calls = new ArrayList<>();
    Subscription[] peer = new Subscription[1];
    signal.subscribe(
        value -> {
          calls.add("first");
          peer[0].close();
        },
        false);
    peer[0] = signal.subscribe(value -> calls.add("peer"), false);
    signal.set("running");
    assertEquals(List.of("first"), calls);
    signal.close();
    assertTrue(signal.isClosed());
    assertThrows(IllegalStateException.class, signal::get);
    assertThrows(IllegalStateException.class, () -> signal.set("closed"));
    assertThrows(IllegalStateException.class, () -> signal.subscribe(ignored -> {}, false));
  }

  @Test
  public void scenesOwnIndependentRadiosAndDisposalClosesOnlyItsSignals() {
    Game firstGame = new Game();
    Game secondGame = new Game();
    Radio firstRadio = firstGame.addSystem(new Radio());
    Radio secondRadio = secondGame.addSystem(new Radio());
    Signal<String> firstSignal = firstRadio.channel("scene", String.class, "open");
    Signal<String> secondSignal = secondRadio.channel("scene", String.class, "open");
    List<String> calls = new ArrayList<>();
    firstSignal.subscribe(value -> calls.add("first:" + value), false);
    secondSignal.subscribe(value -> calls.add("second:" + value), false);
    firstGame.start();
    secondGame.start();
    firstGame.close();
    assertTrue(firstRadio.isDisposed());
    assertTrue(firstSignal.isClosed());
    assertThrows(IllegalStateException.class, firstSignal::get);
    secondSignal.set("alive");
    assertEquals(List.of("second:alive"), calls);
    secondGame.close();
  }
}
