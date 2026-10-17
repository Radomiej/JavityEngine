package org.javity.map;

import static org.junit.Assert.*;

import org.junit.Test;

public class ResourceCacheTest {
  static class Resource implements AutoCloseable {
    int closes;

    public void close() {
      closes++;
    }
  }

  @Test
  public void accessOrderEvictsAndDisposeClosesExactlyOnce() {
    Resource a = new Resource(), b = new Resource(), c = new Resource();
    ResourceCache<String, Resource> cache = new ResourceCache<>(2);
    assertSame(a, cache.get("a", () -> a));
    cache.get("b", () -> b);
    cache.get("a", () -> failLoad());
    cache.get("c", () -> c);
    assertEquals(0, a.closes);
    assertEquals(1, b.closes);
    assertEquals(2, cache.size());
    cache.close();
    cache.close();
    assertEquals(1, a.closes);
    assertEquals(1, c.closes);
    assertThrows(IllegalStateException.class, () -> cache.get("x", Resource::new));
  }

  private Resource failLoad() {
    throw new AssertionError("Should reuse cached resource");
  }

  @Test
  public void missingAndFailedLoadsDoNotEvictWorkingResources() {
    var cache = new ResourceCache<String, Resource>(1);
    var a = new Resource();
    cache.get("a", () -> a);
    assertNull(cache.get("missing", () -> null));
    assertEquals(1, cache.size());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            cache.get(
                "failed",
                () -> {
                  throw new IllegalArgumentException();
                }));
    assertEquals(0, a.closes);
    cache.close();
    assertThrows(IllegalArgumentException.class, () -> new ResourceCache<String, Resource>(0));
  }

  @Test
  public void cleanupContinuesAfterFailure() {
    Resource
        broken =
            new Resource() {
              public void close() {
                super.close();
                throw new IllegalStateException("broken");
              }
            },
        ok = new Resource();
    var cache = new ResourceCache<String, Resource>(2);
    cache.get("broken", () -> broken);
    cache.get("ok", () -> ok);
    assertThrows(IllegalStateException.class, cache::close);
    assertEquals(1, broken.closes);
    assertEquals(1, ok.closes);
    assertEquals(0, cache.size());
    cache.close();
  }
}
