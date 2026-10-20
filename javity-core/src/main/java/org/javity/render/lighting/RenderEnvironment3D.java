package org.javity.render.lighting;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.environment.*;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import java.util.Arrays;
import java.util.Objects;
import org.javity.core.*;
import org.javity.render.sky.SkySettings;

/** Scene-owned CPU snapshot, rebuilt on the render thread before batching. No implicit sun. */
public final class RenderEnvironment3D extends GameSystem {
  public static final int DEFAULT_DIRECTIONAL_CAPACITY = 2,
      DEFAULT_POINT_CAPACITY = 4,
      DEFAULT_SPOT_CAPACITY = 2;
  private final Environment environment = new Environment();
  // A failed rebuild may mutate staging, but never the currently published attributes.
  private SnapshotBuffer published = new SnapshotBuffer(), staging = new SnapshotBuffer();
  private final Color color = new Color();
  private final Vector3 position = new Vector3(), direction = new Vector3();
  private final Color ambient = new Color(0, 0, 0, 1), fog = new Color();
  private float fogNear, fogFar = 1;
  private boolean fogEnabled;
  private SkySettings sky;

  public void setSky(SkySettings settings) {
    sky = Objects.requireNonNull(settings, "settings");
  }

  public SkySettings sky() {
    return sky;
  }

  public void clearSky() {
    sky = null;
  }

  private Overflow overflow = new Overflow(0, 0, 0);

  public record Overflow(int directional, int point, int spot) {}

  public Overflow overflow() {
    return overflow;
  }

  public void setAmbient(float r, float g, float b) {
    Light3D.setRgb(ambient, r, g, b);
  }

  public void setFog(float r, float g, float b, float near, float far) {
    if (!Float.isFinite(near) || !Float.isFinite(far) || near < 0 || far <= near)
      throw new IllegalArgumentException("fog requires finite 0 <= near < far");
    Light3D.setRgb(fog, r, g, b);
    fogNear = near;
    fogFar = far;
    fogEnabled = true;
  }

  public void clearFog() {
    fogEnabled = false;
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
    staging.prepare(directionalCapacity, pointCapacity, spotCapacity);
    staging.ambient.color.set(ambient);
    staging.fog.color.set(fog);
    int nd = 0, np = 0, ns = 0, od = 0, op = 0, os = 0;
    var owners = getGame().getObjectsWith(Light3D.class);
    for (int ownerIndex = 0; ownerIndex < owners.size(); ownerIndex++) {
      GameObject owner = owners.get(ownerIndex);
      if (!owner.isActive()) continue;
      Transform3D transform = owner.getComponent(Transform3D.class);
      if (transform == null || !transform.isInitialized() || !transform.isEnabled()) continue;
      var lights = owner.getComponents(Light3D.class);
      for (int lightIndex = 0; lightIndex < lights.size(); lightIndex++) {
        Light3D light = lights.get(lightIndex);
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
          staging.marker.setSpotRange(ns, spot.range());
          SpotLight target = staging.spotPool[ns];
          if (target == null) staging.spotPool[ns] = target = new SpotLight();
          target.set(
              color, position, direction, spot.intensity(), spot.cutoffDegrees(), spot.exponent());
          staging.spots.lights.add(target);
          ns++;
        } else if (light instanceof PointLight3D point) {
          if (np >= pointCapacity) {
            op++;
            continue;
          }
          staging.marker.setPointRange(np, point.range());
          PointLight target = staging.pointPool[np];
          if (target == null) staging.pointPool[np] = target = new PointLight();
          target.set(color, position, point.intensity());
          staging.points.lights.add(target);
          np++;
        } else if (light instanceof DirectionalLight3D) {
          if (nd >= directionalCapacity) {
            od++;
            continue;
          }
          Light3D.setRgb(
              color,
              color.r * light.intensity(),
              color.g * light.intensity(),
              color.b * light.intensity());
          DirectionalLight target = staging.directionalPool[nd];
          if (target == null) staging.directionalPool[nd] = target = new DirectionalLight();
          target.set(color, direction);
          staging.directionals.lights.add(target);
          nd++;
        }
      }
    }
    staging.marker.finish(np, ns, fogNear, fogFar);
    // Publish only a fully validated buffer, retaining the public borrowed Environment identity.
    environment.clear();
    environment.set(staging.ambient);
    if (fogEnabled) environment.set(staging.fog);
    if (nd != 0) environment.set(staging.directionals);
    if (np != 0) environment.set(staging.points);
    if (ns != 0) environment.set(staging.spots);
    environment.set(staging.marker);
    SnapshotBuffer previous = published;
    published = staging;
    staging = previous;
    if (overflow.directional() != od || overflow.point() != op || overflow.spot() != os)
      overflow = new Overflow(od, op, os);
    return environment;
  }

  private static final class SnapshotBuffer {
    final ColorAttribute ambient = new ColorAttribute(ColorAttribute.AmbientLight);
    final ColorAttribute fog = new ColorAttribute(ColorAttribute.Fog);
    final DirectionalLightsAttribute directionals = new DirectionalLightsAttribute();
    final PointLightsAttribute points = new PointLightsAttribute();
    final SpotLightsAttribute spots = new SpotLightsAttribute();
    final SceneLightingAttribute marker = new SceneLightingAttribute();
    DirectionalLight[] directionalPool = new DirectionalLight[0];
    PointLight[] pointPool = new PointLight[0];
    SpotLight[] spotPool = new SpotLight[0];

    void prepare(int directionalCapacity, int pointCapacity, int spotCapacity) {
      // Retain high-water storage, bounded by requested capacities, independently of active counts.
      if (directionalPool.length < directionalCapacity)
        directionalPool = Arrays.copyOf(directionalPool, directionalCapacity);
      if (pointPool.length < pointCapacity) pointPool = Arrays.copyOf(pointPool, pointCapacity);
      if (spotPool.length < spotCapacity) spotPool = Arrays.copyOf(spotPool, spotCapacity);
      directionals.lights.clear();
      points.lights.clear();
      spots.lights.clear();
      marker.prepare(pointCapacity, spotCapacity);
    }
  }
}
