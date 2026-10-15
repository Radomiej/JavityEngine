package org.javity.script;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.javity.signals.Signal;
import org.javity.signals.Subscription;
import org.javity.ui.declarative.UiDocument;
import org.javity.ui.declarative.UiNode;

/** Document-scoped GraalJS runtime for the bundled React reconciler host. */
public final class ScriptUiRuntime implements AutoCloseable {
  private static final int MAX_CALLBACKS_PER_TICK = 10_000;
  private static final String BUNDLE_RESOURCE = "/org/javity/script/javity-ui.js";

  private final Thread ownerThread = Thread.currentThread();
  private final UiDocument document;
  private Context context;
  private final Map<String, Value> handlers = new HashMap<>();
  private final Map<String, GameAction> gameActions = new HashMap<>();
  private final Map<String, Supplier<?>> gameValues = new HashMap<>();
  private final Map<String, Signal<?>> gameSignals = new HashMap<>();
  private final Map<Long, SignalSubscription> signalSubscriptions = new LinkedHashMap<>();
  private final Map<Long, Timer> timers = new LinkedHashMap<>();
  private final Map<Long, Timer> intervals = new LinkedHashMap<>();
  private final Map<Long, Timer> frames = new LinkedHashMap<>();
  private final List<Value> microtasks = new ArrayList<>();
  private long nextTimerId = 1;
  private long nextSignalSubscriptionId = 1;
  private double nowMillis;
  private boolean closed;
  private boolean closing;
  private boolean tearingDown;
  private boolean reloading;
  private boolean drainingDeferredReload;
  private int guestDepth;
  private boolean ticking;
  private int callbackBudgetDepth;
  private int callbacksThisExecution;
  private int signalInvocationDepth;
  private String deferredReload;

  private record Timer(long id, double due, double interval, Value callback) {}

  @FunctionalInterface
  private interface GameAction {
    void invoke(Value[] args);
  }

  private record SignalSubscription(String name, Subscription subscription) {}

  /** Borrows {@code document}; a null-theme document supports headless View-only use. */
  public ScriptUiRuntime(UiDocument document) {
    this.document = document;
    initializeContext();
  }

  public UiDocument document() {
    checkUsable();
    return document;
  }

  public boolean isClosed() {
    return closed;
  }

  /** Registers a named Java action callable as {@code JavityGame.call(name)}. */
  public void bindAction(String name, Runnable action) {
    checkUsable();
    Objects.requireNonNull(action, "action");
    gameActions.put(
        requireBindingName(name),
        args -> {
          if (args.length != 0) throw new IllegalArgumentException("Action requires no arguments");
          action.run();
        });
  }

  /** Registers an action requiring exactly one finite JavaScript Number. */
  public void bindNumberAction(String name, DoubleConsumer action) {
    checkUsable();
    Objects.requireNonNull(action, "action");
    gameActions.put(
        requireBindingName(name),
        args -> {
          if (args.length != 1 || args[0].isHostObject() || !args[0].isNumber())
            throw new IllegalArgumentException("Action requires exactly one numeric primitive");
          double value = args[0].asDouble();
          if (!Double.isFinite(value))
            throw new IllegalArgumentException("Action number must be finite");
          action.accept(value);
        });
  }

  /** Registers an action requiring exactly one JavaScript Boolean. */
  public void bindBooleanAction(String name, Consumer<Boolean> action) {
    checkUsable();
    Objects.requireNonNull(action, "action");
    gameActions.put(
        requireBindingName(name),
        args -> {
          if (args.length != 1 || args[0].isHostObject() || !args[0].isBoolean())
            throw new IllegalArgumentException("Action requires exactly one boolean primitive");
          action.accept(args[0].asBoolean());
        });
  }

  /** Registers an action requiring exactly one JavaScript String. */
  public void bindStringAction(String name, Consumer<String> action) {
    checkUsable();
    Objects.requireNonNull(action, "action");
    gameActions.put(
        requireBindingName(name),
        args -> {
          if (args.length != 1 || args[0].isHostObject() || !args[0].isString())
            throw new IllegalArgumentException("Action requires exactly one string primitive");
          action.accept(args[0].asString());
        });
  }

