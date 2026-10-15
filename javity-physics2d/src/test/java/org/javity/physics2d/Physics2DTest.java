package org.javity.physics2d;

import static org.junit.Assert.*;

import com.badlogic.gdx.math.Vector2;
import org.javity.core.*;
import org.javity.services.TimeManager;
import org.junit.Test;

public class Physics2DTest {
  static class Contacts extends Component implements CollisionListener2D {
    int collisionEnter, collisionExit, triggerEnter, triggerExit;
    java.util.function.Consumer<Contact2D> onEnter;

    public void onCollisionEnter(Contact2D c) {
      collisionEnter++;
      if (onEnter != null) onEnter.accept(c);
    }

    public void onCollisionExit(Contact2D c) {
      collisionExit++;
    }

    public void onTriggerEnter(Contact2D c) {
      triggerEnter++;
    }

    public void onTriggerExit(Contact2D c) {
      triggerExit++;
    }
  }

  private static GameObject ball(Game game, float x, float y) {
    GameObject ball = game.createObject("ball").setPosition(x, y);
    ball.addComponent(new RigidBody2D());
    ball.addComponent(new CircleCollider2D(10));
    return ball;
  }

  private static Physics2D physics(Game game, float gravity) {
    return game.addSystem(new Physics2D(new Vector2(0, gravity), 100));
  }

  private static void simulate(Game game, int frames, float dt) {
    for (int i = 0; i < frames; i++) game.step(dt);
  }

  @Test
  public void gravityLandsCircleOnStaticRectangle() {
    try (Game game = new Game()) {
      Physics2D physics = physics(game, -980);
      GameObject floor = game.createObject("floor");
      floor.addComponent(new BoxCollider2D(500, 20));
      GameObject ball = ball(game, 0, 120);
      Contacts contacts = ball.addComponent(new Contacts());
      game.start();
      simulate(game, 120, 1f / 60);
      assertEquals(20, ball.transform.position.y, 3);
      assertEquals(1, contacts.collisionEnter);
      assertEquals(2, physics.getBodyCount());
    }
  }

  @Test
  public void fixedStepIsIndependentOfFrameDivision() {
    float[] positions = new float[2];
    for (int i = 0; i < 2; i++)
      try (Game game = new Game()) {
        physics(game, 0);
        GameObject ball = ball(game, 0, 0);
        ball.getComponent(RigidBody2D.class).setLinearVelocity(100, 0);
        game.start();
        simulate(game, i == 0 ? 60 : 30, i == 0 ? 1f / 60 : 1f / 30);
        positions[i] = ball.transform.position.x;
      }
    assertEquals(100, positions[0], .02);
    assertEquals(positions[0], positions[1], .001);
  }

  @Test
  public void pauseDoesNotAccumulatePhysicsTime() {
    try (Game game = new Game()) {
      physics(game, 0);
      GameObject ball = ball(game, 0, 0);
      ball.getComponent(RigidBody2D.class).setLinearVelocity(100, 0);
      game.start();
      game.step(1f / 60);
      float x = ball.transform.position.x;
      game.pause();
      simulate(game, 60, 1f / 60);
      assertEquals(x, ball.transform.position.x, 0);
      game.resume();
      game.step(1f / 60);
      assertEquals(x + 100f / 60, ball.transform.position.x, .01);
    }
  }

  @Test
  public void triggerReportsEnterExitWithoutBlockingMotion() {
    try (Game game = new Game()) {
      physics(game, 0);
      GameObject sensor = game.createObject("sensor").setPosition(50, 0);
      sensor.addComponent(new BoxCollider2D(20, 100).setTrigger(true));
      GameObject ball = ball(game, 0, 0);
      Contacts contacts = ball.addComponent(new Contacts());
      ball.getComponent(RigidBody2D.class).setLinearVelocity(100, 0);
      game.start();
      simulate(game, 60, 1f / 60);
      assertEquals(100, ball.transform.position.x, .02);
      assertEquals(1, contacts.triggerEnter);
      assertEquals(1, contacts.triggerExit);
      assertEquals(0, contacts.collisionEnter);
    }
  }

