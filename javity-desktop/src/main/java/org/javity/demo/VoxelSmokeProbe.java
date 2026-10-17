package org.javity.demo;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import org.javity.avatar.AvatarView;
import org.javity.presentation.CameraPresentation3D;
import org.javity.presentation.PresentationEffects3D;
import com.badlogic.gdx.math.Matrix4;
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
  private boolean avatarModeProof;
  private BlockPos avatarSweepWall;
  private int avatarSweepWallOriginal;
  private double locomotionBeforeModeSwitch;
  private double locomotionWhilePaused;
  private double useWhilePaused;
  private float avatarWallClearanceFraction = Float.NaN;
  private Matrix4 pausedArmPose;
  private Matrix4 pausedLegPose;
  private Matrix4 firstJumpPose;
  private Matrix4 secondJumpPose;
  private int jumpDeadline;
  private int pausedMarkerCount;
  private int playerFloorBlock;
  private int cameraCenterBlock;
  private boolean cameraCenterLoaded;
  private Vector3 clearBoomFeet, clearBoomEye, clearBoomCamera, clearBoomDirection;
  private float clearBoomFov, clearBoomNear;
  private final java.util.List<BlockPos> viewmodelWallCells = new java.util.ArrayList<>();
  private final java.util.List<Integer> viewmodelWallOriginal = new java.util.ArrayList<>();
  private int fpsWallViewmodelSkinPixels;
  private int fpsWallViewmodelToolPixels;
  private int fpsWallViewmodelWallPixels;
  private boolean focusVerified;
  private boolean fpsControllerVerified;
  private long mainWindow, focusWindow;
  private int focusDeadline;
  private int actionStartFrame;
  private boolean toolActionProof;
  private int recoilChangedPixels;
  private Matrix4 actionStartPose;
  private Vector3 recoilFeet;
  private Matrix4 recoilWorldCamera;
  private final StringBuilder toolMetrics = new StringBuilder();

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
      case WAIT_TOOL_ACTION -> verifyToolAction();
      case WAIT_TOOL_RESIZE -> verifyToolResize();
      case WAIT_TOOL_RESIZE_RESTORE -> verifyToolResizeRestore();
      case WAIT_TOOL_WALK -> verifyToolWalk();
      case WAIT_TOOL_SPRINT -> verifyToolSprint();
      case WAIT_AVATAR_TPP -> verifyAvatarTpp();
      case WAIT_AVATAR_FPS_WALL -> verifyFpsViewmodelWall();
      case WAIT_AVATAR_SLOTS_OFF -> verifyAvatarSlotsOff();
      case WAIT_AVATAR_SLOTS_ON -> verifyAvatarSlotsOn();
      case WAIT_AVATAR_ACTIVE_USE -> verifyAvatarActiveUse();
      case WAIT_AVATAR_PAUSED -> verifyAvatarPaused();
      case WAIT_AVATAR_RESUME -> verifyAvatarResume();
      case WAIT_AVATAR_CLEAR -> verifyAvatarClearBoom();
      case WAIT_JUMP_FIRST_POSE -> verifyFirstJumpPose();
      case WAIT_JUMP_FIRST_RELEASE -> verifyFirstJumpReleased();
      case WAIT_JUMP_SECOND_POSE -> verifySecondJumpPose();
      case WAIT_JUMP_SECOND_RELEASE -> verifySecondJumpReleased();
      case CAPTURE -> captureProof();
      case COMPLETE -> {}
    }
  }

  private void waitForInitialMeshes() {
    if (demo.dirtyChunkCount() != 0) return;
    if (!avatarModeProof) {
      beginAvatarModeProof();
      return;
    }
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

  private void beginAvatarModeProof() {
    require(
        demo.avatarCameraMode() == org.javity.render.AvatarCameraMode.FIRST_PERSON,
        "Voxel scene did not start in FPS camera mode");
    require(demo.currentTargetBlock() != null,
        "Initial voxel FPS camera must face a reachable terrain mining target");
    locomotionBeforeModeSwitch = demo.locomotionTime();
    demo.writeAvatarScreenshot("fps");
    demo.writeFpsCompositionProof();
    installFpsViewmodelWall();
    demo.game().pause();
    phase = Phase.WAIT_AVATAR_FPS_WALL;
  }

  private void installFpsViewmodelWall() {
    Vector3 eye = demo.playerEye();
    Vector3 look = demo.fpsController().getLookDirection(new Vector3());
    boolean wallAlongZ = Math.abs(look.z) >= Math.abs(look.x);
    int wallAxisCell = (int) Math.floor(wallAlongZ ? eye.z + look.z * 1.0f : eye.x + look.x * 1.0f);
    int centerX = (int) Math.floor(eye.x),
        centerY = (int) Math.floor(eye.y),
        centerZ = (int) Math.floor(eye.z);
    for (int side = -2; side <= 2; side++) {
      for (int vertical = -1; vertical <= 2; vertical++) {
        BlockPos cell =
            wallAlongZ
                ? new BlockPos(centerX + side, centerY + vertical, wallAxisCell)
                : new BlockPos(wallAxisCell, centerY + vertical, centerZ + side);
        require(
            demo.world()
                .isLoaded(org.javity.voxel.ChunkPos.fromBlock(cell.x(), cell.y(), cell.z())),
            "FPS viewmodel wall fixture must use loaded terrain");
        viewmodelWallCells.add(cell);
        viewmodelWallOriginal.add(demo.world().getBlock(cell.x(), cell.y(), cell.z()));
        demo.world().setBlock(cell.x(), cell.y(), cell.z(), demo.terrain().stoneId());
      }
    }
  }

  private void verifyFpsViewmodelWall() {
    require(demo.game().isPaused(), "FPS viewmodel occlusion fixture must freeze world clocks");
    if (demo.dirtyChunkCount() != 0) return;
    fpsWallViewmodelSkinPixels = demo.writeAvatarScreenshot("fps-wall-viewmodel");
    fpsWallViewmodelToolPixels = demo.lastAvatarToolPixels();
    fpsWallViewmodelWallPixels = demo.lastViewmodelWallPixels();
    require(
        fpsWallViewmodelSkinPixels > 20,
        "Viewmodel skin pixels were occluded by the nearer world wall");
    require(
        fpsWallViewmodelToolPixels > 100,
        "Held tool was not visibly rendered over the nearer world wall");
    require(
        fpsWallViewmodelWallPixels > 10_000,
        "Viewmodel occlusion fixture did not cover the center of the world image");
    if (!toolActionProof) {
      beginToolActionProof();
      return;
    }
    for (int i = 0; i < viewmodelWallCells.size(); i++) {
      BlockPos cell = viewmodelWallCells.get(i);
      demo.world().setBlock(cell.x(), cell.y(), cell.z(), viewmodelWallOriginal.get(i));
    }
    viewmodelWallCells.clear();
    viewmodelWallOriginal.clear();
    demo.game().resume();
    locomotionBeforeModeSwitch = demo.locomotionTime();
    Vector3 feet = demo.playerFeet();
    Vector3 eye = demo.playerEye();
    avatarSweepWall =
        new BlockPos(
            (int) Math.floor(feet.x), (int) Math.floor(eye.y), (int) Math.floor(feet.z + 2));
    avatarSweepWallOriginal =
        demo.world().getBlock(avatarSweepWall.x(), avatarSweepWall.y(), avatarSweepWall.z());
    require(avatarSweepWallOriginal == 0, "TPP sweep wall fixture must begin empty");
    demo.world()
        .setBlock(
            avatarSweepWall.x(),
            avatarSweepWall.y(),
            avatarSweepWall.z(),
            demo.terrain().stoneId());
    demo.dispatchRawKeyDownForSmoke(Input.Keys.F5);
    demo.dispatchRawKeyDownForSmoke(Input.Keys.F5);
    require(
        demo.avatarCameraMode() == org.javity.render.AvatarCameraMode.THIRD_PERSON,
        "Repeated F5 keyDown toggled more than once");
    demo.dispatchRawKeyUpForSmoke(Input.Keys.F5);
    phase = Phase.WAIT_AVATAR_TPP;
  }


  private void beginToolActionProof() {
    captureToolRoi("wall");
    double before = demo.useTime();
    Gdx.input.setCursorCatched(true);
    Gdx.input.getInputProcessor().touchDown(640, 500, 0, Input.Buttons.LEFT);
    Gdx.input.getInputProcessor().touchUp(640, 500, 0, Input.Buttons.LEFT);
    demo.dispatchKeyDownForSmoke(Input.Keys.E);
    require(demo.useTime() == before, "Paused WORLD accepted a tool action");
    demo.game().resume();

    var stage = (com.badlogic.gdx.scenes.scene2d.Stage) field(demo, "stage");
    var blocker = new com.badlogic.gdx.scenes.scene2d.Actor();
    blocker.setBounds(600, 180, 160, 100);
    blocker.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
      @Override public boolean touchDown(com.badlogic.gdx.scenes.scene2d.InputEvent e,
          float x, float y, int pointer, int button) { return true; }
    });
    stage.addActor(blocker);
    stage.setKeyboardFocus(blocker);
    BlockPos target = demo.currentTargetBlock();
    require(target != null, "Mining proof needs a real wall target");
    int original = demo.world().getBlock(target.x(), target.y(), target.z());
    demo.dispatchKeyDownForSmoke(Input.Keys.E);
    Gdx.input.getInputProcessor().touchDown(640, 500, 0, Input.Buttons.LEFT);
    Gdx.input.getInputProcessor().touchUp(640, 500, 0, Input.Buttons.LEFT);
    require(demo.useTime() == before, "Focused UI started a swing");
    require(demo.world().getBlock(target.x(), target.y(), target.z()) == original,
        "Focused UI mined a world block");
    stage.setKeyboardFocus(null);
    blocker.remove();

    Gdx.input.setCursorCatched(false);
    Gdx.input.getInputProcessor().touchDown(640, 500, 0, Input.Buttons.LEFT);
    Gdx.input.getInputProcessor().touchUp(640, 500, 0, Input.Buttons.LEFT);
    require(demo.world().getBlock(target.x(), target.y(), target.z()) == original,
        "Cursor capture click also mined a block");
    Gdx.input.getInputProcessor().touchDown(640, 500, 0, Input.Buttons.LEFT);
    require(demo.world().getBlock(target.x(), target.y(), target.z()) == 0,
        "LMB did not remove its actual raycast target");
    require(((PresentationEffects3D) field(demo, "toolEffects")).activeCount() == 1,
        "LMB did not acquire exactly one recoil playback");
    BlockPos next = demo.currentTargetBlock();
    int nextBlock = next == null ? 0 : demo.world().getBlock(next.x(), next.y(), next.z());
    Gdx.input.getInputProcessor().touchDown(640, 500, 0, Input.Buttons.LEFT);
    require(next == null || demo.world().getBlock(next.x(), next.y(), next.z()) == nextBlock,
        "Held LMB removed a second block");
    require(((PresentationEffects3D) field(demo, "toolEffects")).activeCount() == 1,
        "Held LMB acquired a second recoil playback");
    Gdx.input.getInputProcessor().touchUp(640, 500, 0, Input.Buttons.LEFT);
    demo.world().setBlock(target.x(), target.y(), target.z(), original);
    recoilFeet = demo.playerFeet().cpy();
    recoilWorldCamera = ((com.badlogic.gdx.graphics.PerspectiveCamera) field(demo, "camera")).combined.cpy();
    actionStartPose = demo.rightArmPoseForSmoke();
    captureToolRoi("action-start");
    actionStartFrame = demo.frameNumber();
    phase = Phase.WAIT_TOOL_ACTION;
  }

  private void verifyToolAction() {
    int age = demo.frameNumber() - actionStartFrame;
    verifyHandAndToolNearPlane();
    require(demo.playerFeet().epsilonEquals(recoilFeet, 1e-5f),
        "Recoil moved gameplay feet");
    require(matricesNear(recoilWorldCamera,
        ((com.badlogic.gdx.graphics.PerspectiveCamera) field(demo, "camera")).combined, 1e-5f),
        "Viewmodel recoil changed world camera/picking");
    if (age == 6) {
      require(!matricesNear(actionStartPose, demo.rightArmPoseForSmoke(), 1e-4f),
          "LMB swing did not change the canonical arm pose");
      CameraPresentation3D.Snapshot snapshot = new CameraPresentation3D.Snapshot();
      ((CameraPresentation3D) field(demo, "toolCameraPresentation")).snapshot(snapshot);
      require(Math.abs(snapshot.translationZ()) > .0001f,
          "Active tool recoil produced no viewmodel contribution");
      captureToolRoi("action-mid");
      captureRecoilControl(snapshot);
      demo.game().pause();
      useWhilePaused = demo.useTime();
      pausedArmPose = demo.rightArmPoseForSmoke();
    }
    if (age == 7) {
      require(demo.useTime() == useWhilePaused, "Paused mining animation advanced");
      require(matricesNear(pausedArmPose, demo.rightArmPoseForSmoke(), 1e-5f),
          "Paused mining pose changed");
      captureToolRoi("action-paused");
      demo.game().resume();
    }
    if (age == 8) require(demo.useTime() > useWhilePaused,
        "Mining animation did not resume");
    if (age < 44) return;
    require(((PresentationEffects3D) field(demo, "toolEffects")).activeCount() == 0,
        "Completed recoil retained a playback");
    require(((CameraPresentation3D) field(demo, "toolCameraPresentation")).activeCount() == 0,
        "Completed recoil retained a camera lease");
    captureToolRoi("action-end");
    demo.dispatchKeyDownForSmoke(Input.Keys.T);
    captureToolRoi("tool-hidden");
    demo.dispatchKeyDownForSmoke(Input.Keys.T);
    require(Gdx.graphics.setWindowedMode(960, 720), "Native resize request failed");
    phase = Phase.WAIT_TOOL_RESIZE;
  }

  private void verifyToolResize() {
    if (Gdx.graphics.getWidth() != 960 || Gdx.graphics.getHeight() != 720) return;
    captureToolRoi("resize-960x720");
    require(demo.hudFitsViewport(), "Resized native HUD exceeded viewport");
    var stage = (com.badlogic.gdx.scenes.scene2d.Stage) field(demo, "stage");
    var hud = (com.badlogic.gdx.scenes.scene2d.Actor) field(demo, "hud");
    var left = stage.stageToScreenCoordinates(new com.badlogic.gdx.math.Vector2(hud.getX(), hud.getY()));
    var right = stage.stageToScreenCoordinates(new com.badlogic.gdx.math.Vector2(
        hud.getX() + hud.getWidth(), hud.getY() + hud.getHeight()));
    require(left.x >= 0 && right.x <= Gdx.graphics.getWidth()
        && right.y >= 0 && left.y <= Gdx.graphics.getHeight(),
        "Native resize clipped HUD in screen space: " + left + " / " + right);

    require(Gdx.graphics.setWindowedMode(1280, 720), "Native restore resize request failed");
    phase = Phase.WAIT_TOOL_RESIZE_RESTORE;
  }

  private void verifyToolResizeRestore() {
    if (Gdx.graphics.getWidth() != 1280 || Gdx.graphics.getHeight() != 720) return;
    demo.setAvatarMoveForSmoke(0, .25f);
    actionStartFrame = demo.frameNumber();
    phase = Phase.WAIT_TOOL_WALK;
  }

  private void verifyToolWalk() {
    if (demo.frameNumber() - actionStartFrame < 6) return;
    require(demo.locomotionParameter() > 0, "Walking did not drive canonical arm bob");
    captureToolRoi("walking");
    demo.fpsController().setSprint(true);
    actionStartFrame = demo.frameNumber();
    phase = Phase.WAIT_TOOL_SPRINT;
  }

  private void verifyToolSprint() {
    if (demo.frameNumber() - actionStartFrame < 6) return;
    require(demo.locomotionParameter() > 0, "Sprinting did not drive canonical arm bob");
    captureToolRoi("sprint");
    demo.setAvatarMoveForSmoke(0, 0);
    demo.fpsController().setSprint(false);
    demo.resetPlayerForSmoke();
    demo.dispatchRawKeyDownForSmoke(Input.Keys.E);
    demo.dispatchRawKeyDownForSmoke(Input.Keys.E);
    require(((PresentationEffects3D) field(demo, "toolEffects")).activeCount() == 1,
        "Held E acquired multiple recoil playbacks");
    demo.dispatchRawKeyUpForSmoke(Input.Keys.E);
    toolActionProof = true;
    demo.game().pause();
    phase = Phase.WAIT_AVATAR_FPS_WALL;

  }

  private void captureToolRoi(String name) {
    double clock = demo.locomotionTime(), use = demo.useTime();
    float nearDepth = verifyHandAndToolNearPlane();
    AvatarView view = (AvatarView) field(demo, "viewmodelView");
    boolean toolOn = demo.toolVisible();
    Pixmap on = null, hidden = null, absent = null;
    try {
      drawForProbe(true);
      on = framebuffer();
      var output = Gdx.files.local("docs/artifacts/voxel-fps/task2-" + name + ".png");
      output.parent().mkdirs();
      PixmapIO.writePNG(output, on, -1, true);
      view.slotVisible("tool", false);
      drawForProbe(true);
      hidden = framebuffer();
      drawForProbe(false);
      absent = framebuffer();
      if (name.equals("wall")) {
        PixmapIO.writePNG(Gdx.files.local(
            "docs/artifacts/voxel-fps/task2-wall-tool-hidden-control.png"), hidden, -1, true);
        PixmapIO.writePNG(Gdx.files.local(
            "docs/artifacts/voxel-fps/task2-wall-no-viewmodel-control.png"), absent, -1, true);
      }
      int hand = 0, shaft = 0, head = 0, leakedTool = 0;
      for (int y = 0; y < on.getHeight() * .75f; y++)
        for (int x = (int) (on.getWidth() * .45f); x < on.getWidth() * .99f; x++) {
          int a = on.getPixel(x, y), h = hidden.getPixel(x, y), n = absent.getPixel(x, y);
          int r = a >>> 24 & 255, g = a >>> 16 & 255, b = a >>> 8 & 255;
          if (a != h && y > on.getHeight() * .15f && g > r * 1.4f
              && b > g * .9f && b < g * 1.3f) head++;
          if (a != h && x > on.getWidth() * .55f && y > on.getHeight() * .05f
              && r > 60 && r > g * 1.3f && g > b * 1.2f) shaft++;
          int hr = h >>> 24 & 255, hg = h >>> 16 & 255, hb = h >>> 8 & 255;
          if (h != n && x > on.getWidth() * .55f && y < on.getHeight() * .5f
              && hr > 140 && hg > 80 && hb > 50 && hr > hg * 1.1f && hg > hb * 1.1f) hand++;
          if (!toolOn && a != h) leakedTool++;
        }
      require(hand > 100, name + " missing isolated hand ROI: " + hand);
      require(toolOn ? head > 100 : head == 0, name + " wrong cyan head ROI: " + head);
      require(toolOn ? shaft > 100 : shaft == 0, name + " wrong wood shaft ROI: " + shaft);
      require(leakedTool == 0, "Tool-hidden retained head/shaft contributions");
      require(clock == demo.locomotionTime() && use == demo.useTime(),
          "Matched ROI/negative renders advanced animation clocks");
      if (toolMetrics.length() > 0) toolMetrics.append(",\n");
      toolMetrics.append("    {\"name\":\"").append(name)
          .append("\",\"width\":").append(on.getWidth())
          .append(",\"height\":").append(on.getHeight())
          .append(",\"handPixels\":").append(hand)
          .append(",\"shaftPixels\":").append(shaft)
          .append(",\"headPixels\":").append(head)
          .append(",\"minimumCameraDepth\":").append(nearDepth).append('}');
    } finally {
      view.slotVisible("tool", toolOn);
      drawForProbe(true);
      if (on != null) on.dispose();
      if (hidden != null) hidden.dispose();
      if (absent != null) absent.dispose();
    }
  }

  private void captureRecoilControl(CameraPresentation3D.Snapshot snapshot) {
    var rig = (org.javity.render.CameraRig3D) field(demo, "cameraRig");
    var provider = (CameraPresentation3D) field(demo, "toolCameraPresentation");
    var owner = (org.javity.core.GameObject) field(demo, "cameraObject");
    require(Math.abs(rig.viewmodelCamera().position.z - snapshot.translationZ()) < 1e-5f,
        "Host did not compose recoil into the actual viewmodel camera");
    Matrix4 worldBefore = ((com.badlogic.gdx.graphics.PerspectiveCamera) field(demo, "camera")).combined.cpy();
    double clock = demo.useTime();
    Pixmap on = framebuffer(), off = null;
    Vector3 eye = demo.playerEye();
    Vector3 look = demo.fpsController().getLookDirection(new Vector3());
    try {
      rig.update(eye, look, owner, null, null);
      drawForProbe(true);
      off = framebuffer();
      PixmapIO.writePNG(Gdx.files.local(
          "docs/artifacts/voxel-fps/task2-recoil-disabled-control.png"), off, -1, true);
      for (int y = 0; y < on.getHeight() * .75f; y++)
        for (int x = (int) (on.getWidth() * .45f); x < on.getWidth() * .99f; x++)
          if (on.getPixel(x, y) != off.getPixel(x, y)) recoilChangedPixels++;
      require(recoilChangedPixels > 100, "Disabling recoil did not change native tool pixels");
      require(demo.useTime() == clock && matricesNear(worldBefore,
          ((com.badlogic.gdx.graphics.PerspectiveCamera) field(demo, "camera")).combined, 1e-5f),
          "Recoil negative-control renders changed world camera or canonical time");
    } finally {
      rig.update(eye, look, owner, null, provider);
      drawForProbe(true);
      on.dispose();
      if (off != null) off.dispose();
    }
  }

  private float verifyHandAndToolNearPlane() {
    var avatar = (GeneratedVoxelAvatar) field(demo, "avatar");
    var rig = (org.javity.render.CameraRig3D) field(demo, "cameraRig");
    var view = (AvatarView) field(demo, "viewmodelView");
    var camera = rig.viewmodelCamera();
    // Authored boxes are in bind coordinates; the same canonical bone drives the rigid
    // pickaxe and the weighted hand. Remove the authored bind translation before posing.
    Matrix4 posed = view.baseTransform()
        .mul(avatar.renderer.instance.getNode("right-arm").globalTransform)
        .translate(-.43f, -1.27f, 0);
    var boxes = new java.util.ArrayList<>(GeneratedVoxelAvatar.PICKAXE);
    boxes.add(GeneratedVoxelAvatar.RIGHT_HAND);
    float minimum = Float.POSITIVE_INFINITY;
    for (var box : boxes) for (int corner = 0; corner < 8; corner++) {
      Vector3 point = new Vector3(
          box.x() + ((corner & 1) == 0 ? 0 : box.w()),
          box.y() + ((corner & 2) == 0 ? 0 : box.h()),
          box.z() + ((corner & 4) == 0 ? 0 : box.d()))
          .mul(posed).mul(camera.view);
      minimum = Math.min(minimum, -point.z);
    }
    require(minimum > camera.near + .1f,
        "Actual posed hand/tool intersects viewmodel near plane: " + minimum);
    return minimum;
  }

  private Pixmap framebuffer() {
    return Pixmap.createFromFrameBuffer(0, 0,
        Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
  }

  private void drawForProbe(boolean viewmodel) {
    try {
      var method = VoxelDemo.class.getDeclaredMethod("drawFrame", float.class, boolean.class);
      method.setAccessible(true);
      method.invoke(demo, 0f, viewmodel);
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Cannot invoke matched native render", failure);
    }
  }

  private static Object field(Object owner, String name) {
    try {
      var f = owner.getClass().getDeclaredField(name);
      f.setAccessible(true);
      return f.get(owner);
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Cannot inspect " + name, failure);
    }
  }

  private void verifyAvatarTpp() {
    require(
        demo.avatarCameraMode() == org.javity.render.AvatarCameraMode.THIRD_PERSON,
        "F5 did not enter TPP mode");
    require(
        demo.cameraClearanceFraction() > 0 && demo.cameraClearanceFraction() < 1,
        "TPP near-plane camera volume did not stop at the test wall");
    avatarWallClearanceFraction = demo.cameraClearanceFraction();
    require(
        demo.cameraPosition().dst(demo.playerEye()) <= demo.boomDistance(),
        "TPP camera exceeded its requested boom distance");
    require(
        demo.fpsController().isGrounded(),
        "Avatar camera-mode proof lost its grounded player pose");
    require(
        demo.rightHandSocketMatchesTool(),
        "Right-hand socket diverged from the attached tool grip");
    require(
        Math.abs((demo.locomotionTime() - locomotionBeforeModeSwitch) - 1d / 60d) < .001,
        "World avatar pose clock advanced more than once across world and viewmodel passes");
    demo.writeAvatarScreenshot("tpp-wall");
    double beforeRemoteView = demo.locomotionTime();
    int visibleBeforeRemoteView = demo.visibleChunkCount();
    demo.renderRemoteAvatarForSmoke();
    require(
        demo.locomotionTime() == beforeRemoteView,
        "Submitting a second camera view advanced the canonical avatar clock");
    require(
        demo.visibleChunkCount() == visibleBeforeRemoteView,
        "Observer culling temporarily changed local chunk visibility");
    demo.writeAvatarScreenshot("same-frame-second-camera-full-body");
    beforeRemoteView = demo.locomotionTime();
    demo.renderRemotePresetForSmoke(org.javity.avatar.AvatarPreset.BODY_WITHOUT_HEAD);
    require(
        demo.locomotionTime() == beforeRemoteView,
        "BODY_WITHOUT_HEAD observer preset advanced the canonical avatar clock");
    require(
        demo.visibleChunkCount() == visibleBeforeRemoteView,
        "Observer preset changed local chunk visibility");
    demo.writeAvatarScreenshot("tpp-body-without-head");
    beforeRemoteView = demo.locomotionTime();
    demo.renderRemotePresetForSmoke(org.javity.avatar.AvatarPreset.ARMS_ONLY);
    require(
        demo.locomotionTime() == beforeRemoteView,
        "ARMS_ONLY observer preset advanced the canonical avatar clock");
    demo.writeAvatarScreenshot("tpp-arms-only-preset");
    demo.dispatchRawKeyDownForSmoke(Input.Keys.C);
    demo.dispatchRawKeyUpForSmoke(Input.Keys.C);
    demo.dispatchRawKeyDownForSmoke(Input.Keys.T);
    demo.dispatchRawKeyUpForSmoke(Input.Keys.T);
    phase = Phase.WAIT_AVATAR_SLOTS_OFF;
    demo.setAvatarMoveForSmoke(0, 0);
  }

  private void verifyAvatarSlotsOff() {
    require(
        !demo.clothingVisible() && !demo.toolVisible(),
        "C/T slot controls did not hide shirt and tool");
    demo.writeAvatarScreenshot("tpp-shirt-tool-hidden");
    demo.dispatchRawKeyDownForSmoke(Input.Keys.C);
    demo.dispatchRawKeyUpForSmoke(Input.Keys.C);
    demo.dispatchRawKeyDownForSmoke(Input.Keys.T);
    demo.dispatchRawKeyUpForSmoke(Input.Keys.T);
    phase = Phase.WAIT_AVATAR_SLOTS_ON;
  }

  private void verifyAvatarSlotsOn() {
    require(
        demo.clothingVisible() && demo.toolVisible(),
        "C/T slot controls did not restore shirt and tool");
    demo.writeAvatarScreenshot("tpp-shirt-tool-visible");
    demo.setAvatarMoveForSmoke(0, .4f);
    demo.dispatchRawKeyDownForSmoke(Input.Keys.E);
    locomotionWhilePaused = demo.locomotionTime();
    useWhilePaused = demo.useTime();
    demo.dispatchRawKeyDownForSmoke(Input.Keys.F5);
    demo.dispatchRawKeyUpForSmoke(Input.Keys.F5);
    phase = Phase.WAIT_AVATAR_ACTIVE_USE;
  }

  private void verifyAvatarActiveUse() {
    require(
        demo.avatarCameraMode() == org.javity.render.AvatarCameraMode.FIRST_PERSON,
        "F5 did not switch modes during simultaneous locomotion and use");
    require(demo.locomotionParameter() > 0, "The locomotion mixer remained at idle during use");
    require(demo.useTime() > useWhilePaused, "Masked use did not advance during locomotion");
    double heldUseTime = demo.useTime();
    demo.dispatchRawKeyDownForSmoke(Input.Keys.E);
    require(demo.useTime() == heldUseTime,
        "Held E restarted the canonical use playback instead of requiring a rising edge");
    demo.dispatchRawKeyUpForSmoke(Input.Keys.E);
    captureToolRoi("f5-fps-during-use");
    double useBeforeSwitch = demo.useTime();
    Matrix4 armBeforeSwitch = demo.rightArmPoseForSmoke();
    demo.dispatchRawKeyDownForSmoke(Input.Keys.F5);
    demo.dispatchRawKeyUpForSmoke(Input.Keys.F5);
    require(
        demo.avatarCameraMode() == org.javity.render.AvatarCameraMode.THIRD_PERSON,
        "F5 did not return to TPP after the active-use check");
    require(demo.useTime() == useBeforeSwitch
        && matricesNear(armBeforeSwitch, demo.rightArmPoseForSmoke(), 1e-5f),
        "F5 during use changed the canonical pose or time");
    // Apply the newly selected boom before the draw-only capture, without advancing a clock.
    ((org.javity.render.CameraRig3D) field(demo, "cameraRig")).update(
        demo.playerEye(), demo.fpsController().getLookDirection(new Vector3()),
        (org.javity.core.GameObject) field(demo, "cameraObject"), null,
        (CameraPresentation3D) field(demo, "toolCameraPresentation"));
    drawForProbe(true);
    Pixmap tppUse = framebuffer();
    try {
      PixmapIO.writePNG(Gdx.files.local(
          "docs/artifacts/voxel-fps/task2-f5-tpp-during-use.png"), tppUse, -1, true);
    } finally { tppUse.dispose(); }
    pausedArmPose = demo.rightArmPoseForSmoke();
    pausedLegPose = demo.leftLegPoseForSmoke();
    pausedMarkerCount = demo.avatarMarkerCountForSmoke();
    demo.game().pause();
    locomotionWhilePaused = demo.locomotionTime();
    useWhilePaused = demo.useTime();
    demo.dispatchRawKeyDownForSmoke(Input.Keys.F5);
    demo.dispatchRawKeyUpForSmoke(Input.Keys.F5);
    phase = Phase.WAIT_AVATAR_PAUSED;
  }

  private void verifyAvatarPaused() {
    require(demo.game().isPaused(), "WORLD pause fixture did not hold the game clock");
    require(
        demo.avatarCameraMode() == org.javity.render.AvatarCameraMode.FIRST_PERSON,
        "F5 did not toggle while WORLD was paused");
    require(
        demo.locomotionTime() == locomotionWhilePaused, "Locomotion advanced during WORLD pause");
    require(demo.useTime() == useWhilePaused, "Upper-body use action advanced during WORLD pause");
    require(
        matricesNear(pausedArmPose, demo.rightArmPoseForSmoke(), 1e-5f),
        "F5 or render changed the arm pose while WORLD was paused");
    require(
        matricesNear(pausedLegPose, demo.leftLegPoseForSmoke(), 1e-5f),
        "Locomotion mixer changed the leg pose while WORLD was paused");
    require(
        pausedMarkerCount == demo.avatarMarkerCountForSmoke(),
        "Avatar animation markers fired while WORLD was paused");
    demo.writeAvatarScreenshot("fps-paused-action");
    demo.dispatchRawKeyDownForSmoke(Input.Keys.F5);
    demo.dispatchRawKeyUpForSmoke(Input.Keys.F5);
    demo.game().resume();
    locomotionWhilePaused = demo.locomotionTime();
    phase = Phase.WAIT_AVATAR_RESUME;
  }

  private void verifyAvatarResume() {
    require(!demo.game().isPaused(), "WORLD did not resume after the paused camera toggle");
    require(
        demo.avatarCameraMode() == org.javity.render.AvatarCameraMode.THIRD_PERSON,
        "F5 did not return to TPP after the paused action");
    require(
        Math.abs((demo.locomotionTime() - locomotionWhilePaused) - 1d / 60d) < .001,
        "Locomotion did not advance once after WORLD resumed");
    require(
        demo.locomotionParameter() > 0,
        "Walking mixer did not enter locomotion during the resumed use action");
    demo.setAvatarMoveForSmoke(0, 0);
    demo.writeAvatarScreenshot("tpp-after-action");
    require(
        demo.world()
            .setBlock(
                avatarSweepWall.x(),
                avatarSweepWall.y(),
                avatarSweepWall.z(),
                avatarSweepWallOriginal),
        "Could not restore TPP camera wall fixture");
    phase = Phase.WAIT_AVATAR_CLEAR;
  }

  private void verifyAvatarClearBoom() {
    require(
        demo.cameraClearanceFraction() == 1f,
        "TPP camera did not return to full boom after wall removal");
    require(
        demo.cameraPosition().dst(demo.playerEye()) > 1f,
        "TPP camera failed to restore a useful clear-space boom");
    require(
        demo.fpsController().isGrounded(), "Clear-boom capture did not retain a grounded player");
    Vector3 feet = demo.playerFeet();
    Vector3 cameraPosition = demo.cameraPosition();
    clearBoomFeet = feet.cpy();
    clearBoomEye = demo.playerEye();
    clearBoomCamera = cameraPosition.cpy();
    clearBoomDirection = demo.cameraDirection();
    clearBoomFov = demo.cameraFov();
    clearBoomNear = demo.cameraNear();
    playerFloorBlock =
        demo.world()
            .getBlock(
                (int) Math.floor(feet.x), (int) Math.floor(feet.y) - 1, (int) Math.floor(feet.z));
    int cameraX = (int) Math.floor(cameraPosition.x);
    int cameraY = (int) Math.floor(cameraPosition.y);
    int cameraZ = (int) Math.floor(cameraPosition.z);
    cameraCenterLoaded =
        demo.world().isLoaded(org.javity.voxel.ChunkPos.fromBlock(cameraX, cameraY, cameraZ));
    cameraCenterBlock = cameraCenterLoaded ? demo.world().getBlock(cameraX, cameraY, cameraZ) : -1;
    require(playerFloorBlock != 0, "Clear-boom player was not standing over a solid terrain cell");
    require(
        cameraCenterBlock == 0, "Clear-boom camera center was inside or outside loaded terrain");
    demo.writeAvatarScreenshot("tpp-clear");
    demo.writeClearBoomCullingDiagnostic();
    demo.resetPlayerForSmoke();
    demo.dispatchRawKeyDownForSmoke(Input.Keys.F5);
    demo.dispatchRawKeyUpForSmoke(Input.Keys.F5);
    require(
        demo.avatarCameraMode() == org.javity.render.AvatarCameraMode.FIRST_PERSON,
        "Could not restore FPS mode for voxel controller coverage");
    demo.setAvatarJumpForSmoke(true);
    firstJumpPose = demo.leftLegPoseForSmoke();
    jumpDeadline = demo.frameNumber() + 40;
    phase = Phase.WAIT_JUMP_FIRST_POSE;
  }

  private void verifyFirstJumpPose() {
    require(demo.frameNumber() < jumpDeadline, "First jump clip did not reach a sampled leg pose");
    if (demo.jumpTimeForSmoke() < .08) return;
    require(
        !matricesNear(firstJumpPose, demo.leftLegPoseForSmoke(), 1e-4f),
        "First jump did not change the leg pose");
    captureToolRoi("jump");
    phase = Phase.WAIT_JUMP_FIRST_RELEASE;
  }

  private void verifyFirstJumpReleased() {
    require(demo.frameNumber() < jumpDeadline + 40, "First jump layer did not release on landing");
    if (demo.jumpPlaybackActiveForSmoke() || demo.jumpWeightForSmoke() != 0) return;
    demo.setAvatarJumpForSmoke(true);
    secondJumpPose = demo.leftLegPoseForSmoke();
    jumpDeadline = demo.frameNumber() + 40;
    phase = Phase.WAIT_JUMP_SECOND_POSE;
  }

  private void verifySecondJumpPose() {
    require(demo.frameNumber() < jumpDeadline, "Second jump clip did not restart");
    if (demo.jumpTimeForSmoke() < .08) return;
    require(
        !matricesNear(secondJumpPose, demo.leftLegPoseForSmoke(), 1e-4f),
        "Second jump did not change the leg pose");
    phase = Phase.WAIT_JUMP_SECOND_RELEASE;
  }

  private void verifySecondJumpReleased() {
    require(demo.frameNumber() < jumpDeadline + 40, "Second jump layer did not release on landing");
    if (demo.jumpPlaybackActiveForSmoke() || demo.jumpWeightForSmoke() != 0) return;
    avatarModeProof = true;
    phase = Phase.WAIT_INITIAL_MESHES;
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
    verifyDebugFocusPreservesPlayer();
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

  private void verifyDebugFocusPreservesPlayer() {
    Vector3 before = demo.playerFeet().cpy();
    Vector3 lookBefore = demo.fpsController().getLookDirection(new Vector3()).cpy();
    var tools = (org.javity.ui.inspector.RuntimeToolsOverlay) field(demo, "tools");
    var game = (org.javity.core.Game) field(demo, "game");
    var player = (org.javity.core.GameObject) field(demo, "cameraObject");
    demo.dispatchKeyDownForSmoke(Input.Keys.F);
    tools.inspector().setVisible(true);
    require(tools.inspector().selectObject(player), "Debug focus selection failed");
    require(tools.inspector().focusSelected(), "Explicit free-fly focus refused");
    tools.inspector().setVisible(false);
    demo.moveFreeFly(Vector3.X, .1f, false);
    game.step(1f / 60f);
    require(demo.playerFeet().epsilonEquals(before, .0001f), "Debug focus/movement teleported player feet");
    demo.dispatchKeyDownForSmoke(Input.Keys.F);
    require(demo.playerFeet().epsilonEquals(before, .0001f), "Returning from debug focus teleported player feet");
    require(demo.fpsController().getLookDirection(new Vector3()).epsilonEquals(lookBefore, .0001f),
        "Returning from debug focus changed original controller look");
    // Editing while the debug camera is detached can obstruct the preserved body position.
    BlockPos obstruction = new BlockPos((int) Math.floor(before.x),
        (int) Math.floor(before.y + .5f), (int) Math.floor(before.z));
    int old = demo.world().getBlock(obstruction.x(), obstruction.y(), obstruction.z());
    demo.dispatchKeyDownForSmoke(Input.Keys.F);
    demo.world().setBlock(obstruction.x(), obstruction.y(), obstruction.z(), demo.terrain().stoneId());
    demo.dispatchKeyDownForSmoke(Input.Keys.F);
    require(demo.playerFeet().dst2(before) > .01f, "Obstructed stored feet did not recover to a safe spawn");
    require(demo.fpsController().getLookDirection(new Vector3()).epsilonEquals(lookBefore, .0001f),
        "Debug focus changed controller look");
    demo.world().setBlock(obstruction.x(), obstruction.y(), obstruction.z(), old);
    Gdx.app.log("RuntimeTools", "voxel debug focus PASS: close + step + return preserved feet/look; edited-body obstruction recovered");
  }

  private void requireFpsFallbackAt(Vector3 unsafeEye, String location) {
    Vector3 preservedFeet = demo.playerFeet().cpy();
    demo.dispatchKeyDownForSmoke(Input.Keys.F);
    demo.setCameraPose(unsafeEye, Vector3.Z);
    demo.dispatchKeyDownForSmoke(Input.Keys.F);
    require(!demo.isFreeFlyMode(), "F did not restore FPS mode from " + location);
    require(
        demo.playerFeet().dst2(preservedFeet) < .0001f,
        "Returning from debug camera at " + location + " changed the preserved player feet");
  }

  // Exercise real GLFW focus callbacks through DesktopLauncher configuration. Reflection only
  // observes private input guards here in the test utility; gameplay gets no test-only hooks.
  private void waitForMainFocus() {
    require(demo.frameNumber() <= focusDeadline, "Main GLFW window never gained focus");
    if (GLFW.glfwGetWindowAttrib(mainWindow, GLFW.GLFW_FOCUSED) != GLFW.GLFW_TRUE
        || !inputGuard("focused")) return;
    demo.dispatchRawKeyDownForSmoke(Input.Keys.E);
    require(((PresentationEffects3D) field(demo, "toolEffects")).activeCount() == 1,
        "Native focus fixture did not start active recoil");
    demo.dispatchRawKeyDownForSmoke(Input.Keys.F5);
    require(
        inputGuard("cameraModeToggleHeld"), "F5 native latch was not held before focus-loss proof");
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
    require(
        !inputGuard("cameraModeToggleHeld"), "Focus loss did not clear the held F5 toggle latch");
    require(!inputGuard("useHeld") && !inputGuard("mineHeld"),
        "Native focus loss did not clear tool input guards");
    require(((PresentationEffects3D) field(demo, "toolEffects")).activeCount() == 0
        && ((CameraPresentation3D) field(demo, "toolCameraPresentation")).activeCount() == 0,
        "Native focus loss retained owned recoil or camera lease");
    double beforeUse = demo.useTime();
    BlockPos target = demo.currentTargetBlock();
    int value = target == null ? 0 : demo.world().getBlock(target.x(), target.y(), target.z());
    demo.dispatchKeyDownForSmoke(Input.Keys.E);
    Gdx.input.getInputProcessor().touchDown(640, 500, 0, Input.Buttons.LEFT);
    Gdx.input.getInputProcessor().touchUp(640, 500, 0, Input.Buttons.LEFT);
    require(demo.useTime() == beforeUse, "Unfocused window started tool animation");
    require(target == null || demo.world().getBlock(target.x(), target.y(), target.z()) == value,
        "Unfocused window mined a block");
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
    demo.dispatchRawKeyDownForSmoke(Input.Keys.F5);
    demo.dispatchRawKeyUpForSmoke(Input.Keys.F5);
    require(
        demo.avatarCameraMode() == org.javity.render.AvatarCameraMode.FIRST_PERSON,
        "F5 remained latched across native focus loss/regain");
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
    require(avatarModeProof, "Native FPS/TPP avatar mode proof did not complete");
    demo.writeGalleryScreenshot();
    String sceneProof = (
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
            + "  \"focusLossClearsF5Latch\": true,\n"
            + "  \"cameraMoveAndLook\": true,\n"
            + "  \"fpsSpawnGrounded\": true,\n"
            + "  \"fpsWallCollision\": true,\n"
            + "  \"fpsSprint\": true,\n"
            + "  \"fpsJumpAndHeadCollision\": true,\n"
            + "  \"fpsMouseRightLook\": true,\n"
            + "  \"fpsPlacementProtection\": true,\n"
            + "  \"fpsModeToggle\": true,\n"
            + "  \"avatarF5WhilePaused\": true,\n"
            + "  \"avatarF5DuringLocomotionAndUse\": true,\n"
            + "  \"avatarActionAndLocomotionShareOneStep\": true,\n"
            + "  \"avatarPreparedWeightedBody\": true,\n"
            + "  \"avatarRigidShirtToolSlotsAndSocket\": true,\n"
            + "  \"avatarSocketMatchesToolGrip\": true,\n"
            + "  \"avatarSameFrameSecondCamera\": true,\n"
            + "  \"avatarWallSweepFraction\": "
            + avatarWallClearanceFraction
            + ",\n"
            + "  \"fpsWallViewmodelSkinPixels\": "
            + fpsWallViewmodelSkinPixels
            + ",\n"
            + "  \"fpsWallViewmodelToolPixels\": "
            + fpsWallViewmodelToolPixels
            + ",\n"
            + "  \"fpsWallViewmodelWallPixels\": "
            + fpsWallViewmodelWallPixels
            + ",\n"
            + "  \"cameraVolumeWallClearance\": "
            + demo.cameraClearanceFraction()
            + ",\n"
            + "  \"clearBoomPlayerFloorBlock\": "
            + playerFloorBlock
            + ",\n"
            + "  \"clearBoomCameraCenterLoaded\": "
            + cameraCenterLoaded
            + ",\n"
            + "  \"clearBoomCameraCenterBlock\": "
            + cameraCenterBlock
            + ",\n"
            + "  \"clearBoomFeet\": "
            + vectorJson(clearBoomFeet)
            + ",\n"
            + "  \"clearBoomEye\": "
            + vectorJson(clearBoomEye)
            + ",\n"
            + "  \"clearBoomCamera\": "
            + vectorJson(clearBoomCamera)
            + ",\n"
            + "  \"clearBoomDirection\": "
            + vectorJson(clearBoomDirection)
            + ",\n"
            + "  \"clearBoomFovDegrees\": "
            + clearBoomFov
            + ",\n"
            + "  \"clearBoomNear\": "
            + clearBoomNear
            + ",\n"
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
    verifyCloseDuringSwing();
    demo.finishSmoke(sceneProof);
    artifactsWritten = true;
    phase = Phase.COMPLETE;
    Gdx.app.log(
        "Voxels",
        "SCENE PASS: grounded FPS movement, free-fly, seam edits, resize and gallery proof");
  }

  private void verifyCloseDuringSwing() {
    demo.dispatchKeyDownForSmoke(Input.Keys.E);
    var manager = (PresentationEffects3D) field(demo, "toolEffects");
    var provider = (CameraPresentation3D) field(demo, "toolCameraPresentation");
    require(manager.activeCount() == 1 && provider.activeCount() == 1,
        "Close fixture did not begin during active recoil");
    var avatar = (GeneratedVoxelAvatar) field(demo, "avatar");
    var mesh = (com.badlogic.gdx.graphics.Mesh) field(avatar, "toolMesh");
    int vbo = (Integer) field(field(mesh, "vertices"), "bufferHandle");
    int ibo = (Integer) field(field(mesh, "indices"), "bufferHandle");
    int avatarTexture = avatar.texture.getTextureObjectHandle();
    int atlas = ((com.badlogic.gdx.graphics.Texture) field(demo, "atlas")).getTextureObjectHandle();
    require(vbo > 0 && ibo > 0 && Gdx.gl.glIsBuffer(vbo) && Gdx.gl.glIsBuffer(ibo),
        "Close fixture tool mesh has no real native buffers");
    require(Gdx.gl.glIsTexture(avatarTexture) && Gdx.gl.glIsTexture(atlas),
        "Close fixture textures were already released");
    // The chunk adapter borrows the atlas; its close must preserve the host's texture.
    ((org.javity.voxel.gpu.VoxelChunkRenderer) field(demo, "chunks")).close();
    require(Gdx.gl.glIsTexture(atlas), "Chunk adapter disposed its borrowed atlas");
    GL20 originalGl = Gdx.gl, original20 = Gdx.gl20;
    var deleted = new java.util.HashMap<Integer, Integer>();
    GL20 counting = (GL20) java.lang.reflect.Proxy.newProxyInstance(
        GL20.class.getClassLoader(), new Class<?>[] {GL20.class}, (proxy, method, args) -> {
          if (method.getName().equals("glDeleteBuffer"))
            deleted.merge((Integer) args[0], 1, Integer::sum);
          if (method.getName().equals("glDeleteBuffers")) {
            java.nio.IntBuffer ids = ((java.nio.IntBuffer) args[1]).duplicate();
            for (int i = 0; i < (Integer) args[0]; i++)
              deleted.merge(ids.get(), 1, Integer::sum);
          }
          try { return method.invoke(original20, args); }
          catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
        });
    Gdx.gl = Gdx.gl20 = counting;
    try {
      demo.dispose();
      demo.dispose();
    } finally {
      Gdx.gl = originalGl;
      Gdx.gl20 = original20;
    }
    require(manager.activeCount() == 0 && provider.activeCount() == 0,
        "Scene close during swing retained a playback or presentation lease");
    require(!Gdx.gl.glIsBuffer(vbo) && !Gdx.gl.glIsBuffer(ibo),
        "Scene close leaked the source pickaxe buffers");
    require(deleted.getOrDefault(vbo, 0) == 1 && deleted.getOrDefault(ibo, 0) == 1,
        "Scene double-close did not release tool buffers exactly once: " + deleted);
    require(!Gdx.gl.glIsTexture(avatarTexture) && !Gdx.gl.glIsTexture(atlas),
        "Scene close leaked its owned avatar/atlas textures");
    Gdx.files.local("docs/artifacts/voxel-fps/task2-proof.json").writeString(
        "{\n  \"captures\": [\n" + toolMetrics + "\n  ],\n"
            + "  \"recoilChangedPixels\": " + recoilChangedPixels + ",\n"
            + "  \"LMBRealMiningAndRisingEdge\": true,\n"
            + "  \"pauseResumeUiCursorFocusGates\": true,\n"
            + "  \"gameplayAndWorldCameraUnchangedByRecoil\": true,\n"
            + "  \"F5DuringUsePreservesCanonicalPose\": true,\n"
            + "  \"nativeWindowResize\": true,\n"
            + "  \"closeDuringSwingReleasesLeases\": true,\n"
            + "  \"toolBufferDeleteCounts\": [" + deleted.get(vbo) + "," + deleted.get(ibo) + "],\n"
            + "  \"chunkClosePreservesBorrowedAtlas\": true,\n"
            + "  \"ownedTexturesReleased\": true\n}\n", false, "UTF-8");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private static boolean matricesNear(Matrix4 a, Matrix4 b, float tolerance) {
    for (int i = 0; i < 16; i++) if (Math.abs(a.val[i] - b.val[i]) > tolerance) return false;
    return true;
  }

  private static String vectorJson(Vector3 value) {
    return "{\"x\":" + value.x + ",\"y\":" + value.y + ",\"z\":" + value.z + "}";
  }

  private enum Phase {
    WAIT_TOOL_ACTION,
    WAIT_TOOL_RESIZE,
    WAIT_TOOL_RESIZE_RESTORE,
    WAIT_TOOL_WALK,
    WAIT_TOOL_SPRINT,
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
    WAIT_AVATAR_TPP,
    WAIT_AVATAR_FPS_WALL,
    WAIT_AVATAR_SLOTS_OFF,
    WAIT_AVATAR_SLOTS_ON,
    WAIT_AVATAR_ACTIVE_USE,
    WAIT_AVATAR_PAUSED,
    WAIT_AVATAR_RESUME,
    WAIT_AVATAR_CLEAR,
    WAIT_JUMP_FIRST_POSE,
    WAIT_JUMP_FIRST_RELEASE,
    WAIT_JUMP_SECOND_POSE,
    WAIT_JUMP_SECOND_RELEASE,
    CAPTURE,
    COMPLETE
  }
}
