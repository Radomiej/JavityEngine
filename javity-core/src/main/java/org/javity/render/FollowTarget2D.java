package org.javity.render;

import com.badlogic.gdx.math.Vector2;
import java.util.Objects;
import org.javity.core.*;

/** Attach to a camera object. Updates after gameplay/physics; no static main camera. */
public final class FollowTarget2D extends Component {
  private GameObject target;
  private float smoothing;
  public final Vector2 offset = new Vector2();

  public FollowTarget2D(GameObject target) {
    this.target = Objects.requireNonNull(target);
  }

  public GameObject getTarget() {
    return target;
  }

  public void setTarget(GameObject target) {
    Objects.requireNonNull(target);
    if (getGame() != null && target.game != getGame())
      throw new IllegalArgumentException("Target belongs to another Game");
    this.target = target;
  }

  /** 0 means snap; otherwise exponential response rate per second. */
  public void setSmoothing(float smoothing) {
    if (!Float.isFinite(smoothing) || smoothing < 0)
      throw new IllegalArgumentException("Invalid smoothing");
    this.smoothing = smoothing;
  }

  public float getSmoothing() {
    return smoothing;
  }

  public void onCreate() {
    setTarget(target);
  }

  public void onLateUpdate(float delta) {
    if (target.isDestroyed() || !target.isActive()) return;
    float alpha = smoothing == 0 ? 1 : (float) -Math.expm1(-smoothing * delta);
    Vector2 position = getTransform().position;
    position.x += (target.transform.position.x + offset.x - position.x) * alpha;
    position.y += (target.transform.position.y + offset.y - position.y) * alpha;
  }
}