  /** Registers a named Java value exposed as a primitive by {@code JavityGame.get(name)}. */
  public void bindValue(String name, Supplier<?> value) {
    checkUsable();
    String bindingName = requireBindingName(name);
    if (gameSignals.containsKey(bindingName))
      throw new IllegalArgumentException(
          "JavityGame value name is already bound as a signal: " + bindingName);
    gameValues.put(bindingName, Objects.requireNonNull(value, "value"));
  }

  /**
   * Borrows a primitive-valued scene signal and exposes its current value and events to JavaScript.
   */
  public void bindSignal(String name, Signal<?> signal) {
    checkUsable();
    String bindingName = requireBindingName(name);
    Objects.requireNonNull(signal, "signal");
    primitiveGameValue(bindingName, signal.get());
    if (gameValues.containsKey(bindingName))
      throw new IllegalArgumentException(
          "JavityGame signal name is already bound as a value: " + bindingName);
    clearSignalSubscriptions(bindingName);
    gameSignals.put(bindingName, signal);
  }

  /** Removes a borrowed signal binding and closes all JavaScript subscriptions to it. */
  public void unbindSignal(String name) {
    checkUsable();
    String bindingName = requireBindingName(name);
    clearSignalSubscriptions(bindingName);
    gameSignals.remove(bindingName);
  }

  /** Removes a previously registered action and releases its Java reference. */
  public void unbindAction(String name) {
    checkUsable();
    gameActions.remove(requireBindingName(name));
  }

  /** Removes a previously registered value and releases its Java reference. */
  public void unbindValue(String name) {
    checkUsable();
    gameValues.remove(requireBindingName(name));
  }

  public void eval(String source) {
    checkUsable();
    Objects.requireNonNull(source, "source");
    drainDeferredReloadAtBoundary();
    beginCallbackBudget();
    enterGuest();
    try {
      context.eval("js", source);
      if (!closed && deferredReload == null) drainMicrotasks();
    } catch (PolyglotException ex) {
      throw new ScriptUiException("JavaScript evaluation failed: " + ex.getMessage(), ex);
    } finally {
      leaveGuest();
      endCallbackBudget();
    }
  }

  /** Advances the deterministic, owner-thread clock and invokes a bounded snapshot of due work. */
  public void tick(double seconds) {
    checkUsable();
    if (!Double.isFinite(seconds) || seconds < 0)
      throw new IllegalArgumentException("tick seconds must be finite and nonnegative");
    double delta = seconds * 1000.0;
    if (!Double.isFinite(delta) || !Double.isFinite(nowMillis + delta))
      throw new IllegalArgumentException("tick would overflow the runtime clock");
    if (ticking) throw new IllegalStateException("ScriptUiRuntime.tick is not reentrant");
    drainDeferredReloadAtBoundary();
    beginCallbackBudget();
    ticking = true;
    try {
      nowMillis += delta;
      List<Timer> dueTimers = snapshotDue(timers, nowMillis);
      List<Timer> dueIntervals = snapshotDue(intervals, nowMillis);
      List<Timer> dueFrames = snapshotDue(frames, nowMillis);
      long count = (long) dueTimers.size() + dueIntervals.size() + dueFrames.size();
      if (count > MAX_CALLBACKS_PER_TICK)
        throw new IllegalStateException("Due callback limit exceeded for one tick");
      for (Timer timer : dueTimers) {
        if (closed || deferredReload != null) break;
        invokeOneShot(timers, timer, null);
      }
      for (Timer timer : dueIntervals) {
        if (closed || deferredReload != null) break;
        if (!intervals.containsKey(timer.id())) continue;
        invoke(timer.callback());
        if (!closed && deferredReload == null && intervals.containsKey(timer.id())) {
          double due = nowMillis + timer.interval();
          if (!Double.isFinite(due)) intervals.remove(timer.id());
          else
            intervals.put(
                timer.id(), new Timer(timer.id(), due, timer.interval(), timer.callback()));
        }
      }
      for (Timer frame : dueFrames) {
        if (closed || deferredReload != null) break;
        invokeOneShot(frames, frame, nowMillis);
      }
      if (!closed && deferredReload == null) drainMicrotasks();
    } finally {
      ticking = false;
      endCallbackBudget();
    }
  }

