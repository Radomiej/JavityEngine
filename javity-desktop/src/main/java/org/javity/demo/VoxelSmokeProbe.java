package org.javity.demo;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Vector3;
import java.lang.reflect.Field;
import java.util.List;
import org.javity.voxel.BlockPos;
import org.javity.voxel.ChunkPos;
import org.javity.voxel.VoxelWorld;
import org.javity.voxel.controller.VoxelFpsController;
import org.lwjgl.glfw.GLFW;

/** Scripted scene-level controls and seam-edit proof, run by the graphical demo smoke. */
final class VoxelSmokeProbe {
  private static final BlockPos SEAM_TARGET = new BlockPos(15, 22, 0);
  private static final ChunkPos SEAM_OWNER = new ChunkPos(0, 1, 0);
  private static final ChunkPos SEAM_NEIGHBOR = new ChunkPos(1, 1, 0);
  private static final Vector3 SEAM_CAMERA = new Vector3(13.5f, 22.5f, 0.5f);
  private static final Vector3 SEAM_DIRECTION = Vector3.X.cpy();

  private final VoxelDemo demo;
  private final int originalTargetBlock;
  private final int originalNeighborBlock;
  private Phase phase = Phase.WAIT_INITIAL_MESHES;
  private int ownerRebuildsBeforeRemove;
  private int neighborRebuildsBeforeRemove;
  private int ownerRebuildsBeforePlace;
  private int neighborRebuildsBeforePlace;
  private int ownerRebuildsBeforeRestore;
  private int neighborRebuildsBeforeRestore;
  private int stationaryFrames;
  private boolean artifactsWritten;
  private boolean focusVerified;
  private boolean fpsControllerVerified;
  private long mainWindow, focusWindow;
  private int focusDeadline;

  private VoxelSmokeProbe(VoxelDemo demo, int originalTargetBlock, int originalNeighborBlock) {
    this.demo = demo;
    this.originalTargetBlock = originalTargetBlock;
    this.originalNeighborBlock = originalNeighborBlock;
  }

  static VoxelSmokeProbe install(VoxelDemo demo) {
    VoxelWorld world = demo.world();
    int originalTarget = world.getBlock(SEAM_TARGET.x(), SEAM_TARGET.y(), SEAM_TARGET.z());
    int originalNeighbor = world.getBlock(16, SEAM_TARGET.y(), SEAM_TARGET.z());
    world.setBlock(SEAM_TARGET.x(), SEAM_TARGET.y(), SEAM_TARGET.z(), demo.terrain().grassId());
    world.setBlock(16, SEAM_TARGET.y(), SEAM_TARGET.z(), demo.terrain().stoneId());
    return new VoxelSmokeProbe(demo, originalTarget, originalNeighbor);
  }

  void afterRender() {
    require(demo.frameNumber() <= 600, "Voxel smoke exceeded its 600-frame deadline");
    require(demo.lastRebuilds() <= 1, "Demo exceeded its one-chunk mesh budget");
    switch (phase) {
      case WAIT_INITIAL_MESHES -> waitForInitialMeshes();
      case WAIT_MAIN_FOCUS -> waitForMainFocus();
      case WAIT_FOCUS_LOSS -> waitForFocusLoss();
      case WAIT_FOCUS_REGAIN -> waitForFocusRegain();
      case WAIT_FPS_MESHES -> waitForFpsMeshes();
      case WAIT_REMOVE_MESHES -> waitForRemoveMeshes();
      case WAIT_PLACE_MESHES -> waitForPlaceMeshes();
      case WAIT_FIXTURE_RESTORE -> waitForFixtureRestore();
      case RESTORE_VIEWPORT -> restoreViewport();
      case WAIT_STATIONARY -> verifyStationaryWorld();
      case CAPTURE -> captureProof();
      case COMPLETE -> {}
    }
  }

  private void waitForInitialMeshes() {
    if (demo.dirtyChunkCount() != 0) return;
    if (!focusVerified) {
      mainWindow = ((Lwjgl3Graphics) Gdx.graphics).getWindow().getWindowHandle();
      GLFW.glfwFocusWindow(mainWindow);
      focusDeadline = demo.frameNumber() + 120;
      phase = Phase.WAIT_MAIN_FOCUS;
      return;
    }
    if (!fpsControllerVerified) {
      runFpsControllerProof();
      fpsControllerVerified = true;
      phase = Phase.WAIT_FPS_MESHES;
      return;
    }
  }

