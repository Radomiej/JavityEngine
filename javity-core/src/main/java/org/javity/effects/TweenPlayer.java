package org.javity.effects;

import java.util.Objects;
import org.javity.core.Component;

/** Component that owns and advances its own tweens. Register an EffectSystem before Game.start. */
public final class TweenPlayer extends Component {
  private final Clock clock;
  private TimelineRunner runner;

  public TweenPlayer(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
    updateMode = clock == Clock.WORLD ? UpdateMode.WORLD : UpdateMode.UI;
  }

  @Override
  public void onCreate() {
    ensureRunner();
  }

  public TweenHandle to(FloatTarget target, float destination, double duration, Ease ease) {
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(ease, "ease");
    finite(destination);
    TimelineRunner.validateDuration(duration);
    if (target.gameObject() != getGameObject())
      throw new IllegalArgumentException("Tween target must belong to the player object");
    TimelineRunner runner = ensureRunner();
    TweenHandle[] handle = new TweenHandle[1];
    TimelineRunner.Execution[] execution = new TimelineRunner.Execution[1];
    handle[0] =
        new TweenHandle(
            mode -> {
              if (execution[0] != null) execution[0].cancel(mode);
            });
    execution[0] =
        runner.add(
            TimelineRunner.tween(target, destination, duration, ease),
            handle[0],
            runner.system().eligibleFrameForNewPlay());
    return handle[0];
  }

  @Override
  public void onUpdate(float delta) {
    if (runner != null) runner.update(delta);
  }

  @Override
  protected void onRelease() {
    if (runner != null) runner.release();
  }

  private TimelineRunner ensureRunner() {
    if (runner == null) {
      EffectSystem system = getGame() == null ? null : getGame().getSystem(EffectSystem.class);
      if (system == null)
        throw new IllegalStateException(
            "TweenPlayer requires EffectSystem registered before Game.start");
      runner = new TimelineRunner(system, this::isEffectOwnerValid);
    }
    return runner;
  }

  private boolean isEffectOwnerValid() {
    var owner = getGameObject();
    var game = getGame();
    return owner != null
        && game != null
        && !game.isDisposed()
        && !owner.isDestroyed()
        && owner.isActive()
        && isEnabled()
        && isInitialized()
        && owner.getComponents().contains(this);
  }

  private static void finite(float value) {
    if (!Float.isFinite(value)) throw new IllegalArgumentException("Destination must be finite");
  }
}