  /** Unmounts React while its context is alive, then clears host callbacks and closes GraalJS. */
  @Override
  public void close() {
    if (closed) return;
    checkThread();
    closed = true;
    deferredReload = null;
    closing = true;
    if (guestDepth != 0) return;
    finishClose();
  }

  private void finishClose() {
    if (!closing || guestDepth != 0) return;
    RuntimeException failure = null;
    try {
      if (context != null) {
        tearingDown = true;
        try {
          context.eval("js", "if (globalThis.JavityReact) globalThis.JavityReact.unmount();");
        } finally {
          tearingDown = false;
        }
      }
    } catch (RuntimeException ex) {
      failure = ex;
    }
    handlers.clear();
    gameActions.clear();
    gameValues.clear();
    clearSignalSubscriptions();
    gameSignals.clear();
    timers.clear();
    intervals.clear();
    frames.clear();
    microtasks.clear();
    if (document != null && !document.isClosed()) {
      try {
        document.render(UiNode.element("View", "javity-script-empty", "", Map.of(), List.of()));
      } catch (RuntimeException ex) {
        if (failure == null) failure = ex;
        else failure.addSuppressed(ex);
      }
    }
    try {
      if (context != null) context.close();
    } catch (RuntimeException ex) {
      if (failure == null) failure = ex;
      else failure.addSuppressed(ex);
    } finally {
      context = null;
      closing = false;
    }
    if (failure != null) throw failure;
  }

  /** Uses a fresh context on success; syntax failures leave the running document untouched. */
  public void reload(String source) {
    checkUsable();
    Objects.requireNonNull(source, "source");
    if (guestDepth != 0 && signalInvocationDepth > 0) {
      validateReloadSource(source);
      deferredReload = source;
      clearSignalSubscriptions();
      timers.clear();
      intervals.clear();
      frames.clear();
      microtasks.clear();
      return;
    }
    if (guestDepth != 0)
      throw new IllegalStateException("ScriptUiRuntime.reload cannot run inside a guest callback");
    validateReloadSource(source);
    if (reloading)
      throw new IllegalStateException("ScriptUiRuntime.reload is already replacing its context");
    reloading = true;
    try {
      beginCallbackBudget();
      enterGuest();
      try {
        context.eval("js", "if (globalThis.JavityReact) globalThis.JavityReact.unmount();");
        drainMicrotasks();
      } finally {
        leaveGuest();
        endCallbackBudget();
      }
      if (closed)
        throw new IllegalStateException("ScriptUiRuntime was closed during reload cleanup");
      handlers.clear();
      clearSignalSubscriptions();
      timers.clear();
      intervals.clear();
      frames.clear();
      microtasks.clear();
      context.close();
      if (document != null && !document.isClosed())
        document.render(UiNode.element("View", "javity-script-reload", "", Map.of(), List.of()));
      try {
        initializeContext();
        eval(source);
      } catch (RuntimeException ex) {
        if (context != null) {
          try {
            beginCallbackBudget();
            try {
              context.eval("js", "if (globalThis.JavityReact) globalThis.JavityReact.unmount();");
              drainMicrotasks();
            } finally {
              endCallbackBudget();
            }
          } catch (RuntimeException cleanupError) {
            ex.addSuppressed(cleanupError);
          }
          try {
            context.close();
          } catch (RuntimeException closeError) {
            ex.addSuppressed(closeError);
          }
        }
        context = null;
        handlers.clear();
        gameActions.clear();
        gameValues.clear();
        clearSignalSubscriptions();
        gameSignals.clear();
        timers.clear();
        intervals.clear();
        frames.clear();
        microtasks.clear();
        if (document != null && !document.isClosed()) {
          try {
            document.render(
                UiNode.element("View", "javity-script-reload-failed", "", Map.of(), List.of()));
          } catch (RuntimeException cleanupError) {
            ex.addSuppressed(cleanupError);
          }
        }
        closed = true;
        throw new ScriptUiException(
            "Reload failed after execution began; runtime was closed: " + ex.getMessage(), ex);
      }
    } finally {
      reloading = false;
    }
  }

  private void validateReloadSource(String source) {
    try (Context probe = newContext()) {
      probe.parse(Source.newBuilder("js", source, "javity-reload.js").build());
    } catch (IOException | PolyglotException ex) {
      throw new ScriptUiException(
          "Reload rejected; active runtime and UI were retained: " + ex.getMessage(), ex);
    }
  }

