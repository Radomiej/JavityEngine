package org.javity.signals;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.javity.core.GameSystem;

/** Scene-owned collection of typed, in-process signals. */
public final class Radio extends GameSystem {
  static final int MAX_DISPATCH_EVENTS = 1024;

  private final Map<String, Signal<?>> channels = new LinkedHashMap<>();
  private final ArrayDeque<Delivery> pending = new ArrayDeque<>();
  private boolean dispatching;

  public <T> Signal<T> channel(String name, Class<T> type, T initial) {
    ensureOpen();
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
    if (name.isBlank()) throw new IllegalArgumentException("Channel name must not be blank");
    validate(type, initial);
    Signal<?> existing = channels.get(name);
    if (existing != null) {
      if (existing.type() != type)
        throw new IllegalArgumentException("Channel already exists with another type");
      @SuppressWarnings("unchecked")
      Signal<T> typed = (Signal<T>) existing;
      return typed;
    }
    Signal<T> signal = new Signal<>(this, name, type, initial);
    channels.put(name, signal);
    return signal;
  }

  private static <T> void validate(Class<T> type, Object value) {
    Objects.requireNonNull(value, "value");
    if (!type.isInstance(value))
      throw new IllegalArgumentException("Signal value does not match its declared type");
    if (value instanceof Float number && !Float.isFinite(number))
      throw new IllegalArgumentException("Signal Float value must be finite");
    if (value instanceof Double number && !Double.isFinite(number))
      throw new IllegalArgumentException("Signal Double value must be finite");
  }

  void set(Signal<?> signal, Object value) {
    ensureOpen();
    signal.ensureOpen();
    validate(signal.type(), value);
    pending.addLast(new Delivery(signal, value, null));
    drain();
  }

  <T> Subscription subscribe(Signal<T> signal, Consumer<? super T> callback, boolean emitCurrent) {
    ensureOpen();
    signal.ensureOpen();
    Objects.requireNonNull(callback, "callback");
    Signal.Listener<T> listener = signal.addListener(callback);
    if (emitCurrent) {
      try {
        pending.addLast(new Delivery(signal, signal.get(), listener));
        drain();
      } catch (RuntimeException | Error failure) {
        listener.close();
        throw failure;
      }
    }
    return listener;
  }

  void remove(Signal<?> signal) {
    signal.closeListeners();
    channels.remove(signal.name(), signal);
    pending.removeIf(delivery -> delivery.signal == signal);
  }

  private void drain() {
    if (dispatching) return;
    dispatching = true;
    int processed = 0;
    try {
      while (!pending.isEmpty()) {
        if (++processed > MAX_DISPATCH_EVENTS) {
          pending.clear();
          throw new IllegalStateException("Radio dispatch exceeded 1024 updates");
        }
        Delivery delivery = pending.removeFirst();
        Signal<?> signal = delivery.signal;
        if (isDisposed() || signal.isClosed()) continue;
        if (delivery.target != null) {
          if (!delivery.target.isClosed()) delivery.target.deliver(delivery.value);
        } else {
          signal.apply(delivery.value);
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
    if (isDisposed()) throw new IllegalStateException("Radio is closed");
  }

  @Override
  protected void onRelease() {
    pending.clear();
    for (Signal<?> signal : List.copyOf(channels.values())) signal.closeFromRadio();
    channels.clear();
  }

  private record Delivery(Signal<?> signal, Object value, Signal.Listener<?> target) {}
}
