package org.javity.render.lighting;

import com.badlogic.gdx.graphics.Color;
import org.javity.core.Component;
import org.javity.core.Transform3D;

/** Scene light. Values are copied; Transform3D supplies world position and local -Z direction. */
public abstract class Light3D extends Component {
  private final Color color = new Color(1, 1, 1, 1);
  private float intensity = 1;

  public final void setColor(float r, float g, float b) {
    setRgb(color, r, g, b);
  }

  public final Color color(Color out) {
    return out.set(color);
  }

  public final float intensity() {
    return intensity;
  }

  public final void setIntensity(float value) {
    nonnegative(value, "intensity");
    intensity = value;
  }

  @Override
  public void onCreate() {
    requireComponent(Transform3D.class);
  }

  // Color.set(float...) and Color.mul clamp to [0,1]; lighting colors may exceed one.
  static void setRgb(Color target, float r, float g, float b) {
    nonnegative(r, "red");
    nonnegative(g, "green");
    nonnegative(b, "blue");
    target.r = r;
    target.g = g;
    target.b = b;
    target.a = 1;
  }

  static void nonnegative(float value, String name) {
    if (!Float.isFinite(value) || value < 0)
      throw new IllegalArgumentException(name + " must be finite and nonnegative");
  }
}
