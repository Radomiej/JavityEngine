package org.javity.ecs.artemis;

import com.artemis.World;
import java.util.Objects;
import org.javity.core.GameSystem;

/** Owns and schedules one already initialized native Artemis world for a game scene. */
public class ArtemisSystem extends GameSystem {
  private final World world;
  private final EcsPhase phase;
  private final Thread ownerThread;
  private boolean processing;
  private boolean releasePending;
  private boolean worldReleased;

  public ArtemisSystem(World world) {
    this(world, EcsPhase.BEFORE_WORLD);
  }

  public ArtemisSystem(World world, EcsPhase phase) {
    this.world = Objects.requireNonNull(world, "world");
    this.phase = Objects.requireNonNull(phase, "phase");
    ownerThread = Thread.currentThread();
  }

  /** Returns the native world while it is live. Register this system before using it with a Game. */
  public World world() {
    checkOwnerThread();
    if (isDisposed() || worldReleased) throw new IllegalStateException("Artemis world is closed");
    return world;
  }

  public EcsPhase phase() {
    return phase;
  }

  @Override
  public void onBeforeUpdate(float delta) {
    if (phase == EcsPhase.BEFORE_WORLD) process(delta);
  }

  @Override
  public void onAfterUpdate(float delta) {
    if (phase == EcsPhase.AFTER_WORLD) process(delta);
  }

  @Override
  public void onUiUpdate(float unscaledDelta) {
    if (phase == EcsPhase.UI) process(unscaledDelta);
  }

  private void process(float delta) {
    checkOwnerThread();
    if (isDisposed() || worldReleased) return;
    if (processing) throw new IllegalStateException("Recursive Artemis world process");
    processing = true;
    Throwable failure = null;
    try {
      world.setDelta(delta);
      world.process();
    } catch (RuntimeException | Error e) {
      failure = e;
    } finally {
      processing = false;
      if (releasePending) {
        try {
          releaseWorld();
        } catch (RuntimeException | Error e) {
          if (failure == null) failure = e;
          else failure.addSuppressed(e);
        }
      }
    }
    if (failure instanceof RuntimeException e) throw e;
    if (failure instanceof Error e) throw e;
  }

  @Override
  protected void validateDispose() {
    checkOwnerThread();
  }

  @Override
  protected void onRelease() {
    if (worldReleased) return;
    if (processing) {
      releasePending = true;
      return;
    }
    releaseWorld();
  }

  private void releaseWorld() {
    if (worldReleased) return;
    worldReleased = true;
    releasePending = false;
    world.dispose();
  }

  private void checkOwnerThread() {
    if (Thread.currentThread() != ownerThread)
      throw new IllegalStateException("ArtemisSystem is confined to its owner thread");
  }
}
