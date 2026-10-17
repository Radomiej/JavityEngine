package org.javity.script;

import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;
import org.javity.signals.Signal;
import org.javity.ui.declarative.UiDocument;

/** Platform-neutral public facade for the bundled React host runtime. */
public final class ScriptUiRuntime implements AutoCloseable {
  private final ScriptUiHostSession host;

  /** Uses the platform's selected engine provider (GraalJS on desktop). */
  public ScriptUiRuntime(UiDocument document) {
    this(document, discoverFactory());
  }

  /** Allows a launcher to inject its engine without exposing engine-specific types. */
  public ScriptUiRuntime(UiDocument document, ScriptUiEngineFactory factory) {
    host = new ScriptUiHostSession(document, Objects.requireNonNull(factory, "factory"));
  }

  private static ScriptUiEngineFactory discoverFactory() {
    return ServiceLoader.load(ScriptUiEngineFactory.class).findFirst().orElseThrow(() ->
        new IllegalStateException("No ScriptUiEngineFactory is installed; add the desktop Graal or Android QuickJS provider"));
  }

  public UiDocument document() { return host.document(); }
  public boolean isClosed() { return host.isClosed(); }
  public void bindAction(String name, Runnable action) { host.bindAction(name, action); }
  public void bindNumberAction(String name, DoubleConsumer action) { host.bindNumberAction(name, action); }
  public void bindBooleanAction(String name, Consumer<Boolean> action) { host.bindBooleanAction(name, action); }
  public void bindStringAction(String name, Consumer<String> action) { host.bindStringAction(name, action); }
  public void bindValue(String name, Supplier<?> value) { host.bindValue(name, value); }
  public void bindSignal(String name, Signal<?> signal) { host.bindSignal(name, signal); }
  public void unbindSignal(String name) { host.unbindSignal(name); }
  public void unbindAction(String name) { host.unbindAction(name); }
  public void unbindValue(String name) { host.unbindValue(name); }
  public void eval(String source) { host.eval(source); }
  public void tick(double seconds) { host.tick(seconds); }
  public void reload(String source) { host.reload(source); }
  @Override public void close() { host.close(); }
}
