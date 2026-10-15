package org.javity.voxel;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bounded, explicitly loaded, single-threaded voxel storage. */
public final class VoxelWorld {
  public static final int CHUNK_SIZE = 16;
  public static final int BLOCK_COUNT = CHUNK_SIZE * CHUNK_SIZE * CHUNK_SIZE;
  private static final BlockFace[] FACES = BlockFace.values();
  private final BlockRegistry registry;
  private final int maxLoadedChunks;
  private final Map<ChunkPos, short[]> chunks = new LinkedHashMap<>();
  private final Map<ChunkPos, Long> chunkGenerations = new LinkedHashMap<>();
  private final LinkedHashSet<ChunkPos> dirty = new LinkedHashSet<>();
  private long lastChunkGeneration;

  public VoxelWorld(BlockRegistry registry, int maxLoadedChunks) {
    this.registry = Objects.requireNonNull(registry, "registry");
    if (maxLoadedChunks <= 0)
      throw new IllegalArgumentException("maxLoadedChunks must be positive");
    this.maxLoadedChunks = maxLoadedChunks;
    registry.freeze();
  }

  public BlockRegistry registry() {
    return registry;
  }

  public void loadChunk(ChunkPos position) {
    Objects.requireNonNull(position, "position");
    if (chunks.containsKey(position)) return;
    if (chunks.size() >= maxLoadedChunks)
      throw new IllegalStateException("Loaded chunk cap exceeded");
    if (lastChunkGeneration == Long.MAX_VALUE)
      throw new IllegalStateException("Chunk load generation space exhausted");
    short[] blocks = new short[BLOCK_COUNT];
    long generation = lastChunkGeneration + 1;
    chunks.put(position, blocks);
    chunkGenerations.put(position, generation);
    lastChunkGeneration = generation;
    markDirty(position);
    for (BlockFace face : FACES) neighbor(position, face).ifPresent(this::markDirty);
  }

  public boolean unloadChunk(ChunkPos position) {
    Objects.requireNonNull(position, "position");
    if (chunks.remove(position) == null) return false;
    chunkGenerations.remove(position);
    dirty.remove(position);
    for (BlockFace face : FACES) neighbor(position, face).ifPresent(this::markDirty);
    return true;
  }

  public boolean isLoaded(ChunkPos position) {
    return chunks.containsKey(position);
  }

  /**
   * Returns the nonzero generation token for this currently loaded chunk, or zero when unloaded.
   * Each successful load lifetime receives a larger token. Token metadata is stored only for
   * loaded chunks, so it is bounded by the world's loaded-chunk cap.
   */
  public long chunkGeneration(ChunkPos position) {
    Objects.requireNonNull(position, "position");
    return chunkGenerations.getOrDefault(position, 0L);
  }

  public List<ChunkPos> loadedChunks() {
    return List.copyOf(chunks.keySet());
  }

  public int loadedChunkCount() {
    return chunks.size();
  }

  public int dirtyChunkCount() {
    return dirty.size();
  }

  public List<ChunkPos> dirtyChunks() {
    return List.copyOf(dirty);
  }

  public int getBlock(int x, int y, int z) {
    BlockPos.validateCoordinates(x, y, z);
    ChunkPos position = ChunkPos.fromBlock(x, y, z);
    short[] blocks = chunks.get(position);
    return blocks == null
        ? 0
        : Short.toUnsignedInt(
            blocks[blockIndex(Math.floorMod(x, 16), Math.floorMod(y, 16), Math.floorMod(z, 16))]);
  }

  public boolean setBlock(int x, int y, int z, int blockId) {
    BlockPos.validateCoordinates(x, y, z);
    if (blockId < 0 || blockId > 65_535)
      throw new IllegalArgumentException("Block ID must be unsigned 16-bit");
    if (blockId != 0) registry.get(blockId);
    ChunkPos position = ChunkPos.fromBlock(x, y, z);
    short[] blocks = chunks.get(position);
    if (blocks == null)
      throw new IllegalStateException("Cannot edit an unloaded chunk: " + position);
    int localX = Math.floorMod(x, 16), localY = Math.floorMod(y, 16), localZ = Math.floorMod(z, 16);
    int index = blockIndex(localX, localY, localZ);
    if (Short.toUnsignedInt(blocks[index]) == blockId) return false;
    blocks[index] = (short) blockId;
    markDirty(position);
    if (localX == 0) neighbor(position, BlockFace.NEG_X).ifPresent(this::markDirty);
    if (localX == 15) neighbor(position, BlockFace.POS_X).ifPresent(this::markDirty);
    if (localY == 0) neighbor(position, BlockFace.NEG_Y).ifPresent(this::markDirty);
    if (localY == 15) neighbor(position, BlockFace.POS_Y).ifPresent(this::markDirty);
    if (localZ == 0) neighbor(position, BlockFace.NEG_Z).ifPresent(this::markDirty);
    if (localZ == 15) neighbor(position, BlockFace.POS_Z).ifPresent(this::markDirty);
    return true;
  }

  public ChunkSnapshot snapshot(ChunkPos position) {
    Objects.requireNonNull(position, "position");
    if (!chunks.containsKey(position))
      throw new IllegalStateException("Chunk is not loaded: " + position);
    short[] snapshot = new short[18 * 18 * 18];
    int originX = position.originX(), originY = position.originY(), originZ = position.originZ();
    for (int y = -1; y <= 16; y++)
      for (int z = -1; z <= 16; z++)
        for (int x = -1; x <= 16; x++) {
          int outsideAxes =
              (x == -1 || x == 16 ? 1 : 0)
                  + (y == -1 || y == 16 ? 1 : 0)
                  + (z == -1 || z == 16 ? 1 : 0);
          if (outsideAxes > 1) continue;
          int wx = originX + x, wy = originY + y, wz = originZ + z;
          if (BlockPos.inRange(wx) && BlockPos.inRange(wy) && BlockPos.inRange(wz)) {
            int block = getBlock(wx, wy, wz);
            snapshot[ChunkSnapshotIndex.index(x + 1, y + 1, z + 1)] = (short) block;
          }
        }
    return new ChunkSnapshot(position, snapshot);
  }

  public void markDirty(ChunkPos position) {
    if (chunks.containsKey(position)) dirty.add(position);
  }

  public void clearDirty(ChunkPos position) {
    dirty.remove(position);
  }

  private static int blockIndex(int x, int y, int z) {
    return (y * 16 + z) * 16 + x;
  }

  private java.util.Optional<ChunkPos> neighbor(ChunkPos position, BlockFace face) {
    long x = (long) position.x() + face.dx();
    long y = (long) position.y() + face.dy();
    long z = (long) position.z() + face.dz();
    if (x < ChunkPos.MIN_COORDINATE
        || x > ChunkPos.MAX_COORDINATE
        || y < ChunkPos.MIN_COORDINATE
        || y > ChunkPos.MAX_COORDINATE
        || z < ChunkPos.MIN_COORDINATE
        || z > ChunkPos.MAX_COORDINATE) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.of(new ChunkPos((int) x, (int) y, (int) z));
  }

  private static final class ChunkSnapshotIndex {
    private static int index(int x, int y, int z) {
      return (y * 18 + z) * 18 + x;
    }
  }
}
