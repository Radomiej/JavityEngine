package org.javity.services;

import java.util.*;
import org.javity.core.Component;

/** Scene-owned timers. Callbacks run in late UI phase; repeating tasks coalesce missed ticks. */
public final class SceneTimers extends Component {
  public final class Handle {
    private boolean cancelled;

    public void cancel() {
      cancelled = true;
    }

    public boolean isCancelled() {
      return cancelled;
    }
  }

  private final class Task {
    final Handle handle = new Handle();
    double remaining;
    final double interval;
    final boolean realtime;
    final Runnable callback;

    Task(double delay, double interval, boolean realtime, Runnable callback) {
      remaining = delay;
      this.interval = interval;
      this.realtime = realtime;
      this.callback = Objects.requireNonNull(callback);
    }
  }

  private final List<Task> tasks = new ArrayList<>();
  private boolean closed;

  public SceneTimers() {
    updateMode = UpdateMode.UI;
  }

  public Handle after(double delay, boolean realtime, Runnable callback) {
    return schedule(delay, 0, realtime, callback);
  }

  public Handle every(double interval, boolean realtime, Runnable callback) {
    if (interval <= 0) throw new IllegalArgumentException("Positive interval required");
    return schedule(interval, interval, realtime, callback);
  }

  private Handle schedule(double delay, double interval, boolean realtime, Runnable callback) {
    if (closed) throw new IllegalStateException("Timers closed");
    if (!Double.isFinite(delay) || delay < 0 || !Double.isFinite(interval))
      throw new IllegalArgumentException("Invalid delay");
    Task t = new Task(delay, interval, realtime, callback);
    tasks.add(t);
    return t.handle;
  }

  public int pendingCount() {
    return (int) tasks.stream().filter(t -> !t.handle.cancelled).count();
  }

  public void onLateUpdate(float delta) {
    try {
      for (Task t : List.copyOf(tasks)) {
        if (closed) break;
        if (t.handle.cancelled || (!t.realtime && getGame().isWorldFrozen())) continue;
        t.remaining -= t.realtime ? delta : getGame().deltaTime;
        if (t.remaining <= 1e-8) {
          if (t.interval == 0) t.handle.cancel();
          else t.remaining = t.interval;
          t.callback.run();
        }
      }
    } finally {
      tasks.removeIf(t -> t.handle.cancelled);
    }
  }

  protected void onRelease() {
    closed = true;
    for (Task t : tasks) t.handle.cancel();
    tasks.clear();
  }
}
