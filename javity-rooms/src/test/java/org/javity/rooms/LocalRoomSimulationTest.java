package org.javity.rooms;

import static org.junit.Assert.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public class LocalRoomSimulationTest {
  @Test public void fixedMovementSequenceExpiryAndRoomLimit() {
    AtomicLong now = new AtomicLong();
    LocalRoomSimulation room = new LocalRoomSimulation("r", now::get);
    room.join("a");
    assertTrue(room.acceptInput("a", 0, 1, 1));
    room.step();
    LocalRoomSimulation.PlayerState p = room.snapshot().players().get(0);
    assertEquals(4d / Math.sqrt(2) / 60, p.x(), 1e-6);
    assertEquals(p.x(), p.z(), 1e-9);
    assertFalse(room.acceptInput("a", 0, -1, 0));
    now.set(250_000_001L);
    room.step();
    assertEquals(p.x(), room.snapshot().players().get(0).x(), 1e-8);
    for (int i = 1; i < 8; i++) room.join("p" + i);
    try { room.join("overflow"); fail("room must cap players"); }
    catch (IllegalStateException expected) { }
    room.close();
    assertTrue(room.game().isDisposed());
  }

  @Test public void clampsAtWorldBoundsAndRequiresOwnerThread() throws Exception {
    LocalRoomSimulation room = new LocalRoomSimulation("r", () -> 0L);
    room.join("a"); room.acceptInput("a", 1, 1, 0);
    for (int i = 0; i < 1000; i++) room.step();
    assertEquals(50, room.snapshot().players().get(0).x(), 0);
    AtomicReferenceFailure failure = new AtomicReferenceFailure();
    Thread t = new Thread(() -> { try { room.step(); } catch (Throwable e) { failure.value = e; } });
    t.start(); t.join();
    assertTrue(failure.value instanceof IllegalStateException);
    room.close();
  }
  private static final class AtomicReferenceFailure { volatile Throwable value; }
}
