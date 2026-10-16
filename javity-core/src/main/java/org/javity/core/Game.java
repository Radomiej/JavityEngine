package org.javity.core;

import com.badlogic.gdx.utils.Disposable;
import java.util.*;
import org.javity.services.TimeManager;

/** Scene-local component indexes and deterministic, single-threaded lifecycle. */
public class Game implements Disposable, AutoCloseable {
  private final List<GameObject> objects = new ArrayList<>();
  private final List<Component> removed = new ArrayList<>();
  private final List<GameSystem> systems = new ArrayList<>();
  private final Map<Class<? extends Component>, Set<GameObject>> componentIndex = new HashMap<>();
  private final Map<String, Set<GameObject>> tagIndex = new HashMap<>();
  private int nextId = 1;
  private long frameIndex;
  private boolean started, disposed, paused, stepping, initializing;
  public float deltaTime, unscaledDeltaTime;
  public double elapsed, unscaledElapsed;

  public void onCreate() {}

  public void onUpdate(float delta) {}

  public void onDestroy() {}

  public <T extends GameSystem> T addSystem(T system) {
    if (started || disposed) throw new IllegalStateException("Register systems before Game.start");
    Objects.requireNonNull(system);
    for (GameSystem existing : systems)
      if (existing.getClass() == system.getClass())
        throw new IllegalArgumentException("Duplicate system type");
    system.attach(this);
    systems.add(system);
    return system;
  }

  public <T extends GameSystem> T getSystem(Class<T> type) {
    Objects.requireNonNull(type);
    for (GameSystem system : systems) if (type.isInstance(system)) return type.cast(system);
    return null;
  }

  public GameObject createObject(String name) {
    if (disposed) throw new IllegalStateException("Game disposed");
    GameObject o = new GameObject(this, nextId++, name);
    objects.add(o);
    return o;
  }

  public List<GameObject> getObjects() {
    return objects.stream().filter(o -> !o.isDestroyed()).toList();
  }

  public List<GameObject> getObjectsWith(Class<?>... types) {
    Objects.requireNonNull(types);
    Set<GameObject> smallest = null;
    List<Set<GameObject>> sets = new ArrayList<>();
    for (Class<?> type : types) {
      if (type == null || !Component.class.isAssignableFrom(type))
        throw new IllegalArgumentException("Expected component type");
      var componentType = type.asSubclass(Component.class);
      Set<GameObject> set =
          componentIndex.computeIfAbsent(
              componentType,
              key -> {
                Set<GameObject> initial = new LinkedHashSet<>();
                for (GameObject o : getObjects()) if (o.hasComponent(key)) initial.add(o);
                return initial;
              });
      sets.add(set);
      if (smallest == null || set.size() < smallest.size()) smallest = set;
    }
    Collection<GameObject> candidates = smallest == null ? getObjects() : smallest;
    return candidates.stream()
        .filter(o -> !o.isDestroyed() && sets.stream().allMatch(s -> s.contains(o)))
        .sorted(Comparator.comparingInt(o -> o.id))
        .toList();
  }

  void changed(GameObject o) {
    componentIndex.forEach(
        (type, set) -> {
          if (!o.isDestroyed() && o.hasComponent(type)) set.add(o);
          else set.remove(o);
        });
  }

  void tagChanged(GameObject o, String tag, boolean added) {
    if (added) tagIndex.computeIfAbsent(tag, key -> new LinkedHashSet<>()).add(o);
    else {
      Set<GameObject> set = tagIndex.get(tag);
      if (set != null) {
        set.remove(o);
        if (set.isEmpty()) tagIndex.remove(tag);
      }
    }
  }

  public List<GameObject> getObjectsWithTag(String tag) {
    return tagIndex.getOrDefault(tag, Set.of()).stream()
        .filter(o -> !o.isDestroyed())
        .sorted(Comparator.comparingInt(o -> o.id))
        .toList();
  }

  void removed(Component c) {
    removed.add(c);
  }

  /** Monotonic step index, including paused and zero-delta frames. */
  public long getFrameIndex() {
    return frameIndex;
  }

  public boolean isDisposed() {
    return disposed;
  }

  public void pause() {
    paused = true;
  }

  public void resume() {
    paused = false;
  }

  public boolean isPaused() {
    return paused;
  }

  /** Whether world simulation is paused or held at a zero time scale. */
  public boolean isWorldFrozen() {
    return paused || appliedTimeScale() == 0;
  }

  private double appliedTimeScale() {
    TimeManager timeManager = getSystem(TimeManager.class);
    return timeManager == null ? 1 : timeManager.getScale();
  }

  public void start() {
    if (started || disposed) throw new IllegalStateException("Game can start only once");
    started = true;
    onCreate();
    for (GameSystem system : List.copyOf(systems))
      if (!disposed && !system.isDisposed()) system.initialize();
    initialize();
    flush();
  }