  private void runFpsControllerProof() {
    VoxelFpsController controller = demo.fpsController();
    require(controller.isGrounded(), "FPS spawn did not begin grounded on loaded terrain");
    Vector3 spawn = demo.spawnFeet();
    Vector3 start = demo.playerFeet();
    require(start.dst2(spawn) < .0001f, "FPS body did not spawn at its selected feet pose");

    int x = (int) Math.floor(spawn.x);
    int y = (int) Math.floor(spawn.y);
    int z = (int) Math.floor(spawn.z);
    BlockPos wallLower = new BlockPos(x, y, z - 1);
    BlockPos wallUpper = new BlockPos(x, y + 1, z - 1);
    BlockPos ceiling = new BlockPos(x, y + 2, z);
    BlockPos playerCellAtHead = new BlockPos(x, y + 1, z);
    VoxelWorld world = demo.world();
    int oldWallLower = world.getBlock(wallLower.x(), wallLower.y(), wallLower.z());
    int oldWallUpper = world.getBlock(wallUpper.x(), wallUpper.y(), wallUpper.z());
    int oldCeiling = world.getBlock(ceiling.x(), ceiling.y(), ceiling.z());
    int oldPlayerCellAtHead =
        world.getBlock(playerCellAtHead.x(), playerCellAtHead.y(), playerCellAtHead.z());
    require(
        oldWallLower == 0 && oldWallUpper == 0 && oldCeiling == 0,
        "FPS smoke fixture cells must be empty before installation");
    require(oldPlayerCellAtHead == 0, "FPS placement target cell must start empty");
    try {
      require(
          world.setBlock(wallLower.x(), wallLower.y(), wallLower.z(), demo.terrain().stoneId()),
          "Could not place FPS wall fixture lower block");
      require(
          world.setBlock(wallUpper.x(), wallUpper.y(), wallUpper.z(), demo.terrain().stoneId()),
          "Could not place FPS wall fixture upper block");
      require(
          world.setBlock(ceiling.x(), ceiling.y(), ceiling.z(), demo.terrain().stoneId()),
          "Could not place FPS ceiling fixture");

      controller.setMoveInput(1, 0);
      controller.setSprint(false);
      controller.advance(1f / 60f);
      float walkDistance = spawn.z - demo.playerFeet().z;
      demo.playerFeet().set(spawn);
      controller.setSprint(true);
      controller.advance(1f / 60f);
      float sprintDistance = spawn.z - demo.playerFeet().z;
      require(
          sprintDistance > walkDistance * 1.4f,
          "Shift sprint did not move faster than normal walking");
      controller.setSprint(false);
      demo.playerFeet().set(spawn);

      controller.setMoveInput(1, 0);
      for (int frame = 0; frame < 60; frame++) controller.advance(1f / 60f);
      controller.setMoveInput(0, 0);
      float wallStopZ = demo.playerFeet().z;
      require(wallStopZ < spawn.z - .1f, "FPS forward input did not approach the wall");
      require(
          wallStopZ >= z + VoxelFpsController.BODY_RADIUS - .02f,
          "FPS capsule penetrated its solid wall fixture");

      controller.setJump(true);
      float highestFeet = demo.playerFeet().y;
      for (int frame = 0; frame < 30; frame++) {
        controller.advance(1f / 60f);
        highestFeet = Math.max(highestFeet, demo.playerFeet().y);
      }
      controller.setJump(false);
      require(highestFeet > spawn.y + .1f, "Space jump did not lift the FPS body from ground");
      require(
          highestFeet + VoxelFpsController.BODY_HEIGHT <= y + 2.025f,
          "FPS jump passed through the low ceiling fixture");
      for (int frame = 0; frame < 120 && !controller.isGrounded(); frame++)
        controller.advance(1f / 60f);
      require(controller.isGrounded(), "FPS body did not land after its jump");
      require(
          Math.abs(demo.playerFeet().y - spawn.y) < .02f,
          "FPS body did not settle back on the terrain floor");

      require(
          world.setBlock(ceiling.x(), ceiling.y(), ceiling.z(), 0),
          "Could not clear ceiling fixture for placement collision proof");
      demo.syncFpsCameraForSmoke();
      require(
          wallUpper.equals(demo.currentTargetBlock()),
          "FPS placement fixture was not the selected target block");
      require(!demo.placeTarget(), "FPS placement put a solid block inside the player body");
      require(
          world.getBlock(playerCellAtHead.x(), playerCellAtHead.y(), playerCellAtHead.z()) == 0,
          "Rejected FPS placement changed the player cell");
    } finally {
      controller.setMoveInput(0, 0);
      controller.setSprint(false);
      controller.setJump(false);
      world.setBlock(wallLower.x(), wallLower.y(), wallLower.z(), oldWallLower);
      world.setBlock(wallUpper.x(), wallUpper.y(), wallUpper.z(), oldWallUpper);
      world.setBlock(ceiling.x(), ceiling.y(), ceiling.z(), oldCeiling);
      world.setBlock(
          playerCellAtHead.x(), playerCellAtHead.y(), playerCellAtHead.z(), oldPlayerCellAtHead);
      demo.restoreFpsSpawnPose();
    }
    require(
        world.getBlock(wallLower.x(), wallLower.y(), wallLower.z()) == oldWallLower
            && world.getBlock(wallUpper.x(), wallUpper.y(), wallUpper.z()) == oldWallUpper
            && world.getBlock(ceiling.x(), ceiling.y(), ceiling.z()) == oldCeiling
            && world.getBlock(playerCellAtHead.x(), playerCellAtHead.y(), playerCellAtHead.z())
                == oldPlayerCellAtHead,
        "FPS smoke fixture remained in the gallery world");
  }

