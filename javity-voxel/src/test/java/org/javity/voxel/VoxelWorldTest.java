package org.javity.voxel;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public class VoxelWorldTest {
  private BlockRegistry registry() {
    BlockRegistry registry = new BlockRegistry();
    registry.register(new BlockDefinition("stone", 0));
    return registry;
  }

  @Test
  public void usesFloorChunkingForNegativeAndPositiveCoordinates() {
    int[] coordinates = {-17, -16, -1, 0, 15, 16, 17};
    int[] chunks = {-2, -1, -1, 0, 0, 1, 1};
    int[] locals = {15, 0, 15, 0, 15, 0, 1};
    for (int i = 0; i < coordinates.length; i++) {
      ChunkPos chunk = ChunkPos.fromBlock(coordinates[i], 0, 0);
      assertEquals(chunks[i], chunk.x());
      assertEquals(locals[i], Math.floorMod(coordinates[i], 16));
      assertEquals(chunks[i] * 16, chunk.originX());
    }
    assertThrows(IllegalArgumentException.class, () -> new BlockPos(1_048_576, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new ChunkPos(65_536, 0, 0));
  }

  @Test
  public void registryFreezesWhenWorldIsCreatedAndRejectsUnknownIds() {
    BlockRegistry registry = registry();
    VoxelWorld world = new VoxelWorld(registry, 2);
    assertTrue(registry.isFrozen());
    assertThrows(
        IllegalStateException.class, () -> registry.register(new BlockDefinition("dirt", 1)));
    assertThrows(IllegalArgumentException.class, () -> world.setBlock(0, 0, 0, 42));
  }

  @Test
  public void loadsExplicitlyWithinCapAndDoesNotLoadForWrites() {
    VoxelWorld world = new VoxelWorld(registry(), 2);
    ChunkPos a = new ChunkPos(0, 0, 0);
    ChunkPos b = new ChunkPos(-1, 0, 0);
    world.loadChunk(a);
    world.loadChunk(b);
    assertEquals(2, world.loadedChunkCount());
    assertThrows(IllegalStateException.class, () -> world.loadChunk(new ChunkPos(1, 0, 0)));
    assertTrue(world.setBlock(0, 0, 0, 1));
    assertEquals(2, world.loadedChunkCount());
    assertFalse(world.setBlock(0, 0, 0, 1));
    assertEquals(0, world.getBlock(32, 0, 0));
    assertThrows(IllegalStateException.class, () -> world.setBlock(32, 0, 0, 1));
  }

  @Test
  public void chunkGenerationChangesForEveryLoadLifetimeAndIsZeroWhenUnloaded() {
    VoxelWorld world = new VoxelWorld(registry(), 1);
    ChunkPos position = new ChunkPos(4, -2, 7);

    assertEquals(0L, world.chunkGeneration(position));
    world.loadChunk(position);
    long first = world.chunkGeneration(position);
    assertTrue(first > 0L);
    world.loadChunk(position);
    assertEquals(first, world.chunkGeneration(position));

    assertTrue(world.unloadChunk(position));
    assertEquals(0L, world.chunkGeneration(position));
    world.loadChunk(position);
    long second = world.chunkGeneration(position);
    assertTrue(second > first);
  }

  @Test
  public void editsDirtyOwnerAndOnlyLoadedFaceNeighborsAndDeduplicate() {
    VoxelWorld world = new VoxelWorld(registry(), 8);
    ChunkPos owner = new ChunkPos(0, 0, 0);
    for (ChunkPos p :
        List.of(
            owner,
            new ChunkPos(1, 0, 0),
            new ChunkPos(0, 1, 0),
            new ChunkPos(0, 0, 1),
            new ChunkPos(1, 1, 0))) world.loadChunk(p);
    world.clearDirty(owner);
    for (ChunkPos p :
        List.of(
            new ChunkPos(1, 0, 0),
            new ChunkPos(0, 1, 0),
            new ChunkPos(0, 0, 1),
            new ChunkPos(1, 1, 0))) world.clearDirty(p);
    assertTrue(world.setBlock(15, 15, 15, 1));
    assertEquals(
        List.of(owner, new ChunkPos(1, 0, 0), new ChunkPos(0, 1, 0), new ChunkPos(0, 0, 1)),
        world.dirtyChunks());
    assertFalse(world.setBlock(15, 15, 15, 1));
    assertEquals(4, world.dirtyChunkCount());
  }

  @Test
  public void loadAndUnloadInvalidateLoadedNeighborsAndRemoveDirtyEntries() {
    VoxelWorld world = new VoxelWorld(registry(), 4);
    ChunkPos origin = new ChunkPos(0, 0, 0);
    ChunkPos east = new ChunkPos(1, 0, 0);
    world.loadChunk(origin);
    world.clearDirty(origin);
    world.loadChunk(east);
    assertEquals(List.of(east, origin), world.dirtyChunks());
    world.clearDirty(origin);
    world.clearDirty(east);
    assertTrue(world.unloadChunk(east));
    assertEquals(List.of(origin), world.dirtyChunks());
    assertFalse(world.unloadChunk(east));
    world.markDirty(east);
    assertEquals(List.of(origin), world.dirtyChunks());
  }

  @Test
  public void snapshotIsImmutableStandaloneAndIncludesNeighborFaceHalo() {
    VoxelWorld world = new VoxelWorld(registry(), 3);
    ChunkPos center = new ChunkPos(0, 0, 0);
    world.loadChunk(center);
    world.loadChunk(new ChunkPos(-1, 0, 0));
    world.loadChunk(new ChunkPos(1, 0, 0));
    world.setBlock(-1, 2, 3, 1);
    world.setBlock(16, 4, 5, 1);
    ChunkSnapshot snapshot = world.snapshot(center);
    world.setBlock(-1, 2, 3, 0);
    assertEquals(1, snapshot.block(-1, 2, 3));
    assertEquals(1, snapshot.block(16, 4, 5));
    assertEquals(0, snapshot.block(0, 2, 3));
    assertThrows(IllegalArgumentException.class, () -> snapshot.block(-2, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> snapshot.block(17, 0, 0));
  }

  @Test
  public void worldEdgeLoadsAndHaloReadsDoNotOverflowChunkCoordinates() {
    VoxelWorld world = new VoxelWorld(registry(), 2);
    ChunkPos eastEdge = new ChunkPos(65_535, 0, 0);
    ChunkPos westEdge = new ChunkPos(-65_536, 0, 0);
    world.loadChunk(eastEdge);
    world.loadChunk(westEdge);
    assertEquals(0, world.snapshot(eastEdge).block(16, 0, 0));
    assertEquals(0, world.snapshot(westEdge).block(-1, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> world.loadChunk(new ChunkPos(65_536, 0, 0)));
    assertEquals(List.of(eastEdge, westEdge), world.dirtyChunks());
    assertFalse(world.unloadChunk(new ChunkPos(65_535, 1, 0)));
  }
}
