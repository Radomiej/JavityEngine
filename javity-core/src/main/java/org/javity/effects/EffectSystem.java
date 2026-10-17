package org.javity.effects;

import java.util.*;
import java.util.function.BooleanSupplier;
import org.javity.core.GameSystem;

/** Scene-local ownership registry for properties animated by effect players. */
public final class EffectSystem extends GameSystem {
  static final int CALLBACK_BUDGET_PER_FRAME = 1024;

  interface Playback {
    void superseded();
  }

  static final class Lease {
    final Object token = new Object();
    final Playback playback;

    Lease(Playback playback) {
      this.playback = playback;
    }
  }

  private final IdentityHashMap<Object, Map<String, Lease>> leases = new IdentityHashMap<>();
  private int callbackDepth;
  private long budgetFrame = Long.MIN_VALUE;
  private int callbacksThisFrame;

  long frame() {
    return getGame() == null ? 0 : getGame().getFrameIndex();
  }

  long eligibleFrameForNewPlay() {
    return frame() + (callbackDepth > 0 ? 1 : 0);
  }

  void enterCallback() {
    long currentFrame = frame();
    if (currentFrame != budgetFrame) {
      budgetFrame = currentFrame;
      callbacksThisFrame = 0;
    }
    if (callbacksThisFrame >= CALLBACK_BUDGET_PER_FRAME)
      throw new IllegalStateException(
          "Effect callback budget exceeded (" + CALLBACK_BUDGET_PER_FRAME + " per frame)");
    callbacksThisFrame++;
    callbackDepth++;
  }

  void enterCleanupCallback() {
    callbackDepth++;
  }

  void leaveCallback() {
    callbackDepth--;
  }

  /**
   * Dispatches scene-local user code with the same budget and next-play deferral as effect nodes.
   */
  public boolean dispatchCallback(BooleanSupplier ownerValid, Runnable callback) {
    Objects.requireNonNull(ownerValid, "ownerValid");
    Objects.requireNonNull(callback, "callback");
    if (getGame() == null || getGame().isDisposed() || !ownerValid.getAsBoolean()) return false;
    enterCallback();
    try {
      callback.run();
      return true;
    } finally {
      leaveCallback();
    }
  }

  Lease acquire(FloatTarget target, Playback playback) {
    if (target == null || !target.isValid())
      throw new IllegalStateException("Invalid effect target");
    Map<String, Lease> objectLeases =
        leases.computeIfAbsent(target.identity(), ignored -> new HashMap<>());
    Lease previous = objectLeases.get(target.propertyKey());
    if (previous != null) previous.playback.superseded();
    // Superseding cleanup can remove the now-empty identity entry. Reacquire its current map.
    objectLeases = leases.computeIfAbsent(target.identity(), ignored -> new HashMap<>());
    Lease next = new Lease(playback);
    objectLeases.put(target.propertyKey(), next);
    return next;
  }

  boolean isCurrent(FloatTarget target, Lease lease) {
    Map<String, Lease> objectLeases = leases.get(target.identity());
    return objectLeases != null && objectLeases.get(target.propertyKey()) == lease;
  }

  void release(FloatTarget target, Lease lease) {
    Map<String, Lease> objectLeases = leases.get(target.identity());
    if (objectLeases != null && objectLeases.get(target.propertyKey()) == lease) {
      objectLeases.remove(target.propertyKey());
      if (objectLeases.isEmpty()) leases.remove(target.identity());
    }
  }

  int leaseCount() {
    return leases.values().stream().mapToInt(Map::size).sum();
  }

  @Override
  protected void onRelease() {
    leases.clear();
  }
}
