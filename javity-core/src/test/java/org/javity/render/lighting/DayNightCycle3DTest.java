package org.javity.render.lighting;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import org.javity.core.*;
import org.junit.Test;

public class DayNightCycle3DTest {
  @Test
  public void phasesAndWorldPauseClock() {
    try (Game game = new Game()) {
      var env = game.addSystem(new RenderEnvironment3D());
      var time = game.addSystem(new org.javity.services.TimeManager());
      var owner = game.createObject("sun");
      var transform = owner.addComponent(new Transform3D());
      var sun = owner.addComponent(new DirectionalLight3D());
      var cycle = owner.addComponent(new DayNightCycle3D(sun, env, 100));
      game.start();
      for (double phase : new double[] {0, .25, .5, .75}) {
        cycle.setPhase(phase);
        assertEquals(phase, cycle.phase(), 0);
        Vector3 direction = new Vector3(0, 0, -1).rot(transform.worldMatrix());
        assertEquals(1, direction.len(), 1e-5);
        if (phase == 0) assertTrue(direction.y > .9f);
        if (phase == .25) assertTrue(direction.x > .9f);
        if (phase == .5) assertTrue(direction.y < -.9f);
        if (phase == .75) assertTrue(direction.x < -.9f);
      }
      cycle.setPhase(.5);
      float day = sun.intensity();
      Color sky = env.sky().zenith(new Color());
      cycle.setPhase(0);
      assertTrue(day > sun.intensity());
      assertTrue(sky.b > env.sky().zenith(new Color()).b);
      game.pause();
      game.step(10);
      assertEquals(0, cycle.phase(), 0);
      game.resume();
      game.step(.05f);
      assertEquals(.0005, cycle.phase(), 1e-6);
      time.setBaseScale(0);
      game.step(.05f);
      assertEquals(.0005, cycle.phase(), 1e-6);
      time.setBaseScale(2);
      game.step(.05f);
      assertEquals(.0015, cycle.phase(), 1e-6);
      owner.removeComponent(sun);
      game.step(.05f);
      assertEquals(.0015, cycle.phase(), 1e-6);
      assertThrows(IllegalStateException.class, () -> cycle.setPhase(.5));
      assertThrows(IllegalArgumentException.class, () -> cycle.setPhase(Double.NaN));
      assertThrows(IllegalArgumentException.class, () -> cycle.setPhase(1.1));
    }
  }

  @Test
  public void rejectsForeignOwnersAndInvalidDuration() {
    try (Game a = new Game();
        Game b = new Game()) {
      var env = a.addSystem(new RenderEnvironment3D());
      var sunOwner = b.createObject("foreign sun");
      sunOwner.addComponent(new Transform3D());
      var sun = sunOwner.addComponent(new DirectionalLight3D());
      assertThrows(IllegalArgumentException.class, () -> new DayNightCycle3D(sun, env, 0));
      a.createObject("cycle").addComponent(new DayNightCycle3D(sun, env, 100));
      assertThrows(IllegalStateException.class, a::start);
    }
  }
}
