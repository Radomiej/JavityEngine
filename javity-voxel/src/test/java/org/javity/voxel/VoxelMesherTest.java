package org.javity.voxel;

import static org.junit.Assert.*;

import org.junit.Test;

public class VoxelMesherTest {
  private BlockRegistry registry() {
    BlockRegistry registry = new BlockRegistry();
    registry.register(new BlockDefinition("stone", 0, 1, 2, 3, 4, 5));
    new VoxelWorld(registry, 1);
    return registry;
  }

  private AtlasLayout atlas() {
    return new AtlasLayout(80, 40, 16, 2, 4, 2);
  }

  private VoxelMeshData mesh(int[][] blocks) {
    BlockRegistry registry = registry();
    VoxelWorld world = new VoxelWorld(registry, 1);
    ChunkPos position = new ChunkPos(0, 0, 0);
    world.loadChunk(position);
    for (int[] block : blocks) world.setBlock(block[0], block[1], block[2], 1);
    return VoxelMesher.build(world.snapshot(position), registry, atlas());
  }

  @Test
  public void emitsOnlyExposedFacesForEmptySingleAdjacentAndFullChunks() {
    assertEquals(0, mesh(new int[0][]).faceCount());
    assertEquals(6, mesh(new int[][] {{0, 0, 0}}).faceCount());
    assertEquals(10, mesh(new int[][] {{0, 0, 0}, {1, 0, 0}}).faceCount());
    BlockRegistry registry = registry();
    VoxelWorld world = new VoxelWorld(registry, 1);
    ChunkPos p = new ChunkPos(0, 0, 0);
    world.loadChunk(p);
    for (int y = 0; y < 16; y++)
      for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) world.setBlock(x, y, z, 1);
    assertEquals(1536, VoxelMesher.build(world.snapshot(p), registry, atlas()).faceCount());
  }

  @Test
  public void adjacentFullChunksHideTheirSharedSeam() {
    BlockRegistry registry = registry();
    VoxelWorld world = new VoxelWorld(registry, 2);
    ChunkPos west = new ChunkPos(0, 0, 0), east = new ChunkPos(1, 0, 0);
    world.loadChunk(west);
    world.loadChunk(east);
    for (int y = 0; y < 16; y++)
      for (int z = 0; z < 16; z++)
        for (int x = 0; x < 16; x++) {
          world.setBlock(x, y, z, 1);
          world.setBlock(x + 16, y, z, 1);
        }
    assertEquals(1280, VoxelMesher.build(world.snapshot(west), registry, atlas()).faceCount());
    assertEquals(1280, VoxelMesher.build(world.snapshot(east), registry, atlas()).faceCount());
  }

  @Test
  public void verticesHaveOutwardNormalsConsistentWindingAndFaceAtlasTiles() {
    BlockRegistry registry = registry();
    VoxelMeshData data = mesh(new int[][] {{3, 4, 5}});
    float[] vertices = data.vertices();
    short[] indices = data.indices();
    assertEquals(192, vertices.length);
    assertEquals(36, indices.length);
    assertEquals(new ChunkPos(0, 0, 0), data.position());
    for (int face = 0; face < 6; face++) {
      int vi = face * 4 * 8;
      float nx = vertices[vi + 3], ny = vertices[vi + 4], nz = vertices[vi + 5];
      BlockFace expected = BlockFace.values()[face];
      assertEquals(expected.dx(), nx, 0f);
      assertEquals(expected.dy(), ny, 0f);
      assertEquals(expected.dz(), nz, 0f);
      int ia = Short.toUnsignedInt(indices[face * 6]);
      int ib = Short.toUnsignedInt(indices[face * 6 + 1]);
      int ic = Short.toUnsignedInt(indices[face * 6 + 2]);
      float[] ab = subtract(vertices, ib * 8, ia * 8);
      float[] ac = subtract(vertices, ic * 8, ia * 8);
      float[] cross = cross(ab, ac);
      assertTrue(cross[0] * nx + cross[1] * ny + cross[2] * nz > 0f);
      AtlasLayout layout = atlas();
      int tile = registry.get(1).tile(expected);
      float u0 = layout.u0(tile), v0 = layout.v0(tile), u1 = layout.u1(tile), v1 = layout.v1(tile);
      for (int vertex = 0; vertex < 4; vertex++) {
        float u = vertices[vi + vertex * 8 + 6], v = vertices[vi + vertex * 8 + 7];
        assertTrue(u >= u0 && u <= u1);
        assertTrue(v >= v0 && v <= v1);
        if (expected.dx() != 0 || expected.dz() != 0) {
          float vertexY = vertices[vi + vertex * 8 + 1];
          assertEquals(vertexY > 4 ? v0 : v1, v, 0f);
        }
      }
    }
    float[] copy = data.vertices();
    copy[0] = 999f;
    assertNotEquals(999f, data.vertices()[0], 0f);
  }

  @Test
  public void checkerboardStaysWithinUnsignedShortVertexRange() {
    BlockRegistry registry = registry();
    VoxelWorld world = new VoxelWorld(registry, 1);
    ChunkPos p = new ChunkPos(0, 0, 0);
    world.loadChunk(p);
    for (int y = 0; y < 16; y++)
      for (int z = 0; z < 16; z++)
        for (int x = 0; x < 16; x++) if (((x + y + z) & 1) == 0) world.setBlock(x, y, z, 1);
    VoxelMeshData data = VoxelMesher.build(world.snapshot(p), registry, atlas());
    assertEquals(12_288, data.faceCount());
    assertEquals(49_152, data.vertexCount());
    assertEquals(73_728, data.indexCount());
    short[] indices = data.indices();
    int maximumIndex = 0;
    boolean foundAboveSignedRange = false;
    for (int face = 0; face < data.faceCount(); face++) {
      int base = face * 4;
      int offset = face * 6;
      assertArrayEquals(
          new int[] {base, base + 1, base + 2, base, base + 2, base + 3},
          new int[] {
            Short.toUnsignedInt(indices[offset]),
            Short.toUnsignedInt(indices[offset + 1]),
            Short.toUnsignedInt(indices[offset + 2]),
            Short.toUnsignedInt(indices[offset + 3]),
            Short.toUnsignedInt(indices[offset + 4]),
            Short.toUnsignedInt(indices[offset + 5])
          });
    }
    for (short index : indices) {
      int value = Short.toUnsignedInt(index);
      assertTrue(value < 49_152);
      maximumIndex = Math.max(maximumIndex, value);
      foundAboveSignedRange |= value > Short.MAX_VALUE;
    }
    assertEquals(49_151, maximumIndex);
    assertTrue(foundAboveSignedRange);
    float[] copy = data.vertices();
    copy[0] = 71f;
    short[] indexCopy = data.indices();
    indexCopy[0] = -1;
    assertNotEquals(71f, data.vertices()[0], 0f);
    assertTrue(Short.toUnsignedInt(data.indices()[0]) < 49_152);
  }

  @Test
  public void storesMaximumUnsignedBlockIdInShortChunkStorage() {
    BlockRegistry registry = new BlockRegistry();
    BlockDefinition definition = new BlockDefinition("block", 0);
    for (int id = 1; id <= 65_535; id++) assertEquals(id, registry.register(definition));
    assertThrows(IllegalStateException.class, () -> registry.register(definition));
    VoxelWorld world = new VoxelWorld(registry, 1);
    world.loadChunk(new ChunkPos(0, 0, 0));
    assertTrue(world.setBlock(0, 0, 0, 65_535));
    assertEquals(65_535, world.getBlock(0, 0, 0));
    assertEquals(65_535, world.snapshot(new ChunkPos(0, 0, 0)).block(0, 0, 0));
  }

  private static float[] subtract(float[] a, int ai, int bi) {
    return new float[] {a[ai] - a[bi], a[ai + 1] - a[bi + 1], a[ai + 2] - a[bi + 2]};
  }

  private static float[] cross(float[] a, float[] b) {
    return new float[] {
      a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]
    };
  }
}
