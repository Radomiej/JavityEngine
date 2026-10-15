package org.javity.signals;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** One typed value channel owned by a {@link Radio}. */
public final class Signal<T> implements AutoCloseable {
  private final Radio radio;
  private final String name;
  private final Class<T> type;
  private final List<Listener<T>> listeners = new ArrayList<>();
  private T value;
  private boolean closed;

  Signal(Radio radio, String name, Class<T> type, T initial) {
    this.radio = radio;
    this.name = name;
    this.type = type;
    this.value = initial;
  }

  public T get() {
    ensureOpen();
    return value;
  }

  public void set(T value) {
    radio.set(this, value);
  }

  public Subscription subscribe(Consumer<? super T> listener, boolean emitCurrent) {
    return radio.subscribe(this, listener, emitCurrent);
  }

  public boolean isClosed() {
    return closed;
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    radio.remove(this);
  }

  Class<T> type() {
    return type;
  }

  String name() {
    return name;
  }

  void ensureOpen() {
    if (closed || radio.isDisposed()) throw new IllegalStateException("Signal is closed");
  }

  void apply(Object next) {
    ensureOpen();
    T typed = type.cast(next);
    if (Objects.equals(value, typed)) return;
    value = typed;
    for (Listener<T> listener : List.copyOf(listeners)) {
      if (radio.isDisposed() || closed) break;
      if (!listener.isClosed()) listener.deliver(typed);
    }
  }

  Listener<T> addListener(Consumer<? super T> callback) {
    Listener<T> listener = new Listener<>(this, callback);
    listeners.add(listener);
    return listener;
  }

  void closeListeners() {
    for (Listener<T> listener : List.copyOf(listeners)) listener.close();
    listeners.clear();
  }

  void closeFromRadio() {
    closed = true;
    closeListeners();
  }

  private void remove(Listener<T> listener) {
    listeners.remove(listener);
  }

  static final class Listener<T> implements Subscription {
    private final Signal<T> signal;
    private final Consumer<? super T> callback;
    private boolean closed;

    Listener(Signal<T> signal, Consumer<? super T> callback) {
      this.signal = signal;
      this.callback = callback;
    }

    void deliver(Object value) {
      callback.accept(signal.type.cast(value));
    }

    @Override
    public void close() {
      if (closed) return;
      closed = true;
      signal.remove(this);
    }

    @Override
    public boolean isClosed() {
      return closed;
    }
  }
}