  private void waitForFpsMeshes() {
    if (demo.dirtyChunkCount() != 0) return;
    Vector3 spawn = demo.spawnFeet();
    Vector3 look = new Vector3();
    demo.applyFpsMouseLookForSmoke(8, 0);
    demo.fpsController().getLookDirection(look);
    require(look.x > 0, "Positive mouse X must turn FPS camera right toward +X");
    demo.applyFpsMouseLookForSmoke(-8, 0);
    demo.fpsController().getLookDirection(look);
    require(
        Math.abs(look.x) < .0001f && look.z < 0,
        "FPS mouse look did not return to the original -Z heading");
    demo.dispatchKeyDownForSmoke(Input.Keys.F);
    require(demo.isFreeFlyMode(), "F did not switch the demo into collision-free free-fly");
    require(!demo.fpsController().isEnabled(), "Free-fly did not suspend the FPS controller");
    demo.dispatchKeyDownForSmoke(Input.Keys.F);
    require(!demo.isFreeFlyMode(), "F did not restore normal FPS movement");
    require(demo.fpsController().isEnabled(), "FPS controller stayed suspended after F toggle");
    Vector3 invalidSolidEye =
        new Vector3(spawn.x, spawn.y - .4f + VoxelFpsController.EYE_HEIGHT, spawn.z);
    requireFpsFallbackAt(invalidSolidEye, "solid block");
    Vector3 unloadedEye = new Vector3(spawn.x, spawn.y + VoxelFpsController.EYE_HEIGHT, -16.5f);
    requireFpsFallbackAt(unloadedEye, "unloaded chunk");
    demo.setCameraPose(SEAM_CAMERA, SEAM_DIRECTION);
    Vector3 initialPosition = demo.cameraPosition();
    Vector3 initialDirection = demo.cameraDirection();
    demo.moveFreeFly(new Vector3(0, 0, -1), .2f, false);
    demo.rotateLook(8, -3);
    require(
        demo.cameraPosition().dst2(initialPosition) > .01f,
        "Scripted free-fly input did not move the camera");
    require(
        demo.cameraDirection().dst2(initialDirection) > .0001f,
        "Scripted mouse input did not turn the camera");
    demo.setCameraPose(SEAM_CAMERA, new Vector3(0, 0, -1));
    demo.rotateLook(8, 0);
    require(demo.cameraDirection().x > 0, "Positive mouse X must turn right from -Z toward +X");
    demo.setCameraPose(SEAM_CAMERA, SEAM_DIRECTION);
    demo.rotateLook(8, 0);
    require(demo.cameraDirection().z > 0, "Positive mouse X must turn right from +X toward +Z");
    demo.setCameraPose(SEAM_CAMERA, SEAM_DIRECTION);
    ownerRebuildsBeforeRemove = demo.rebuildCount(SEAM_OWNER);
    neighborRebuildsBeforeRemove = demo.rebuildCount(SEAM_NEIGHBOR);
    require(demo.removeTarget(), "Gameplay remove action did not hit the seam fixture");
    require(
        demo.world().getBlock(SEAM_TARGET.x(), SEAM_TARGET.y(), SEAM_TARGET.z()) == 0,
        "Gameplay remove action left the targeted seam block in the world");
    require(
        demo.world().dirtyChunks().containsAll(List.of(SEAM_OWNER, SEAM_NEIGHBOR)),
        "Removing the seam block did not dirty its owner and loaded neighbor");
    phase = Phase.WAIT_REMOVE_MESHES;
  }