  private void initializeContext() {
    context = newContext();
    try {
      installHostBridge();
      installScheduler();
      eval(readBundle());
    } catch (RuntimeException ex) {
      context.close();
      context = null;
      throw ex;
    }
  }

  private void installHostBridge() {
    ProxyObject nativeApi =
        ProxyObject.fromMap(
            Map.of(
                "commit",
                    (ProxyExecutable)
                        args -> {
                          commit(args[0].asString(), args[1]);
                          return null;
                        },
                "logError",
                    (ProxyExecutable)
                        args -> {
                          throw new ScriptUiException(args[0].asString());
                        },
                "isOpen", (ProxyExecutable) args -> !closed && deferredReload == null));
    context.getBindings("js").putMember("JavityNative", nativeApi);
    ProxyObject gameApi =
        ProxyObject.fromMap(
            Map.of(
                "call",
                    (ProxyExecutable)
                        args -> {
                          checkGameBindingAccess();
                          if (args.length < 1 || !args[0].isString())
                            throw new IllegalArgumentException(
                                "JavityGame.call requires a string action name");
                          String name = requireBindingName(args[0].asString());
                          GameAction action = gameActions.get(name);
                          if (action == null)
                            throw new IllegalArgumentException(
                                "Unknown JavityGame action: " + name);
                          Value[] values = java.util.Arrays.copyOfRange(args, 1, args.length);
                          action.invoke(values);
                          return null;
                        },
                "get",
                    (ProxyExecutable)
                        args -> {
                          checkGameBindingAccess();
                          String name = readBindingName(args, "get");
                          Supplier<?> supplier = gameValues.get(name);
                          if (supplier != null) return primitiveGameValue(name, supplier.get());
                          Signal<?> signal = gameSignals.get(name);
                          if (signal != null) return primitiveGameValue(name, signal.get());
                          throw new IllegalArgumentException(
                              "Unknown JavityGame value or signal: " + name);
                        },
                "subscribe",
                    (ProxyExecutable)
                        args -> {
                          checkThread();
                          if (closed) throw new IllegalStateException("ScriptUiRuntime is closed");
                          if (args.length != 2 || !args[0].isString() || !args[1].canExecute())
                            throw new IllegalArgumentException(
                                "JavityGame.subscribe requires a signal name and callback"
                                    + " function");
                          String name = requireBindingName(args[0].asString());
                          if (deferredReload != null)
                            throw new IllegalStateException(
                                "ScriptUiRuntime is replacing its context");
                          Signal<?> signal = gameSignals.get(name);
                          if (signal == null)
                            throw new IllegalArgumentException(
                                "Unknown JavityGame signal: " + name);
                          if (nextSignalSubscriptionId >= 9_007_199_254_740_991L)
                            throw new IllegalStateException(
                                "JavityGame subscription id space exhausted");
                          long id = nextSignalSubscriptionId++;
                          Value callback = args[1];
                          Subscription subscription = subscribeSignal(name, signal, callback);
                          signalSubscriptions.put(id, new SignalSubscription(name, subscription));
                          return id;
                        },
                "unsubscribe",
                    (ProxyExecutable)
                        args -> {
                          checkThread();
                          if (args.length != 1 || !args[0].fitsInLong())
                            throw new IllegalArgumentException(
                                "JavityGame.unsubscribe requires a numeric subscription id");
                          SignalSubscription subscription =
                              signalSubscriptions.remove(args[0].asLong());
                          if (subscription != null) subscription.subscription().close();
                          return null;
                        }));
    context.getBindings("js").putMember("JavityGame", gameApi);
  }

  private Object primitiveGameValue(String name, Object value) {
    if (value == null || value instanceof String || value instanceof Boolean) return value;
    if (value instanceof Number number) {
      double primitive = number.doubleValue();
      if (Double.isFinite(primitive)) return primitive;
    }
    throw new IllegalArgumentException(
        "Unsupported JavityGame value for "
            + name
            + "; expected null, String, Boolean, or finite Number");
  }

  private Subscription subscribeSignal(String name, Signal<?> signal, Value callback) {
    return subscribeSignalTyped(name, signal, callback);
  }

