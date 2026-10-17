package org.javity.physics2d;

import static org.junit.Assert.*;

import com.badlogic.gdx.math.Vector2;
import java.util.List;
import org.javity.core.*;
import org.junit.Test;

public class PhysicsQueriesTest {
  @Test
  public void polygonRaycastSortedFilteredAndSnapshot() {
    try (Game g = new Game()) {
      var p = g.addSystem(new Physics2D(Vector2.Zero, 100));
      var a = g.createObject("triangle").setPosition(100, 0);
      a.addComponent(
          new PolygonCollider2D(new Vector2(-20, -20), new Vector2(20, -20), new Vector2(0, 20)));
      var b = g.createObject("sensor").setPosition(200, 0);
      b.addComponent(new BoxCollider2D(20, 20).setTrigger(true).setFilter(2, 65535));
      g.start();
      List<RayHit2D> hits = p.raycast(new Vector2(0, 0), new Vector2(300, 0), 65535, true);
      assertEquals(2, hits.size());
      assertSame(a, hits.get(0).object());
      assertEquals(90, hits.get(0).point().x, .1);
      Vector2 copy = hits.get(0).point();
      copy.x = 999;
      assertEquals(90, hits.get(0).point().x, .1);
      assertEquals(1, p.raycast(new Vector2(0, 0), new Vector2(300, 0), 1, false).size());
      a.destroy();
      assertEquals(1, p.raycast(new Vector2(0, 0), new Vector2(300, 0), 65535, true).size());
    }
  }

  @Test
  public void impulseAndForceUseWorldUnits() {
    try (Game g = new Game()) {
      g.addSystem(new Physics2D(Vector2.Zero, 100));
      var a = g.createObject("body");
      var r = a.addComponent(new RigidBody2D());
      a.addComponent(new BoxCollider2D(100, 100));
      g.start();
      g.step(0);
      assertEquals(1, r.getMass(), .001);
      r.applyLinearImpulse(100, 0);
      g.step(1f / 60);
      assertEquals(100, r.getLinearVelocity().x, .01);
      r.applyForce(60, 0);
      g.step(1f / 60);
      assertEquals(101, r.getLinearVelocity().x, .01);
      g.pause();
      r.applyLinearImpulse(100, 0);
      g.step(1f / 60);
      assertEquals(101, r.getLinearVelocity().x, .01);
      g.resume();
      g.step(1f / 60);
      assertEquals(201, r.getLinearVelocity().x, .01);
    }
  }

  @Test
  public void rejectConcaveDegenerateAndCopyVertices() {
    Vector2 a = new Vector2(0, 0);
    var p = new PolygonCollider2D(a, new Vector2(20, 0), new Vector2(0, 20));
    a.x = 200;
    assertEquals(0, p.getVertices().get(0).x, 0);
    assertThrows(
        IllegalArgumentException.class,
        () -> new PolygonCollider2D(new Vector2(0, 0), new Vector2(1, 1), new Vector2(2, 2)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PolygonCollider2D(
                new Vector2(0, 0),
                new Vector2(20, 0),
                new Vector2(10, 5),
                new Vector2(20, 20),
                new Vector2(0, 20)));
  }

  @Test
  public void physicsSceneCodecRebuildsFixtures() {
    var schema = PhysicsSceneCodecs.install(org.javity.serialization.SceneJson.standard());
    try (Game a = new Game();
        Game b = new Game()) {
      var o = a.createObject("body").setPosition(30, 40);
      o.addComponent(new RigidBody2D().setFixedRotation(true).setLinearVelocity(100, 0));
      o.addComponent(new CircleCollider2D(20).setFilter(2, 4).setTrigger(true));
      b.addSystem(new Physics2D(Vector2.Zero, 100));
      var copy = schema.read(b, schema.write(a.getObjects())).getFirst();
      b.start();
      b.step(1f / 60);
      assertTrue(copy.getComponent(RigidBody2D.class).isFixedRotation());
      assertEquals(2, copy.getComponent(CircleCollider2D.class).getCategory());
      assertTrue(copy.transform.position.x > 30);
    }
  }
}
