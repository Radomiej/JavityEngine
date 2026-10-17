package org.javity.wasm;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class SceneCleanupTest {
  @Test public void failingOwnerDoesNotPreventGpuRelease() {
    List<String> released = new ArrayList<>();
    var ownerFailure = new IllegalStateException("component release");
    var modelFailure = new AssertionError("model release");
    Throwable actual = assertThrows(IllegalStateException.class, () -> SceneCleanup.close(null,
        () -> { released.add("game"); throw ownerFailure; },
        () -> released.add("renderer"),
        () -> { released.add("model"); throw modelFailure; },
        () -> released.add("last model")));
    assertSame(ownerFailure, actual);
    assertArrayEquals(new Throwable[]{modelFailure}, actual.getSuppressed());
    assertEquals(List.of("game", "renderer", "model", "last model"), released);
  }
  @Test public void initializationErrorRemainsPrimaryAfterPartialCleanupFailures() {
    var initialization = new IllegalArgumentException("shader initialization");
    var cleanup = new IllegalStateException("renderer cleanup");
    boolean[] lastReleased = {false};
    Throwable actual = assertThrows(IllegalArgumentException.class, () -> SceneCleanup.close(initialization,
        () -> { throw cleanup; }, null, () -> lastReleased[0] = true));
    assertSame(initialization, actual);
    assertArrayEquals(new Throwable[]{cleanup}, actual.getSuppressed());
    assertTrue(lastReleased[0]);
  }
  @Test public void successSkipsUnallocatedResourcesAndDoesNotThrow() {
    boolean[] released = {false};
    SceneCleanup.close(null, null, () -> released[0] = true);
    assertTrue(released[0]);
  }
  @Test public void sameFailureInstanceDoesNotAbortLaterCleanup() {
    var failure = new IllegalStateException("same failure");
    boolean[] released = {false};
    assertSame(failure, assertThrows(IllegalStateException.class, () -> SceneCleanup.close(failure,
        () -> { throw failure; }, () -> released[0] = true)));
    assertTrue(released[0]);
  }
}
