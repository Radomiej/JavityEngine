package org.javity.render.lighting;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.*;
import java.util.Objects;
import org.javity.core.*;
import org.javity.render.sky.SkySettings;

/**
 * Simple WORLD-clock cycle: midnight 0, sunrise .25, noon .5, sunset .75. No resource ownership.
 */
public final class DayNightCycle3D extends Component {
  private final DirectionalLight3D sun;
  private final RenderEnvironment3D environment;
  private final double daySeconds;
  private double phase;

  public DayNightCycle3D(
      DirectionalLight3D sun, RenderEnvironment3D environment, double daySeconds) {
    this.sun = Objects.requireNonNull(sun);
    this.environment = Objects.requireNonNull(environment);
    if (!Double.isFinite(daySeconds) || daySeconds <= 0)
      throw new IllegalArgumentException("daySeconds must be finite and positive");
    this.daySeconds = daySeconds;
  }

  public double phase() {
    return phase;
  }

  public void setPhase(double value) {
    if (!Double.isFinite(value) || value < 0 || value > 1)
      throw new IllegalArgumentException("phase must be in [0,1]");
    if (isInitialized()) requireOwners();
    phase = value;
    if (isInitialized()) apply();
  }

  private boolean ownersLive() {
    return getGame() != null
        && !getGame().isDisposed()
        && environment.getGame() == getGame()
        && !environment.isDisposed()
        && sun.getGame() == getGame()
        && !sun.getGameObject().isDestroyed()
        && sun.getGameObject().containsComponent(sun)
        && sun.getGameObject().getComponent(Transform3D.class) != null;
  }

  private void requireOwners() {
    if (!ownersLive())
      throw new IllegalStateException("Cycle, sun and environment require the same live Game");
  }

  @Override
  public void onCreate() {
    requireOwners();
    apply();
  }

  @Override
  public void onUpdate(float delta) {
    if (!ownersLive()) return;
    phase = (phase + delta / daySeconds) % 1;
    apply();
  }

  private void apply() {
    double angle = (phase - .25) * Math.PI * 2;
    float height = (float) Math.sin(angle);
    float daylight = MathUtils.clamp(height * 2, 0, 1);
    sun.setIntensity(.035f + daylight * .965f);
    sun.setColor(1, .55f + daylight * .4f, .38f + daylight * .52f);
    Transform3D transform = sun.getGameObject().getComponent(Transform3D.class);
    Vector3 direction = new Vector3((float) Math.cos(angle), -height, -.25f).nor();
    if (transform.getParent() != null)
      direction.rot(transform.getParent().worldMatrix().cpy().inv()).nor();
    transform.rotation.setFromCross(new Vector3(0, 0, -1), direction);
    environment.setAmbient(
        .025f + .26f * daylight, .035f + .29f * daylight, .075f + .29f * daylight);
    float twilight = Math.max(0, 1 - Math.abs(height) / .35f);
    Color horizon =
        new Color(.045f + .62f * daylight, .055f + .70f * daylight, .11f + .75f * daylight, 1)
            .lerp(new Color(.9f, .35f, .16f, 1), twilight * .75f);
    SkySettings sky = environment.sky();
    if (sky == null || sky.mode() == SkySettings.Mode.GRADIENT)
      environment.setSky(
          SkySettings.gradient(
              new Color(
                  .008f + .12f * daylight, .018f + .36f * daylight, .065f + .64f * daylight, 1),
              horizon,
              new Color(
                  .015f + .2f * daylight, .02f + .26f * daylight, .045f + .3f * daylight, 1)));
  }
}
