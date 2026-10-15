package org.javity.animation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.javity.core.Component;
import org.javity.render.SpriteAnimator;
import org.javity.render.SpriteRenderer2D;

/** Plays named sprite clips using WORLD time and caller-owned texture regions. */
public final class SpriteAnimationPlayer extends Component {
  private final Map<String, SpriteClip> clips = new LinkedHashMap<>();
  private SpriteRenderer2D sprite;
  private SpritePlayback current;

  public SpriteAnimationPlayer register(SpriteClip clip) {
    Objects.requireNonNull(clip);
    if (clips.putIfAbsent(clip.name(), clip) != null)
      throw new IllegalArgumentException("Duplicate sprite clip: " + clip.name());
    return this;
  }

  @Override
  public void onCreate() {
    if (getComponent(SpriteAnimator.class) != null)
      throw new IllegalStateException("SpriteAnimationPlayer conflicts with SpriteAnimator");
    if (getGameObject().getComponents(SpriteAnimationPlayer.class).size() > 1)
      throw new IllegalStateException("Only one SpriteAnimationPlayer may own a sprite");
    resolveSprite();
  }

  private void resolveSprite() {
    sprite = getComponent(SpriteRenderer2D.class);
    if (sprite == null)
      throw new IllegalStateException("SpriteAnimationPlayer needs SpriteRenderer2D");
  }

  private void invalidatePlayback(SpritePlayback playback) {
    playback.clock.stop();
    if (current == playback) current = null;
  }

  public SpritePlayback play(String name, PlayMode mode) {
    return play(AnimationTransition.clip(name).mode(mode));
  }

  /** Creates fresh playback state from an immutable reusable transition. */
  public SpritePlayback play(AnimationTransition transition) {
    Objects.requireNonNull(transition);
    requireAttachedAndUsable();
    SpriteClip clip = clips.get(transition.clipName());
    if (clip == null)
      throw new IllegalArgumentException("Unknown sprite clip: " + transition.clipName());
    if (transition.fadeSeconds() != 0)
      throw new IllegalArgumentException("Sprite playback does not support crossfade");
    resolveSprite();
    PlaybackClock clock =
        new PlaybackClock(
            transition.clipName(),
            clip.duration(),
            transition.mode(),
            transition.normalizedStart(),
            transition.speed(),
            transition.markers());
    SpritePlayback next = new SpritePlayback(clock, this);
    if (current != null) current.clock.stop();
    current = next;
    applyFrame(clip, clock.time());
    return current;
  }

  public SpritePlayback current() {
    return current;
  }

  public void stop() {
    if (current != null) current.clock.stop();
    current = null;
  }

  public SpritePlayback restart() {
    if (current == null) return null;
    return restart(current);
  }

  SpritePlayback restart(SpritePlayback playback) {
    if (current != playback || !playback.clock.isActive() || !isAttachedAndUsable())
      return playback;
    SpriteClip clip = clips.get(playback.clipName());
    playback.clock.restart();
    applyFrame(clip, 0);
    return playback;
  }

  @Override
  public void onUpdate(float delta) {
    SpritePlayback playback = current;
    if (playback == null || !isOwnedAndRunnable()) return;
    playback.clock.preflight(delta);
    if (!playback.clock.fireStart(() -> current == playback && isOwnedAndRunnable())) {
      if (current == playback && !isOwnedAndRunnable()) invalidatePlayback(playback);
      return;
    }
    if (current != playback || !isOwnedAndRunnable()) return;
    PlaybackClock.Step step = playback.clock.preflight(delta);
    SpriteClip clip = clips.get(playback.clipName());
    applyFrame(clip, step.nextElapsed());
    playback.clock.advance(step, () -> current == playback && isOwnedAndRunnable());
  }

  SpritePlayback seek(SpritePlayback playback, double seconds) {
    if (!Double.isFinite(seconds) || seconds < 0 || seconds > playback.duration())
      throw new IllegalArgumentException("Seek time must be finite and within the clip duration");
    if (current != playback || !playback.clock.isActive() || !isAttachedAndUsable())
      return playback;
    playback.clock.seek(seconds);
    SpriteClip clip = clips.get(playback.clipName());
    if (clip != null && isOwnedAndRunnable()) applyFrame(clip, playback.clock.time());
    return playback;
  }

  private void applyFrame(SpriteClip clip, double time) {
    double framePosition = time / clip.secondsPerFrame();
    int frame = (int) Math.floor(framePosition + Math.ulp((float) framePosition));
    if (frame >= clip.frames().size()) frame = clip.frames().size() - 1;
    sprite.region = clip.frames().get(Math.max(0, frame));
  }

  @Override
  protected void onRelease() {
    stop();
  }

  private void requireAttachedAndUsable() {
    if (!isAttachedAndUsable())
      throw new IllegalStateException("SpriteAnimationPlayer must be attached to a live object");
  }

  private boolean isAttachedAndUsable() {
    return getGameObject() != null
        && getGame() != null
        && !getGame().isDisposed()
        && !getGameObject().isDestroyed()
        && getGameObject().getComponents(SpriteAnimationPlayer.class).contains(this)
        && (sprite == null
            || (sprite.getGameObject() == getGameObject()
                && getGameObject().getComponents(SpriteRenderer2D.class).contains(sprite)));
  }

  private boolean isOwnedAndRunnable() {
    return isInitialized()
        && isEnabled()
        && getGameObject().isActive()
        && !getGameObject().isDestroyed()
        && !getGame().isDisposed()
        && sprite != null
        && sprite.isInitialized()
        && sprite.getGameObject() == getGameObject()
        && getGameObject().getComponents(SpriteRenderer2D.class).contains(sprite);
  }
}