  private <T> Subscription subscribeSignalTyped(String name, Signal<T> signal, Value callback) {
    return signal.subscribe(value -> invokeSignal(name, signal, callback, value), false);
  }

  private void invokeSignal(String name, Signal<?> source, Value callback, Object value) {
    checkThread();
    if (closed || gameSignals.get(name) != source || callback == null || !callback.canExecute())
      return;
    beginCallbackBudget();
    enterGuest();
    signalInvocationDepth++;
    try {
      consumeCallback();
      Object primitive = primitiveGameValue(name, value);
      Value react = context.getBindings("js").getMember("JavityReact");
      if (react == null || !react.hasMember("dispatchSignal"))
        throw new IllegalStateException("Javity React signal dispatcher is unavailable");
      react.getMember("dispatchSignal").execute(callback, primitive);
      if (!closed && deferredReload == null) drainMicrotasks();
    } catch (PolyglotException ex) {
      throw new ScriptUiException("JavityGame signal callback failed: " + ex.getMessage(), ex);
    } finally {
      signalInvocationDepth--;
      leaveGuest();
      endCallbackBudget();
    }
  }

  private void clearSignalSubscriptions(String name) {
    for (var entry : List.copyOf(signalSubscriptions.entrySet())) {
      if (entry.getValue().name().equals(name)) {
        signalSubscriptions.remove(entry.getKey());
        entry.getValue().subscription().close();
      }
    }
  }

  private void clearSignalSubscriptions() {
    for (SignalSubscription subscription : List.copyOf(signalSubscriptions.values()))
      subscription.subscription().close();
    signalSubscriptions.clear();
  }

  private static String readBindingName(Value[] args, String operation) {
    if (args.length != 1 || !args[0].isString())
      throw new IllegalArgumentException("JavityGame." + operation + " requires one string name");
    return requireBindingName(args[0].asString());
  }

  private static String requireBindingName(String name) {
    Objects.requireNonNull(name, "name");
    if (name.isBlank()) throw new IllegalArgumentException("game binding name must not be blank");
    return name;
  }

  private void installScheduler() {
    ProxyObject scheduler =
        ProxyObject.fromMap(
            Map.of(
                "now", (ProxyExecutable) args -> nowMillis,
                "timeout",
                    (ProxyExecutable) args -> schedule(timers, args[0], args[1].asDouble(), 0),
                "clearTimeout",
                    (ProxyExecutable)
                        args -> {
                          remove(timers, args[0].asLong());
                          return null;
                        },
                "interval",
                    (ProxyExecutable)
                        args ->
                            schedule(intervals, args[0], args[1].asDouble(), args[2].asDouble()),
                "clearInterval",
                    (ProxyExecutable)
                        args -> {
                          remove(intervals, args[0].asLong());
                          return null;
                        },
                "frame", (ProxyExecutable) args -> schedule(frames, args[0], 0, 0),
                "cancelFrame",
                    (ProxyExecutable)
                        args -> {
                          remove(frames, args[0].asLong());
                          return null;
                        },
                "microtask",
                    (ProxyExecutable)
                        args -> {
                          if (!closed && deferredReload == null) microtasks.add(args[0]);
                          return null;
                        }));
    context.getBindings("js").putMember("JavityScheduler", scheduler);
    context.eval(
        "js",
        """
        globalThis.setTimeout = (fn, ms=0) => JavityScheduler.timeout(fn, Math.max(0, Number(ms)||0));
        globalThis.clearTimeout = id => JavityScheduler.clearTimeout(id);
        globalThis.setInterval = (fn, ms=0) => JavityScheduler.interval(fn, Math.max(1, Number(ms)||0), Math.max(1, Number(ms)||0));
        globalThis.clearInterval = id => JavityScheduler.clearInterval(id);
        globalThis.requestAnimationFrame = fn => JavityScheduler.frame(fn);
        globalThis.cancelAnimationFrame = id => JavityScheduler.cancelFrame(id);
        globalThis.queueMicrotask = fn => JavityScheduler.microtask(fn);
        globalThis.performance = Object.freeze({now: () => JavityScheduler.now()});
        globalThis.__javityInvokeFrame = fn => fn(JavityScheduler.now());
        """);
  }