  private void initialize() {
    if (initializing) throw new IllegalStateException("Recursive component initialization");
    initializing = true;
    try {
      for (int pass = 0; pass < 1000; pass++) {
        boolean pending = false;
        for (GameObject o : getObjects())
          for (Component c : o.getComponents()) {
            if (disposed) return;
            if (!o.isDestroyed() && !c.created && !c.removed) {
              pending = true;
              c.initialize();
            }
          }
        if (!pending) return;
      }
      throw new IllegalStateException("Unbounded component creation in onCreate");
    } finally {
      initializing = false;
    }
  }

  /**
   * Initializes components attached after start without advancing any simulation clocks. This is
   * intended for render-thread adapters that create scene objects between fixed world steps.
   */
  public void initializePendingComponents() {
    if (stepping || initializing)
      throw new IllegalStateException("Cannot initialize components while Game is updating");
    if (!started || disposed) return;
    initialize();
  }

  public void step(float delta) {
    if (!Float.isFinite(delta) || delta < 0) throw new IllegalArgumentException("Invalid delta");
    if (!started || disposed) return;
    if (initializing) throw new IllegalStateException("Cannot step while initializing components");
    if (stepping) throw new IllegalStateException("Recursive game.step");
    stepping = true;
    frameIndex++;
    try {
      unscaledDeltaTime = Math.min(delta, .1f);
      unscaledElapsed += unscaledDeltaTime;
      initialize();
      TimeManager timeManager = getSystem(TimeManager.class);
      if (timeManager != null) timeManager.resolveScaleForStep(unscaledDeltaTime);
      double scaleSnapshot = timeManager == null ? 1 : timeManager.getScale();
      boolean scaleFrozen = scaleSnapshot == 0;
      float worldDelta = (float) (unscaledDeltaTime * scaleSnapshot);
      deltaTime = paused ? 0 : worldDelta;
      List<Component> snapshot = new ArrayList<>();
      List<GameSystem> systemSnapshot = List.copyOf(systems);
      for (GameObject o : getObjects()) snapshot.addAll(o.getComponents());
      for (GameSystem system : systemSnapshot)
        if (!disposed && !system.isDisposed()) system.onUiUpdate(unscaledDeltaTime);
      for (Component c : snapshot)
        if (runnable(c) && c.updateMode == Component.UpdateMode.UI) c.onUpdate(unscaledDeltaTime);
      deltaTime = paused || disposed ? 0 : worldDelta;
      elapsed += deltaTime;
      for (GameSystem system : systemSnapshot)
        if (!paused && !scaleFrozen && !disposed && !system.isDisposed())
          system.onBeforeUpdate(deltaTime);
      if (!paused && !scaleFrozen && !disposed) onUpdate(deltaTime);
      for (Component c : snapshot)
        if (!paused && !scaleFrozen && runnable(c) && c.updateMode == Component.UpdateMode.WORLD)
          c.onUpdate(deltaTime);
      for (GameSystem system : systemSnapshot)
        if (!paused && !scaleFrozen && !disposed && !system.isDisposed())
          system.onAfterUpdate(deltaTime);
      for (Component c : snapshot)
        if (runnable(c) && (c.updateMode == Component.UpdateMode.UI || (!paused && !scaleFrozen)))
          c.onLateUpdate(c.updateMode == Component.UpdateMode.UI ? unscaledDeltaTime : deltaTime);
    } finally {
      try {
        flush();
      } finally {
        stepping = false;
      }
    }
  }

  private boolean runnable(Component c) {
    return !disposed
        && c.created
        && !c.removed
        && c.isEnabled()
        && c.getGameObject().isActive()
        && !c.getGameObject().isDestroyed();
  }

  private void flush() {
    Throwable failure = null;
    while (!removed.isEmpty()) {
      Component c = removed.removeFirst();
      try {
        c.disposeComponent();
      } catch (RuntimeException | Error e) {
        if (failure == null) failure = e;
        else failure.addSuppressed(e);
      }
    }
    for (GameObject o : List.copyOf(objects))
      if (o.isDestroyed()) {
        objects.remove(o);
        try {
          o.cleanup();
        } catch (RuntimeException | Error e) {
          if (failure == null) failure = e;
          else failure.addSuppressed(e);
        }
      }
    if (failure instanceof RuntimeException e) throw e;
    if (failure instanceof Error e) throw e;
  }

  @Override
  public void close() {
    dispose();
  }

  @Override
  public void dispose() {
    if (disposed) return;
    disposed = true;
    for (GameObject o : List.copyOf(objects)) o.destroy();
    Throwable failure = null;
    try {
      flush();
    } catch (RuntimeException | Error e) {
      failure = e;
    }
    List<GameSystem> releaseOrder = new ArrayList<>(systems);
    Collections.reverse(releaseOrder);
    for (GameSystem system : releaseOrder) {
      try {
        system.dispose();
      } catch (RuntimeException | Error e) {
        if (failure == null) failure = e;
        else if (failure != e) failure.addSuppressed(e);
      }
    }
    systems.clear();
    componentIndex.clear();
    tagIndex.clear();
    try {
      onDestroy();
    } catch (RuntimeException | Error e) {
      if (failure == null) failure = e;
      else failure.addSuppressed(e);
    }
    if (failure instanceof RuntimeException e) throw e;
    if (failure instanceof Error e) throw e;
  }
}
