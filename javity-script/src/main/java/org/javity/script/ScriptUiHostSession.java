package org.javity.script;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;
import org.javity.signals.Signal;
import org.javity.signals.Subscription;
import org.javity.ui.declarative.UiDocument;
import org.javity.ui.declarative.UiNode;

/** Portable document-scoped host policy for the bundled React reconciler. */
final class ScriptUiHostSession implements AutoCloseable {
  private static final int MAX_CALLBACKS_PER_TICK = 10_000;
  private static final String BUNDLE_RESOURCE = "/org/javity/script/javity-ui.js";

  private final Thread ownerThread = Thread.currentThread();
  private final UiDocument document;
  private final ScriptUiEngineFactory engineFactory;
  private ScriptUiEngine engine;
  private final Map<String, String> handlers = new HashMap<>();
  private final Map<String, GameAction> gameActions = new HashMap<>();
  private final Map<String, Supplier<?>> gameValues = new HashMap<>();
  private final Map<String, Signal<?>> gameSignals = new HashMap<>();
  private final Map<Long, SignalSubscription> signalSubscriptions = new LinkedHashMap<>();
  private final Deque<PendingSignal> pendingSignals = new ArrayDeque<>();
  private final Deque<String> pendingSignalCallbackReleases = new ArrayDeque<>();
  private boolean drainingSignalCallbackReleases;
  private long contextGeneration = 1;
  private boolean drainingSignals;
  private final Map<Long, Timer> timers = new LinkedHashMap<>();
  private final Map<Long, Timer> intervals = new LinkedHashMap<>();
  private final Map<Long, Timer> frames = new LinkedHashMap<>();
  private final Deque<Long> microtasks = new ArrayDeque<>();
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

  private record Timer(long id, double due, double interval, long callbackId) {}

  @FunctionalInterface
  private interface GameAction {
    void invoke(Object[] args);
  }

  private record SignalSubscription(String name, String callbackId, long generation, Subscription subscription) {}
  private record PendingSignal(String name, Signal<?> source, String callbackId, Object value, long generation) {}

