package org.javity.audio;

/** Independent temporary reduction applied to one bus. */
public final class DuckHandle implements AutoCloseable {
  final SoundManager manager;
  final SoundManager.Duck duck;

  DuckHandle(SoundManager manager, SoundManager.Duck duck) {
    this.manager = manager;
    this.duck = duck;
  }

  public boolean isActive() {
    return manager.isDuckActive(duck);
  }

  @Override
  public void close() {
    manager.cancelDuck(duck);
  }
}