  private void requireFpsFallbackAt(Vector3 unsafeEye, String location) {
    demo.dispatchKeyDownForSmoke(Input.Keys.F);
    demo.setCameraPose(unsafeEye, Vector3.Z);
    demo.dispatchKeyDownForSmoke(Input.Keys.F);
    require(!demo.isFreeFlyMode(), "F did not restore FPS mode from " + location);
    require(
        demo.playerFeet().dst2(demo.spawnFeet()) < .0001f,
        "Returning from " + location + " did not use the safe FPS spawn");
  }

  // Exercise real GLFW focus callbacks through DesktopLauncher configuration. Reflection only
  // observes private input guards here in the test utility; gameplay gets no test-only hooks.
  private void waitForMainFocus() {
    require(demo.frameNumber() <= focusDeadline, "Main GLFW window never gained focus");
    if (GLFW.glfwGetWindowAttrib(mainWindow, GLFW.GLFW_FOCUSED) != GLFW.GLFW_TRUE
        || !inputGuard("focused")) return;
    Gdx.input.setCursorCatched(true);
    focusWindow = GLFW.glfwCreateWindow(64, 64, "Javity focus smoke helper", 0, 0);
    require(focusWindow != 0, "Could not create native focus helper window");
    // The libGDX backend leaves GLFW_VISIBLE=false in the global creation hints.
    // Explicitly show this raw GLFW helper before requesting real OS focus.
    GLFW.glfwShowWindow(focusWindow);
    GLFW.glfwFocusWindow(focusWindow);
    focusDeadline = demo.frameNumber() + 120;
    phase = Phase.WAIT_FOCUS_LOSS;
  }

  private void waitForFocusLoss() {
    require(
        demo.frameNumber() <= focusDeadline,
        "Native focus loss did not invoke VoxelDemo.pause through launcher wiring; mainFocused="
            + GLFW.glfwGetWindowAttrib(mainWindow, GLFW.GLFW_FOCUSED)
            + ", helperVisible="
            + GLFW.glfwGetWindowAttrib(focusWindow, GLFW.GLFW_VISIBLE)
            + ", helperFocused="
            + GLFW.glfwGetWindowAttrib(focusWindow, GLFW.GLFW_FOCUSED)
            + ", inputFocused="
            + inputGuard("focused"));
    if (GLFW.glfwGetWindowAttrib(mainWindow, GLFW.GLFW_FOCUSED) != GLFW.GLFW_FALSE
        || inputGuard("focused")) return;
    require(!Gdx.input.isCursorCatched(), "Native focus loss retained cursor capture");
    require(inputGuard("discardFirstMouseDelta"), "Native focus loss did not reset mouse delta");
    GLFW.glfwFocusWindow(mainWindow);
    focusDeadline = demo.frameNumber() + 120;
    phase = Phase.WAIT_FOCUS_REGAIN;
  }

  private void waitForFocusRegain() {
    require(demo.frameNumber() <= focusDeadline, "Native focus regain did not invoke resume");
    if (GLFW.glfwGetWindowAttrib(mainWindow, GLFW.GLFW_FOCUSED) != GLFW.GLFW_TRUE
        || !inputGuard("focused")) return;
    require(inputGuard("discardFirstMouseDelta"), "Native resume omitted first-delta reset");
    require(!Gdx.input.isCursorCatched(), "Native resume unexpectedly captured cursor");
    GLFW.glfwDestroyWindow(focusWindow);
    focusWindow = 0;
    focusVerified = true;
    phase = Phase.WAIT_INITIAL_MESHES;
  }

