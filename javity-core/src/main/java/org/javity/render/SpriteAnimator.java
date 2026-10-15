package org.javity.render;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.List;
import org.javity.animation.SpriteAnimationPlayer;
import org.javity.core.Component;

/** Reuses atlas regions; time is scene WORLD time, so pause stops animation. */
public final class SpriteAnimator extends Component {
  private final List<TextureRegion> frames;
  private final float secondsPerFrame;
  private final boolean loop;
  private double time;
  private SpriteRenderer2D sprite;

  public SpriteAnimator(List<TextureRegion> frames, float secondsPerFrame, boolean loop) {
    this.frames = List.copyOf(frames);
    if (frames.isEmpty() || !Float.isFinite(secondsPerFrame) || secondsPerFrame <= 0)
      throw new IllegalArgumentException("Frames and positive duration required");
    this.secondsPerFrame = secondsPerFrame;
    this.loop = loop;
  }

  public void onCreate() {
    if (getComponent(SpriteAnimationPlayer.class) != null)
      throw new IllegalStateException("SpriteAnimator conflicts with SpriteAnimationPlayer");
    sprite = getComponent(SpriteRenderer2D.class);
    if (sprite == null) throw new IllegalStateException("SpriteAnimator needs SpriteRenderer2D");
    sprite.region = frames.getFirst();
  }

  public void onUpdate(float delta) {
    time += delta;
    int frame =
        loop
            ? (int) ((time / secondsPerFrame) % frames.size())
            : (int) Math.min(frames.size() - 1, time / secondsPerFrame);
    sprite.region = frames.get(frame);
  }

  public void restart() {
    time = 0;
    if (sprite != null) sprite.region = frames.getFirst();
  }
}
