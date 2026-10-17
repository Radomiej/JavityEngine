package org.javity.voxel;

/** Integer coordinates of a 16-cube chunk. */
public record ChunkPos(int x, int y, int z) {
  public static final int MIN_COORDINATE = -65_536;
  public static final int MAX_COORDINATE = 65_535;

  public ChunkPos {
    if (!inRange(x) || !inRange(y) || !inRange(z)) {
      throw new IllegalArgumentException("Chunk coordinates must be in [-65536, 65535]");
    }
  }

  private static boolean inRange(int value) {
    return value >= MIN_COORDINATE && value <= MAX_COORDINATE;
  }

  public int originX() {
    return x * VoxelWorld.CHUNK_SIZE;
  }

  public int originY() {
    return y * VoxelWorld.CHUNK_SIZE;
  }

  public int originZ() {
    return z * VoxelWorld.CHUNK_SIZE;
  }

  public static ChunkPos fromBlock(int x, int y, int z) {
    BlockPos.validateCoordinates(x, y, z);
    return new ChunkPos(
        Math.floorDiv(x, VoxelWorld.CHUNK_SIZE),
        Math.floorDiv(y, VoxelWorld.CHUNK_SIZE),
        Math.floorDiv(z, VoxelWorld.CHUNK_SIZE));
  }
}
