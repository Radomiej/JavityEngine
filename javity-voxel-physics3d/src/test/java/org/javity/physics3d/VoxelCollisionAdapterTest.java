package org.javity.physics3d;

import static org.junit.Assert.*;

import com.badlogic.gdx.math.Vector3;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.javity.core.Component;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.voxel.BlockDefinition;
import org.javity.voxel.BlockRegistry;
import org.javity.voxel.ChunkPos;
import org.javity.voxel.VoxelWorld;
import org.javity.voxel.physics3d.VoxelCollisionAdapter;
import org.javity.voxel.physics3d.VoxelCollisionGeometry;
import org.junit.Test;

public class VoxelCollisionAdapterTest {
  private static BlockRegistry blocks() {
    BlockRegistry registry = new BlockRegistry();
    registry.register(new BlockDefinition("stone", 0));
    return registry;
  }

  private static GameObject sphere(Game game, Vector3 position) {
    GameObject object = game.createObject("sphere");
    Transform3D transform = object.addComponent(new Transform3D());
    transform.position.set(position);
    object.addComponent(new RigidBody3D(RigidBody3D.Type.DYNAMIC).setMass(1));
    object.addComponent(new SphereCollider3D(.5f));
    return object;
  }

  private static void stepUntilReady(Game game, VoxelCollisionAdapter adapter, ChunkPos chunk) {
    for (int i = 0; i < 20 && !adapter.isChunkCollisionReady(chunk); i++)
      game.step(Physics3D.FIXED_STEP);
    assertTrue(
        "not ready after 20 ticks; bodies="
            + game.getSystem(Physics3D.class).getBodyCount()
            + ", tracked="
            + adapter.getTrackedChunkCount()
            + ", tick="
            + game.getSystem(Physics3D.class).getFixedTick()
            + ", allocations="
            + game.getSystem(Physics3D.class).getNativeAllocationCount(),
        adapter.isChunkCollisionReady(chunk));
  }

  @Test
  public void mergedOccupancyIsDeterministicAndCoversEverySolidCell() {
    VoxelWorld world = new VoxelWorld(blocks(), 2);
    ChunkPos negative = new ChunkPos(-1, 0, 0);
    world.loadChunk(negative);
    world.setBlock(-1, 0, 0, 1);
    world.setBlock(-2, 0, 0, 1);
    world.setBlock(-4, 0, 0, 1);
    VoxelWorld.CollisionSnapshot snapshot = world.collisionSnapshot(negative);
    List<CompoundCollider3D.Child> first = VoxelCollisionGeometry.merge(snapshot);
    List<CompoundCollider3D.Child> second = VoxelCollisionGeometry.merge(snapshot);
    assertEquals(first, second);
    assertEquals(2, first.size());
    assertEquals(
        3,
        VoxelCollisionGeometry.merge(world.collisionSnapshot(negative)).stream()
            .mapToInt(
                child -> {
                  CompoundCollider3D.Box box = (CompoundCollider3D.Box) child;
                  Vector3 dimensions = box.dimensions();
                  return Math.round(dimensions.x * dimensions.y * dimensions.z);
                })
            .sum());

    VoxelWorld checker = new VoxelWorld(blocks(), 1);
    checker.loadChunk(new ChunkPos(0, 0, 0));
    for (int y = 0; y < 16; y++)
      for (int z = 0; z < 16; z++)
        for (int x = 0; x < 16; x++) if (((x + y + z) & 1) == 0) checker.setBlock(x, y, z, 1);
    List<CompoundCollider3D.Child> checkerBoxes =
        VoxelCollisionGeometry.merge(checker.collisionSnapshot(new ChunkPos(0, 0, 0)));
    assertEquals(2048, checkerBoxes.size());
  }

