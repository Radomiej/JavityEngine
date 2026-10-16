package org.javity.ui.inspector;

import java.util.Objects;
import java.util.function.Supplier;
import org.javity.core.GameObject;
import org.javity.effects.Clock;
import org.javity.presentation.PresentationEffects3D;

/** UI-thread playback ownership; Game alone advances the selected manager's clock. */
final class FeelPreviewSession implements AutoCloseable {
  private final Supplier<GameObject> selection;
  private GameObject target;
  private Clock clock = Clock.WORLD;
  private PresentationEffects3D.Playback owned;
  private boolean closed;
  private String status = "Select an object with PresentationEffects3D (WORLD)";

  FeelPreviewSession(Supplier<GameObject> selection) {
    this.selection = Objects.requireNonNull(selection);
  }

  void update() {
    if (closed) return;
    GameObject next = selection.get();
    if (next != null && (next.isDestroyed() || !next.isActive() || next.game.isDisposed()
        || !next.game.getObjects().contains(next))) next = null;
    if (next != target) {
      stop(); target = next;
      status = next == null ? "Target unavailable: select a live active object"
          : "Ready: " + next.name + " / " + clock;
    }
    if (owned != null && !owned.isActive()) { owned = null; status = "Preview ended / provider unavailable"; }
    if (target == null) status = "Target unavailable: select a live active object";
    else if (manager() == null) status = "Clock unavailable: needs enabled initialized PresentationEffects3D(" + clock + ") on " + target.name;
  }

  void setClock(Clock value) {
    Objects.requireNonNull(value);
    if (closed || clock == value) return;
    stop(); clock = value; update();
    status = "Clock " + clock + ": requires same-owner PresentationEffects3D";
  }

  Clock clock() { return clock; }

  private PresentationEffects3D manager() {
    if (target == null) return null;
    for (var manager : target.getComponents(PresentationEffects3D.class))
      if (manager.clock() == clock && manager.isEnabled() && manager.isInitialized()) return manager;
    return null;
  }

  boolean play(PresentationEffects3D.Preset preset) {
    Objects.requireNonNull(preset);
    if (closed) return false;
    update();
    PresentationEffects3D manager = manager();
    if (manager == null) {
      status = target == null ? "Target unavailable: select a live active object"
          : "Clock unavailable: needs enabled initialized PresentationEffects3D(" + clock + ") on " + target.name;
      return false;
    }
    stop();
    try {
      owned = manager.play(preset);
      status = "Playing " + preset.kind() + " / " + preset.surface() + " / " + clock;
      return true;
    } catch (IllegalArgumentException | IllegalStateException failure) {
      status = "Preview unavailable: " + failure.getMessage();
      return false;
    }
  }

  void pause() {
    update(); if (owned != null) { owned.pause(); status = "Paused / " + clock; }
  }
  void resume() {
    update(); if (owned != null) { owned.resume(); status = "Playing / " + clock; }
  }
  void stop() {
    if (owned != null) owned.cancel();
    owned = null; if (!closed) status = "Stopped (owned preview only)";
  }
  boolean isPlaying() { update(); return owned != null; }
  int activeCount() { update(); var manager = manager(); return manager == null ? 0 : manager.activeCount(); }
  String status() { return status; }
  @Override public void close() { if (!closed) { stop(); closed = true; target = null; status = "Closed"; } }
}