  private long schedule(Map<Long, Timer> queue, Value callback, double delay, double interval) {
    checkThread();
    if (closed) return -1;
    if (!callback.canExecute())
      throw new IllegalArgumentException("scheduler callback must be a function");
    if (!Double.isFinite(delay) || delay < 0 || !Double.isFinite(interval) || interval < 0)
      throw new IllegalArgumentException("scheduler delay must be finite and nonnegative");
    if (nextTimerId >= 9_007_199_254_740_991L)
      throw new IllegalStateException("scheduler id space exhausted");
    long id = nextTimerId++;
    if (deferredReload != null) return id;
    double due = nowMillis + delay;
    if (!Double.isFinite(due)) throw new IllegalArgumentException("scheduler deadline overflow");
    queue.put(id, new Timer(id, due, interval, callback));
    return id;
  }

  private void remove(Map<Long, Timer> queue, long id) {
    checkThread();
    queue.remove(id);
  }

  private List<Timer> snapshotDue(Map<Long, Timer> queue, double now) {
    List<Timer> out = new ArrayList<>();
    for (Timer timer : List.copyOf(queue.values())) if (timer.due() <= now) out.add(timer);
    return out;
  }

  private void invoke(Value callback) {
    invoke(callback, null);
  }

  private void invoke(Value callback, Double argument) {
    consumeCallback();
    invokeNoDrain(callback, argument, "Scheduled JavaScript callback failed: ");
    if (deferredReload == null) drainMicrotasks();
  }

  private void invokeOneShot(Map<Long, Timer> queue, Timer timer, Double argument) {
    if (queue.get(timer.id()) != timer) return;
    consumeCallback();
    queue.remove(timer.id());
    invokeNoDrain(timer.callback(), argument, "Scheduled JavaScript callback failed: ");
    if (deferredReload == null) drainMicrotasks();
  }

  private void drainMicrotasks() {
    while (!closed && deferredReload == null && !microtasks.isEmpty()) {
      Value callback = microtasks.getFirst();
      consumeCallback();
      microtasks.removeFirst();
      invokeNoDrain(callback, (Double) null, "Microtask callback failed: ");
    }
  }

  private void invokeNoDrain(Value callback, Double argument, String message) {
    enterGuest();
    try {
      if (argument == null) callback.execute();
      else callback.execute(argument);
    } catch (PolyglotException ex) {
      throw new ScriptUiException(message + ex.getMessage(), ex);
    } finally {
      leaveGuest();
    }
  }

  private void invokeNoDrain(Value callback, String argument, String message) {
    enterGuest();
    try {
      if (argument == null) callback.execute();
      else callback.execute(argument);
    } catch (PolyglotException ex) {
      throw new ScriptUiException(message + ex.getMessage(), ex);
    } finally {
      leaveGuest();
    }
  }

  private void invokeNoDrain(Value callback, Boolean argument, String message) {
    try {
      callback.execute((Object) argument);
    } catch (PolyglotException ex) {
      throw new ScriptUiException(message + ex.getMessage(), ex);
    }
  }

  private void beginCallbackBudget() {
    if (callbackBudgetDepth++ == 0) callbacksThisExecution = 0;
  }

  private void endCallbackBudget() {
    if (--callbackBudgetDepth == 0) {
      callbacksThisExecution = 0;
      drainDeferredReloadAtBoundary();
    }
  }

  private void drainDeferredReloadAtBoundary() {
    if (closed
        || closing
        || reloading
        || drainingDeferredReload
        || ticking
        || guestDepth != 0
        || signalInvocationDepth != 0
        || callbackBudgetDepth != 0
        || deferredReload == null) return;
    String source = deferredReload;
    deferredReload = null;
    drainingDeferredReload = true;
    try {
      reload(source);
    } finally {
      drainingDeferredReload = false;
    }
  }

  private void consumeCallback() {
    if (callbackBudgetDepth == 0) throw new IllegalStateException("No callback budget is active");
    if (++callbacksThisExecution > MAX_CALLBACKS_PER_TICK)
      throw new IllegalStateException("JavaScript callback budget exceeded");
  }

