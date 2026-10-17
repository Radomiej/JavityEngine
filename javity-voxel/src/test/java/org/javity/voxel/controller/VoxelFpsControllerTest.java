package org.javity.voxel.controller;

import static org.junit.Assert.*;

import com.badlogic.gdx.math.Vector3;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.voxel.BlockDefinition;
import org.javity.voxel.BlockRegistry;
import org.javity.voxel.ChunkPos;
import org.javity.voxel.VoxelWorld;
import org.junit.Test;

public class VoxelFpsControllerTest {
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

  private GameObject player(Game game, VoxelWorld world, float x, float y, float z) {
    GameObject object = game.createObject("player");
    Transform3D transform = object.addComponent(new Transform3D());
    transform.position.set(x, y, z);
    object.addComponent(new VoxelFpsController(world));
    return object;
  }

  private void floor(VoxelWorld world, int fromX, int toX, int fromZ, int toZ) {
    for (int x = fromX; x <= toX; x++)
      for (int z = fromZ; z <= toZ; z++) world.setBlock(x, 0, z, STONE);
  }

  @Test
  public void requiresTransformAndCachesItOnCreate() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("missing-transform");
      object.addComponent(new VoxelFpsController(world()));
      assertThrows(IllegalStateException.class, game::start);
    }
    try (Game game = new Game()) {
      VoxelWorld world = world();
      GameObject object = player(game, world, 0, 3, 0);
      game.start();
      VoxelFpsController controller = object.getComponent(VoxelFpsController.class);
      assertSame(object.getComponent(Transform3D.class), controller.getTransform3D());
      float beforeRemoval = object.getComponent(Transform3D.class).position.x;
      object.removeComponent(controller);
      assertThrows(IllegalStateException.class, () -> controller.advance(.1f));
      assertEquals(beforeRemoval, object.getComponent(Transform3D.class).position.x, 0f);
    }
  }

  @Test
  public void fallsToGroundJumpsOnceAndZeroDeltaIsStable() {
    VoxelWorld world = world();
    floor(world, -3, 3, -3, 3);
    try (Game game = new Game()) {
      GameObject player = player(game, world, 0, 3, 0);
      game.start();
      VoxelFpsController controller = player.getComponent(VoxelFpsController.class);
      for (int i = 0; i < 40; i++) controller.advance(.05f);
      assertEquals(1f, player.getComponent(Transform3D.class).position.y, .001f);
      assertTrue(controller.isGrounded());

      controller.setJump(true);
      controller.advance(.05f);
      assertFalse(controller.isGrounded());
      assertTrue(player.getComponent(Transform3D.class).position.y > 1f);
      float beforeZero = player.getComponent(Transform3D.class).position.y;
      float velocityBeforeZero = controller.getVerticalVelocity();
      controller.advance(0f);
      assertEquals(beforeZero, player.getComponent(Transform3D.class).position.y, 0f);
      assertEquals(velocityBeforeZero, controller.getVerticalVelocity(), 0f);

      for (int i = 0; i < 60; i++) controller.advance(.05f);
      assertTrue(controller.isGrounded());
      float landed = player.getComponent(Transform3D.class).position.y;
      controller.advance(.05f); // held jump has no repeat edge
      assertTrue(controller.isGrounded());
      assertEquals(landed, player.getComponent(Transform3D.class).position.y, .001f);
    }
  }

  @Test
  public void jumpPressedJustBeforeLandingIsBufferedUntilNextAdvance() {
    VoxelWorld world = world();
    floor(world, -2, 2, -2, 2);
    try (Game game = new Game()) {
      GameObject player = player(game, world, 0, 1.0001f, 0);
      game.start();
      VoxelFpsController controller = player.getComponent(VoxelFpsController.class);
      assertFalse(controller.isGrounded());
      controller.setJump(true);
      controller.advance(.12f);
      assertTrue(controller.isGrounded());
      assertEquals(1f, player.getComponent(Transform3D.class).position.y, .001f);

      controller.advance(.016f);
      assertFalse("buffered edge should launch after the landing step", controller.isGrounded());
      assertTrue(player.getComponent(Transform3D.class).position.y > 1f);
      float velocityAfterLaunch = controller.getVerticalVelocity();
      controller.advance(.016f);
      assertTrue(
          "one press must not restart vertical speed",
          controller.getVerticalVelocity() < velocityAfterLaunch);
    }
  }

  @Test
  public void ceilingStopsJumpAndWallCollisionAllowsSliding() {
    VoxelWorld world = world();
    floor(world, -4, 4, -4, 4);
    for (int x = -10; x <= 10; x++) for (int y = 1; y <= 5; y++) world.setBlock(x, y, -2, STONE);
    world.setBlock(0, 3, 0, STONE);
    try (Game game = new Game()) {
      GameObject player = player(game, world, 0, 1, 0);
      game.start();
      VoxelFpsController controller = player.getComponent(VoxelFpsController.class);
      controller.setJump(true);
      controller.advance(.1f);
      Transform3D transform = player.getComponent(Transform3D.class);
      assertTrue(
          "head must stop below ceiling",
          transform.position.y + VoxelFpsController.BODY_HEIGHT <= 3.001f);

      controller.setJump(false);
      controller.setMoveInput(1, 1);
      controller.setSprint(true);
      for (int i = 0; i < 20; i++) controller.advance(.05f);
      assertEquals("wall prevents forward movement", -.7f, transform.position.z, .015f);
      assertTrue("tangential movement continues", transform.position.x > .8f);
    }
  }

  @Test
  public void diagonalMovementIsNormalizedAndThinWallsCannotBeCrossedAtHighSpeed() {
    VoxelWorld world = world();
    floor(world, -8, 8, -8, 8);
    world.setBlock(0, 1, -2, STONE);
    world.setBlock(0, 2, -2, STONE);
    try (Game game = new Game()) {
      GameObject player = player(game, world, 0, 1, 0);
      game.start();
      VoxelFpsController controller = player.getComponent(VoxelFpsController.class);
      controller.setMoveInput(1, 0);
      controller.setSprint(true);
      controller.advance(100f); // clamps frame time but still sweeps every movement step
      assertEquals(
          "thin wall blocks at its near face",
          -.7f,
          player.getComponent(Transform3D.class).position.z,
          .001f);
      controller.setMoveInput(1, 1);
      controller.setSprint(false);
      controller.advance(.1f);
      Transform3D transform = player.getComponent(Transform3D.class);
      assertTrue(transform.position.x > .2f);
      assertEquals(-.7f, transform.position.z, .001f);
      assertTrue(
          "normalized diagonal displacement stays bounded",
          Math.hypot(transform.position.x, transform.position.z) < 1.3);
    }
  }

  @Test
  public void diagonalMotionStopsAtCornerWithoutCrossingEitherWall() {
    VoxelWorld world = world();
    floor(world, -5, 5, -5, 5);
    for (int x = -4; x <= 4; x++) {
      world.setBlock(x, 1, -2, STONE);
      world.setBlock(x, 2, -2, STONE);
    }
    for (int z = -4; z <= 1; z++) {
      world.setBlock(2, 1, z, STONE);
      world.setBlock(2, 2, z, STONE);
    }
    try (Game game = new Game()) {
      GameObject player = player(game, world, 0, 1, 0);
      game.start();
      VoxelFpsController controller = player.getComponent(VoxelFpsController.class);
      controller.setMoveInput(1, 1);
      controller.setSprint(true);
      controller.advance(.25f);
      Transform3D transform = player.getComponent(Transform3D.class);
      assertTrue(transform.position.z >= -.7f - .001f);
      assertTrue(transform.position.x <= 1.7f + .001f);
    }
  }

  @Test
  public void unloadedChunkIsASolidBoundaryIncludingNegativeChunkEdges() {
    BlockRegistry registry = new BlockRegistry();
    registry.register(new BlockDefinition("stone", 0));
    VoxelWorld world = new VoxelWorld(registry, 2);
    world.loadChunk(new ChunkPos(-1, 0, 0));
    world.loadChunk(new ChunkPos(0, 0, 0));
    try (Game game = new Game()) {
      GameObject player = player(game, world, -15.6f, 5, 0.5f);
      game.start();
      VoxelFpsController controller = player.getComponent(VoxelFpsController.class);
      controller.setMoveInput(0, -1); // yaw zero, negative local-right moves toward -X
      controller.setSprint(true);
      controller.advance(.25f);
      assertEquals(-15.7f, player.getComponent(Transform3D.class).position.x, .001f);
    }
  }

  @Test
  public void advanceCapsFiniteLargeDeltaAndRejectsNonFiniteDelta() {
    VoxelWorld world = world();
    try (Game game = new Game()) {
      GameObject player = player(game, world, 0, 10, 0);
      game.start();
      VoxelFpsController controller = player.getComponent(VoxelFpsController.class);
      Transform3D transform = player.getComponent(Transform3D.class);
      controller.setMoveInput(1, 0);
      controller.advance(Float.MAX_VALUE);
      assertEquals(
          -VoxelFpsController.WALK_SPEED * VoxelFpsController.MAX_FRAME_DELTA,
          transform.position.z,
          .001f);
      assertThrows(IllegalArgumentException.class, () -> controller.advance(Float.NaN));
      assertThrows(
          IllegalArgumentException.class, () -> controller.advance(Float.POSITIVE_INFINITY));
      assertThrows(IllegalArgumentException.class, () -> controller.advance(-1f));
    }
  }

  @Test
  public void manualAdvanceRespectsDisabledAndInactiveOwner() {
    VoxelWorld world = world();
    try (Game game = new Game()) {
      GameObject player = player(game, world, 0, 10, 0);
      game.start();
      VoxelFpsController controller = player.getComponent(VoxelFpsController.class);
      Transform3D transform = player.getComponent(Transform3D.class);
      controller.setMoveInput(1, 0);
      controller.setEnabled(false);
      controller.advance(.1f);
      assertEquals(10f, transform.position.y, 0f);
      assertEquals(0f, transform.position.z, 0f);
      assertThrows(IllegalArgumentException.class, () -> controller.advance(Float.NaN));

      controller.setEnabled(true);
      player.setActive(false);
      controller.advance(.1f);
      assertEquals(10f, transform.position.y, 0f);
      assertEquals(0f, transform.position.z, 0f);
      player.setActive(true);
      controller.advance(.1f);
      assertTrue(transform.position.y < 10f);
      assertTrue(transform.position.z < 0f);
    }
  }

  @Test
  public void playerWalksOffLoadedLedgeAndFalls() {
    VoxelWorld world = world();
    for (int z = -1; z <= 1; z++) world.setBlock(0, 0, z, STONE);
    try (Game game = new Game()) {
      GameObject player = player(game, world, .5f, 1, .5f);
      game.start();
      VoxelFpsController controller = player.getComponent(VoxelFpsController.class);
      controller.setMoveInput(0, 1);
      for (int i = 0; i < 20; i++) controller.advance(.05f);
      Transform3D transform = player.getComponent(Transform3D.class);
      assertTrue(transform.position.x > 1.3f);
      assertTrue(transform.position.y < .95f);
      assertFalse(controller.isGrounded());
    }
  }

  @Test
  public void rejectsInvalidInputsAndExposesEyeAndLookPose() {
    VoxelWorld world = world();
    try (Game game = new Game()) {
      GameObject player = player(game, world, 0, 10, 0);
      game.start();
      VoxelFpsController controller = player.getComponent(VoxelFpsController.class);
      assertThrows(IllegalArgumentException.class, () -> controller.setMoveInput(Float.NaN, 0));
      assertThrows(
          IllegalArgumentException.class,
          () -> controller.addLookInput(0, Float.POSITIVE_INFINITY));
      assertThrows(IllegalArgumentException.class, () -> controller.advance(-1));
      controller.addLookInput(90, 30);
      Vector3 direction = controller.getLookDirection(new Vector3());
      assertEquals(-0.8660254f, direction.x, .001f);
      assertEquals(.5f, direction.y, .001f);
      Vector3 eye = controller.getEyePosition(new Vector3());
      assertEquals(10 + VoxelFpsController.EYE_HEIGHT, eye.y, .0001f);
      Vector3 rotatedForward =
          player.getComponent(Transform3D.class).rotation.transform(new Vector3(0, 0, -1)).nor();
      assertEquals(direction.x, rotatedForward.x, .001f);
      assertEquals(direction.y, rotatedForward.y, .001f);
      assertEquals(direction.z, rotatedForward.z, .001f);
    }
  }
}
