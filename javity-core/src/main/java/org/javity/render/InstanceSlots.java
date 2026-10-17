package org.javity.render;

import com.badlogic.gdx.math.*;
import com.badlogic.gdx.math.collision.BoundingBox;
import java.util.*;
import java.util.function.LongPredicate;

/** Stable CPU owner slots; capacity bounds each draw, not total owners. GL-thread confined. */
final class InstanceSlots {
  record Chunk(long[] ownerIds, float[] transforms) {}

  private final int capacity;
  private final Map<Long, Integer> owners = new LinkedHashMap<>();
  private final ArrayList<Matrix4> matrices = new ArrayList<>();
  private final ArrayDeque<Integer> free = new ArrayDeque<>();
  private int dirtyStart = -1, dirtyEnd;

  InstanceSlots(int capacity) {
    if (capacity <= 0 || capacity > Integer.MAX_VALUE / 16)
      throw new IllegalArgumentException("capacity");
    this.capacity = capacity;
  }

  int capacity() {
    return capacity;
  }

  int size() {
    return owners.size();
  }

  int slot(long owner) {
    return owners.getOrDefault(owner, -1);
  }

  void put(long owner, Matrix4 transform) {
    Objects.requireNonNull(transform);
    Integer slot = owners.get(owner);
    if (slot == null) {
      slot = free.isEmpty() ? matrices.size() : free.removeFirst();
      if (slot == matrices.size()) matrices.add(null);
      owners.put(owner, slot);
    }
    Matrix4 old = matrices.get(slot);
    if (old == null || !Arrays.equals(old.val, transform.val)) {
      matrices.set(slot, new Matrix4(transform));
      dirty(slot);
    }
  }

  boolean remove(long owner) {
    Integer slot = owners.remove(owner);
    if (slot == null) return false;
    matrices.set(slot, null);
    free.addLast(slot);
    dirty(slot);
    return true;
  }

  void clear() {
    owners.clear();
    matrices.clear();
    free.clear();
    dirtyStart = -1;
    dirtyEnd = 0;
  }

  private void dirty(int slot) {
    dirtyStart = dirtyStart < 0 ? slot : Math.min(dirtyStart, slot);
    dirtyEnd = Math.max(dirtyEnd, slot + 1);
  }

  int dirtyStart() {
    return dirtyStart;
  }

  int dirtyEndExclusive() {
    return dirtyEnd;
  }

  void clearDirty() {
    dirtyStart = -1;
    dirtyEnd = 0;
  }

  boolean visible(long owner, BoundingBox localBounds, Frustum frustum) {
    Integer slot = owners.get(owner);
    return slot != null
        && frustum.boundsInFrustum(new BoundingBox(localBounds).mul(matrices.get(slot)));
  }

  List<Chunk> pack(LongPredicate visible) {
    List<Chunk> result = new ArrayList<>();
    long[] ids = new long[capacity];
    float[] data = new float[capacity * 16];
    int count = 0;
    for (var entry : owners.entrySet()) {
      if (!visible.test(entry.getKey())) continue;
      ids[count] = entry.getKey();
      System.arraycopy(matrices.get(entry.getValue()).val, 0, data, count * 16, 16);
      if (++count == capacity) {
        result.add(new Chunk(ids, data));
        ids = new long[capacity];
        data = new float[capacity * 16];
        count = 0;
      }
    }
    if (count > 0)
      result.add(new Chunk(Arrays.copyOf(ids, count), Arrays.copyOf(data, count * 16)));
    return List.copyOf(result);
  }
}
