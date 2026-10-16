package org.javity.ui.inspector;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.*;
import org.javity.core.*;
import org.javity.render.lighting.*;

/** Draws into a borrowed, already begun LINE ShapeRenderer with the supplied camera projection. */
public final class LightGizmoAdapter {
  private LightGizmoAdapter() {}

  public static void draw(ShapeRenderer borrowed, Camera camera, GameObject target) {
    Transform3D transform = target.getComponent(Transform3D.class);
    if (transform == null) return;
    Matrix4 world = transform.worldMatrix();
    Vector3 origin = world.getTranslation(new Vector3());
    Vector3 direction = new Vector3(0, 0, -1).rot(world).nor();
    for (Light3D light : target.getComponents(Light3D.class)) {
      Color color = light.color(new Color());
      float max = Math.max(1, Math.max(color.r, Math.max(color.g, color.b)));
      borrowed.setColor(
          light.isEnabled()
              ? new Color(color.r / max, color.g / max, color.b / max, 1)
              : Color.GRAY);
      if (light instanceof SpotLight3D spot) {
        Vector3 center = origin.cpy().mulAdd(direction, spot.range());
        float radius =
            spot.range() * MathUtils.tan(spot.cutoffDegrees() * MathUtils.degreesToRadians);
        Vector3 u = direction.cpy().crs(Math.abs(direction.y) < .9f ? Vector3.Y : Vector3.X).nor();
        Vector3 v = direction.cpy().crs(u).nor();
        for (int i = 0; i < 32; i++) {
          Vector3 a = ring(center, u, v, radius, i), b = ring(center, u, v, radius, i + 1);
          borrowed.line(a, b);
          if (i % 8 == 0) borrowed.line(origin, a);
        }
      } else if (light instanceof PointLight3D point) {
        for (int axis = 0; axis < 3; axis++)
          for (int i = 0; i < 48; i++) {
            Vector3 u = axis == 0 ? Vector3.X : Vector3.Y, v = axis == 2 ? Vector3.X : Vector3.Z;
            borrowed.line(
                ring(origin, u, v, point.range(), i * 32f / 48),
                ring(origin, u, v, point.range(), (i + 1) * 32f / 48));
          }
      } else {
        Vector3 end = origin.cpy().mulAdd(direction, 2);
        borrowed.line(origin, end);
        Vector3 side = direction.cpy().crs(camera.up).nor().scl(.22f);
        borrowed.line(end, end.cpy().mulAdd(direction, -.4f).add(side));
        borrowed.line(end, end.cpy().mulAdd(direction, -.4f).sub(side));
      }
    }
  }

  private static Vector3 ring(Vector3 center, Vector3 u, Vector3 v, float radius, float step) {
    float angle = step * MathUtils.PI2 / 32;
    return center
        .cpy()
        .mulAdd(u, MathUtils.cos(angle) * radius)
        .mulAdd(v, MathUtils.sin(angle) * radius);
  }
}