  private boolean inputGuard(String name) {
    try {
      Field field = VoxelDemo.class.getDeclaredField(name);
      field.setAccessible(true);
      return field.getBoolean(demo);
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Cannot inspect native input reset guard " + name, failure);
    }
  }

  private void waitForRemoveMeshes() {
    if (demo.dirtyChunkCount() != 0) return;
    require(
        demo.rebuildCount(SEAM_OWNER) == ownerRebuildsBeforeRemove + 1,
        "Removed block owner did not rebuild exactly once");
    require(
        demo.rebuildCount(SEAM_NEIGHBOR) == neighborRebuildsBeforeRemove + 1,
        "Removed block neighbor did not rebuild exactly once");
    ownerRebuildsBeforePlace = demo.rebuildCount(SEAM_OWNER);
    neighborRebuildsBeforePlace = demo.rebuildCount(SEAM_NEIGHBOR);
    demo.setCameraPose(SEAM_CAMERA, SEAM_DIRECTION);
    require(demo.placeTarget(), "Gameplay place action did not use the hit face across the seam");
    require(
        demo.world().getBlock(SEAM_TARGET.x(), SEAM_TARGET.y(), SEAM_TARGET.z())
            == demo.selectedBlockId(),
        "Gameplay place action did not fill the adjacent seam cell");
    require(
        demo.world().dirtyChunks().containsAll(List.of(SEAM_OWNER, SEAM_NEIGHBOR)),
        "Placing at the seam did not dirty its owner and loaded neighbor");
    phase = Phase.WAIT_PLACE_MESHES;
  }

  private void waitForPlaceMeshes() {
    if (demo.dirtyChunkCount() != 0) return;
    require(
        demo.rebuildCount(SEAM_OWNER) == ownerRebuildsBeforePlace + 1,
        "Placed block owner did not rebuild exactly once");
    require(
        demo.rebuildCount(SEAM_NEIGHBOR) == neighborRebuildsBeforePlace + 1,
        "Placed block neighbor did not rebuild exactly once");
    ownerRebuildsBeforeRestore = demo.rebuildCount(SEAM_OWNER);
    neighborRebuildsBeforeRestore = demo.rebuildCount(SEAM_NEIGHBOR);
    require(
        demo.world()
            .setBlock(SEAM_TARGET.x(), SEAM_TARGET.y(), SEAM_TARGET.z(), originalTargetBlock),
        "Smoke fixture target did not restore to its original value");
    require(
        demo.world().setBlock(16, SEAM_TARGET.y(), SEAM_TARGET.z(), originalNeighborBlock),
        "Smoke fixture neighbor did not restore to its original value");
    require(
        demo.world().dirtyChunks().containsAll(List.of(SEAM_OWNER, SEAM_NEIGHBOR)),
        "Restoring the smoke fixture did not dirty both seam chunks");
    phase = Phase.WAIT_FIXTURE_RESTORE;
  }

  private void waitForFixtureRestore() {
    if (demo.dirtyChunkCount() != 0) return;
    require(
        demo.rebuildCount(SEAM_OWNER) == ownerRebuildsBeforeRestore + 1,
        "Restored fixture owner did not rebuild exactly once");
    require(
        demo.rebuildCount(SEAM_NEIGHBOR) == neighborRebuildsBeforeRestore + 1,
        "Restored fixture neighbor did not rebuild exactly once");
    require(
        demo.world().getBlock(SEAM_TARGET.x(), SEAM_TARGET.y(), SEAM_TARGET.z())
            == originalTargetBlock,
        "Smoke fixture target remained in the gallery world");
    require(
        demo.world().getBlock(16, SEAM_TARGET.y(), SEAM_TARGET.z()) == originalNeighborBlock,
        "Smoke fixture neighbor remained in the gallery world");
    demo.resize(Math.min(960, Gdx.graphics.getWidth()), Math.min(540, Gdx.graphics.getHeight()));
    demo.restoreGalleryCamera();
    require(demo.currentTargetBlock() != null, "Gallery camera has no terrain block in reach");
    phase = Phase.RESTORE_VIEWPORT;
  }

