package org.javity.combat;

import com.badlogic.gdx.math.Vector2;
import org.javity.core.Component;

/**
 * WORLD-clock melee timing and cone geometry. Caller owns damage, targeting and obstruction rules.
 */
public final class MeleeAttack2D extends Component {
  private final float range, cooldown, duration, halfAngle;
  private float cooldownLeft, activeLeft;
  private final Vector2 direction = new Vector2(1, 0);

  public MeleeAttack2D(float range, float cooldown, float duration, float coneDegrees) {
    if (!Float.isFinite(range)
        || range <= 0
        || !Float.isFinite(cooldown)
        || cooldown <= 0
        || !Float.isFinite(duration)
        || duration <= 0
        || duration > cooldown
        || !Float.isFinite(coneDegrees)
        || coneDegrees <= 0
        || coneDegrees > 360) throw new IllegalArgumentException("Invalid melee configuration");
    this.range = range;
    this.cooldown = cooldown;
    this.duration = duration;
    this.halfAngle = (float) Math.toRadians(coneDegrees / 2);
  }

  public boolean tryBegin(float x, float y) {
    if (!isInitialized()
        || !isEnabled()
        || !getGameObject().isActive()
        || getGame().isPaused()
        || cooldownLeft > 0) return false;
    if (!Float.isFinite(x) || !Float.isFinite(y) || (x == 0 && y == 0)) return false;
    double length = Math.hypot((double) x, (double) y);
    direction.set((float) (x / length), (float) (y / length));
    cooldownLeft = cooldown;
    activeLeft = duration;
    return true;
  }

  public float activeSeconds() {
    return activeLeft;
  }

  public float cooldownSeconds() {
    return cooldownLeft;
  }

  public float directionX() {
    return direction.x;
  }

  public float directionY() {
    return direction.y;
  }

  public boolean intersects(float x, float y, float radius) {
    if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(radius) || radius < 0)
      throw new IllegalArgumentException("Invalid target circle");
    if (!isInitialized() || !isEnabled() || !getGameObject().isActive() || activeLeft <= 0)
      return false;
    float dx = x - getTransform().position.x, dy = y - getTransform().position.y;
    double distance = Math.hypot(dx, dy);
    if (distance > range + (double) radius) return false;
    if (distance <= radius) return true;
    double angle =
        Math.acos(Math.max(-1, Math.min(1, (dx * direction.x + dy * direction.y) / distance)));
    return angle <= halfAngle + Math.asin(Math.min(1, radius / distance));
  }

  @Override
  public void onUpdate(float delta) {
    cooldownLeft = Math.max(0, cooldownLeft - delta);
    activeLeft = Math.max(0, activeLeft - delta);
  }

  public void cancel() {
    cooldownLeft = 0;
    activeLeft = 0;
  }
}
