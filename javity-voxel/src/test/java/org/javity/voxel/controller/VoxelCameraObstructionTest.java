package org.javity.voxel.controller;

import static org.junit.Assert.*;

import com.badlogic.gdx.math.Vector3;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.voxel.BlockDefinition;
import org.javity.voxel.BlockRegistry;
import org.javity.voxel.ChunkPos;
import org.javity.voxel.VoxelWorld;
import org.junit.Test;

public class VoxelCameraObstructionTest {
  private static final int STONE = 1;

  private VoxelWorld world() {
    BlockRegistry registry = new BlockRegistry();
    registry.register(new BlockDefinition("stone", 0));
    VoxelWorld world = new VoxelWorld(registry, 27);
    for (int x = -1; x <= 1; x++)
      for (int y = -1; y <= 1; y++)
        for (int z = -1; z <= 1; z++) world.loadChunk(new ChunkPos(x, y, z));
    return world;
  }

  @Test
  public void continuousWallCornerCeilingAndNegativeCoordinatesStopTheBoom() {
    VoxelWorld world = world();
    VoxelCameraObstruction query = new VoxelCameraObstruction(world);
    world.setBlock(3, 1, 0, STONE);
    float wall =
        query.sweepFraction(new Vector3(.5f, 1.5f, .5f), new Vector3(5.5f, 1.5f, .5f), .15f, null);
    assertTrue(wall > 0 && wall < .5f);
    world.setBlock(3, 1, 0, 0);
    world.setBlock(1, 1, 2, STONE);
    float corner =
        query.sweepFraction(new Vector3(.7f, 1.5f, -1), new Vector3(.7f, 1.5f, 4), .35f, null);
    assertTrue("sphere bound must catch obstacle missed by center ray", corner < 1);
    world.setBlock(1, 1, 2, 0);
    world.setBlock(0, 3, 0, STONE);
    assertTrue(
        query.sweepFraction(new Vector3(.5f, 1.5f, .5f), new Vector3(.5f, 4.5f, .5f), .15f, null)
            < 1);
    world.setBlock(-2, 1, 0, STONE);
    assertTrue(
        query.sweepFraction(new Vector3(-.5f, 1.5f, .5f), new Vector3(-5, 1.5f, .5f), .12f, null)
            < 1);
  }

  @Test
  public void unloadedAndStartOverlapAreBlockedAndOwnerHasNoTerrainEffect() {
    VoxelWorld world = world();
    VoxelCameraObstruction query = new VoxelCameraObstruction(world);
    float unloaded =
        query.sweepFraction(new Vector3(31.5f, 2, .5f), new Vector3(34, 2, .5f), .2f, null);
    assertTrue(unloaded >= 0 && unloaded < 1);
    world.setBlock(1, 1, 1, STONE);
    assertEquals(
        0,
        query.sweepFraction(new Vector3(1.5f, 1.5f, 1.5f), new Vector3(4, 1.5f, 1.5f), .1f, null),
        0);
    try (Game game = new Game()) {
      GameObject owner = game.createObject("player");
      float ignored =
          query.sweepFraction(new Vector3(.5f, 1.5f, 1.5f), new Vector3(4, 1.5f, 1.5f), .1f, owner);
      assertEquals(
          query.sweepFraction(new Vector3(.5f, 1.5f, 1.5f), new Vector3(4, 1.5f, 1.5f), .1f, null),
          ignored,
          0);
    }
  }

  @Test
  public void workIsBoundedAndInputsValidated() {
    VoxelCameraObstruction query = new VoxelCameraObstruction(world());
    assertEquals(0, query.sweepFraction(new Vector3(0, 0, 0), new Vector3(32, 32, 32), 0, null), 0);
    assertThrows(
        IllegalArgumentException.class,
        () -> query.sweepFraction(Vector3.Zero, new Vector3(65, 0, 0), .1f, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> query.sweepFraction(Vector3.Zero, Vector3.X, Float.NaN, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> query.sweepFraction(new Vector3(Float.NaN, 0, 0), Vector3.X, .1f, null));
    assertEquals(
        0,
        query.sweepFraction(
            new Vector3(Float.MAX_VALUE, 0, 0), new Vector3(Float.MAX_VALUE, 0, 0), 0, null),
        0);
    float edge =
        query.sweepFraction(
            new Vector3(1_048_575.5f, 2, 0), new Vector3(1_048_576.5f, 2, 0), .1f, null);
    assertTrue(edge >= 0 && edge < 1);
  }

  @Test
  public void touchingWallAllowsMovingAwayAndBlocksMovingToward() {
    VoxelWorld world = world();
    world.setBlock(1, 1, 0, STONE);
    VoxelCameraObstruction query = new VoxelCameraObstruction(world);
    float away =
        query.sweepFraction(new Vector3(.9f, 1.5f, .5f), new Vector3(.5f, 1.5f, .5f), .1f, null);
    float toward =
        query.sweepFraction(new Vector3(.9f, 1.5f, .5f), new Vector3(1.5f, 1.5f, .5f), .1f, null);
    assertEquals(1, away, 0);
    assertEquals(0, toward, 0);
  }
}
