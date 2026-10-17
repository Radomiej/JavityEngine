package org.javity.animation;

import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;
import java.util.Objects;
import org.javity.core.Component;
import org.javity.render.ModelRenderer;

/** Controls one model renderer's single libGDX animation channel. */
public final class ModelAnimator extends Component {
  private ModelRenderer renderer;
  private ModelPlayback current;

  @Override
  public void onCreate() {
    if (getComponent(ModelPoseAnimator.class) != null)
      throw new IllegalStateException("ModelAnimator conflicts with ModelPoseAnimator");
    resolveRenderer();
  }

  @Override
  public void onComponentChange(org.javity.core.ComponentChange change) {
    if (change.component() instanceof ModelPoseAnimator)
      throw new IllegalStateException("ModelAnimator conflicts with ModelPoseAnimator");
  }

  /** Starts an animation from its first frame, replacing any current playback. */
  public ModelPlayback play(String name, PlayMode mode) {
    return play(AnimationTransition.clip(name).mode(mode));
  }

  /** Crossfades from the current animation over a finite, non-negative duration. */
  public ModelPlayback crossFade(String name, double seconds, PlayMode mode) {
    return play(AnimationTransition.clip(name).mode(mode).fade(seconds));
  }

  /** Creates fresh controller and clock state from an immutable reusable transition. */
  public ModelPlayback play(AnimationTransition transition) {
    Objects.requireNonNull(transition);
    requireAttachedAndUsable();
    resolveRenderer();
    if (!isRendererPresent())
      throw new IllegalStateException("ModelAnimator needs an attached ModelRenderer");
    Animation animation = findAnimation(transition.clipName());
    if (animation == null)
      throw new IllegalArgumentException("Unknown model animation: " + transition.clipName());
    if (!Float.isFinite(animation.duration) || animation.duration <= 0)
      throw new IllegalArgumentException("Model animation duration must be finite and positive");
    PlaybackClock clock =
        new PlaybackClock(
            transition.clipName(),
            animation.duration,
            transition.mode(),
            transition.normalizedStart(),
            transition.speed(),
            transition.markers());
    ModelPlayback next = new ModelPlayback(clock, this);
    boolean crossFade = transition.fadeSeconds() > 0 && renderer.animation.current != null;
    startAnimation(
        transition.clipName(),
        transition.mode() == PlayMode.LOOP ? -1 : 1,
        (float) transition.fadeSeconds(),
        crossFade);
    if (renderer.animation.current != null) renderer.animation.current.time = (float) clock.time();
    renderer.animation.update(0);
    if (current != null) current.clock.stop();
    current = next;
    return current;
  }

  private Animation findAnimation(String name) {
    for (Animation candidate : renderer.instance.animations)
      if (candidate.id.equals(name)) return candidate;
    return null;
  }

  public ModelPlayback current() {
    return current;
  }

  /** The exact controller owned and used by the sibling ModelRenderer. */
  public AnimationController controller() {
    requireAttachedAndUsable();
    resolveRenderer();
    return renderer.animation;
  }

  public void stop() {
    if (current != null) current.clock.stop();
    current = null;
    clearController();
  }

  public ModelPlayback restart() {
    if (current == null) return null;
    return restart(current);
  }

  ModelPlayback restart(ModelPlayback playback) {
    if (current != playback
        || !playback.clock.isActive()
        || !isAttachedAndUsable()
        || !isRendererPresent()) return playback;
    String name = playback.animationName();
    PlayMode mode = playback.mode();
    int loops = mode == PlayMode.LOOP ? -1 : 1;
    startAnimation(name, loops, 0, false);
    playback.clock.restart();
    renderer.animation.update(0);
    return playback;
  }