  private void restoreViewport() {
    require(demo.dirtyChunkCount() == 0, "Resize caused terrain edits");
    demo.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    phase = Phase.WAIT_STATIONARY;
  }

  private void verifyStationaryWorld() {
    require(demo.dirtyChunkCount() == 0, "Stationary scene unexpectedly became dirty");
    require(demo.lastRebuilds() == 0, "Stationary clean scene rebuilt a chunk");
    stationaryFrames++;
    if (stationaryFrames < 3) return;
    require(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "Voxel scene left an OpenGL error");
    phase = Phase.CAPTURE;
  }

  private void captureProof() {
    if (artifactsWritten) return;
    require(demo.dirtyChunkCount() == 0, "Cannot save proof while terrain meshes are dirty");
    require(demo.visibleChunkCount() > 0, "Terrain has no visible chunks in the gallery view");
    require(demo.currentTargetBlock() != null, "Gallery screenshot has no selected terrain block");
    require(demo.hudFitsViewport(), "HUD content exceeds its visible panel bounds");
    require(
        Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "Voxel scene left an OpenGL error before capture");
    require(focusVerified, "Native focus probe was not completed");
    demo.writeGalleryScreenshot();
    demo.finishSmoke(
        "{\n"
            + "  \"frames\": "
            + demo.frameNumber()
            + ",\n"
            + "  \"loadedChunks\": "
            + demo.world().loadedChunkCount()
            + ",\n"
            + "  \"visibleChunks\": "
            + demo.visibleChunkCount()
            + ",\n"
            + "  \"dirtyChunks\": "
            + demo.dirtyChunkCount()
            + ",\n"
            + "  \"rebuilds\": "
            + demo.totalRebuilds()
            + ",\n"
            + "  \"seamOwnerRebuilds\": "
            + demo.rebuildCount(SEAM_OWNER)
            + ",\n"
            + "  \"seamNeighborRebuilds\": "
            + demo.rebuildCount(SEAM_NEIGHBOR)
            + ",\n"
            + "  \"faces\": "
            + demo.faceCount()
            + ",\n"
            + "  \"stationaryFrames\": "
            + stationaryFrames
            + ",\n"
            + "  \"focusLossAndRegain\": true,\n"
            + "  \"cameraMoveAndLook\": true,\n"
            + "  \"fpsSpawnGrounded\": true,\n"
            + "  \"fpsWallCollision\": true,\n"
            + "  \"fpsSprint\": true,\n"
            + "  \"fpsJumpAndHeadCollision\": true,\n"
            + "  \"fpsMouseRightLook\": true,\n"
            + "  \"fpsPlacementProtection\": true,\n"
            + "  \"fpsModeToggle\": true,\n"
            + "  \"fpsUnsafeFlyReturnFallback\": true,\n"
            + "  \"crossSeamRemoveAndPlace\": true,\n"
            + "  \"smokeFixtureRestored\": true,\n"
            + "  \"galleryTarget\": {\"x\": "
            + demo.currentTargetBlock().x()
            + ", \"y\": "
            + demo.currentTargetBlock().y()
            + ", \"z\": "
            + demo.currentTargetBlock().z()
            + "},\n"
            + "  \"hudFitsViewport\": true,\n"
            + "  \"resize\": true,\n"
            + "  \"glErrorAfterCleanup\": 0\n"
            + "}\n");
    artifactsWritten = true;
    phase = Phase.COMPLETE;
    Gdx.app.log(
        "Voxels",
        "SCENE PASS: grounded FPS movement, free-fly, seam edits, resize and gallery proof");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private enum Phase {
    WAIT_INITIAL_MESHES,
    WAIT_MAIN_FOCUS,
    WAIT_FOCUS_LOSS,
    WAIT_FOCUS_REGAIN,
    WAIT_FPS_MESHES,
    WAIT_REMOVE_MESHES,
    WAIT_PLACE_MESHES,
    WAIT_FIXTURE_RESTORE,
    RESTORE_VIEWPORT,
    WAIT_STATIONARY,
    CAPTURE,
    COMPLETE
  }
}
