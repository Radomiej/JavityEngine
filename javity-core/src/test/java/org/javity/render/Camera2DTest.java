package org.javity.render;

import static org.junit.Assert.*;

import com.badlogic.gdx.math.Vector2;
import org.javity.core.*;
import org.junit.Test;

public class Camera2DTest {
  @Test
  public void screenWorldRoundTripWithZoomRotationAndDensity() {
    Game game = new Game();
    var o = game.createObject("camera");
    o.setPosition(100, 50);
    o.transform.rotation = (float) Math.PI / 2;
    Camera2D c = o.addComponent(new Camera2D());
    c.resize(800, 600);
    c.setPixelsPerUnit(2);
    c.setZoom(2);
    Vector2 p = c.screenToWorld(600, 300);
    assertEquals(100, p.x, .001);
    assertEquals(250, p.y, .001);
    Vector2 screen = c.worldToScreen(p.x, p.y);
    assertEquals(600, screen.x, .001);
    assertEquals(300, screen.y, .001);
    Vector2 direction = c.directionFromScreen(100, 50, 600, 300);
    assertEquals(0, direction.x, .001);
    assertEquals(1, direction.y, .001);
    assertEquals(new Vector2(400, 300), c.worldToScreen(100, 50));
    game.dispose();
  }

  @Test
  public void followTargetRunsLateAndRespectsPauseAndRemoval() {
    Game game = new Game();
    var target = game.createObject("player");
    target.setPosition(10, 20);
    target.addComponent(
        new Component() {
          public void onUpdate(float dt) {
            getTransform().position.x += 10;
          }
        });
    var camera = game.createObject("camera");
    camera.addComponent(new Camera2D());
    FollowTarget2D f = camera.addComponent(new FollowTarget2D(target));
    f.offset.set(2, 3);
    game.start();
    game.step(.016f);
    assertEquals(new Vector2(22, 23), camera.transform.position);
    game.pause();
    target.setPosition(200, 200);
    game.step(.016f);
    assertEquals(new Vector2(22, 23), camera.transform.position);
    game.resume();
    target.destroy();
    game.step(.016f);
    assertEquals(new Vector2(22, 23), camera.transform.position);
    game.dispose();
  }

  @Test
  public void smoothingIndependentOfFrameSplittingAndRejectsOtherScene() {
    assertEquals(follow(.05f, 2), follow(.1f, 1), .001);
    Game a = new Game(), b = new Game();
    var foreign = b.createObject("target");
    a.createObject("camera").addComponent(new FollowTarget2D(foreign));
    assertThrows(IllegalArgumentException.class, a::start);
    a.dispose();
    b.dispose();
    Camera2D c = new Camera2D();
    assertThrows(IllegalArgumentException.class, () -> c.setZoom(0));
    assertThrows(IllegalArgumentException.class, () -> c.setPixelsPerUnit(Float.NaN));
  }

  private float follow(float step, int count) {
    Game g = new Game();
    var t = g.createObject("target");
    t.setPosition(100, 0);
    var c = g.createObject("camera");
    var f = c.addComponent(new FollowTarget2D(t));
    f.setSmoothing(5);
    g.start();
    for (int i = 0; i < count; i++) g.step(step);
    float result = c.transform.position.x;
    g.dispose();
    return result;
  }
}