  @Override
  public void onUpdate(float delta) {
    ModelPlayback playback = current;
    if (playback == null || !isAnimatorRunnable()) return;
    if (!isRendererLive()) {
      invalidatePlayback(playback);
      return;
    }
    if (!playback.clock.isPlaying()) return;
    PlaybackClock.Step step = playback.clock.preflight(delta);
    double scaled = delta * playback.speed();
    if (!Double.isFinite(scaled) || scaled > Float.MAX_VALUE)
      throw new IllegalArgumentException("Playback step is too large for the model controller");
    if (!playback.clock.fireStart(() -> current == playback && isOwnedAndRunnable())) {
      if (current == playback && !isOwnedAndRunnable()) invalidatePlayback(playback);
      return;
    }
    if (current != playback || !isOwnedAndRunnable() || !playback.clock.isPlaying()) return;
    if (scaled > 0) renderer.animation.update((float) scaled);
    playback.clock.advance(step, () -> current == playback && isOwnedAndRunnable());
    if (!isRendererLive()) invalidatePlayback(current);
  }

  ModelPlayback seek(ModelPlayback playback, double seconds) {
    if (!Double.isFinite(seconds) || seconds < 0 || seconds > playback.duration())
      throw new IllegalArgumentException("Seek time must be finite and within the clip duration");
    if (current != playback
        || !playback.clock.isActive()
        || !isAttachedAndUsable()
        || !isRendererPresent()) return playback;
    boolean wasCompleted = playback.clock.isCompleted();
    if (wasCompleted) {
      int loops = playback.mode() == PlayMode.LOOP ? -1 : 1;
      startAnimation(playback.animationName(), loops, 0, false);
    }
    playback.clock.seek(seconds);
    if (isRendererLive() && renderer.animation.current != null) {
      renderer.animation.current.time = (float) playback.clock.time();
      renderer.animation.update(0);
    }
    return playback;
  }

  @Override
  protected void onRelease() {
    stop();
  }

  private void resolveRenderer() {
    if (renderer == null) {
      renderer = getComponent(ModelRenderer.class);
      if (renderer == null) throw new IllegalStateException("ModelAnimator needs ModelRenderer");
      if (getGameObject().getComponents(ModelAnimator.class).size() > 1)
        throw new IllegalStateException("Only one ModelAnimator may own a ModelRenderer");
    }
  }

  private void requireAttachedAndUsable() {
    if (!isAttachedAndUsable())
      throw new IllegalStateException("ModelAnimator must be attached to a live object");
    if (getComponent(ModelPoseAnimator.class) != null)
      throw new IllegalStateException("ModelAnimator conflicts with ModelPoseAnimator");
  }

  private boolean isAttachedAndUsable() {
    return getGameObject() != null
        && getGame() != null
        && !getGame().isDisposed()
        && !getGameObject().isDestroyed()
        && getGameObject().getComponents(ModelAnimator.class).contains(this)
        && (renderer == null
            || (renderer.getGameObject() == getGameObject()
                && getGameObject().getComponents(ModelRenderer.class).contains(renderer)));
  }

  private boolean isAnimatorRunnable() {
    return isInitialized()
        && isEnabled()
        && getGameObject().isActive()
        && !getGameObject().isDestroyed()
        && !getGame().isDisposed();
  }

  private boolean isRendererPresent() {
    return renderer != null
        && renderer.getGameObject() == getGameObject()
        && getGameObject().getComponents(ModelRenderer.class).contains(renderer);
  }

  private boolean isRendererLive() {
    return isRendererPresent() && renderer.isInitialized();
  }

  private boolean isOwnedAndRunnable() {
    return isAnimatorRunnable() && isRendererLive();
  }

  private void invalidatePlayback(ModelPlayback playback) {
    if (playback != null) playback.clock.stop();
    if (current == playback || playback == null) {
      current = null;
      clearController();
    }
  }

  private void clearController() {
    if (renderer == null) return;
    renderer.animation.current = null;
    renderer.animation.previous = null;
    renderer.animation.queued = null;
    renderer.animation.transitionCurrentTime = 0;
    renderer.animation.transitionTargetTime = 0;
  }

  private void startAnimation(String name, int loops, float fade, boolean crossFade) {
    AnimationController controller = renderer.animation;
    boolean allowSame = controller.allowSameAnimation;
    controller.allowSameAnimation = true;
    try {
      if (crossFade) controller.animate(name, loops, 1, null, fade);
      else controller.setAnimation(name, loops);
    } finally {
      controller.allowSameAnimation = allowSame;
    }
  }
}
