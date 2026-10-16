package org.javity.render.lighting;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.*;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import java.util.Arrays;
import org.javity.core.*;

/** Scene-owned CPU snapshot, rebuilt on the render thread before batching. No implicit sun. */
public final class RenderEnvironment3D extends GameSystem {
  public static final int DEFAULT_DIRECTIONAL_CAPACITY = 2,
      DEFAULT_POINT_CAPACITY = 4,
      DEFAULT_SPOT_CAPACITY = 2;
  private final Environment environment = new Environment();
  private final Color ambient = new Color(0, 0, 0, 1), fog = new Color();
  private float fogNear, fogFar = 1;
  private boolean fogEnabled;
  private Overflow overflow = new Overflow(0, 0, 0);

  public record Overflow(int directional, int point, int spot) {}

  public Overflow overflow() {
    return overflow;
  }

  public void setAmbient(float r, float g, float b) {
    validateColor(r, g, b);
    ambient.set(r, g, b, 1);
  }

  public void setFog(float r, float g, float b, float near, float far) {
    validateColor(r, g, b);
    if (!Float.isFinite(near) || !Float.isFinite(far) || near < 0 || far <= near)
      throw new IllegalArgumentException("fog requires finite 0 <= near < far");
    fog.set(r, g, b, 1);
    fogNear = near;
    fogFar = far;
    fogEnabled = true;
  }

  public void clearFog() {
    fogEnabled = false;
  }

  private static void validateColor(float r, float g, float b) {
    Light3D.nonnegative(r, "red");
    Light3D.nonnegative(g, "green");
    Light3D.nonnegative(b, "blue");
  }

  public static Environment select(
      Game game, Environment legacy, int directional, int point, int spot) {
    RenderEnvironment3D scene = game.getSystem(RenderEnvironment3D.class);
    return scene == null ? legacy : scene.snapshot(directional, point, spot);
  }

  /** Borrowed snapshot, valid until the next call. Light values never alias mutable components. */
  public Environment snapshot(int directionalCapacity, int pointCapacity, int spotCapacity) {
    if (getGame() == null || isDisposed())
      throw new IllegalStateException("Environment must belong to a live scene");
    if (directionalCapacity < 0 || pointCapacity < 0 || spotCapacity < 0)
      throw new IllegalArgumentException("Light capacities must be nonnegative");
    environment.clear();
    environment.set(new ColorAttribute(ColorAttribute.AmbientLight, ambient));
    if (fogEnabled) environment.set(new ColorAttribute(ColorAttribute.Fog, fog));
    float[] points = new float[pointCapacity], spots = new float[spotCapacity];
    int nd = 0, np = 0, ns = 0, od = 0, op = 0, os = 0;
    Color color = new Color();
    Vector3 position = new Vector3(), direction = new Vector3();
    for (GameObject owner : getGame().getObjectsWith(Light3D.class)) {
      if (!owner.isActive()) continue;
      Transform3D transform = owner.getComponent(Transform3D.class);
      if (transform == null || !transform.isInitialized() || !transform.isEnabled()) continue;
      for (Light3D light : owner.getComponents(Light3D.class)) {
        if (!light.isInitialized() || !light.isEnabled()) continue;
        Matrix4 world = transform.worldMatrix();
        for (float value : world.val)
          if (!Float.isFinite(value))
            throw new IllegalArgumentException("Light world transform must be finite");
        world.getTranslation(position);
        light.color(color);
        if (light instanceof DirectionalLight3D || light instanceof SpotLight3D) {
          if (world.det3x3() == 0)
            throw new IllegalArgumentException("Light direction transform is singular");
          direction.set(0, 0, -1).rot(world);
          float length = direction.len();
          if (!Float.isFinite(length) || length == 0)
            throw new IllegalArgumentException("Light direction must be finite and nonzero");
          direction.scl(1 / length);
        }
        if (light instanceof SpotLight3D spot) {
          if (ns >= spotCapacity) {
            os++;
            continue;
          }
          spots[ns++] = spot.range();
          environment.add(
              new SpotLight()
                  .set(
                      color,
                      position,
                      direction,
                      spot.intensity(),
                      spot.cutoffDegrees(),
                      spot.exponent()));
        } else if (light instanceof PointLight3D point) {
          if (np >= pointCapacity) {
            op++;
            continue;
          }
          points[np++] = point.range();
          environment.add(new PointLight().set(color, position, point.intensity()));
        } else if (light instanceof DirectionalLight3D) {
          if (nd >= directionalCapacity) {
            od++;
            continue;
          }
          color.mul(light.intensity());
          validateColor(color.r, color.g, color.b);
          environment.add(new DirectionalLight().set(color, direction));
          nd++;
        }
      }
    }
    environment.set(
        new SceneLightingAttribute(
            Arrays.copyOf(points, np), Arrays.copyOf(spots, ns), fogNear, fogFar));
    overflow = new Overflow(od, op, os);
    return environment;
  }
}