  @Test
  public void filtersPreventCollisionOnBothSides() {
    try (Game game = new Game()) {
      physics(game, -980);
      game.createObject("floor").addComponent(new BoxCollider2D(500, 20).setFilter(2, 4));
      GameObject ball = ball(game, 0, 120);
      Contacts contacts = ball.addComponent(new Contacts());
      game.start();
      simulate(game, 60, 1f / 60);
      assertTrue(ball.transform.position.y < 0);
      assertEquals(0, contacts.collisionEnter);
    }
  }

  @Test
  public void compoundCollidersShareBodyAndUpdateMassWhenResized() {
    try (Game game = new Game()) {
      Physics2D physics = physics(game, 0);
      GameObject o = game.createObject("compound");
      RigidBody2D body = o.addComponent(new RigidBody2D());
      CircleCollider2D first = o.addComponent(new CircleCollider2D(10));
      first.setOffset(-20, 0);
      o.addComponent(new CircleCollider2D(10).setOffset(20, 0));
      game.start();
      game.step(0);
      assertEquals(1, physics.getBodyCount());
      assertEquals(2, physics.getFixtureCount());
      assertEquals(Math.PI * .02, body.getMass(), .001);
      first.setRadius(20);
      game.step(0);
      assertEquals(Math.PI * .05, body.getMass(), .001);
      o.removeComponent(first);
      game.step(0);
      assertEquals(1, physics.getFixtureCount());
    }
  }

  @Test
  public void contactCallbackCanDestroyObjectOutsideWorldLock() {
    try (Game game = new Game()) {
      Physics2D physics = physics(game, 0);
      GameObject obstacle = game.createObject("obstacle");
      obstacle.addComponent(new BoxCollider2D(20, 20));
      GameObject ball = ball(game, 0, 0);
      Contacts contacts = ball.addComponent(new Contacts());
      contacts.onEnter = c -> c.other().destroy();
      game.start();
      game.step(1f / 60);
      assertTrue(obstacle.isDestroyed());
      assertEquals(1, contacts.collisionEnter);
      assertEquals(1, physics.getBodyCount());
    }
  }

  @Test
  public void teleportAndEnableChangesSynchronizeWithBodies() {
    try (Game game = new Game()) {
      physics(game, 0);
      GameObject ball = ball(game, 0, 0);
      ball.getComponent(RigidBody2D.class).setLinearVelocity(100, 0);
      game.start();
      game.step(1f / 60);
      ball.setPosition(250, 100);
      game.step(0);
      assertEquals(250, ball.transform.position.x, .001);
      ball.setActive(false);
      game.step(1f / 60);
      assertEquals(250, ball.transform.position.x, .001);
      ball.setActive(true);
      game.step(1f / 60);
      assertTrue(ball.transform.position.x > 250);
    }
  }

  @Test
  public void newComponentsBindNextFrameAndWorldIsSceneLocal() {
    try (Game a = new Game();
        Game b = new Game()) {
      Physics2D pa = physics(a, 0), pb = physics(b, 0);
      GameObject o = a.createObject("new");
      o.addComponent(
          new Component() {
            boolean added;

            public void onUpdate(float dt) {
              if (!added) {
                o.addComponent(new CircleCollider2D(10));
                added = true;
              }
            }
          });
      a.start();
      b.start();
      a.step(0);
      b.step(0);
      assertEquals(0, pa.getBodyCount());
      a.step(0);
      assertEquals(1, pa.getBodyCount());
      assertEquals(0, pb.getBodyCount());
      a.dispose();
      assertTrue(pa.isDisposed());
      assertEquals(0, pa.getBodyCount());
    }
  }

