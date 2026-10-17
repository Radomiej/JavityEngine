package org.javity.demo;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;
import org.javity.voxel.BlockDefinition;
import org.javity.voxel.BlockRegistry;
import org.javity.voxel.ChunkPos;
import org.javity.voxel.VoxelWorld;
import org.junit.Test;

public class VoxelTerrainTest {
  @Test
  public void loadsExactlyTheFiniteThreeByThreeByTwoPatch() {
    VoxelTerrain terrain = new VoxelTerrain(0x5eedL);
    terrain.populate();

    Set<ChunkPos> expected = new HashSet<>();
    for (int y = 0; y < 2; y++)
      for (int z = -1; z <= 1; z++)
        for (int x = -1; x <= 1; x++) expected.add(new ChunkPos(x, y, z));

    assertEquals(18, terrain.world().loadedChunkCount());
    assertEquals(expected, new HashSet<>(terrain.world().loadedChunks()));
    assertTrue(
        "terrain must reach negative world coordinates",
        nonAirIn(terrain.world(), -16, 0, -16, 0, 32, 0));
    assertEquals(0, terrain.world().getBlock(-17, 31, 0));
    assertEquals(0, terrain.world().getBlock(32, 31, 0));
    assertEquals(0, terrain.world().getBlock(0, 31, 32));
  }

  @Test
  public void seededTerrainIsStableAndDifferentSeedsChangeItsShape() {
    VoxelTerrain first = new VoxelTerrain(0x5eedL);
    VoxelTerrain same = new VoxelTerrain(0x5eedL);
    VoxelTerrain other = new VoxelTerrain(0x5eeeL);
    first.populate();
    same.populate();
    other.populate();

    assertEquals(summary(first.world()), summary(same.world()));
    assertNotEquals(
        "seed must influence the generated terrain",
        summary(first.world()),
        summary(other.world()));
  }

  @Test
  public void originalBlockSetProducesHillsAndAtLeastOneTree() {
    VoxelTerrain terrain = new VoxelTerrain(0x5eedL);
    terrain.populate();
    VoxelWorld world = terrain.world();

    int grass = id(terrain.registry(), "grass");
    int dirt = id(terrain.registry(), "dirt");
    int stone = id(terrain.registry(), "stone");
    int wood = id(terrain.registry(), "wood");
    int leaves = id(terrain.registry(), "leaves");
    int lowTop = highestSolid(world, -12, -12);
    int highTop = highestSolid(world, 0, 0);
    assertTrue("height field should form a visible hill", Math.abs(highTop - lowTop) >= 2);
    assertTrue("terrain should contain grass", count(world, grass) > 0);
    assertTrue("terrain should contain dirt", count(world, dirt) > 0);
    assertTrue("terrain should contain stone", count(world, stone) > 0);
    assertTrue("terrain should contain a tree trunk", count(world, wood) >= 3);
    assertTrue("terrain should contain tree leaves", count(world, leaves) >= 8);
  }

  @Test
  public void repeatedPopulationDoesNotAddDirtyWorkOrChangeTheWorld() {
    VoxelTerrain terrain = new VoxelTerrain(0x5eedL);
    terrain.populate();
    long before = summary(terrain.world());
    var dirtyBefore = terrain.world().dirtyChunks();

    terrain.populate();

    assertEquals(before, summary(terrain.world()));
    assertEquals(dirtyBefore, terrain.world().dirtyChunks());
    assertEquals(18, terrain.world().dirtyChunkCount());
  }

  private static boolean nonAirIn(
      VoxelWorld world, int x0, int y0, int z0, int x1, int y1, int z1) {
    for (int y = y0; y <= y1; y++)
      for (int z = z0; z <= z1; z++)
        for (int x = x0; x <= x1; x++) if (world.getBlock(x, y, z) != 0) return true;
    return false;
  }

  private static int id(BlockRegistry registry, String name) {
    for (int id = 1; id <= registry.size(); id++) {
      BlockDefinition definition = registry.get(id);
      if (definition.name().equals(name)) return id;
    }
    fail("missing terrain block definition: " + name);
    return -1;
  }

  private static long summary(VoxelWorld world) {
    long hash = 0xcbf29ce484222325L;
    for (int y = 0; y < 32; y++)
      for (int z = -16; z < 32; z++)
        for (int x = -16; x < 32; x++) {
          hash ^= world.getBlock(x, y, z);
          hash *= 0x100000001b3L;
        }
    return hash;
  }

  private static int highestSolid(VoxelWorld world, int x, int z) {
    for (int y = 31; y >= 0; y--) if (world.getBlock(x, y, z) != 0) return y;
    return -1;
  }

  private static int count(VoxelWorld world, int blockId) {
    int count = 0;
    for (int y = 0; y < 32; y++)
      for (int z = -16; z < 32; z++)
        for (int x = -16; x < 32; x++) if (world.getBlock(x, y, z) == blockId) count++;
    return count;
  }
}
