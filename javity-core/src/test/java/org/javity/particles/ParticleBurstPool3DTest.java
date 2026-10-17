package org.javity.particles;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import org.javity.core.Game;
import org.javity.effects.Clock;
import org.junit.Test;

public class ParticleBurstPool3DTest {
  private static ParticleBurstSpec3D spec(Vector3 origin, int count, long seed, float life) {
    return new ParticleBurstSpec3D(
        origin,
        new Vector3(-2, 1, 0),
        new Vector3(2, 3, 0),
        new Vector3(0, -4, 0),
        life,
        .25f,
        new Color(.8f, .3f, .1f, .7f),
        count,
        seed);
  }

  @Test
  public void deterministicTrajectoryAndCopiedInputs() {
    Vector3 origin = new Vector3(1, 2, 3), min = new Vector3(-2, 1, 0), max = new Vector3(2, 3, 0);
    ParticleBurstSpec3D description =
        new ParticleBurstSpec3D(
            origin, min, max, new Vector3(0, -4, 0), 1, .25f, Color.ORANGE, 4, 9891);
    origin.set(90, 90, 90);
    min.set(8, 8, 8);
    max.set(9, 9, 9);
    assertEquals(1, description.origin().x, 0);
    assertEquals(-2, description.minVelocity().x, 0);
    try (Game first = new Game();
        Game second = new Game()) {
      ParticleBurstPool3D a = first.addSystem(new ParticleBurstPool3D(Clock.WORLD, 8, 2));
      ParticleBurstPool3D b = second.addSystem(new ParticleBurstPool3D(Clock.WORLD, 8, 2));
      first.start();
      second.start();
      a.burst(description);
      b.burst(description);
      first.step(.1f);
      second.step(.1f);
      float[] ar = new float[8 * 8], br = new float[8 * 8];
      assertEquals(4, a.copyRenderData(ar));
      assertEquals(4, b.copyRenderData(br));
      assertArrayEquals(ar, br, 0);
      assertTrue(ar[0] >= 1 && ar[1] > 2);
      assertEquals(4, a.getActiveParticleCount());
    }
  }

  @Test
  public void fixedCapacityIsAtomicAndExpiryReleasesLease() {
    try (Game game = new Game()) {
      ParticleBurstPool3D pool = game.addSystem(new ParticleBurstPool3D(Clock.WORLD, 3, 1));
      game.start();
      ParticleBurstHandle3D first = pool.burst(spec(new Vector3(), 2, 1, .2f));
      assertNotNull(first);
      assertNull(pool.burst(spec(new Vector3(), 2, 2, .2f)));
      assertNull(pool.burst(spec(new Vector3(), 4, 3, .2f)));
      assertEquals(2, pool.getActiveParticleCount());
      game.step(.1f);
      assertTrue(first.isActive());
      game.step(.1f);
      assertFalse(first.isActive());
      assertEquals(0, pool.getActiveBurstCount());
      assertNotNull(pool.burst(spec(new Vector3(), 3, 4, .3f)));
      assertEquals(3, pool.getActiveParticleCount());
    }
  }

  @Test
  public void cancellationPauseAndStaleGenerationOnlyAffectOwnedBurst() {
    try (Game game = new Game()) {
      ParticleBurstPool3D pool = game.addSystem(new ParticleBurstPool3D(Clock.WORLD, 4, 2));
      game.start();
      ParticleBurstHandle3D old = pool.burst(spec(new Vector3(), 2, 5, 1));
      ParticleBurstHandle3D independent = pool.burst(spec(new Vector3(3, 0, 0), 2, 6, 1));
      old.pause();
      game.step(.1f);
      assertTrue(old.isActive());
      assertTrue(independent.isActive());
      old.cancel();
      assertEquals(2, pool.getActiveParticleCount());
      ParticleBurstHandle3D reused = pool.burst(spec(new Vector3(9, 0, 0), 2, 7, 1));
      old.cancel();
      assertTrue(reused.isActive());
      assertTrue(independent.isActive());
      game.pause();
      game.step(.1f);
      assertTrue(independent.isActive());
      game.resume();
      for (int i = 0; i < 9; i++) game.step(.1f);
      assertFalse(independent.isActive());
      assertTrue(reused.isActive());
      reused.resume();
      game.step(.1f);
      assertFalse(reused.isActive());
      assertEquals(0, pool.getActiveParticleCount());
    }
  }

