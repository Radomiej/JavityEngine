package org.javity.render;

import java.util.Objects;
import org.javity.core.Component;
import org.javity.effects.Clock;

/** Advances one FLOAT uniform on a borrowed shape material using scene WORLD or UI time. */
public final class ShapeShaderAnimator extends Component {
  private static final float MAX_RATE = 10_000f;
  private final ShapeMaterial2D material;
  private final String uniform;
  private final float rate;

  /** Creates an animator without taking ownership of or attaching the material. */
  public ShapeShaderAnimator(ShapeMaterial2D material, String uniform, float rate, Clock clock) {
    this.material = Objects.requireNonNull(material, "material");
    this.uniform = Objects.requireNonNull(uniform, "uniform");
    if (material.uniformType(uniform) != ShapeMaterial2D.UniformType.FLOAT)
      throw new IllegalArgumentException("Animated material uniform must be FLOAT: " + uniform);
    if (!Float.isFinite(rate) || Math.abs(rate) > MAX_RATE)
      throw new IllegalArgumentException("Animator rate must be finite and in [-10000, 10000]");
    Objects.requireNonNull(clock, "clock");
    this.rate = rate;
    updateMode = clock == Clock.UI ? UpdateMode.UI : UpdateMode.WORLD;
  }

  public ShapeMaterial2D material() {
    return material;
  }

  @Override
  public void onUpdate(float delta) {
    if (!Float.isFinite(delta) || delta < 0)
      throw new IllegalStateException("Shape shader animator received invalid delta");
    double next = material.getFloat(uniform) + (double) rate * delta;
    if (!Double.isFinite(next) || Math.abs(next) > Float.MAX_VALUE)
      throw new IllegalStateException("Shape shader animator overflow for uniform " + uniform);
    material.setFloat(uniform, (float) next);
  }
}
