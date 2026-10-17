package org.javity.map;

import java.util.*;
import java.util.function.Supplier;

/** Render-thread LRU. Owns successfully loaded resources, including on eviction. */
final class ResourceCache<K, V extends AutoCloseable> implements AutoCloseable {
  private final int capacity;
  private final LinkedHashMap<K, V> entries = new LinkedHashMap<>(16, .75f, true);
  private boolean closed;

  ResourceCache(int capacity) {
    if (capacity < 1) throw new IllegalArgumentException("Positive cache capacity required");
    this.capacity = capacity;
  }

  V get(K key, Supplier<V> loader) {
    if (closed) throw new IllegalStateException("Cache closed");
    V resource = entries.get(key);
    if (resource != null) return resource;
    resource = loader.get();
    if (resource == null) return null;
    entries.put(key, resource);
    if (entries.size() > capacity) {
      var oldest = entries.entrySet().iterator();
      V evicted = oldest.next().getValue();
      oldest.remove();
      release(evicted);
    }
    return resource;
  }

  boolean contains(K key) {
    return entries.containsKey(key);
  }

  int size() {
    return entries.size();
  }

  private static void release(AutoCloseable resource) {
    try {
      resource.close();
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Resource cleanup failed", e);
    }
  }

  public void close() {
    if (closed) return;
    closed = true;
    Throwable failure = null;
    for (V resource : entries.values())
      try {
        release(resource);
      } catch (RuntimeException | Error e) {
        if (failure == null) failure = e;
        else failure.addSuppressed(e);
      }
    entries.clear();
    if (failure instanceof RuntimeException e) throw e;
    if (failure instanceof Error e) throw e;
  }
}
