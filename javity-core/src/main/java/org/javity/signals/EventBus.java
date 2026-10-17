package org.javity.signals;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.javity.core.GameSystem;

/** Scene-local exact-type event dispatch with FIFO reentrant publication. */
public final class EventBus extends GameSystem {
  static final int MAX_DISPATCH_EVENTS = 1024;

  private final Map<Class<?>, List<Listener<?>>> listeners = new LinkedHashMap<>();
  private final ArrayDeque<Publication> pending = new ArrayDeque<>();
  private boolean dispatching;

  public <T> Subscription subscribe(Class<T> eventType, Consumer<? super T> listener) {
    ensureOpen();
    Objects.requireNonNull(eventType, "eventType");
    Objects.requireNonNull(listener, "listener");
    Listener<T> registration = new Listener<>(eventType, listener);
    listeners.computeIfAbsent(eventType, ignored -> new ArrayList<>()).add(registration);
    return registration;
  }

  public <T> void publish(Class<T> eventType, T event) {
    ensureOpen();
    Objects.requireNonNull(eventType, "eventType");
    Objects.requireNonNull(event, "event");
    if (!eventType.isInstance(event))
      throw new IllegalArgumentException("Event does not match its declared type");
    pending.addLast(new Publication(eventType, event));
    drain();
  }

  private void drain() {
    if (dispatching) return;
    dispatching = true;
    int processed = 0;
    try {
      while (!pending.isEmpty()) {
        if (++processed > MAX_DISPATCH_EVENTS) {
          pending.clear();
          throw new IllegalStateException("Event dispatch exceeded 1024 publications");
        }
        Publication publication = pending.removeFirst();
        List<Listener<?>> snapshot =
            List.copyOf(listeners.getOrDefault(publication.type, List.of()));
        for (Listener<?> listener : snapshot) {
          if (isDisposed()) break;
          if (!listener.isClosed()) listener.deliver(publication.value);
        }
      }
    } catch (RuntimeException | Error failure) {
      pending.clear();
      throw failure;
    } finally {
      dispatching = false;
    }
  }

  private void ensureOpen() {
    if (isDisposed()) throw new IllegalStateException("EventBus is closed");
  }

  @Override
  protected void onRelease() {
    pending.clear();
    for (List<Listener<?>> registrations : List.copyOf(listeners.values()))
      for (Listener<?> registration : List.copyOf(registrations)) registration.close();
    listeners.clear();
  }

  private final class Listener<T> implements Subscription {
    private final Class<T> type;
    private final Consumer<? super T> callback;
    private boolean closed;

    private Listener(Class<T> type, Consumer<? super T> callback) {
      this.type = type;
      this.callback = callback;
    }

    private void deliver(Object value) {
      callback.accept(type.cast(value));
    }

    @Override
    public void close() {
      if (closed) return;
      closed = true;
      List<Listener<?>> registrations = listeners.get(type);
      if (registrations != null) {
        registrations.remove(this);
        if (registrations.isEmpty()) listeners.remove(type);
      }
    }

    @Override
    public boolean isClosed() {
      return closed;
    }
  }

  private record Publication(Class<?> type, Object value) {}
}
