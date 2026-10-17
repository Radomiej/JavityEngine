package org.javity.state;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.javity.signals.Subscription;

/** Caller-owned enum state machine with deterministic queued reentrant transitions. */
public final class StateMachine<S extends Enum<S>> implements AutoCloseable {
  private static final int MAX_DISPATCH_TRANSITIONS = 1024;

  private final Class<S> stateType;
  private final Map<S, Runnable> enter;
  private final Map<S, Runnable> exit;
  private final List<ChangeListener<S>> listeners = new ArrayList<>();
  private final ArrayDeque<S> pending = new ArrayDeque<>();
  private S current;
  private boolean started;
  private boolean closed;
  private boolean dispatching;

  public StateMachine(S initial) {
    current = Objects.requireNonNull(initial, "initial");
    @SuppressWarnings("unchecked")
    Class<S> enumType = (Class<S>) initial.getDeclaringClass();
    stateType = enumType;
    enter = new EnumMap<>(stateType);
    exit = new EnumMap<>(stateType);
  }

  public S current() {
    return current;
  }

  public boolean isStarted() {
    return started;
  }

  public boolean isClosed() {
    return closed;
  }

  public StateMachine<S> onEnter(S state, Runnable callback) {
    configure(enter, state, callback);
    return this;
  }

  public StateMachine<S> onExit(S state, Runnable callback) {
    configure(exit, state, callback);
    return this;
  }

  public Subscription onChange(Consumer<Transition<S>> callback) {
    ensureOpen();
    ChangeListener<S> listener =
        new ChangeListener<>(this, Objects.requireNonNull(callback, "callback"));
    listeners.add(listener);
    return listener;
  }

  public void start() {
    ensureOpen();
    if (started) return;
    started = true;
    dispatching = true;
    try {
      Runnable callback = enter.get(current);
      if (callback != null) callback.run();
      processPending();
    } catch (RuntimeException | Error failure) {
      pending.clear();
      throw failure;
    } finally {
      dispatching = false;
    }
  }

  public void transitionTo(S state) {
    ensureOpen();
    requireState(state);
    if (!started)
      throw new IllegalStateException("StateMachine must be started before transitioning");
    pending.addLast(state);
    drain();
  }

  private void drain() {
    if (dispatching) return;
    dispatching = true;
    try {
      processPending();
    } catch (RuntimeException | Error failure) {
      pending.clear();
      throw failure;
    } finally {
      dispatching = false;
    }
  }

  private void processPending() {
    int processed = 0;
    while (!pending.isEmpty()) {
      if (++processed > MAX_DISPATCH_TRANSITIONS) {
        pending.clear();
        throw new IllegalStateException("State transition dispatch exceeded 1024 transitions");
      }
      if (closed) break;
      S next = pending.removeFirst();
      if (next == current) continue;
      S previous = current;
      Runnable exitCallback = exit.get(previous);
      if (exitCallback != null) exitCallback.run();
      if (closed) break;
      current = next;
      Runnable enterCallback = enter.get(next);
      if (enterCallback != null) enterCallback.run();
      if (closed) break;
      Transition<S> transition = new Transition<>(previous, next);
      for (ChangeListener<S> listener : List.copyOf(listeners)) {
        if (closed) break;
        if (!listener.isClosed()) listener.deliver(transition);
      }
    }
  }

  private void configure(Map<S, Runnable> callbacks, S state, Runnable callback) {
    ensureOpen();
    requireState(state);
    callbacks.put(state, Objects.requireNonNull(callback, "callback"));
  }

  private void requireState(S state) {
    Objects.requireNonNull(state, "state");
    if (state.getDeclaringClass() != stateType)
      throw new IllegalArgumentException("State belongs to another enum type");
  }

  private void ensureOpen() {
    if (closed) throw new IllegalStateException("StateMachine is closed");
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    pending.clear();
    for (ChangeListener<S> listener : List.copyOf(listeners)) listener.close();
    listeners.clear();
  }

  public record Transition<S extends Enum<S>>(S previous, S current) {}

  private static final class ChangeListener<S extends Enum<S>> implements Subscription {
    private final StateMachine<S> owner;
    private final Consumer<Transition<S>> callback;
    private boolean closed;

    private ChangeListener(StateMachine<S> owner, Consumer<Transition<S>> callback) {
      this.owner = owner;
      this.callback = callback;
    }

    private void deliver(Transition<S> transition) {
      callback.accept(transition);
    }

    @Override
    public void close() {
      if (closed) return;
      closed = true;
      owner.listeners.remove(this);
    }

    @Override
    public boolean isClosed() {
      return closed;
    }
  }
}
