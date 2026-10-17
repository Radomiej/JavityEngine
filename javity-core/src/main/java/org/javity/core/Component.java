package org.javity.core;

import java.util.List;

/** Gameplay behavior; independent of Artemis and rendering backends. */
public abstract class Component {
  public enum UpdateMode {
    WORLD,
    UI
  }

  public UpdateMode updateMode = UpdateMode.WORLD;
  private GameObject owner;
  private boolean enabled = true;
  private long enabledRevision;
  boolean created, removed, destroyed;

  public final GameObject getGameObject() {
    return owner;
  }

  public final Game getGame() {
    return owner == null ? null : owner.game;
  }

  public final Transform2D getTransform() {
    return owner.transform;
  }

  public final boolean isEnabled() {
    return enabled;
  }

  public final boolean isInitialized() {
    return created && !removed && !destroyed;
  }

  public final void setEnabled(boolean enabled) {
    if (this.enabled == enabled) return;
    if (enabledRevision == Long.MAX_VALUE)
      throw new IllegalStateException("Component enabled revision exhausted");
    this.enabled = enabled;
    enabledRevision++;
  }

  /** Monotonically changes whenever enabled state actually transitions. */
  public final long enabledRevision() {
    return enabledRevision;
  }

  public final <T extends Component> T getComponent(Class<T> type) {
    return owner.getComponent(type);
  }

  public final List<Component> getComponents() {
    return owner.getComponents();
  }

  public final <T extends Component> List<T> getComponents(Class<T> type) {
    return owner.getComponents(type);
  }

  public final <T extends Component> T requireComponent(Class<T> type) {
    return owner.requireComponent(type);
  }

  public final boolean hasComponent(Class<? extends Component> type) {
    return owner.hasComponent(type);
  }

  public final int removeComponents(Class<? extends Component> type) {
    return owner.removeComponents(type);
  }

  public final boolean removeComponent(Component component) {
    return owner.removeComponent(component);
  }

  public final boolean removeComponent(Class<? extends Component> type) {
    return owner.removeComponent(type);
  }

  public void onCreate() {}

  public void onUpdate(float delta) {}

  public void onLateUpdate(float delta) {}

  public void onDestroy() {}

  /** Release constructor-owned resources even when removed before initialization. */
  protected void onRelease() {}

  public void onComponentChange(ComponentChange change) {}

  final void attach(GameObject owner) {
    if (this.owner != null) throw new IllegalArgumentException("Component already attached");
    this.owner = owner;
  }

  final void initialize() {
    if (!created && !removed) {
      created = true;
      onCreate();
    }
  }

  final void disposeComponent() {
    if (!destroyed) {
      destroyed = true;
      Throwable failure = null;
      try {
        if (created) onDestroy();
      } catch (RuntimeException | Error e) {
        failure = e;
      }
      try {
        onRelease();
      } catch (RuntimeException | Error e) {
        if (failure == null) failure = e;
        else failure.addSuppressed(e);
      }
      if (failure instanceof RuntimeException e) throw e;
      if (failure instanceof Error e) throw e;
    }
  }
}