  /** Borrows {@code document}; a null-theme document supports headless View-only use. */
  ScriptUiHostSession(UiDocument document, ScriptUiEngineFactory engineFactory) {
    this.document = document;
    this.engineFactory = Objects.requireNonNull(engineFactory, "engineFactory");
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
          if (args.length != 1 || !(args[0] instanceof Number number))
            throw new IllegalArgumentException("Action requires exactly one numeric primitive");
          double value = number.doubleValue();
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
          if (args.length != 1 || !(args[0] instanceof Boolean value))
            throw new IllegalArgumentException("Action requires exactly one boolean primitive");
          action.accept(value);
        });
  }

  /** Registers an action requiring exactly one JavaScript String. */
  public void bindStringAction(String name, Consumer<String> action) {
    checkUsable();
    Objects.requireNonNull(action, "action");
    gameActions.put(
        requireBindingName(name),
        args -> {
          if (args.length != 1 || !(args[0] instanceof String value))
            throw new IllegalArgumentException("Action requires exactly one string primitive");
          action.accept(value);
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
      engine.evaluateDiscardingResult(source);
      if (!closed && deferredReload == null) drainMicrotasks();
    } catch (ScriptEngineException ex) {
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
        invoke(timer.callbackId(), "[]", false, "Scheduled JavaScript callback failed: ");
        if (!closed && deferredReload == null && intervals.containsKey(timer.id())) {
          double due = nowMillis + timer.interval();
          if (!Double.isFinite(due)) intervals.remove(timer.id());
          else
            intervals.put(
                timer.id(), new Timer(timer.id(), due, timer.interval(), timer.callbackId()));
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

  /** Unmounts React while its engine is alive, then clears host callbacks and closes it. */
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
      if (engine != null) {
        tearingDown = true;
        try {
          engine.evaluateDiscardingResult("if (globalThis.JavityReact) globalThis.JavityReact.unmount();");
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
      if (engine != null) engine.close();
    } catch (RuntimeException ex) {
      if (failure == null) failure = ex;
      else failure.addSuppressed(ex);
    } finally {
      engine = null;
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
        engine.evaluateDiscardingResult("if (globalThis.JavityReact) globalThis.JavityReact.unmount();");
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
      engine.close();
      pendingSignalCallbackReleases.clear();
      contextGeneration++;
      if (document != null && !document.isClosed())
        document.render(UiNode.element("View", "javity-script-reload", "", Map.of(), List.of()));
      try {
        initializeContext();
        eval(source);
      } catch (RuntimeException ex) {
        if (engine != null) {
          try {
            beginCallbackBudget();
            try {
              engine.evaluateDiscardingResult("if (globalThis.JavityReact) globalThis.JavityReact.unmount();");
              drainMicrotasks();
            } finally {
              endCallbackBudget();
            }
          } catch (RuntimeException cleanupError) {
            ex.addSuppressed(cleanupError);
          }
          try {
            engine.close();
          } catch (RuntimeException closeError) {
            ex.addSuppressed(closeError);
          }
        }
        engine = null;
        pendingSignalCallbackReleases.clear();
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
      drainDeferredSignals();
    }
  }

  private void validateReloadSource(String source) {
    try {
      engine.checkSyntax(source);
    } catch (ScriptEngineException ex) {
      throw new ScriptUiException(
          "Reload rejected; active runtime and UI were retained: " + ex.getMessage(), ex);
    }
  }

  private void initializeContext() {
    engine = engineFactory.create();
    try {
      installHostBridge();
      installScheduler();
      eval(readBundle());
    } catch (RuntimeException ex) {
      engine.close();
      engine = null;
      throw ex;
    }
  }

  private void installHostBridge() {
    engine.defineFunction("__javityCommit", args -> {
      if (args.length != 2) throw new IllegalArgumentException("commit requires tree and handler JSON");
      commit(requireString(args[0], "tree JSON"), requireString(args[1], "handler JSON"));
      return null;
    });
    engine.defineFunction("__javityLogError", args -> {
      if (args.length != 1) throw new IllegalArgumentException("logError requires one message");
      throw new ScriptUiException(requireString(args[0], "error message"));
    });
    engine.defineFunction("__javityIsOpen", args -> !closed && deferredReload == null);
    engine.defineFunction("__javityGameCall", args -> {
      checkGameBindingAccess();
      if (args.length != 2) throw new IllegalArgumentException("JavityGame.call requires an action name and JSON arguments");
      String name = requireBindingName(requireString(args[0], "action name"));
      GameAction action = gameActions.get(name);
      if (action == null) throw new IllegalArgumentException("Unknown JavityGame action: " + name);
      action.invoke(ScriptUiTreeDecoder.readPrimitiveArray(requireString(args[1], "action arguments")));
      return null;
    });
    engine.defineFunction("__javityGameGet", args -> {
      checkGameBindingAccess();
      if (args.length != 1) throw new IllegalArgumentException("get requires one name");
      String name = requireBindingName(requireString(args[0], "binding name"));
      Supplier<?> supplier = gameValues.get(name);
      if (supplier != null) return primitiveGameValue(name, supplier.get());
      Signal<?> signal = gameSignals.get(name);
      if (signal != null) return primitiveGameValue(name, signal.get());
      throw new IllegalArgumentException("Unknown JavityGame value or signal: " + name);
    });
    engine.defineFunction("__javitySubscribe", args -> {
      checkThread();
      if (closed) throw new IllegalStateException("ScriptUiRuntime is closed");
      if (args.length != 2) throw new IllegalArgumentException("subscribe requires a signal name and callback id");
      String name = requireBindingName(requireString(args[0], "signal name"));
      String callbackId = requireString(args[1], "signal callback id");
      if (!callbackId.matches("s[0-9]+")) throw new IllegalArgumentException("Invalid signal callback id");
      if (deferredReload != null) throw new IllegalStateException("ScriptUiRuntime is replacing its context");
      Signal<?> signal = gameSignals.get(name);
      if (signal == null) throw new IllegalArgumentException("Unknown JavityGame signal: " + name);
      if (nextSignalSubscriptionId >= 9_007_199_254_740_991L)
        throw new IllegalStateException("JavityGame subscription id space exhausted");
      long id = nextSignalSubscriptionId++;
      long generation = contextGeneration;
      Subscription subscription = subscribeSignal(name, signal, callbackId, generation);
      signalSubscriptions.put(id, new SignalSubscription(name, callbackId, generation, subscription));
      return id;
    });
    engine.defineFunction("__javityUnsubscribe", args -> {
      checkThread();
      if (args.length != 1 || !(args[0] instanceof Number number))
        throw new IllegalArgumentException("unsubscribe requires a numeric subscription id");
      SignalSubscription subscription = signalSubscriptions.remove(number.longValue());
      if (subscription != null) subscription.subscription().close();
      return null;
    });
    engine.evaluateDiscardingResult("""
        globalThis.JavityNative = Object.freeze({
          commit: __javityCommit, logError: __javityLogError, isOpen: __javityIsOpen,
          gameCall: __javityGameCall, gameGet: __javityGameGet,
          subscribe: __javitySubscribe, unsubscribe: __javityUnsubscribe
        });
        """);
  }

  private static String requireString(Object value, String name) {
    if (!(value instanceof String text)) throw new IllegalArgumentException(name + " must be a string");
    return text;
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

  private Subscription subscribeSignal(String name, Signal<?> signal, String callbackId, long generation) {
    return subscribeSignalTyped(name, signal, callbackId, generation);
  }

  private <T> Subscription subscribeSignalTyped(String name, Signal<T> signal, String callbackId, long generation) {
    return signal.subscribe(value -> queueSignal(name, signal, callbackId, generation, value), false);
  }

  private void queueSignal(String name, Signal<?> source, String callbackId, long generation, Object value) {
    checkThread();
    if (closed || generation != contextGeneration || gameSignals.get(name) != source) return;
    PendingSignal event = new PendingSignal(name, source, callbackId, value, generation);
    if (guestDepth != 0 || drainingSignals) {
      pendingSignals.addLast(event);
      return;
    }
    dispatchSignal(event);
  }

  private void dispatchSignal(PendingSignal event) {
    if (closed || event.generation() != contextGeneration || gameSignals.get(event.name()) != event.source()) return;
    boolean active = signalSubscriptions.values().stream().anyMatch(subscription ->
        subscription.callbackId().equals(event.callbackId()) && subscription.generation() == event.generation());
    if (!active) return;
    beginCallbackBudget();
    enterGuest();
    signalInvocationDepth++;
    try {
      consumeCallback();
      Object value = primitiveGameValue(event.name(), event.value());
      String source = "globalThis.JavityReact.dispatchSignal(" + quote(event.callbackId())
          + ", JSON.parse(" + quote(jsonPrimitive(value)) + "))";
      engine.evaluateDiscardingResult(source);
      if (!closed && deferredReload == null) drainMicrotasks();
    } catch (ScriptEngineException ex) {
      throw new ScriptUiException("JavityGame signal callback failed: " + ex.getMessage(), ex);
    } finally {
      signalInvocationDepth--;
      leaveGuest();
      endCallbackBudget();
    }
  }

  private void drainDeferredSignals() {
    if (drainingSignals || guestDepth != 0 || closed || closing || reloading || deferredReload != null) return;
    drainingSignals = true;
    try {
    while (!pendingSignals.isEmpty() && !closed && !reloading && deferredReload == null)
        dispatchSignal(pendingSignals.removeFirst());
    } finally {
      drainingSignals = false;
    }
  }

  private void clearSignalSubscriptions(String name) {
    pendingSignals.removeIf(event -> event.name().equals(name));
    for (var entry : List.copyOf(signalSubscriptions.entrySet())) {
      if (entry.getValue().name().equals(name)) {
        signalSubscriptions.remove(entry.getKey());
        pendingSignalCallbackReleases.addLast(entry.getValue().callbackId());
        entry.getValue().subscription().close();
      }
    }
    drainGuestSignalCallbackReleases();
  }

  private void clearSignalSubscriptions() {
    pendingSignals.clear();
    for (SignalSubscription subscription : List.copyOf(signalSubscriptions.values()))
      subscription.subscription().close();
    signalSubscriptions.clear();
    pendingSignalCallbackReleases.clear();
  }

  /** Releases guest closures only after the current engine evaluation has returned. */
  private void drainGuestSignalCallbackReleases() {
    if (drainingSignalCallbackReleases
        || guestDepth != 0
        || closed
        || closing
        || reloading
        || tearingDown
        || engine == null) return;
    drainingSignalCallbackReleases = true;
    try {
      while (!pendingSignalCallbackReleases.isEmpty() && !closed && engine != null) {
        String callbackId = pendingSignalCallbackReleases.removeFirst();
        engine.evaluateDiscardingResult("globalThis.JavityReact.releaseSignal(" + quote(callbackId) + ")");
      }
    } finally {
      drainingSignalCallbackReleases = false;
    }
  }

  private static String readBindingName(Object[] args, String operation) {
    if (args.length != 1)
      throw new IllegalArgumentException("JavityGame." + operation + " requires one string name");
    return requireBindingName(requireString(args[0], operation + " name"));
  }

  private static String requireBindingName(String name) {
    Objects.requireNonNull(name, "name");
    if (name.isBlank()) throw new IllegalArgumentException("game binding name must not be blank");
    return name;
  }

  private void installScheduler() {
    engine.defineFunction("__javitySchedulerNow", args -> nowMillis);
    engine.defineFunction("__javitySchedulerTimeout", args -> schedule(timers, requireLong(args, 0), requireDouble(args, 1), 0));
    engine.defineFunction("__javitySchedulerClearTimeout", args -> { remove(timers, requireLong(args, 0)); return null; });
    engine.defineFunction("__javitySchedulerInterval", args -> schedule(intervals, requireLong(args, 0), requireDouble(args, 1), requireDouble(args, 2)));
    engine.defineFunction("__javitySchedulerClearInterval", args -> { remove(intervals, requireLong(args, 0)); return null; });
    engine.defineFunction("__javitySchedulerFrame", args -> schedule(frames, requireLong(args, 0), 0, 0));
    engine.defineFunction("__javitySchedulerCancelFrame", args -> { remove(frames, requireLong(args, 0)); return null; });
    engine.defineFunction("__javitySchedulerMicrotask", args -> {
      if (args.length != 1) throw new IllegalArgumentException("microtask requires a callback id");
      if (!closed && deferredReload == null) microtasks.addLast(requireLong(args, 0));
      return null;
    });
    engine.evaluateDiscardingResult("""
        globalThis.JavityScheduler = Object.freeze({
          now: __javitySchedulerNow, timeout: __javitySchedulerTimeout,
          clearTimeout: __javitySchedulerClearTimeout, interval: __javitySchedulerInterval,
          clearInterval: __javitySchedulerClearInterval, frame: __javitySchedulerFrame,
          cancelFrame: __javitySchedulerCancelFrame, microtask: __javitySchedulerMicrotask
        });
        """);
    engine.evaluateDiscardingResult("""
        const __javityGuestCallbacks = new Map();
        let __javityNextCallback = 1;
        const __javityRetain = fn => {
          if (typeof fn !== 'function') throw new TypeError('scheduled callback must be a function');
          if (__javityNextCallback >= Number.MAX_SAFE_INTEGER) throw new Error('callback id space exhausted');
          const id = __javityNextCallback++;
          __javityGuestCallbacks.set(id, fn);
          return id;
        };
        globalThis.__javityClearCallbacks = () => __javityGuestCallbacks.clear();
        globalThis.__javityRunCallback = (id, args, oneShot) => {
          const callback = __javityGuestCallbacks.get(id);
          if (!callback) return;
          if (oneShot) __javityGuestCallbacks.delete(id);
          return callback(...args);
        };
        const __javitySchedule = (fn, schedule) => {
          const id = __javityRetain(fn);
          try { schedule(id); return id; }
          catch (error) { __javityGuestCallbacks.delete(id); throw error; }
        };
        globalThis.setTimeout = (fn, ms=0) => {
          return __javitySchedule(fn, id => JavityScheduler.timeout(id, Math.max(0, Number(ms)||0)));
        };
        globalThis.clearTimeout = id => { __javityGuestCallbacks.delete(id); JavityScheduler.clearTimeout(id); };
        globalThis.setInterval = (fn, ms=0) => {
          return __javitySchedule(fn, id => JavityScheduler.interval(id, Math.max(1, Number(ms)||0), Math.max(1, Number(ms)||0)));
        };
        globalThis.clearInterval = id => { __javityGuestCallbacks.delete(id); JavityScheduler.clearInterval(id); };
        globalThis.requestAnimationFrame = fn => __javitySchedule(fn, id => JavityScheduler.frame(id));
        globalThis.cancelAnimationFrame = id => { __javityGuestCallbacks.delete(id); JavityScheduler.cancelFrame(id); };
        globalThis.queueMicrotask = fn => { const id = __javityRetain(fn); JavityScheduler.microtask(id); };
        globalThis.performance = Object.freeze({now: () => JavityScheduler.now()});
        """);
  }

  private static long requireLong(Object[] args, int index) {
    if (index >= args.length || !(args[index] instanceof Number number))
      throw new IllegalArgumentException("scheduler callback id must be numeric");
    return number.longValue();
  }

  private static double requireDouble(Object[] args, int index) {
    if (index >= args.length || !(args[index] instanceof Number number))
      throw new IllegalArgumentException("scheduler delay must be numeric");
    return number.doubleValue();
  }

  private long schedule(Map<Long, Timer> queue, long callbackId, double delay, double interval) {
    checkThread();
    if (closed) return -1;
    if (callbackId <= 0 || callbackId >= 9_007_199_254_740_991L)
      throw new IllegalArgumentException("scheduler callback id is invalid");
    if (!Double.isFinite(delay) || delay < 0 || !Double.isFinite(interval) || interval < 0)
      throw new IllegalArgumentException("scheduler delay must be finite and nonnegative");
    if (deferredReload != null) return callbackId;
    double due = nowMillis + delay;
    if (!Double.isFinite(due)) throw new IllegalArgumentException("scheduler deadline overflow");
    queue.put(callbackId, new Timer(callbackId, due, interval, callbackId));
    return callbackId;
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

  private void invoke(long callbackId, String argsJson, boolean oneShot, String message) {
    consumeCallback();
    invokeNoDrain(callbackId, argsJson, oneShot, message);
    if (deferredReload == null) drainMicrotasks();
  }

  private void invokeOneShot(Map<Long, Timer> queue, Timer timer, Double argument) {
    if (queue.get(timer.id()) != timer) return;
    queue.remove(timer.id());
    String args = argument == null ? "[]" : "[" + Double.toString(argument) + "]";
    consumeCallback();
    invokeNoDrain(timer.callbackId(), args, true, "Scheduled JavaScript callback failed: ");
    if (deferredReload == null) drainMicrotasks();
  }

  private void drainMicrotasks() {
    while (!closed && deferredReload == null && !microtasks.isEmpty()) {
      long callbackId = microtasks.removeFirst();
      consumeCallback();
      invokeNoDrain(callbackId, "[]", true, "Microtask callback failed: ");
    }
  }

  private void invokeNoDrain(long callbackId, String argsJson, boolean oneShot, String message) {
    enterGuest();
    try {
      engine.evaluateDiscardingResult("globalThis.__javityRunCallback(" + callbackId + ", JSON.parse(" + quote(argsJson) + "), " + oneShot + ")");
    } catch (ScriptEngineException ex) {
      throw new ScriptUiException(message + ex.getMessage(), ex);
    } finally {
      leaveGuest();
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

  private void commit(String json, String handlerIdsJson) {
    checkThread();
    if (closed || deferredReload != null) return;
    if (document == null) throw new IllegalStateException("No native UiDocument is attached");
    try {
      ScriptUiTreeDecoder.Result decoded = ScriptUiTreeDecoder.decode(
          json,
          handlerIdsJson,
          new ScriptUiTreeDecoder.Events() {
            @Override public void invoke(String id, Object value) { invokeHandler(id, value); }
            @Override public boolean invokeKey(String id, org.javity.ui.declarative.UiKeyEvent event) {
              return invokeKeyHandler(id, event);
            }
          });
      document.render(decoded.root());
      handlers.clear();
      for (String id : decoded.handlerIds()) handlers.put(id, id);
    } catch (RuntimeException ex) {
      throw new ScriptUiException("React tree could not be applied: " + ex.getMessage(), ex);
    }
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
    if (!handlers.containsKey(id)) return false;
    String payload = "{\"keyCode\":" + event.keyCode()
        + ",\"key\":" + quote(event.key())
        + ",\"shift\":" + event.shift()
        + ",\"ctrl\":" + event.ctrl()
        + ",\"alt\":" + event.alt() + "}";
    beginCallbackBudget();
    enterGuest();
    try {
      consumeCallback();
      Object result = engine.evaluate("globalThis.JavityReact.dispatchKeyHandler(" + quote(id)
          + ", Object.freeze(JSON.parse(" + quote(payload) + "))) ");
      if (!closed && deferredReload == null) drainMicrotasks();
      return Boolean.TRUE.equals(result);
    } catch (ScriptEngineException ex) {
      throw new ScriptUiException("React key handler failed: " + ex.getMessage(), ex);
    } finally {
      leaveGuest();
      endCallbackBudget();
    }
  }

  private void invokeHandler(String id, Object argument) {
    checkUsable();
    drainDeferredReloadAtBoundary();
    if (!handlers.containsKey(id)) return;
    String payload = jsonPrimitive(argument);
    beginCallbackBudget();
    enterGuest();
    try {
      consumeCallback();
      engine.evaluateDiscardingResult("globalThis.JavityReact.dispatchHandler(" + quote(id)
          + ", JSON.parse(" + quote(payload) + "))");
      if (!closed && deferredReload == null) drainMicrotasks();
    } catch (ScriptEngineException ex) {
      throw new ScriptUiException("React event handler failed: " + ex.getMessage(), ex);
    } finally {
      leaveGuest();
      endCallbackBudget();
    }
  }

  private static String jsonPrimitive(Object value) {
    if (value == null) return "null";
    if (value instanceof String string) return quote(string);
    if (value instanceof Boolean bool) return bool.toString();
    if (value instanceof Number number) {
      double primitive = number.doubleValue();
      if (!Double.isFinite(primitive)) throw new IllegalArgumentException("Event number must be finite");
      return Double.toString(primitive);
    }
    throw new IllegalArgumentException("Unsupported event payload primitive");
  }

  private String readBundle() {
    try (InputStream in = ScriptUiHostSession.class.getResourceAsStream(BUNDLE_RESOURCE)) {
      if (in == null) return "";
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new ScriptUiException("Cannot read bundled Javity React host", ex);
    }
  }

  private static String quote(String s) {
    StringBuilder out = new StringBuilder(s.length() + 2).append('"');
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      switch (ch) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (ch < 0x20) out.append(String.format("\\u%04x", (int) ch));
          else out.append(ch);
        }
      }
    }
    return out.append('"').toString();
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
    if (--guestDepth == 0) {
      if (closing) finishClose();
      else {
        drainGuestSignalCallbackReleases();
        drainDeferredSignals();
      }
    }
  }
}