  @Test
  public void uiClockAdvancesDuringPauseAndRemovalInvalidatesHandles() {
    Game game = new Game();
    ParticleBurstPool3D pool = game.addSystem(new ParticleBurstPool3D(Clock.UI, 2, 1));
    game.start();
    ParticleBurstHandle3D handle = pool.burst(spec(new Vector3(), 1, 8, .2f));
    game.pause();
    game.step(.1f);
    assertTrue(handle.isActive());
    game.step(.1f);
    assertFalse(handle.isActive());
    ParticleBurstHandle3D second = pool.burst(spec(new Vector3(), 1, 9, 1));
    pool.dispose();
    assertFalse(second.isActive());
    assertEquals(0, pool.getActiveParticleCount());
    game.dispose();
  }

  @Test
  public void rejectsInvalidDescriptionBeforePoolMutation() {
    try (Game game = new Game()) {
      ParticleBurstPool3D pool = game.addSystem(new ParticleBurstPool3D(Clock.WORLD, 2, 1));
      game.start();
      assertThrows(
          IllegalArgumentException.class, () -> spec(new Vector3(Float.NaN, 0, 0), 1, 1, 1));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ParticleBurstSpec3D(
                  new Vector3(),
                  new Vector3(2, 0, 0),
                  new Vector3(1, 0, 0),
                  new Vector3(),
                  1,
                  1,
                  Color.WHITE,
                  1,
                  1));
      assertEquals(0, pool.getActiveParticleCount());
    }
  }

  @Test
  public void exactConstantAccelerationTrajectoryMatchesPartitionedSteps() {
    ParticleBurstSpec3D description =
        new ParticleBurstSpec3D(
            new Vector3(100, -10, 4),
            new Vector3(10, -2, .5f),
            new Vector3(10, -2, .5f),
            new Vector3(2, 4, -1),
            2,
            .25f,
            Color.WHITE,
            1,
            1);
    try (Game oneStepGame = new Game();
        Game partitionedGame = new Game()) {
      ParticleBurstPool3D oneStepPool =
          oneStepGame.addSystem(new ParticleBurstPool3D(Clock.WORLD, 1, 1));
      ParticleBurstPool3D partitionedPool =
          partitionedGame.addSystem(new ParticleBurstPool3D(Clock.WORLD, 1, 1));
      oneStepGame.start();
      partitionedGame.start();
      oneStepPool.burst(description);
      partitionedPool.burst(description);
      oneStepGame.step(.1f);
      partitionedGame.step(.04f);
      partitionedGame.step(.06f);
      float[] one = new float[8], partitioned = new float[8];
      assertEquals(1, oneStepPool.copyRenderData(one));
      assertEquals(1, partitionedPool.copyRenderData(partitioned));
      assertEquals(101.01f, one[0], 1e-5f);
      assertEquals(-10.18f, one[1], 1e-5f);
      assertEquals(4.045f, one[2], 1e-5f);
      assertArrayEquals(one, partitioned, 2e-5f);
    }
  }

  @Test
  public void oppositeLargeVelocityEndpointsSampleWithoutOverflow() {
    ParticleBurstSpec3D description =
        new ParticleBurstSpec3D(
            new Vector3(),
            new Vector3(-Float.MAX_VALUE, 0, 0),
            new Vector3(Float.MAX_VALUE, 0, 0),
            new Vector3(),
            1e-7f,
            1,
            Color.WHITE,
            1,
            42);
    try (Game game = new Game()) {
      ParticleBurstPool3D pool = game.addSystem(new ParticleBurstPool3D(Clock.WORLD, 1, 1));
      game.start();
      pool.burst(description);
      game.step(1e-8f);
      float[] record = new float[8];
      assertEquals(1, pool.copyRenderData(record));
      assertTrue(Float.isFinite(record[0]));
      assertTrue(Math.abs(record[0]) < 4e31f);
    }
  }

  @Test
  public void supportedLargeWorldValuesStayFiniteAndUnsupportedEnvelopeIsRejectedAtomically() {
    float max = Float.MAX_VALUE;
    ParticleBurstSpec3D largeButSafe =
        new ParticleBurstSpec3D(
            new Vector3(max / 16, -max / 16, max / 16),
            new Vector3(max / 64, -max / 64, max / 64),
            new Vector3(max / 64, -max / 64, max / 64),
            new Vector3(-max / 512, max / 512, 0),
            .25f,
            max / 64,
            Color.WHITE,
            1,
            11);
    try (Game game = new Game()) {
      ParticleBurstPool3D pool = game.addSystem(new ParticleBurstPool3D(Clock.WORLD, 2, 2));
      game.start();
      ParticleBurstHandle3D live = pool.burst(largeButSafe);
      game.step(.125f);
      float[] record = new float[16];
      assertEquals(1, pool.copyRenderData(record));
      assertTrue(Float.isFinite(record[0]));
      assertTrue(Float.isFinite(record[1]));
      assertTrue(Float.isFinite(record[2]));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ParticleBurstSpec3D(
                  new Vector3(),
                  new Vector3(-max, 0, 0),
                  new Vector3(max, 0, 0),
                  new Vector3(),
                  1,
                  1,
                  Color.WHITE,
                  1,
                  12));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ParticleBurstSpec3D(
                  new Vector3(max * .24f, 0, 0),
                  new Vector3(),
                  new Vector3(),
                  new Vector3(),
                  1e-10f,
                  max * .02f,
                  Color.WHITE,
                  1,
                  13));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ParticleBurstSpec3D(
                  new Vector3(),
                  new Vector3(),
                  new Vector3(),
                  new Vector3(max, 0, 0),
                  1,
                  1,
                  Color.WHITE,
                  1,
                  14));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ParticleBurstSpec3D(
                  new Vector3(),
                  new Vector3(max, 0, 0),
                  new Vector3(max, 0, 0),
                  new Vector3(max, 0, 0),
                  .01f,
                  1,
                  Color.WHITE,
                  1,
                  17));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ParticleBurstSpec3D(
                  new Vector3(max * .2f, 0, 0),
                  new Vector3(max * .1f, 0, 0),
                  new Vector3(max * .1f, 0, 0),
                  new Vector3(),
                  .6f,
                  1,
                  Color.WHITE,
                  1,
                  15));
      assertEquals(1, pool.getActiveParticleCount());
      assertTrue(live.isActive());
      assertNotNull(
          pool.burst(
              new ParticleBurstSpec3D(
                  new Vector3(),
                  new Vector3(),
                  new Vector3(),
                  new Vector3(),
                  1,
                  1,
                  Color.WHITE,
                  1,
                  16)));
      assertEquals(2, pool.getActiveParticleCount());
    }
  }

  @Test
  public void rendererRejectsGl20BeforeAnyGpuStateMutation() {
    UnsupportedOperationException error =
        assertThrows(
            UnsupportedOperationException.class,
            () -> ParticleBurstRenderer3D.requireGl30Support(false));
    assertTrue(error.getMessage().contains("requires GL30"));
    ParticleBurstRenderer3D.requireGl30Support(true);
  }

  @Test
  public void nearFloatMaximumTrajectoryUsesAnalyticDoubleAgeWithoutRoundingDrift() {
    float start = Float.intBitsToFloat(Float.floatToIntBits(Float.MAX_VALUE) - 10);
    float acceleration = (float) (Math.ulp(Float.MAX_VALUE) * 9.6d / .1d);
    ParticleBurstSpec3D nearMaximum =
        new ParticleBurstSpec3D(
            new Vector3(),
            new Vector3(start, 0, 0),
            new Vector3(start, 0, 0),
            new Vector3(acceleration, 0, 0),
            .1f,
            1,
            Color.WHITE,
            1,
            1);
    ParticleBurstSpec3D independent =
        new ParticleBurstSpec3D(
            new Vector3(5, 0, 0),
            new Vector3(2, 0, 0),
            new Vector3(2, 0, 0),
            new Vector3(1, 0, 0),
            1,
            .25f,
            Color.ORANGE,
            1,
            2);
    try (Game game = new Game()) {
      ParticleBurstPool3D pool = game.addSystem(new ParticleBurstPool3D(Clock.WORLD, 2, 2));
      game.start();
      ParticleBurstHandle3D nearHandle = pool.burst(nearMaximum);
      ParticleBurstHandle3D independentHandle = pool.burst(independent);
      assertNotNull(nearHandle);
      assertNotNull(independentHandle);
      double delta = .00625f;
      float[] records = new float[16];
      for (int i = 1; i <= 15; i++) {
        game.step((float) delta);
        assertTrue(nearHandle.isActive());
        assertTrue(independentHandle.isActive());
        assertEquals(2, pool.copyRenderData(records));
        double age = delta * i;
        float analyticNear = (float) (start * age + .5 * acceleration * age * age);
        float analyticIndependent = (float) (5 + 2 * age + .5 * age * age);
        assertTrue(Float.isFinite(records[0]));
        assertEquals(analyticNear, records[0], 0);
        assertEquals(analyticIndependent, records[8], 1e-5f);
      }
      game.step((float) delta);
      assertFalse(nearHandle.isActive());
      assertTrue(independentHandle.isActive());
      assertEquals(1, pool.getActiveParticleCount());
      assertEquals(1, pool.copyRenderData(records));
      assertEquals((float) (5 + 2 * .1d + .5 * .1d * .1d), records[0], 1e-5f);
    }
  }
}