  private void commit(String json, Value callbackObject) {
    checkThread();
    if (closed || deferredReload != null) return;
    if (document == null) throw new IllegalStateException("No native UiDocument is attached");
    try {
      Value parsed = context.eval("js", "JSON.parse(" + quote(json) + ")");
      Map<String, Value> nextHandlers = new HashMap<>();
      if (callbackObject != null && callbackObject.hasMembers())
        for (String id : callbackObject.getMemberKeys())
          nextHandlers.put(id, callbackObject.getMember(id));
      UiNode nextTree = readNode(parsed);
      document.render(nextTree);
      handlers.clear();
      handlers.putAll(nextHandlers);
    } catch (RuntimeException ex) {
      throw new ScriptUiException("React tree could not be applied: " + ex.getMessage(), ex);
    }
  }

  private UiNode readNode(Value value) {
    if (!value.hasMembers())
      throw new IllegalArgumentException("React host emitted an invalid node");
    String type = memberString(value, "type", "");
    String key = memberString(value, "key", "");
    String classes = memberString(value, "classes", "");
    Map<String, Object> props = new HashMap<>();
    Value pv = value.getMember("props");
    if (pv != null && pv.hasMembers())
      for (String name : pv.getMemberKeys()) {
        Value prop = pv.getMember(name);
        switch (name) {
          case "text" -> props.put(name, prop.isNull() ? "" : prop.asString());
          case "source", "fit" -> {
            if (!prop.isString())
              throw new IllegalArgumentException("Image " + name + " must be a string");
            props.put(name, prop.asString());
          }
          case "value" ->
              props.put(
                  name,
                  type.equals("Progress") || type.equals("Slider")
                      ? readFiniteNumber(prop, name)
                      : prop.isNull() ? "" : prop.asString());
          case "disabled" -> props.put(name, prop.asBoolean());
          case "wrap" -> {
            if (!prop.isBoolean()) throw new IllegalArgumentException("wrap must be boolean");
            props.put(name, prop.asBoolean());
          }
          case "checked" -> {
            if (!prop.isBoolean()) throw new IllegalArgumentException("checked must be boolean");
            props.put(name, prop.asBoolean());
          }
          case "min", "max", "step", "duration", "delay" -> {
            double number = readFiniteNumber(prop, name);
            if ((name.equals("duration") || name.equals("delay")) && number < 0)
              throw new IllegalArgumentException(name + " must be finite and nonnegative");
            props.put(name, number);
          }
          case "onClick", "onChange", "onKeyDown", "onKeyUp", "onFocus", "onBlur" -> {
            String id = prop.asString();
            switch (name) {
              case "onClick", "onFocus", "onBlur" ->
                  props.put(name, (Runnable) () -> invokeHandler(id));
              case "onKeyDown" ->
                  props.put(
                      name,
                      (java.util.function.Function<org.javity.ui.declarative.UiKeyEvent, Boolean>)
                          keyEvent -> invokeKeyHandler(id, keyEvent));
              case "onKeyUp" ->
                  props.put(
                      name,
                      (java.util.function.Function<org.javity.ui.declarative.UiKeyEvent, Boolean>)
                          keyEvent -> invokeKeyHandler(id, keyEvent));
              case "onChange" -> {
                if (type.equals("TextField"))
                  props.put(name, (Consumer<String>) text -> invokeChangeHandler(id, text));
                else if (type.equals("Toggle"))
                  props.put(name, (Consumer<Boolean>) checked -> invokeTypedHandler(id, checked));
                else if (type.equals("Slider"))
                  props.put(
                      name,
                      (Consumer<Float>)
                          sliderValue -> invokeTypedHandler(id, sliderValue.doubleValue()));
                else throw new IllegalArgumentException("onChange is not supported on " + type);
              }
            }
          }
          default -> throw new IllegalArgumentException("Unsupported React property: " + name);
        }
      }
    List<UiNode> children = new ArrayList<>();
    Value ch = value.getMember("children");
    if (ch != null && ch.hasArrayElements())
      for (long i = 0; i < ch.getArraySize(); i++) children.add(readNode(ch.getArrayElement(i)));
    return UiNode.element(type, key, classes, props, children);
  }

  private static double readFiniteNumber(Value value, String name) {
    if (value.isHostObject() || !value.isNumber())
      throw new IllegalArgumentException(name + " must be a JavaScript number");
    double number = value.asDouble();
    if (!Double.isFinite(number)) throw new IllegalArgumentException(name + " must be finite");
    return number;
  }

