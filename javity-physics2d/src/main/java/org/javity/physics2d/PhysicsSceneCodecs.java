package org.javity.physics2d;

import static org.javity.serialization.SceneJson.*;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonValue;
import java.util.*;
import org.javity.serialization.SceneJson;

/** Optional physics codecs, configuration only; no Body/Fixture or pending impulses persisted. */
public final class PhysicsSceneCodecs {
  private PhysicsSceneCodecs() {}

  private static JsonValue collider(Collider2D c) {
    var d = object();
    number(d, "ox", c.getOffset().x);
    number(d, "oy", c.getOffset().y);
    number(d, "density", c.getDensity());
    number(d, "friction", c.getFriction());
    number(d, "restitution", c.getRestitution());
    d.addChild("trigger", new JsonValue(c.isTrigger()));
    d.addChild("category", new JsonValue(c.getCategory()));
    d.addChild("mask", new JsonValue(c.getMask()));
    return d;
  }

  private static <T extends Collider2D> T settings(T c, JsonValue d) {
    c.setOffset(finite(d, "ox", 0), finite(d, "oy", 0));
    c.setDensity(finite(d, "density", 1));
    c.setFriction(finite(d, "friction", .2f));
    c.setRestitution(finite(d, "restitution", 0));
    c.setTrigger(d.getBoolean("trigger", false));
    c.setFilter(d.getInt("category", 1), d.getInt("mask", 65535));
    return c;
  }

  public static SceneJson install(SceneJson schema) {
    schema.register(
        "box2d",
        BoxCollider2D.class,
        c -> {
          var d = collider(c);
          number(d, "width", c.getWidth());
          number(d, "height", c.getHeight());
          return d;
        },
        d -> settings(new BoxCollider2D(finite(d, "width", 0), finite(d, "height", 0)), d));
    schema.register(
        "circle2d",
        CircleCollider2D.class,
        c -> {
          var d = collider(c);
          number(d, "radius", c.getRadius());
          return d;
        },
        d -> settings(new CircleCollider2D(finite(d, "radius", 0)), d));
    schema.register(
        "polygon2d",
        PolygonCollider2D.class,
        c -> {
          var d = collider(c);
          JsonValue pts = array();
          for (Vector2 p : c.getVertices()) {
            var v = array();
            v.addChild(new JsonValue(p.x));
            v.addChild(new JsonValue(p.y));
            pts.addChild(v);
          }
          d.addChild("vertices", pts);
          return d;
        },
        d -> {
          List<Vector2> points = new ArrayList<>();
          for (JsonValue p : d.require("vertices"))
            points.add(new Vector2(p.getFloat(0), p.getFloat(1)));
          return settings(new PolygonCollider2D(points.toArray(Vector2[]::new)), d);
        });
    schema.register(
        "rigidbody2d",
        RigidBody2D.class,
        c -> {
          var d = object();
          text(d, "type", c.getType().name());
          d.addChild("fixed", new JsonValue(c.isFixedRotation()));
          d.addChild("bullet", new JsonValue(c.isBullet()));
          number(d, "gravity", c.getGravityScale());
          number(d, "linearDamping", c.getLinearDamping());
          number(d, "angularDamping", c.getAngularDamping());
          number(d, "vx", c.getLinearVelocity().x);
          number(d, "vy", c.getLinearVelocity().y);
          number(d, "angularVelocity", c.getAngularVelocity());
          return d;
        },
        d ->
            new RigidBody2D(RigidBody2D.Type.valueOf(d.getString("type")))
                .setFixedRotation(d.getBoolean("fixed", false))
                .setBullet(d.getBoolean("bullet", false))
                .setGravityScale(finite(d, "gravity", 1))
                .setLinearDamping(finite(d, "linearDamping", 0))
                .setAngularDamping(finite(d, "angularDamping", 0))
                .setLinearVelocity(finite(d, "vx", 0), finite(d, "vy", 0))
                .setAngularVelocity(finite(d, "angularVelocity", 0)));
    return schema;
  }
}
