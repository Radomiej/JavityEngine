package org.javity.demo;

import org.javity.voxel.BlockDefinition;
import org.javity.voxel.BlockRegistry;
import org.javity.voxel.ChunkPos;
import org.javity.voxel.VoxelWorld;

/** Deterministic, finite terrain fixture used by the voxel demo and its CPU tests. */
final class VoxelTerrain {
  static final int CHUNK_SIZE = 16;
  static final int PATCH_CHUNK_RADIUS = 1;
  static final int PATCH_CHUNK_HEIGHT = 2;
  static final int MAX_LOADED_CHUNKS = 54;

  private final long seed;
  private final BlockRegistry registry = new BlockRegistry();
  private final int grass;
  private final int dirt;
  private final int stone;
  private final int wood;
  private final int sand;
  private final int leaves;
  private final VoxelWorld world;
  private boolean populated;

  VoxelTerrain(long seed) {
    this.seed = seed;
    // NEG_X, POS_X, NEG_Y, POS_Y, NEG_Z, POS_Z. Atlas tile zero is image-top.
    grass = registry.register(new BlockDefinition("grass", 1, 1, 2, 0, 1, 1));
    dirt = registry.register(new BlockDefinition("dirt", 2));
    stone = registry.register(new BlockDefinition("stone", 3));
    wood = registry.register(new BlockDefinition("wood", 4, 4, 4, 5, 4, 4));
    sand = registry.register(new BlockDefinition("sand", 6));
    leaves = registry.register(new BlockDefinition("leaves", 7));
    world = new VoxelWorld(registry, MAX_LOADED_CHUNKS);
  }

  void populate() {
    if (populated) return;
    populated = true;
    for (int chunkY = 0; chunkY < PATCH_CHUNK_HEIGHT; chunkY++)
      for (int chunkZ = -PATCH_CHUNK_RADIUS; chunkZ <= PATCH_CHUNK_RADIUS; chunkZ++)
        for (int chunkX = -PATCH_CHUNK_RADIUS; chunkX <= PATCH_CHUNK_RADIUS; chunkX++)
          world.loadChunk(new ChunkPos(chunkX, chunkY, chunkZ));

    for (int z = -16; z < 32; z++)
      for (int x = -16; x < 32; x++) {
        int surface = surfaceHeight(x, z);
        for (int y = 0; y < surface; y++) {
          int block = y == surface - 1 ? surfaceBlock(x, z) : y >= surface - 4 ? dirt : stone;
          world.setBlock(x, y, z, block);
        }
      }
    addTree(treeX(), treeZ());
    addTree(treeX() + 17, treeZ() + 13);
  }

  VoxelWorld world() {
    return world;
  }

  BlockRegistry registry() {
    return registry;
  }

  int grassId() {
    return grass;
  }

  int dirtId() {
    return dirt;
  }

  int stoneId() {
    return stone;
  }

  int woodId() {
    return wood;
  }

  int sandId() {
    return sand;
  }

  int leavesId() {
    return leaves;
  }

  private int surfaceBlock(int x, int z) {
    // Sparse sandy clearings break up the grass without introducing transparent blocks.
    return Math.floorMod(hash(seed ^ 0x6a09e667f3bcc909L, x, z), 37) == 0 ? sand : grass;
  }

  private int surfaceHeight(int x, int z) {
    int peakX = -2 + Math.floorMod(hash(seed, 0x1357, 0x2468), 13);
    int peakZ = -3 + Math.floorMod(hash(seed, 0xabc, 0xdef), 13);
    int dx = Math.abs(x - peakX);
    int dz = Math.abs(z - peakZ);
    int mainHill = Math.max(0, 9 - (dx + dz) / 2);

    int secondX = 14 + Math.floorMod(hash(seed ^ 0x9e3779b97f4a7c15L, 7, 11), 11);
    int secondZ = 8 + Math.floorMod(hash(seed ^ 0x3c6ef372fe94f82aL, 13, 17), 11);
    int secondaryHill = Math.max(0, 6 - (Math.abs(x - secondX) + Math.abs(z - secondZ)) / 3);
    int smallNoise = Math.floorMod(hash(seed, x, z), 3) - 1;
    return Math.max(4, Math.min(15, 5 + mainHill + secondaryHill + smallNoise));
  }

  private int treeX() {
    return -12 + Math.floorMod(hash(seed ^ 0xbb67ae8584caa73bL, 19, 23), 12);
  }

  private int treeZ() {
    return -12 + Math.floorMod(hash(seed ^ 0x510e527fade682d1L, 29, 31), 12);
  }

  private void addTree(int x, int z) {
    if (x < -15 || x > 30 || z < -15 || z > 30) return;
    int base = surfaceHeight(x, z);
    for (int y = base; y < base + 4; y++) world.setBlock(x, y, z, wood);
    for (int dy = 2; dy <= 4; dy++) {
      int radius = dy == 4 ? 1 : 2;
      for (int dz = -radius; dz <= radius; dz++)
        for (int dx = -radius; dx <= radius; dx++) {
          if (dy == 4 && Math.abs(dx) + Math.abs(dz) > 2) continue;
          if (dx == 0 && dz == 0 && dy < 4) continue;
          int leafX = x + dx;
          int leafY = base + dy;
          int leafZ = z + dz;
          if (leafX >= -16 && leafX < 32 && leafZ >= -16 && leafZ < 32 && leafY < 32)
            world.setBlock(leafX, leafY, leafZ, leaves);
        }
    }
  }

  private static int hash(long seed, int x, int z) {
    long value = seed ^ ((long) x * 0x9e3779b185ebca87L) ^ ((long) z * 0xc2b2ae3d27d4eb4fL);
    value ^= value >>> 30;
    value *= 0xbf58476d1ce4e5b9L;
    value ^= value >>> 27;
    value *= 0x94d049bb133111ebL;
    value ^= value >>> 31;
    return (int) (value ^ (value >>> 32));
  }
}
