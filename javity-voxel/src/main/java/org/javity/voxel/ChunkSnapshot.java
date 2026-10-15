package org.javity.voxel;

/** Immutable chunk contents and its six face-neighbor halo planes. */
public final class ChunkSnapshot {
  private static final int SIDE = VoxelWorld.CHUNK_SIZE + 2;
  private final ChunkPos position;
  private final short[] blocks;

  ChunkSnapshot(ChunkPos position, short[] blocks) {
    this.position = position;
    this.blocks = blocks.clone();
  }

  public ChunkPos position() {
    return position;
  }

  /** Reads local coordinates in [-1, 16], including the captured face halo. */
  public int block(int localX, int localY, int localZ) {
    if (localX < -1 || localX > 16 || localY < -1 || localY > 16 || localZ < -1 || localZ > 16) {
      throw new IllegalArgumentException("Snapshot coordinates must be in [-1, 16]");
    }
    return Short.toUnsignedInt(blocks[index(localX + 1, localY + 1, localZ + 1)]);
  }

  private static int index(int x, int y, int z) {
    return (y * SIDE + z) * SIDE + x;
  }
}
