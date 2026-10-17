package org.javity.voxel;

import static org.junit.Assert.*;

import com.badlogic.gdx.math.Vector3;
import java.util.Optional;
import org.junit.Test;

public class VoxelRaycasterTest {
  private VoxelWorld worldWith(int x, int y, int z) {
    BlockRegistry registry = new BlockRegistry();
    registry.register(new BlockDefinition("stone", 0));
    VoxelWorld world = new VoxelWorld(registry, 2);
    world.loadChunk(ChunkPos.fromBlock(x, y, z));
    world.setBlock(x, y, z, 1);
    return world;
  }

  @Test
  public void normalizesDirectionAndReturnsPlacementFaceAndWorldDistance() {
    VoxelWorld world = worldWith(3, 0, 0);
    Vector3 origin = new Vector3(0.5f, 0.5f, 0.5f);
    Vector3 direction = new Vector3(2, 0, 0);
    VoxelHit hit = VoxelRaycaster.cast(world, origin, direction, 8, 20).orElseThrow();
    assertEquals(new BlockPos(3, 0, 0), hit.block());
    assertEquals(BlockFace.NEG_X, hit.enteredFace());
    assertEquals(new BlockPos(2, 0, 0), hit.placementCell());
    assertEquals(2.5, hit.distance(), 1e-7);
    assertEquals(new Vector3(2, 0, 0), direction);
    assertEquals(new Vector3(0.5f, 0.5f, 0.5f), origin);
  }

  @Test
  public void handlesNegativeStepsAndNegativeWorldCoordinates() {
    VoxelWorld world = worldWith(-2, 0, 0);
    VoxelHit hit =
        VoxelRaycaster.cast(world, new Vector3(0.5f, 0.5f, 0.5f), new Vector3(-1, 0, 0), 8, 20)
            .orElseThrow();
    assertEquals(new BlockPos(-2, 0, 0), hit.block());
    assertEquals(BlockFace.POS_X, hit.enteredFace());
    assertEquals(new BlockPos(-1, 0, 0), hit.placementCell());
    assertEquals(1.5, hit.distance(), 1e-7);
  }

  @Test
  public void returnsCorrectEnteredFaceAndPlacementForAllSixAxes() {
    assertAxisHit(3, 0, 0, new Vector3(1, 0, 0), BlockFace.NEG_X, new BlockPos(2, 0, 0), 2.5);
    assertAxisHit(-2, 0, 0, new Vector3(-1, 0, 0), BlockFace.POS_X, new BlockPos(-1, 0, 0), 1.5);
    assertAxisHit(0, 3, 0, new Vector3(0, 1, 0), BlockFace.NEG_Y, new BlockPos(0, 2, 0), 2.5);
    assertAxisHit(0, -2, 0, new Vector3(0, -1, 0), BlockFace.POS_Y, new BlockPos(0, -1, 0), 1.5);
    assertAxisHit(0, 0, 3, new Vector3(0, 0, 1), BlockFace.NEG_Z, new BlockPos(0, 0, 2), 2.5);
    assertAxisHit(0, 0, -2, new Vector3(0, 0, -1), BlockFace.POS_Z, new BlockPos(0, 0, -1), 1.5);
  }

  @Test
  public void startInsideSolidHasNoInventedPlacementFace() {
    VoxelWorld world = worldWith(0, 0, 0);
    VoxelHit hit =
        VoxelRaycaster.cast(world, new Vector3(0.5f, 0.5f, 0.5f), new Vector3(1, 0, 0), 1, 4)
            .orElseThrow();
    assertEquals(new BlockPos(0, 0, 0), hit.block());
    assertNull(hit.enteredFace());
    assertNull(hit.placementCell());
    assertEquals(0, hit.distance(), 0);
  }

  @Test
  public void exactBoundaryAndAxisTiesUseFloorThenXyZOrder() {
    VoxelWorld boundary = worldWith(1, 0, 0);
    VoxelHit atBoundary =
        VoxelRaycaster.cast(boundary, new Vector3(1, 0.5f, 0.5f), new Vector3(1, 0, 0), 2, 4)
            .orElseThrow();
    assertEquals(0, atBoundary.distance(), 0);
    assertNull(atBoundary.enteredFace());

    VoxelWorld tie = worldWith(1, 1, 0);
    VoxelHit tied =
        VoxelRaycaster.cast(tie, new Vector3(0.5f, 0.5f, 0.5f), new Vector3(1, 1, 0), 4, 4)
            .orElseThrow();
    assertEquals(new BlockPos(1, 1, 0), tied.block());
    assertEquals(BlockFace.NEG_Y, tied.enteredFace());
    assertEquals(new BlockPos(1, 0, 0), tied.placementCell());
  }

