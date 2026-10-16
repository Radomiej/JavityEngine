package org.javity.android;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class FixtureLifecycleTest {
  @Test
  public void teardownAttemptsEveryPhaseAndKeepsFirstFailurePrimary() {
    IllegalStateException cancelFailure = new IllegalStateException("cancel focus");
    IllegalArgumentException uiFailure = new IllegalArgumentException("close runtime");
    AtomicInteger renderCloses = new AtomicInteger();

    Throwable failure =
        FixtureLifecycle.attempt(
            null,
            () -> {
              throw cancelFailure;
            });
    failure =
        FixtureLifecycle.attempt(
            failure,
            () -> {
              throw uiFailure;
            });
    failure = FixtureLifecycle.attempt(failure, renderCloses::incrementAndGet);

    assertSame(cancelFailure, failure);
    assertArrayEquals(new Throwable[] {uiFailure}, failure.getSuppressed());
    assertEquals("render cleanup still runs after earlier failures", 1, renderCloses.get());
  }

  @Test
  public void startupRollbackPreservesConstructionFailureAndAttemptsAllOwnedCloses() {
    RuntimeException constructionFailure = new RuntimeException("QuickJS startup");
    AtomicInteger attempted = new AtomicInteger();
    Throwable failure =
        FixtureLifecycle.rollback(
            constructionFailure,
            () -> {
              attempted.incrementAndGet();
              throw new IllegalStateException("Stage close");
            });
    failure = FixtureLifecycle.rollback(failure, attempted::incrementAndGet);

    assertSame(constructionFailure, failure);
    assertEquals(2, attempted.get());
    assertEquals("construction remains primary", "QuickJS startup", failure.getMessage());
    assertEquals(1, failure.getSuppressed().length);
  }
}