  @Test
  public void invalidConfigurationFailsBeforeNativeAllocation() {
    assertThrows(IllegalArgumentException.class, () -> new CircleCollider2D(0));
    assertThrows(IllegalArgumentException.class, () -> new BoxCollider2D(Float.NaN, 10));
    assertThrows(IllegalArgumentException.class, () -> new Physics2D(new Vector2(), 0));
    assertThrows(
        IllegalArgumentException.class, () -> new BoxCollider2D(10, 10).setFilter(65536, 1));
    assertThrows(
        IllegalArgumentException.class, () -> new RigidBody2D().setLinearVelocity(Float.NaN, 0));
  }

  @Test
  public void changingGravityScaleWakesSleepingBody() {
    try (Game game = new Game()) {
      physics(game, -980);
      GameObject ball = ball(game, 0, 100);
      RigidBody2D body = ball.getComponent(RigidBody2D.class).setGravityScale(0);
      game.start();
      simulate(game, 180, 1f / 60);
      assertEquals(100, ball.transform.position.y, .001);
      body.setGravityScale(1);
      game.step(.1f);
      assertTrue("Changed gravity must wake a sleeping body", ball.transform.position.y < 99);
    }
  }

  @Test
  public void filteredEnterCallbackCanUnsubscribe() {
    try (Game game = new Game()) {
      physics(game, 0);
      GameObject target = game.createObject("target");
      target.addComponent(new BoxCollider2D(20, 20).setTrigger(true));
      target.addComponent(new Contacts());
      GameObject ball = ball(game, 0, 0);
      int[] calls = {0};
      Runnable detach =
          ball.getComponent(CircleCollider2D.class)
              .onContactEnter(Contacts.class, other -> calls[0]++);
      game.start();
      game.step(1f / 60);
      assertEquals(1, calls[0]);
      ball.setPosition(100, 0);
      game.step(1f / 60);
      detach.run();
      ball.setPosition(0, 0);
      game.step(1f / 60);
      assertEquals(1, calls[0]);
    }
  }

  @Test
  public void sceneCanBeDisposedFromCollisionCallback() {
    Game game = new Game();
    Physics2D physics = physics(game, 0);
    game.createObject("wall").addComponent(new BoxCollider2D(20, 20));
    Contacts contacts = ball(game, 0, 0).addComponent(new Contacts());
    contacts.onEnter = contact -> game.dispose();
    game.start();
    game.step(1f / 60);
    assertTrue(game.isDisposed());
    assertTrue(physics.isDisposed());
    assertEquals(0, physics.getBodyCount());
  }

  @Test
  public void timeManagerScalesRealBox2DAndFreezeKeepsUiClockRunning() {
    float frame = 1f / 60;
    float[] distance = new float[2];
    for (int run = 0; run < 2; run++) {
      try (Game game = new Game()) {
        TimeManager time = game.addSystem(new TimeManager());
        time.setBaseScale(run == 0 ? 1 : 2);
        physics(game, 0);
        GameObject body = ball(game, 0, 0);
        body.getComponent(RigidBody2D.class).setLinearVelocity(100, 0);
        int[] uiSteps = {0};
        game.addSystem(
            new GameSystem() {
              public void onUiUpdate(float unscaledDelta) {
                uiSteps[0]++;
              }
            });
        game.start();
        simulate(game, 10, frame);
        distance[run] = body.transform.position.x;
        assertTrue("Real Box2D must advance", distance[run] > 0);
        assertEquals(10, uiSteps[0]);
        float beforeFreeze = body.transform.position.x;
        time.freeze(.035);
        simulate(game, 3, frame);
        assertEquals(
            "Frozen time must halt the real body", beforeFreeze, body.transform.position.x, 0);
        assertEquals(13, uiSteps[0]);
        game.step(frame);
        assertTrue("Physics resumes after freeze lease", body.transform.position.x > beforeFreeze);
      }
    }
    assertEquals("Scale two should double real Box2D travel", distance[0] * 2, distance[1], .03);
  }
}
