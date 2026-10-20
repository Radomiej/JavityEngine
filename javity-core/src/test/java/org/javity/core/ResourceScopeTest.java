package org.javity.core;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class ResourceScopeTest {
  @Test
  public void closesInLifoOrderOnceAndContinuesAfterFailures() {
    ResourceScope scope = new ResourceScope();
    List<Integer> closed = new ArrayList<>();
    RuntimeException first = new RuntimeException("first");
    RuntimeException second = new RuntimeException("second");
    scope.own(
        () -> {
          closed.add(1);
          throw first;
        });
    scope.own(
        () -> {
          closed.add(2);
          throw second;
        });
    scope.own(() -> closed.add(3));

    RuntimeException thrown = assertThrows(RuntimeException.class, scope::close);
    assertSame(second, thrown);
    assertArrayEquals(new Throwable[] {first}, thrown.getSuppressed());
    assertEquals(List.of(3, 2, 1), closed);
    scope.close();
    assertEquals(List.of(3, 2, 1), closed);
    assertTrue(scope.isClosed());
    assertThrows(IllegalStateException.class, () -> scope.own(() -> {}));
  }

  @Test
  public void identicalFailuresAreNeverSuppressedOntoThemselves() {
    ResourceScope scope = new ResourceScope();
    RuntimeException shared = new RuntimeException("shared");
    scope.own(
        () -> {
          throw shared;
        });
    scope.own(
        () -> {
          throw shared;
        });
    assertSame(shared, assertThrows(RuntimeException.class, scope::close));
    assertEquals(0, shared.getSuppressed().length);
  }

  @Test
  public void componentAlwaysReleasesOwnedResourcesAfterCallbackFailures() {
    List<String> order = new ArrayList<>();
    RuntimeException destroyError = new RuntimeException("destroy");
    Component component =
        new Component() {
          {
            own(() -> order.add("backing"));
            own(
                () -> {
                  order.add("subscription");
                  throw new IllegalStateException("scope");
                });
          }

          @Override
          public void onDestroy() {
            order.add("destroy");
            throw destroyError;
          }

          @Override
          protected void onRelease() {
            order.add("release");
            throw new IllegalArgumentException("release");
          }
        };
    component.created = true;

    assertSame(destroyError, assertThrows(RuntimeException.class, component::disposeComponent));
    assertEquals(List.of("destroy", "release", "subscription", "backing"), order);
    assertEquals(2, destroyError.getSuppressed().length);
    component.disposeComponent();
    assertEquals(4, order.size());
  }
}