  private String memberString(Value v, String key, String fallback) {
    Value x = v.getMember(key);
    return x == null || x.isNull() ? fallback : x.asString();
  }

  private void invokeHandler(String id) {
    invokeHandler(id, null);
  }

  private void invokeChangeHandler(String id, String value) {
    invokeHandler(id, value);
  }

  private void invokeTypedHandler(String id, Object value) {
    invokeHandler(id, value);
  }

  private boolean invokeKeyHandler(String id, org.javity.ui.declarative.UiKeyEvent event) {
    checkUsable();
    drainDeferredReloadAtBoundary();
    Value callback = handlers.get(id);
    if (callback == null) return false;
    String payload =
        "{\"keyCode\":"
            + event.keyCode()
            + ",\"key\":"
            + quote(event.key())
            + ",\"shift\":"
            + event.shift()
            + ",\"ctrl\":"
            + event.ctrl()
            + ",\"alt\":"
            + event.alt()
            + "}";
    Value argument = context.eval("js", "Object.freeze(JSON.parse(" + quote(payload) + "))");
    Object result = invokeHandlerValue(callback, argument);
    return Boolean.TRUE.equals(result);
  }

  private void invokeHandler(String id, Object argument) {
    checkUsable();
    drainDeferredReloadAtBoundary();
    Value callback = handlers.get(id);
    if (callback == null) return;
    beginCallbackBudget();
    enterGuest();
    try {
      consumeCallback();
      if (argument == null) invokeNoDrain(callback, (String) null, "React event handler failed: ");
      else if (argument instanceof String string)
        invokeNoDrain(callback, string, "React event handler failed: ");
      else if (argument instanceof Boolean bool)
        invokeNoDrain(callback, bool, "React event handler failed: ");
      else if (argument instanceof Number number)
        invokeNoDrain(callback, number.doubleValue(), "React event handler failed: ");
      if (!closed && deferredReload == null) drainMicrotasks();
      if (!closed && deferredReload == null)
        context.eval("js", "if (globalThis.JavityReact) globalThis.JavityReact.flush()");
    } finally {
      leaveGuest();
      endCallbackBudget();
    }
  }

  private Object invokeHandlerValue(Value callback, Value argument) {
    beginCallbackBudget();
    enterGuest();
    try {
      consumeCallback();
      Value result = callback.execute(argument);
      if (!closed && deferredReload == null) drainMicrotasks();
      if (!closed && deferredReload == null)
        context.eval("js", "if (globalThis.JavityReact) globalThis.JavityReact.flush()");
      return result != null && result.isBoolean() ? result.asBoolean() : null;
    } catch (PolyglotException ex) {
      throw new ScriptUiException("React key handler failed: " + ex.getMessage(), ex);
    } finally {
      leaveGuest();
      endCallbackBudget();
    }
  }

  private Context newContext() {
    return Context.newBuilder("js")
        .allowHostAccess(HostAccess.NONE)
        .allowHostClassLookup(name -> false)
        .allowIO(IOAccess.NONE)
        .allowNativeAccess(false)
        .option("js.foreign-object-prototype", "false")
        .build();
  }

  private String readBundle() {
    try (InputStream in = ScriptUiRuntime.class.getResourceAsStream(BUNDLE_RESOURCE)) {
      if (in == null) return "";
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new ScriptUiException("Cannot read bundled Javity React host", ex);
    }
  }

  private static String quote(String s) {
    return "\""
        + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
        + "\"";
  }

  private void checkThread() {
    if (Thread.currentThread() != ownerThread)
      throw new IllegalStateException("ScriptUiRuntime is bound to its construction thread");
  }

  private void checkUsable() {
    checkThread();
    if (closed) throw new IllegalStateException("ScriptUiRuntime is closed");
  }

  private void checkGameBindingAccess() {
    checkThread();
    if (closed && !tearingDown) throw new IllegalStateException("ScriptUiRuntime is closed");
  }

  private void enterGuest() {
    guestDepth++;
  }

  private void leaveGuest() {
    if (--guestDepth == 0 && closing) finishClose();
  }
}
