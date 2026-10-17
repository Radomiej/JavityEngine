package org.javity.effects;

import java.util.Objects;
import org.javity.core.Component;

/** Component that plays reusable immutable feedback descriptions. */
public final class FeedbackPlayer extends Component {
  private final Clock clock;
  private TimelineRunner runner;

  public FeedbackPlayer(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
    updateMode = clock == Clock.WORLD ? UpdateMode.WORLD : UpdateMode.UI;
  }

  @Override
  public void onCreate() {
    ensureRunner();
  }

  public PlaybackHandle play(Feedback feedback) {
    Objects.requireNonNull(feedback, "feedback");
    var owner = getGameObject();
    var game = getGame();
    if (owner == null
        || game == null
        || game.isDisposed()
        || owner.isDestroyed()
        || !owner.getComponents().contains(this))
      throw new IllegalStateException("FeedbackPlayer owner is no longer available");
    if (!feedback.targetsOnly(getGameObject()))
      throw new IllegalArgumentException("Feedback targets must belong to the player object");
    TimelineRunner runner = ensureRunner();
    PlaybackHandle[] handle = new PlaybackHandle[1];
    TimelineRunner.Execution[] execution = new TimelineRunner.Execution[1];
    handle[0] =
        new PlaybackHandle(
            mode -> {
              if (execution[0] != null) execution[0].cancel(mode);
            });
    execution[0] =
        runner.add(feedback.newPlayback(), handle[0], runner.system().eligibleFrameForNewPlay());
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
            "FeedbackPlayer requires EffectSystem registered before Game.start");
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
}
