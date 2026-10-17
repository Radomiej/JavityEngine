package org.javity.signals;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.javity.core.Game;
import org.junit.Test;

public class EventBusTest {
  @Test
  public void deliversExactTypesInStableOrderAndDefersReentrantPublication() {
    List<String> calls = new ArrayList<>();
    EventBus bus = new EventBus();
    bus.subscribe(Number.class, n -> calls.add("number"));
    bus.subscribe(
        Integer.class,
        n -> {
          calls.add("first:" + n);
          if (n == 1) bus.publish(Integer.class, 2);
        });
    bus.subscribe(Integer.class, n -> calls.add("second:" + n));
    bus.publish(Integer.class, 1);
    assertEquals(List.of("first:1", "second:1", "first:2", "second:2"), calls);
  }

  @Test
  public void listenersAddedDuringDispatchStartWithNextPublicationAndRemovedPeersAreSkipped() {
    EventBus bus = new EventBus();
    List<String> calls = new ArrayList<>();
    Subscription[] peer = new Subscription[1];
    boolean[] added = {false};
    bus.subscribe(
        String.class,
        value -> {
          calls.add("first");
          peer[0].close();
          if (!added[0]) {
            added[0] = true;
            bus.subscribe(String.class, later -> calls.add("late"));
          }
        });
    peer[0] = bus.subscribe(String.class, value -> calls.add("peer"));
    bus.publish(String.class, "one");
    assertEquals(List.of("first"), calls);
    bus.publish(String.class, "two");
    assertEquals(List.of("first", "first", "late"), calls);
  }

  @Test
  public void rejectsNullAndMismatchedValuesAndRemainsUsableAfterListenerFailure() {
    EventBus bus = new EventBus();
    assertThrows(NullPointerException.class, () -> bus.subscribe(null, ignored -> {}));
    assertThrows(NullPointerException.class, () -> bus.subscribe(String.class, null));
    assertThrows(NullPointerException.class, () -> bus.publish(String.class, null));
    assertThrows(IllegalArgumentException.class, () -> publishRaw(bus, String.class, 3));
    Subscription failing =
        bus.subscribe(
            String.class,
            value -> {
              throw new IllegalStateException("listener");
            });
    assertEquals(
        "listener",
        assertThrows(IllegalStateException.class, () -> bus.publish(String.class, "x"))
            .getMessage());
    failing.close();
    List<String> received = new ArrayList<>();
    bus.subscribe(String.class, received::add);
    bus.publish(String.class, "usable");
    assertEquals(List.of("usable"), received);
  }

  @Test
  public void subscriptionsAndBusAreOwnedByTheirGame() {
    EventBus first = new EventBus();
    EventBus second = new EventBus();
    List<String> calls = new ArrayList<>();
    first.subscribe(String.class, value -> calls.add("first:" + value));
    second.subscribe(String.class, value -> calls.add("second:" + value));
    Game firstGame = new Game();
    Game secondGame = new Game();
    firstGame.addSystem(first);
    secondGame.addSystem(second);
    firstGame.start();
    secondGame.start();
    firstGame.close();
    assertTrue(first.isDisposed());
    assertThrows(IllegalStateException.class, () -> first.subscribe(String.class, ignored -> {}));
    second.publish(String.class, "alive");
    assertEquals(List.of("second:alive"), calls);
    secondGame.close();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void publishRaw(EventBus bus, Class type, Object value) {
    bus.publish(type, value);
  }

  @Test
  public void infiniteReentrantPublicationIsCappedAndBusRecovers() {
    EventBus bus = new EventBus();
    int[] calls = {0};
    Subscription cycling =
        bus.subscribe(
            Integer.class,
            value -> {
              calls[0]++;
              bus.publish(Integer.class, value + 1);
            });
    assertThrows(IllegalStateException.class, () -> bus.publish(Integer.class, 0));
    assertTrue(calls[0] <= 1025);
    cycling.close();
    List<Integer> later = new ArrayList<>();
    bus.subscribe(Integer.class, later::add);
    bus.publish(Integer.class, 7);
    assertEquals(List.of(7), later);
  }
}
