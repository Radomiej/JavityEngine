package org.javity.android;

/** CPU-only ownership for one active right-side look pointer. */
final class LookPointerClaim {
  private int pointer = -1;

  boolean claim(int candidate) {
    if (candidate < 0 || pointer >= 0) return false;
    pointer = candidate;
    return true;
  }

  boolean owns(int candidate) {
    return pointer == candidate;
  }

  boolean ownsPointer() {
    return pointer >= 0;
  }

  boolean release(int candidate) {
    if (!owns(candidate)) return false;
    pointer = -1;
    return true;
  }

  void clear() {
    pointer = -1;
  }
}