  @Test
  public void nativeTerrainWorksWithoutRendererAndPublishesEditsEmptyAndReloads() {
    VoxelWorld voxels = new VoxelWorld(blocks(), 2);
    ChunkPos chunk = new ChunkPos(0, 0, 0);
    voxels.loadChunk(chunk);
    voxels.setBlock(0, 0, 0, 1);
    voxels.setBlock(3, 0, 0, 1);
    voxels.clearDirty(chunk); // Rendering already consumed its independent queue.
    Physics3D physics = new Physics3D(new Vector3(0, -9.81f, 0));
    Game game = new Game();
    game.addSystem(physics);
    VoxelCollisionAdapter adapter = game.addSystem(new VoxelCollisionAdapter(voxels, physics));
    GameObject ball = sphere(game, new Vector3(.5f, 3, .5f));
    game.start();
    stepUntilReady(game, adapter, chunk);
    for (int i = 0; i < 180; i++) game.step(Physics3D.FIXED_STEP);
    assertEquals(1.5f, ball.getComponent(Transform3D.class).position.y, .04f);
    assertEquals(2, physics.getBodyCount()); // One terrain body plus the test sphere.
    assertEquals(0, voxels.dirtyChunkCount());
    assertTrue(
        physics
            .raycast(new Vector3(.5f, 3, .5f), new Vector3(.5f, -1, .5f), PhysicsFilter3D.all())
            .isPresent());
    assertTrue(adapter.isCollisionReadyVolume(.1f, 0, .1f, .9f, 1, .9f));
    assertFalse(adapter.isCollisionReadyVolume(.1f, 0, .1f, 16.1f, 1, .9f));

    long oldRevision = voxels.contentRevision(chunk);
    voxels.setBlock(0, 0, 0, 0);
    assertFalse(adapter.isChunkCollisionReady(chunk));
    for (int i = 0; i < 20 && !adapter.isChunkCollisionReady(chunk); i++)
      game.step(Physics3D.FIXED_STEP);
    assertTrue(
        "empty collision not published; bodies="
            + physics.getBodyCount()
            + ", boxes="
            + game.getObjectsWith(StaticCompoundCollider3D.class).stream()
                .map(o -> o.getComponent(StaticCompoundCollider3D.class).getBoxes().size())
                .toList(),
        adapter.isChunkCollisionReady(chunk));
    assertEquals(oldRevision + 1, voxels.contentRevision(chunk));
    assertTrue(voxels.dirtyChunks().contains(chunk));
    assertEquals(2, physics.getBodyCount()); // One solid cell remains in the same chunk body.
    voxels.clearDirty(chunk); // Renderer consumes the edit after collision already published it.

    voxels.setBlock(3, 0, 0, 0);
    voxels.clearDirty(chunk); // Renderer consumes this edit before collision publication.
    for (int i = 0; i < 20 && !adapter.isChunkCollisionReady(chunk); i++)
      game.step(Physics3D.FIXED_STEP);
    assertTrue(
        "empty collision not published; bodies="
            + physics.getBodyCount()
            + ", boxes="
            + game.getObjectsWith(StaticCompoundCollider3D.class).stream()
                .map(o -> o.getComponent(StaticCompoundCollider3D.class).getBoxes().size())
                .toList(),
        adapter.isChunkCollisionReady(chunk));
    assertEquals(1, physics.getBodyCount());

    voxels.setBlock(1, 0, 0, 1);
    for (int i = 0; i < 20 && !adapter.isChunkCollisionReady(chunk); i++)
      game.step(Physics3D.FIXED_STEP);
    assertTrue(adapter.isChunkCollisionReady(chunk));
    assertEquals(2, physics.getBodyCount());
    assertTrue(voxels.unloadChunk(chunk));
    assertFalse(adapter.isChunkCollisionReady(chunk));
    game.step(Physics3D.FIXED_STEP);
    assertEquals(1, physics.getBodyCount());
    voxels.loadChunk(chunk);
    voxels.setBlock(5, 0, 0, 1);
    for (int i = 0; i < 20 && !adapter.isChunkCollisionReady(chunk); i++)
      game.step(Physics3D.FIXED_STEP);
    assertTrue(adapter.isChunkCollisionReady(chunk));
    assertEquals(2, physics.getBodyCount());
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void failedNativeReplacementKeepsOldBodyAndReadinessClosedUntilRetry() {
    VoxelWorld voxels = new VoxelWorld(blocks(), 1);
    ChunkPos chunk = new ChunkPos(-1, 0, 0);
    voxels.loadChunk(chunk);
    voxels.setBlock(-1, 0, 0, 1);
    AtomicBoolean fail = new AtomicBoolean();
    Physics3D physics =
        new Physics3D(
            Vector3.Zero,
            stage -> {
              if (fail.get() && stage.equals("shape-child-1"))
                throw new IllegalStateException("injected terrain candidate failure");
            });
    Game game = new Game();
    game.addSystem(physics);
    VoxelCollisionAdapter adapter = game.addSystem(new VoxelCollisionAdapter(voxels, physics));
    game.start();
    stepUntilReady(game, adapter, chunk);
    assertEquals(1, physics.getBodyCount());
    fail.set(true);
    voxels.setBlock(-2, 0, 0, 1);
    game.step(0); // UI maintenance must not publish a staged terrain replacement.
    physics.refresh();
    physics.raycast(new Vector3(-.5f, 2, .5f), new Vector3(-.5f, -2, .5f), PhysicsFilter3D.all());
    assertFalse(adapter.isChunkCollisionReady(chunk));
    assertEquals(1, physics.getBodyCount());
    assertThrows(IllegalStateException.class, () -> game.step(Physics3D.FIXED_STEP));
    assertFalse(adapter.isChunkCollisionReady(chunk));
    assertEquals(1, physics.getBodyCount());
    assertTrue(
        physics
            .raycast(new Vector3(-.5f, 2, .5f), new Vector3(-.5f, -2, .5f), PhysicsFilter3D.all())
            .isPresent());
    fail.set(false);
    stepUntilReady(game, adapter, chunk);
    assertEquals(1, physics.getBodyCount());
    assertTrue(
        physics
            .raycast(new Vector3(-1.5f, 2, .5f), new Vector3(-1.5f, -2, .5f), PhysicsFilter3D.all())
            .isPresent());
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void catchupTicksCannotMultiplyFramePublicationBudget() {
    VoxelWorld voxels = new VoxelWorld(blocks(), 4);
    ChunkPos first = new ChunkPos(0, 0, 0);
    ChunkPos second = new ChunkPos(1, 0, 0);
    voxels.loadChunk(first);
    voxels.loadChunk(second);
    voxels.setBlock(0, 0, 0, 1);
    voxels.setBlock(16, 0, 0, 1);
    Physics3D physics = new Physics3D(Vector3.Zero);
    Game game = new Game();
    game.addSystem(physics);
    VoxelCollisionAdapter adapter = game.addSystem(new VoxelCollisionAdapter(voxels, physics, 1));
    game.start();
    game.step(.1f); // Eight possible fixed ticks, one collision-work slot.
    assertEquals(1, adapter.getLastFramePublicationAttempts());
    assertEquals(0, adapter.getReadyChunkCount()); // New object remains pending until next frame.
    for (int i = 0; i < 8 && adapter.getReadyChunkCount() < 2; i++) {
      game.step(Physics3D.FIXED_STEP);
      assertTrue(adapter.getLastFramePublicationAttempts() <= 1);
    }
    assertEquals(2, adapter.getReadyChunkCount());
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void sourceChangeAfterPreparationCannotPublishStaleSnapshot() {
    VoxelWorld voxels = new VoxelWorld(blocks(), 1);
    ChunkPos chunk = new ChunkPos(0, 0, 0);
    voxels.loadChunk(chunk);
    voxels.setBlock(0, 0, 0, 1);
    Physics3D physics = new Physics3D(Vector3.Zero);
    Game game = new Game();
    game.addSystem(physics);
    VoxelCollisionAdapter adapter = game.addSystem(new VoxelCollisionAdapter(voxels, physics));
    GameObject mutator = game.createObject("source-mutator");
    AtomicBoolean mutate = new AtomicBoolean();
    mutator.addComponent(
        new Component() {
          @Override
          public void onUpdate(float delta) {
            if (mutate.compareAndSet(true, false)) voxels.setBlock(1, 0, 0, 1);
          }
        });
    game.start();
    mutate.set(true);
    game.step(Physics3D.FIXED_STEP);
    assertFalse(adapter.isChunkCollisionReady(chunk));
    stepUntilReady(game, adapter, chunk);
    assertEquals(1, physics.getBodyCount());
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void unloadReloadBeforeNextBudgetSlotDoesNotReuseOldReadiness() {
    VoxelWorld voxels = new VoxelWorld(blocks(), 1);
    ChunkPos chunk = new ChunkPos(-1, 0, 0);
    voxels.loadChunk(chunk);
    voxels.setBlock(-1, 0, 0, 1);
    Physics3D physics = new Physics3D(Vector3.Zero);
    Game game = new Game();
    game.addSystem(physics);
    VoxelCollisionAdapter adapter = game.addSystem(new VoxelCollisionAdapter(voxels, physics));
    game.start();
    stepUntilReady(game, adapter, chunk);
    long oldGeneration = voxels.chunkGeneration(chunk);
    assertTrue(voxels.unloadChunk(chunk));
    voxels.loadChunk(chunk);
    voxels.setBlock(-6, 0, 0, 1);
    assertTrue(voxels.chunkGeneration(chunk) > oldGeneration);
    assertFalse(adapter.isChunkCollisionReady(chunk));
    for (int i = 0; i < 20 && !adapter.isChunkCollisionReady(chunk); i++)
      game.step(Physics3D.FIXED_STEP);
    assertTrue(adapter.isChunkCollisionReady(chunk));
    assertFalse(
        physics
            .raycast(new Vector3(-.5f, 2, .5f), new Vector3(-.5f, -2, .5f), PhysicsFilter3D.all())
            .isPresent());
    assertTrue(
        physics
            .raycast(new Vector3(-5.5f, 2, .5f), new Vector3(-5.5f, -2, .5f), PhysicsFilter3D.all())
            .isPresent());
    assertEquals(1, physics.getBodyCount());
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void nativeCharacterUsesVoxelBridgeReadinessAcrossNegativeChunkSeam() {
    VoxelWorld voxels = new VoxelWorld(blocks(), 8);
    ChunkPos negativeBottom = new ChunkPos(-1, -1, -1);
    ChunkPos negativeUpper = new ChunkPos(-1, 0, -1);
    voxels.loadChunk(negativeBottom);
    voxels.loadChunk(negativeUpper);
    voxels.setBlock(-1, -1, -1, 1);
    Physics3D physics = new Physics3D(new Vector3(0, -9.81f, 0));
    Game game = new Game();
    game.addSystem(physics);
    VoxelCollisionAdapter adapter = game.addSystem(new VoxelCollisionAdapter(voxels, physics, 4));
    GameObject player = game.createObject("seam-character");
    Transform3D transform = player.addComponent(new Transform3D());
    transform.position.set(-.5f, .02f, -.5f);
    BulletCharacterController3D character =
        player.addComponent(
            new BulletCharacterController3D(.35f, 1.8f, .3f)
                .setSpeed(3)
                .setReadinessPolicy(adapter.characterReadiness()));
    assertFalse(
        "unpublished terrain keeps a new character out of Bullet", character.isBoundToPhysics());
    game.start();
    for (int i = 0;
        i < 20
            && (!adapter.isChunkCollisionReady(negativeBottom)
                || !adapter.isChunkCollisionReady(negativeUpper));
        i++) game.step(Physics3D.FIXED_STEP);
    assertTrue(adapter.isChunkCollisionReady(negativeBottom));
    assertTrue(adapter.isChunkCollisionReady(negativeUpper));
    assertTrue("the default fall envelope publishes the lower chunk", character.isBoundToPhysics());
    assertTrue(adapter.isCollisionReadyVolume(-.9f, -.2f, -.9f, -.1f, 2.3f, -.1f));
    character.setMoveAxes(1, 0);
    for (int i = 0; i < 180; i++) game.step(Physics3D.FIXED_STEP);
    assertTrue("the loaded negative chunk admits movement", transform.position.x < -.35f);
    assertTrue(
        "the swept capsule cannot advance into the unloaded positive chunk",
        transform.position.x <= -.34f);
    assertFalse(adapter.isCollisionReadyVolume(-.75f, -.2f, -.9f, .01f, 2.3f, -.1f));

    long revision = voxels.contentRevision(negativeBottom);
    voxels.setBlock(-2, -1, -1, 1);
    assertFalse(
        "an edited source revision closes admission immediately",
        adapter.isChunkCollisionReady(negativeBottom));
    for (int i = 0; i < 20 && !adapter.isChunkCollisionReady(negativeBottom); i++)
      game.step(Physics3D.FIXED_STEP);
    assertEquals(revision + 1, voxels.contentRevision(negativeBottom));
    assertTrue(adapter.isChunkCollisionReady(negativeBottom));

    long upperGeneration = voxels.chunkGeneration(negativeUpper);
    assertTrue(voxels.unloadChunk(negativeUpper));
    assertFalse(
        "unloading any touched chunk closes capsule admission",
        adapter.isCollisionReadyVolume(-.9f, -.2f, -.9f, -.1f, 3.3f, -.1f));
    float seamStop = transform.position.x;
    game.step(Physics3D.FIXED_STEP);
    assertEquals(
        "the native controller freezes while its vertical envelope is unloaded",
        seamStop,
        transform.position.x,
        .01f);
    voxels.loadChunk(negativeUpper);
    assertTrue(voxels.chunkGeneration(negativeUpper) > upperGeneration);
    for (int i = 0; i < 20 && !adapter.isChunkCollisionReady(negativeUpper); i++)
      game.step(Physics3D.FIXED_STEP);
    assertTrue(adapter.isChunkCollisionReady(negativeUpper));

    ChunkPos positiveBottom = new ChunkPos(0, -1, -1);
    ChunkPos positiveUpper = new ChunkPos(0, 0, -1);
    voxels.loadChunk(positiveBottom);
    voxels.loadChunk(positiveUpper);
    voxels.setBlock(0, -1, -1, 1);
    for (int i = 0;
        i < 20
            && (!adapter.isChunkCollisionReady(positiveBottom)
                || !adapter.isChunkCollisionReady(positiveUpper));
        i++) game.step(Physics3D.FIXED_STEP);
    assertTrue(adapter.isChunkCollisionReady(positiveBottom));
    assertTrue(adapter.isChunkCollisionReady(positiveUpper));
    assertTrue(adapter.isCollisionReadyVolume(-.75f, -.2f, -.9f, .01f, 2.3f, -.1f));
    for (int i = 0; i < 60; i++) game.step(Physics3D.FIXED_STEP);
    assertTrue(
        "published positive-side collision allows the character through",
        transform.position.x > .1f);
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void pendingFirstPublicationCanBeUnloadedWithoutLeakingAChunkObject() {
    VoxelWorld voxels = new VoxelWorld(blocks(), 1);
    ChunkPos chunk = new ChunkPos(0, 0, 0);
    voxels.loadChunk(chunk);
    voxels.setBlock(0, 0, 0, 1);
    Physics3D physics = new Physics3D(Vector3.Zero);
    Game game = new Game();
    game.addSystem(physics);
    VoxelCollisionAdapter adapter = game.addSystem(new VoxelCollisionAdapter(voxels, physics));
    game.start();
    game.step(Physics3D.FIXED_STEP); // Candidate GameObject is pending initialization.
    assertEquals(1, adapter.getTrackedChunkCount());
    assertTrue(voxels.unloadChunk(chunk));
    game.step(Physics3D.FIXED_STEP);
    assertEquals(0, adapter.getTrackedChunkCount());
    assertEquals(0, physics.getBodyCount());
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }
}
