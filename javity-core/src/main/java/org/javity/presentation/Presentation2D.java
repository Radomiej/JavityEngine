package org.javity.presentation;

import org.javity.core.Component;

/**
 * Additive, visual-only 2D pose contributions. Gameplay and physics continue to use Transform2D.
 * The caller-controlled Transform2D.visualOffset remains an extra renderer-only translation.
 */
public final class Presentation2D extends Component {
  static final int CONTRIBUTIONS = 64;

  private final double[] x = new double[CONTRIBUTIONS];
  private final double[] y = new double[CONTRIBUTIONS];
  private final double[] rotation = new double[CONTRIBUTIONS];
  private final double[] scaleX = new double[CONTRIBUTIONS];
  private final double[] scaleY = new double[CONTRIBUTIONS];
  private final boolean[] active = new boolean[CONTRIBUTIONS];
  private final PresentationEffects[] sources = new PresentationEffects[CONTRIBUTIONS];
  private double offsetX, offsetY, angle, scaleOffsetX, scaleOffsetY;

  int acquire(PresentationEffects source) {
    for (int i = 0; i < active.length; i++) {
      if (!active[i]) {
        active[i] = true;
        sources[i] = source;
        return i;
      }
    }
    throw new IllegalStateException("Presentation contribution limit exceeded");
  }

  void set(int index, double px, double py, double radians, double sx, double sy) {
    if (!active[index]) return;
    x[index] = px;
    y[index] = py;
    rotation[index] = radians;
    scaleX[index] = sx;
    scaleY[index] = sy;
    recompute();
  }

  void release(int index) {
    if (index < 0 || index >= active.length || !active[index]) return;
    active[index] = false;
    sources[index] = null;
    x[index] = y[index] = rotation[index] = scaleX[index] = scaleY[index] = 0;
    recompute();
  }

  int activeContributionCount() {
    int count = 0;
    for (boolean contribution : active) if (contribution) count++;
    return count;
  }

  @Override
  protected void onRelease() {
    // A provider can be removed before the owning effects have initialized, so component-change
    // callbacks alone cannot invalidate every cached lease. Notify sources while their slot
    // identity is still available; each source clears only its own contributions.
    for (int i = 0; i < sources.length; i++) {
      PresentationEffects source = sources[i];
      if (source != null) source.poseProviderRemoved(this);
    }
    for (int i = 0; i < active.length; i++) release(i);
  }

  private void recompute() {
    double px = 0, py = 0, angleSum = 0, sx = 0, sy = 0;
    for (int i = 0; i < active.length; i++) {
      PresentationEffects source = sources[i];
      if (!active[i] || source != null && (!source.isInitialized() || !source.isEnabled()))
        continue;
      px += x[i];
      py += y[i];
      angleSum += rotation[i];
      sx += scaleX[i];
      sy += scaleY[i];
    }
    offsetX = px;
    offsetY = py;
    angle = angleSum;
    scaleOffsetX = sx;
    scaleOffsetY = sy;
  }

  /** Presented world X, excluding the independent Transform2D.visualOffset. */
  public float presentedX() {
    recompute();
    return (float) (getTransform().position.x + offsetX);
  }

  /** Presented world Y, excluding the independent Transform2D.visualOffset. */
  public float presentedY() {
    recompute();
    return (float) (getTransform().position.y + offsetY);
  }

  /** Presented rotation in radians. */
  public float presentedRotation() {
    recompute();
    return (float) (getTransform().rotation + angle);
  }

  /** Presented X scale; effects contribute additive scale deltas. */
  public float presentedScaleX() {
    recompute();
    return (float) (getTransform().scale.x + scaleOffsetX);
  }

  /** Presented Y scale; effects contribute additive scale deltas. */
  public float presentedScaleY() {
    recompute();
    return (float) (getTransform().scale.y + scaleOffsetY);
  }

  /** Renderer X including the legacy caller-controlled visual offset. */
  public float renderX() {
    return presentedX() + getTransform().visualOffset.x;
  }

  /** Renderer Y including the legacy caller-controlled visual offset. */
  public float renderY() {
    return presentedY() + getTransform().visualOffset.y;
  }
}