  @Test
  public void missesRespectDistanceAndStepLimitsAndAbsentChunksAreAir() {
    VoxelWorld world = worldWith(3, 0, 0);
    assertEquals(
        Optional.empty(),
        VoxelRaycaster.cast(world, new Vector3(.5f, .5f, .5f), new Vector3(1, 0, 0), 2.49, 20));
    assertEquals(
        Optional.empty(),
        VoxelRaycaster.cast(world, new Vector3(.5f, .5f, .5f), new Vector3(1, 0, 0), 8, 2));
    assertEquals(
        Optional.empty(),
        VoxelRaycaster.cast(world, new Vector3(.5f, .5f, .5f), new Vector3(0, 1, 0), 8, 20));
    assertTrue(
        VoxelRaycaster.cast(world, new Vector3(.5f, .5f, .5f), new Vector3(1, 0, 0), 2.5, 20)
            .isPresent());
  }

  @Test
  public void rejectsInvalidRayInputsAndOutOfWorldOrigin() {
    VoxelWorld world = worldWith(0, 0, 0);
    assertThrows(
        IllegalArgumentException.class,
        () -> VoxelRaycaster.cast(world, new Vector3(), new Vector3(), 3, 4));
    assertThrows(
        IllegalArgumentException.class,
        () -> VoxelRaycaster.cast(world, new Vector3(Float.NaN, 0, 0), new Vector3(1, 0, 0), 3, 4));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            VoxelRaycaster.cast(
                world, new Vector3(), new Vector3(Float.POSITIVE_INFINITY, 0, 0), 3, 4));
    assertThrows(
        IllegalArgumentException.class,
        () -> VoxelRaycaster.cast(world, new Vector3(), new Vector3(1, 0, 0), -1, 4));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            VoxelRaycaster.cast(
                world, new Vector3(), new Vector3(1, 0, 0), Double.POSITIVE_INFINITY, 4));
    assertThrows(
        IllegalArgumentException.class,
        () -> VoxelRaycaster.cast(world, new Vector3(), new Vector3(1, 0, 0), 3, 0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            VoxelRaycaster.cast(world, new Vector3(1_048_576f, 0, 0), new Vector3(1, 0, 0), 3, 4));
  }

  @Test
  public void terminatesAtWorldRangeWithoutIntegerOverflow() {
    BlockRegistry registry = new BlockRegistry();
    registry.register(new BlockDefinition("stone", 0));
    VoxelWorld world = new VoxelWorld(registry, 2);
    world.loadChunk(new ChunkPos(65_535, 0, 0));
    world.loadChunk(new ChunkPos(-65_536, 0, 0));
    assertEquals(
        Optional.empty(),
        VoxelRaycaster.cast(
            world, new Vector3(1_048_574.5f, .5f, .5f), new Vector3(1, 0, 0), 100, 10));
    assertEquals(
        Optional.empty(),
        VoxelRaycaster.cast(
            world, new Vector3(-1_048_575.5f, .5f, .5f), new Vector3(-1, 0, 0), 100, 10));
  }

  @Test
  public void normalizesExtremeFiniteNonzeroDirectionsWithoutOverflowOrUnderflow() {
    VoxelWorld world = worldWith(3, 0, 0);
    assertEquals(
        new BlockPos(3, 0, 0),
        VoxelRaycaster.cast(world, 0.5, 0.5, 0.5, Double.MAX_VALUE, 0, 0, 8, 20)
            .orElseThrow()
            .block());
    assertEquals(
        new BlockPos(3, 0, 0),
        VoxelRaycaster.cast(world, 0.5, 0.5, 0.5, Double.MIN_VALUE, 0, 0, 8, 20)
            .orElseThrow()
            .block());
  }

  private void assertAxisHit(
      int x, int y, int z, Vector3 direction, BlockFace face, BlockPos placement, double distance) {
    VoxelHit hit =
        VoxelRaycaster.cast(worldWith(x, y, z), new Vector3(.5f, .5f, .5f), direction, 8, 20)
            .orElseThrow();
    assertEquals(new BlockPos(x, y, z), hit.block());
    assertEquals(face, hit.enteredFace());
    assertEquals(placement, hit.placementCell());
    assertEquals(distance, hit.distance(), 1e-7);
  }
}
