package org.javity.core;

import java.util.*;
import java.util.function.Consumer;

public final class GameObject {
  public final int id;
  public final Game game;
  public final Transform2D transform = new Transform2D();
  public String name;
  private boolean active = true, destroyed;
  private long activeRevision;
  private final List<Component> components = new ArrayList<>();
  private final Set<String> tags = new LinkedHashSet<>();
  private final List<ChangeListener> listeners = new ArrayList<>();

  private static final class ChangeListener {
    final Consumer<ComponentChange> callback;
    boolean subscribed = true;

    ChangeListener(Consumer<ComponentChange> callback) {
      this.callback = callback;
    }
  }

  GameObject(Game game, int id, String name) {
    this.game = game;
    this.id = id;
    this.name = Objects.requireNonNull(name);
  }

  public GameObject setPosition(float x, float y) {
    if (!Float.isFinite(x) || !Float.isFinite(y))
      throw new IllegalArgumentException("Invalid position");
    transform.position.set(x, y);
    return this;
  }

  public boolean isActive() {
    return active;
  }

  public boolean isDestroyed() {
    return destroyed;
  }

  public void setActive(boolean active) {
    if (this.active == active) return;
    if (activeRevision == Long.MAX_VALUE)
      throw new IllegalStateException("GameObject active revision exhausted");
    this.active = active;
    activeRevision++;
    Throwable failure = null;
    for (Component component : getComponents()) {
      try {
        component.activationChanged();
      } catch (RuntimeException | Error e) {
        if (failure == null) failure = e;
        else if (failure != e) failure.addSuppressed(e);
      }
    }
    if (failure instanceof RuntimeException e) throw e;
    if (failure instanceof Error e) throw e;
  }

  /** Monotonically changes whenever active state actually transitions. */
  public long activeRevision() {
    return activeRevision;
  }

  /**
   * Attaches the component and updates scene indexes before notifying observers. Notification
   * failures are rethrown after delivery to the remaining eligible observers; they do not roll back
   * the addition. Reentrant callbacks may make further changes before this method returns or
   * throws.
   */
  public <T extends Component> T addComponent(T component) {
    if (destroyed || game.isDisposed()) throw new IllegalStateException("Object destroyed");
    Objects.requireNonNull(component).attach(this);
    components.add(component);
    game.changed(this);
    notifyChange(new ComponentChange(ComponentChange.Type.ADDED, this, component));
    return component;
  }

  public <T extends Component> T getComponent(Class<T> type) {
    Objects.requireNonNull(type);
    for (Component c : components) if (type.isInstance(c)) return type.cast(c);
    return null;
  }

  public boolean hasComponent(Class<? extends Component> type) {
    return getComponent(type) != null;
  }

  /**
   * Returns whether this exact component instance is attached, without copying the component list.
   */
  public boolean containsComponent(Component component) {
    if (component == null) return false;
    for (int i = 0; i < components.size(); i++) if (components.get(i) == component) return true;
    return false;
  }

  public <T extends Component> T requireComponent(Class<T> type) {
    T component = getComponent(type);
    if (component == null)
      throw new IllegalStateException(name + ": missing " + type.getSimpleName());
    return component;
  }

  public List<Component> getComponents() {
    return List.copyOf(components);
  }

  public <T extends Component> List<T> getComponents(Class<T> type) {
    Objects.requireNonNull(type);
    List<T> result = new ArrayList<>();
    for (Component c : components) if (type.isInstance(c)) result.add(type.cast(c));
    return List.copyOf(result);
  }

  /**
   * Removes this exact attached instance; component equality is not an ownership relation. Removal
   * and index updates are committed before notification, and cleanup is queued even if an observer
   * throws. Notification failures do not roll back the removal.
   */
  public boolean removeComponent(Component c) {
    int index = -1;
    for (int i = 0; i < components.size(); i++) {
      if (components.get(i) == c) {
        index = i;
        break;
      }
    }
    if (index < 0) return false;
    components.remove(index);
    c.removed = true;
    game.changed(this);
    game.removed(c);
    notifyChange(new ComponentChange(ComponentChange.Type.REMOVED, this, c));
    return true;
  }

  public boolean removeComponent(Class<? extends Component> type) {
    Component c = getComponent(type);
    return c != null && removeComponent(c);
  }

  public int removeComponents(Class<? extends Component> type) {
    int count = 0;
    for (Component c : getComponents(type)) if (removeComponent(c)) count++;
    return count;
  }

  /**
   * Registers an observer and returns its idempotent unsubscribe action. Each change snapshots
   * registrations and initialized components before delivery, then calls subscribed observers in
   * registration order followed by still-attached components in attachment order. Newly registered,
   * attached or initialized recipients wait for a subsequent change, including a nested change.
   * Unsubscribed or removed recipients and recipients whose object is destroyed or game is closed
   * are skipped. Active/enabled state does not affect structural notifications. Runtime exceptions
   * and errors are collected, preserving the first failure and attaching other failures as
   * suppressed, then rethrown after delivery completes.
   */
  public Runnable onComponentChange(Consumer<ComponentChange> listener) {
    ChangeListener registration = new ChangeListener(Objects.requireNonNull(listener));
    listeners.add(registration);
    return () -> {
      registration.subscribed = false;
      listeners.remove(registration);
    };
  }

  private void notifyChange(ComponentChange change) {
    List<ChangeListener> listenerSnapshot = List.copyOf(listeners);
    List<Component> componentSnapshot = new ArrayList<>(components.size());
    for (Component component : components)
      if (component.created && !component.removed && !component.destroyed)
        componentSnapshot.add(component);
    Throwable failure = null;
    for (ChangeListener listener : listenerSnapshot) {
      if (destroyed || game.isDisposed()) break;
      if (!listener.subscribed) continue;
      try {
        listener.callback.accept(change);
      } catch (RuntimeException | Error e) {
        if (failure == null) failure = e;
        else if (failure != e) failure.addSuppressed(e);
      }
    }
    for (Component component : componentSnapshot) {
      if (destroyed || game.isDisposed()) break;
      if (!component.created || component.removed || component.destroyed) continue;
      try {
        component.onComponentChange(change);
      } catch (RuntimeException | Error e) {
        if (failure == null) failure = e;
        else if (failure != e) failure.addSuppressed(e);
      }
    }
    if (failure instanceof RuntimeException e) throw e;
    if (failure instanceof Error e) throw e;
  }

  public GameObject addTag(String tag) {
    if (destroyed) throw new IllegalStateException("Object destroyed");
    if (tag == null || tag.isBlank()) throw new IllegalArgumentException("Empty tag");
    if (tags.add(tag)) game.tagChanged(this, tag, true);
    return this;
  }

  public boolean removeTag(String tag) {
    boolean removed = tags.remove(tag);
    if (removed) game.tagChanged(this, tag, false);
    return removed;
  }

  public boolean hasTag(String tag) {
    return tags.contains(tag);
  }

  public Set<String> getTags() {
    return Set.copyOf(tags);
  }

  public void destroy() {
    if (destroyed) return;
    destroyed = true;
    game.changed(this);
    for (String tag : tags) game.tagChanged(this, tag, false);
  }

  void cleanup() {
    Throwable failure = null;
    for (Component c : getComponents()) {
      c.removed = true;
      try {
        c.disposeComponent();
      } catch (RuntimeException | Error e) {
        if (failure == null) failure = e;
        else if (failure != e) failure.addSuppressed(e);
      }
    }
    components.clear();
    listeners.clear();
    tags.clear();
    if (failure instanceof RuntimeException e) throw e;
    if (failure instanceof Error e) throw e;
  }
}
