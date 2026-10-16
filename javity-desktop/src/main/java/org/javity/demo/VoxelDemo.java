package org.javity.demo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.util.Optional;
import org.javity.avatar.AvatarPreset;
import org.javity.avatar.AvatarView;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.render.AvatarCameraMode;
import org.javity.render.Camera3D;
import org.javity.render.CameraRig3D;
import org.javity.render.GdxRenderer;
import org.javity.ui.flex.Flex;
import org.javity.ui.flex.UiTheme;
import org.javity.voxel.AtlasLayout;
import org.javity.voxel.BlockPos;
import org.javity.voxel.ChunkPos;
import org.javity.voxel.VoxelHit;
import org.javity.voxel.VoxelRaycaster;
import org.javity.voxel.VoxelWorld;
import org.javity.voxel.controller.VoxelCameraObstruction;
import org.javity.voxel.controller.VoxelFpsController;
import org.javity.voxel.gpu.VoxelChunkRenderer;

/** Small finite textured voxel field demonstrating the optional data and GPU adapter. */
final class VoxelDemo extends ApplicationAdapter {
  private static final String ATLAS_PATH = "org/javity/demo/voxels/atlas.png";
  private static final AtlasLayout ATLAS_LAYOUT = new AtlasLayout(80, 40, 16, 2, 4, 2);
  private static final long TERRAIN_SEED = 0x5eedc0deL;
  private static final int REBUILD_BUDGET = 1;
  private static final float WALK_SPEED = 9f;
  private static final float FAST_SPEED = 24f;
  private static final float LOOK_SENSITIVITY = .12f;
  private static final float FPS_LOOK_SENSITIVITY = .12f;
  private final boolean smoke;
  private Game game;
  private GdxRenderer renderer;
  private VoxelTerrain terrain;
  private VoxelWorld world;
  private VoxelChunkRenderer chunks;
  private Texture atlas;
  private ShapeRenderer outline;
  private Stage stage;
  private InputMultiplexer inputMultiplexer;
  private UiTheme theme;
  private Flex hud;
  private com.badlogic.gdx.scenes.scene2d.ui.Label status;
  private com.badlogic.gdx.scenes.scene2d.ui.Label counters;
  private com.badlogic.gdx.scenes.scene2d.ui.Label targetStatus;
  private GameObject cameraObject;
  private GameObject remoteCameraObject;
  private Camera3D remoteCameraComponent;
  private Transform3D cameraTransform;
  private VoxelFpsController fpsController;
  private Camera3D cameraComponent;
  private CameraRig3D cameraRig;
  private PerspectiveCamera camera;
  private GeneratedVoxelAvatar avatar;
  private Transform3D avatarTransform;
  private AvatarView worldHiddenView;
  private AvatarView worldFullView;
  private AvatarView viewmodelView;
  private AvatarView remoteProofView;
  private final Vector3 cameraPositionScratch = new Vector3();
  private final Vector3 cameraDirectionScratch = new Vector3();
  private final Vector3 previousPlayerFeet = new Vector3();
  private Vector3 spawnFeet;
  private VoxelSmokeProbe smokeProbe;
  private int selectedBlockId;
  private int frames;
  private int lastAvatarToolPixels;
  private int lastViewmodelWallPixels;
  private float yaw;
  private float pitch;
  private boolean disposed;
  private boolean focused = true;
  private boolean discardFirstMouseDelta = true;
  private boolean freeFlyMode;
  private boolean freeFlyToggleHeld;
  private boolean cameraModeToggleHeld;
  private boolean clothingVisible = true;
  private boolean toolVisible = true;

  VoxelDemo(boolean smoke) {
    this.smoke = smoke;
  }

  @Override
  public void create() {
    try {
      createScene();
    } catch (RuntimeException | Error failure) {
      try {
        dispose();
      } catch (RuntimeException | Error cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      }
      throw failure;
    }
  }

  private void createScene() {
    if (smoke) VoxelGpuSmokeProbe.run();
    renderer = new GdxRenderer();
    outline = new ShapeRenderer();
    terrain = new VoxelTerrain(TERRAIN_SEED);
    terrain.populate();
    world = terrain.world();
    selectedBlockId = terrain.stoneId();
    atlas = new Texture(Gdx.files.internal(ATLAS_PATH), false);
    atlas.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
    atlas.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);

    game = new Game();
    cameraObject = game.createObject("voxel FPS player");
    cameraTransform = cameraObject.addComponent(new Transform3D());
    cameraComponent = cameraObject.addComponent(new Camera3D(67));
    fpsController = cameraObject.addComponent(new VoxelFpsController(world));
    remoteCameraObject = game.createObject("avatar observer camera");
    remoteCameraComponent = remoteCameraObject.addComponent(new Camera3D(67));
    remoteCameraComponent.camera.near = .1f;
    remoteCameraComponent.camera.far = 240;
    camera = cameraComponent.camera;
    camera.near = .1f;
    camera.far = 240;
    spawnFeet = findSpawnFeet();
    cameraTransform.position.set(spawnFeet);
    game.start();
    avatar = GeneratedVoxelAvatar.create(game, .9f);
    avatarTransform = avatar.object.getComponent(Transform3D.class);
    worldHiddenView = avatar.binding.view().parts(java.util.Set.of());
    worldFullView = avatar.binding.view().preset(AvatarPreset.FULL_BODY).slotVisible("shirt", true);
    viewmodelView = avatar.binding.view().preset(AvatarPreset.ARMS_ONLY).slotVisible("tool", true);
    viewmodelView.slotVisible("shirt", false);
    remoteProofView = avatar.binding.view().preset(AvatarPreset.FULL_BODY);
    cameraRig =
        new CameraRig3D(
            cameraComponent, new PerspectiveCamera(72, 1, 1), new VoxelCameraObstruction(world));
    cameraRig.setWorldProjection(67, .1f, 240);
    cameraRig.setViewmodelProjection(72, .03f);
    syncCameraFromFpsController();
    previousPlayerFeet.set(cameraTransform.position);
    chunks = new VoxelChunkRenderer(game, world, atlas, ATLAS_LAYOUT);

    stage = new Stage(new ScreenViewport());
    theme = new UiTheme();
    createHud();
    inputMultiplexer = new InputMultiplexer(stage, createInputProcessor());
    Gdx.input.setInputProcessor(inputMultiplexer);
    resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    if (smoke) smokeProbe = VoxelSmokeProbe.install(this);
  }

  private void createHud() {
    hud = new Flex(theme, "flex-col p-4 gap-1 bg-slate-900 rounded-lg border-2 border-slate-700");
    hud.add(theme.label("JAVITY / VOXEL FIELD", "text-xl text-cyan-400"), "h-8");
    status = theme.label("FPS ready", "text-sm text-emerald-500");
    hud.add(status, "h-6");
    counters =
        theme.label(
            "Chunks 0 / 18  |  visible 0  |  dirty 0  |  faces 0", "text-sm text-slate-200");
    hud.add(counters, "h-6");
    targetStatus = theme.label("Target: none  |  seed " + TERRAIN_SEED, "text-sm text-amber-400");
    hud.add(targetStatus, "h-6");
    hud.add(
        theme.label(
            "WASD move  Space jump  Shift sprint  F free-fly\n"
                + "F5 FPS/TPP  E use  C shirt  T tool  Esc cursor\nLMB remove  RMB place",
            "text-sm text-slate-400",
            true),
        "h-10");
    stage.addActor(hud);
  }

  private InputAdapter createInputProcessor() {
    return new InputAdapter() {
      @Override
      public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.F) {
          if (!freeFlyToggleHeld) {
            freeFlyToggleHeld = true;
            toggleFreeFly();
          }
          return true;
        }
        if (keycode == Input.Keys.F5) {
          if (!cameraModeToggleHeld) {
            cameraModeToggleHeld = true;
            if (!freeFlyMode)
              cameraRig.setMode(
                  cameraRig.mode() == AvatarCameraMode.FIRST_PERSON
                      ? AvatarCameraMode.THIRD_PERSON
                      : AvatarCameraMode.FIRST_PERSON);
          }
          return true;
        }
        if (keycode == Input.Keys.E) {
          avatar.beginUse();
          return true;
        }
        if (keycode == Input.Keys.C) {
          clothingVisible = !clothingVisible;
          worldFullView.slotVisible("shirt", clothingVisible);
          return true;
        }
        if (keycode == Input.Keys.T) {
          toolVisible = !toolVisible;
          worldFullView.slotVisible("tool", toolVisible);
          viewmodelView.slotVisible("tool", toolVisible);
          return true;
        }
        if (keycode == Input.Keys.ESCAPE) {
          Gdx.input.setCursorCatched(!Gdx.input.isCursorCatched());
          discardFirstMouseDelta = true;
          return true;
        }
        if (keycode == Input.Keys.NUM_1) selectedBlockId = terrain.grassId();
        else if (keycode == Input.Keys.NUM_2) selectedBlockId = terrain.dirtId();
        else if (keycode == Input.Keys.NUM_3) selectedBlockId = terrain.stoneId();
        else if (keycode == Input.Keys.NUM_4) selectedBlockId = terrain.woodId();
        else if (keycode == Input.Keys.NUM_5) selectedBlockId = terrain.sandId();
        return false;
      }

      @Override
      public boolean keyUp(int keycode) {
        if (keycode == Input.Keys.F) {
          freeFlyToggleHeld = false;
          return true;
        }
        if (keycode == Input.Keys.F5) {
          cameraModeToggleHeld = false;
          return true;
        }
        return false;
      }

      @Override
      public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (!Gdx.input.isCursorCatched()) {
          if (button == Input.Buttons.LEFT) {
            Gdx.input.setCursorCatched(true);
            discardFirstMouseDelta = true;
          }
          return true;
        }
        if (button == Input.Buttons.LEFT) removeTarget();
        else if (button == Input.Buttons.RIGHT) placeTarget();
        return true;
      }

      @Override
      public boolean scrolled(float amountX, float amountY) {
        int size = terrain.registry().size();
        selectedBlockId = 1 + Math.floorMod(selectedBlockId - 1 + Math.round(amountY), size);
        return true;
      }
    };
  }

  @Override
  public void render() {
    float delta = smoke ? 1f / 60f : Math.min(Gdx.graphics.getDeltaTime(), .1f);
    if (!smoke) updateControls(delta);
    game.step(delta);
    boolean worldAdvanced = !game.isWorldFrozen() && game.deltaTime > 0f;
    if (worldAdvanced) {
      avatar.updateUse();
      avatar.updateJump(fpsController.getVerticalVelocity() > .05f);
    }
    if (!freeFlyMode) {
      syncCameraFromFpsController();
      syncAvatarYawOnly();
      if (worldAdvanced) {
        float speed = cameraTransform.position.dst(previousPlayerFeet) / game.deltaTime;
        avatar.setWalking(MathUtils.clamp(speed / VoxelFpsController.SPRINT_SPEED, 0f, 1f));
        previousPlayerFeet.set(cameraTransform.position);
      }
      cameraRig.update(cameraPositionScratch, cameraDirectionScratch, cameraObject);
    }
    camera.update();
    chunks.update(camera, REBUILD_BUDGET);

    Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    Gdx.gl.glClearColor(.29f, .55f, .78f, 1f);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    if (freeFlyMode) renderer.renderWorld(game, cameraComponent, null);
    else
      renderer.renderWorld(
          game,
          cameraComponent,
          cameraRig.mode() == AvatarCameraMode.FIRST_PERSON ? worldHiddenView : worldFullView);
    drawTargetOutline();
    if (!freeFlyMode && cameraRig.mode() == AvatarCameraMode.FIRST_PERSON) {
      com.badlogic.gdx.math.Matrix4 viewBase =
          new com.badlogic.gdx.math.Matrix4()
              .translate(.05f, -.8f, -1.35f)
              .rotate(Vector3.X, 15)
              .scale(.65f, .65f, .65f);
      viewmodelView.setBaseTransform(viewBase);
      renderer.renderViewmodel(cameraRig.viewmodelCamera(), viewmodelView);
    }
    renderer.render2D(game);
    drawCrosshair();
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    updateHud();
    stage.act(delta);
    stage.draw();
    frames++;
    if (smokeProbe != null) smokeProbe.afterRender();
  }

  private void updateControls(float delta) {
    if (freeFlyMode) {
      updateFreeFly(delta);
      return;
    }
    if (!focused) {
      fpsController.setMoveInput(0, 0);
      fpsController.setSprint(false);
      fpsController.setJump(false);
      return;
    }
    fpsController.setMoveInput(axis(Input.Keys.W, Input.Keys.S), axis(Input.Keys.D, Input.Keys.A));
    fpsController.setSprint(
        Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
            || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT));
    fpsController.setJump(Gdx.input.isKeyPressed(Input.Keys.SPACE));
    if (Gdx.input.isCursorCatched()) {
      if (discardFirstMouseDelta) discardFirstMouseDelta = false;
      else applyFpsMouseLook(Gdx.input.getDeltaX(), Gdx.input.getDeltaY());
    }
  }

  private void applyFpsMouseLook(float deltaX, float deltaY) {
    fpsController.addLookInput(-deltaX * FPS_LOOK_SENSITIVITY, -deltaY * FPS_LOOK_SENSITIVITY);
  }

  private void updateFreeFly(float delta) {
    if (!focused) return;
    float strafe = axis(Input.Keys.D, Input.Keys.A);
    float vertical =
        (Gdx.input.isKeyPressed(Input.Keys.SPACE) ? 1 : 0)
            - ((Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT))
                ? 1
                : 0);
    float forward = axis(Input.Keys.W, Input.Keys.S);
    if (strafe != 0 || vertical != 0 || forward != 0) {
      moveFreeFly(
          new Vector3(strafe, vertical, -forward),
          delta,
          Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
              || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT));
    }
    if (Gdx.input.isCursorCatched()) {
      if (discardFirstMouseDelta) discardFirstMouseDelta = false;
      else rotateLook(Gdx.input.getDeltaX(), Gdx.input.getDeltaY());
    }
    cameraTransform.position.set(camera.position).add(0, -VoxelFpsController.EYE_HEIGHT, 0);
  }

  private float axis(int positive, int negative) {
    return (Gdx.input.isKeyPressed(positive) ? 1 : 0) - (Gdx.input.isKeyPressed(negative) ? 1 : 0);
  }

  void moveFreeFly(Vector3 localInput, float delta, boolean fast) {
    if (localInput.isZero() || delta <= 0) return;
    Vector3 up = Vector3.Y;
    Vector3 right = camera.direction.cpy().crs(up).nor();
    Vector3 movement =
        camera
            .direction
            .cpy()
            .scl(-localInput.z)
            .mulAdd(right, localInput.x)
            .mulAdd(up, localInput.y);
    if (!movement.isZero())
      camera.position.mulAdd(movement.nor(), (fast ? FAST_SPEED : WALK_SPEED) * delta);
    camera.position.x = MathUtils.clamp(camera.position.x, -16, 32);
    camera.position.y = MathUtils.clamp(camera.position.y, 0, 32);
    camera.position.z = MathUtils.clamp(camera.position.z, -16, 32);
    cameraTransform.position.set(camera.position).add(0, -VoxelFpsController.EYE_HEIGHT, 0);
  }

  void rotateLook(float deltaX, float deltaY) {
    yaw += deltaX * LOOK_SENSITIVITY;
    pitch = MathUtils.clamp(pitch - deltaY * LOOK_SENSITIVITY, -89f, 89f);
    updateDirection();
  }

  private void updateDirection() {
    float yawRadians = yaw * MathUtils.degreesToRadians;
    float pitchRadians = pitch * MathUtils.degreesToRadians;
    float cosPitch = MathUtils.cos(pitchRadians);
    camera
        .direction
        .set(
            cosPitch * MathUtils.cos(yawRadians),
            MathUtils.sin(pitchRadians),
            cosPitch * MathUtils.sin(yawRadians))
        .nor();
    camera.up.set(Vector3.Y);
    camera.update();
  }

  void setCameraPose(Vector3 position, Vector3 direction) {
    setFreeFlyMode(true);
    camera.position.set(position);
    camera.direction.set(direction).nor();
    camera.up.set(Vector3.Y);
    pitch = MathUtils.asin(camera.direction.y) * MathUtils.radiansToDegrees;
    yaw = MathUtils.atan2(camera.direction.z, camera.direction.x) * MathUtils.radiansToDegrees;
    cameraTransform.position.set(camera.position).add(0, -VoxelFpsController.EYE_HEIGHT, 0);
    camera.update();
  }

  private void toggleFreeFly() {
    setFreeFlyMode(!freeFlyMode);
  }

  private void setFreeFlyMode(boolean enabled) {
    if (freeFlyMode == enabled) return;
    if (enabled) {
      syncCameraFromFpsController();
      freeFlyMode = true;
      fpsController.setEnabled(false);
    } else {
      cameraTransform.position.set(camera.position).add(0, -VoxelFpsController.EYE_HEIGHT, 0);
      if (!isClearFpsBody(cameraTransform.position)) {
        // Editing may have obstructed the original spawn since startup.
        spawnFeet.set(findSpawnFeet());
        cameraTransform.position.set(spawnFeet);
        camera.position.set(spawnFeet).add(0, VoxelFpsController.EYE_HEIGHT, 0);
      }
      float nextYaw = (float) Math.toDegrees(Math.atan2(-camera.direction.x, -camera.direction.z));
      float nextPitch =
          (float) Math.toDegrees(Math.asin(MathUtils.clamp(camera.direction.y, -1f, 1f)));
      fpsController.addLookInput(
          shortestAngleDelta(fpsController.getYawDegrees(), nextYaw),
          nextPitch - fpsController.getPitchDegrees());
      freeFlyMode = false;
      fpsController.setEnabled(true);
    }
  }

  private static float shortestAngleDelta(float from, float to) {
    return ((to - from + 540f) % 360f) - 180f;
  }

  private void syncCameraFromFpsController() {
    fpsController.getEyePosition(cameraPositionScratch);
    fpsController.getLookDirection(cameraDirectionScratch);
    camera.position.set(cameraPositionScratch);
    camera.direction.set(cameraDirectionScratch);
    camera.up.set(Vector3.Y);
  }

  private void syncAvatarYawOnly() {
    avatarTransform.position.set(fpsController.getTransform3D().position);
    avatarTransform.rotation.idt().setFromAxis(Vector3.Y, fpsController.getYawDegrees());
  }

  private boolean isClearFpsBody(Vector3 feet) {
    if (!Float.isFinite(feet.x) || !Float.isFinite(feet.y) || !Float.isFinite(feet.z)) return false;
    float radius = VoxelFpsController.BODY_RADIUS;
    float minX = feet.x - radius, maxX = feet.x + radius;
    float minY = feet.y, maxY = feet.y + VoxelFpsController.BODY_HEIGHT;
    float minZ = feet.z - radius, maxZ = feet.z + radius;
    if (minX < BlockPos.MIN_COORDINATE
        || minY < BlockPos.MIN_COORDINATE
        || minZ < BlockPos.MIN_COORDINATE
        || maxX > BlockPos.MAX_COORDINATE + 1f
        || maxY > BlockPos.MAX_COORDINATE + 1f
        || maxZ > BlockPos.MAX_COORDINATE + 1f) return false;
    int x0 = (int) Math.floor(minX + 1e-5f), x1 = (int) Math.floor(maxX - 1e-5f);
    int y0 = (int) Math.floor(minY + 1e-5f), y1 = (int) Math.floor(maxY - 1e-5f);
    int z0 = (int) Math.floor(minZ + 1e-5f), z1 = (int) Math.floor(maxZ - 1e-5f);
    for (int y = y0; y <= y1; y++)
      for (int z = z0; z <= z1; z++)
        for (int x = x0; x <= x1; x++)
          if (!world.isLoaded(ChunkPos.fromBlock(x, y, z)) || world.getBlock(x, y, z) != 0)
            return false;
    return true;
  }

  private Vector3 findSpawnFeet() {
    for (int radius = 0; radius <= 40; radius++) {
      for (int z = -16; z < 32; z++) {
        for (int x = -16; x < 32; x++) {
          if (Math.abs(x - 8) + Math.abs(z - 8) != radius) continue;
          if (x <= -16
              || x >= 31
              || z <= -16
              || z >= 31
              || !world.isLoaded(ChunkPos.fromBlock(x, 0, z))
              || !world.isLoaded(ChunkPos.fromBlock(x, 0, z - 1))) continue;
          int top = -1;
          for (int y = 31; y >= 0; y--) {
            if (world.getBlock(x, y, z) != 0) {
              top = y;
              break;
            }
          }
          int feetY = top + 1;
          if (top < 0 || feetY + VoxelFpsController.BODY_HEIGHT > 32f) continue;
          boolean clear = true;
          for (int y = feetY; y < feetY + VoxelFpsController.BODY_HEIGHT; y++)
            if (world.getBlock(x, y, z) != 0) clear = false;
          if (world.getBlock(x, feetY, z - 1) != 0
              || world.getBlock(x, feetY + 1, z - 1) != 0
              || world.getBlock(x, feetY + 2, z) != 0) clear = false;
          if (clear) return new Vector3(x + .5f, feetY, z + .5f);
        }
      }
    }
    throw new IllegalStateException("Could not find a loaded terrain spawn with player headroom");
  }

  boolean removeTarget() {
    Optional<VoxelHit> hit = currentHit();
    if (hit.isEmpty()) return false;
    var block = hit.get().block();
    return world.setBlock(block.x(), block.y(), block.z(), 0);
  }

  boolean placeTarget() {
    Optional<VoxelHit> hit = currentHit();
    if (hit.isEmpty() || hit.get().placementCell() == null) return false;
    BlockPos cell = hit.get().placementCell();
    if (!world.isLoaded(ChunkPos.fromBlock(cell.x(), cell.y(), cell.z()))) return false;
    if (world.getBlock(cell.x(), cell.y(), cell.z()) != 0) return false;
    if (!freeFlyMode && intersectsFpsPlayer(cell)) return false;
    return world.setBlock(cell.x(), cell.y(), cell.z(), selectedBlockId);
  }

  private boolean intersectsFpsPlayer(BlockPos cell) {
    Vector3 feet = cameraTransform.position;
    float radius = VoxelFpsController.BODY_RADIUS;
    return feet.x + radius > cell.x() + 1e-5f
        && feet.x - radius < cell.x() + 1f - 1e-5f
        && feet.y + VoxelFpsController.BODY_HEIGHT > cell.y() + 1e-5f
        && feet.y < cell.y() + 1f - 1e-5f
        && feet.z + radius > cell.z() + 1e-5f
        && feet.z - radius < cell.z() + 1f - 1e-5f;
  }

  private Optional<VoxelHit> currentHit() {
    if (freeFlyMode) return VoxelRaycaster.cast(world, camera.position, camera.direction, 8, 512);
    fpsController.getEyePosition(cameraPositionScratch);
    fpsController.getLookDirection(cameraDirectionScratch);
    return VoxelRaycaster.cast(world, cameraPositionScratch, cameraDirectionScratch, 8, 512);
  }

  private void drawTargetOutline() {
    Optional<VoxelHit> hit = currentHit();
    if (hit.isEmpty()) return;
    BlockPos block = hit.get().block();
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    outline.setProjectionMatrix(camera.combined);
    outline.begin(ShapeRenderer.ShapeType.Line);
    outline.setColor(new Color(.98f, .83f, .37f, 1));
    outline.box(block.x(), block.y(), block.z() + 1, 1, 1, 1);
    outline.end();
  }

  private void drawCrosshair() {
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    float x = Gdx.graphics.getWidth() * .5f;
    float y = Gdx.graphics.getHeight() * .5f;
    outline.setProjectionMatrix(renderer.camera2D.combined);
    outline.begin(ShapeRenderer.ShapeType.Line);
    outline.setColor(new Color(1, .94f, .72f, .9f));
    outline.line(x - 8, y, x - 2, y);
    outline.line(x + 2, y, x + 8, y);
    outline.line(x, y - 8, x, y - 2);
    outline.line(x, y + 2, x, y + 8);
    outline.end();
  }

  private void updateHud() {
    counters.setText(
        "Chunks "
            + world.loadedChunkCount()
            + " / 18  |  visible "
            + chunks.visibleChunkCount()
            + "  |  dirty "
            + world.dirtyChunkCount()
            + "  |  rebuilds "
            + chunks.totalRebuilds()
            + "  |  faces "
            + chunks.faceCount());
    String selected = terrain.registry().get(selectedBlockId).name();
    status.setText(
        (freeFlyMode ? "Free-fly" : "FPS " + (fpsController.isGrounded() ? "grounded" : "airborne"))
            + "  |  selected: "
            + selected
            + "  |  reach 8");
    Optional<VoxelHit> hit = currentHit();
    targetStatus.setText(
        hit.map(
                    value ->
                        "Target: "
                            + value.block().x()
                            + ", "
                            + value.block().y()
                            + ", "
                            + value.block().z())
                .orElse("Target: none")
            + "  |  seed "
            + TERRAIN_SEED);
  }

  @Override
  public void resize(int width, int height) {
    if (renderer != null) renderer.resize(width, height);
    if (cameraRig != null && width > 0 && height > 0) cameraRig.resize(width, height);
    if (camera != null) {
      camera.viewportWidth = Math.max(1, width);
      camera.viewportHeight = Math.max(1, height);
      camera.update();
    }
    if (stage != null) {
      stage.getViewport().update(width, height, false);
      if (hud != null) {
        hud.setBounds(
            16, Math.max(16, height - 224), Math.min(640, Math.max(340, width - 32)), 208);
        hud.invalidateHierarchy();
      }
    }
  }

  @Override
  public void pause() {
    focused = false;
    freeFlyToggleHeld = false;
    cameraModeToggleHeld = false;
    if (fpsController != null) {
      fpsController.setMoveInput(0, 0);
      fpsController.setSprint(false);
      fpsController.setJump(false);
    }
    discardFirstMouseDelta = true;
    if (Gdx.input != null) Gdx.input.setCursorCatched(false);
  }

  @Override
  public void resume() {
    focused = true;
    freeFlyToggleHeld = false;
    cameraModeToggleHeld = false;
    discardFirstMouseDelta = true;
  }

  @Override
  public void dispose() {
    if (disposed) return;
    disposed = true;
    Throwable failure = null;
    try {
      if (chunks != null) chunks.close();
    } catch (RuntimeException | Error e) {
      failure = e;
    }
    try {
      if (avatar != null) avatar.close();
    } catch (RuntimeException | Error e) {
      failure = combine(failure, e);
    }
    try {
      if (game != null) game.close();
    } catch (RuntimeException | Error e) {
      failure = combine(failure, e);
    }
    try {
      if (renderer != null) renderer.dispose();
    } catch (RuntimeException | Error e) {
      failure = combine(failure, e);
    }
    try {
      if (outline != null) outline.dispose();
    } catch (RuntimeException | Error e) {
      failure = combine(failure, e);
    }
    try {
      if (stage != null) stage.dispose();
    } catch (RuntimeException | Error e) {
      failure = combine(failure, e);
    }
    try {
      if (hud != null) hud.close();
    } catch (RuntimeException | Error e) {
      failure = combine(failure, e);
    }
    try {
      if (theme != null) theme.dispose();
    } catch (RuntimeException | Error e) {
      failure = combine(failure, e);
    }
    try {
      if (atlas != null) atlas.dispose();
    } catch (RuntimeException | Error e) {
      failure = combine(failure, e);
    }
    if (failure instanceof RuntimeException runtime) throw runtime;
    if (failure instanceof Error error) throw error;
  }

  private static Throwable combine(Throwable previous, Throwable next) {
    if (previous == null) return next;
    previous.addSuppressed(next);
    return previous;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  Vector3 cameraPosition() {
    return camera.position.cpy();
  }

  Vector3 cameraDirection() {
    return camera.direction.cpy();
  }

  float cameraFov() {
    return camera.fieldOfView;
  }

  float cameraNear() {
    return camera.near;
  }

  void restoreGalleryCamera() {
    for (int radius = 0; radius <= 20; radius++) {
      for (int z = -16; z < 32; z++) {
        for (int x = -16; x < 32; x++) {
          if (Math.abs(x - 8) + Math.abs(z - 8) != radius) continue;
          for (int y = 31; y >= 0; y--) {
            int block = world.getBlock(x, y, z);
            if (block != terrain.grassId() && block != terrain.sandId()) continue;
            if (world.getBlock(x, y + 1, z) != 0) continue;
            Vector3 target = new Vector3(x + .5f, y + .5f, z + .5f);
            Vector3 position = target.cpy().add(3.5f, 2.8f, 3.5f);
            setCameraPose(position, target.sub(position));
            if (currentHit().isPresent()) return;
          }
        }
      }
    }
    throw new IllegalStateException("Could not find a visible gallery target within reach");
  }

  BlockPos currentTargetBlock() {
    return currentHit().map(VoxelHit::block).orElse(null);
  }

  boolean hudFitsViewport() {
    if (hud == null || hud.getY() < 0 || hud.getY() + hud.getHeight() > Gdx.graphics.getHeight())
      return false;
    hud.validate();
    for (Actor child : hud.getChildren())
      if (child.getY() < 0 || child.getY() + child.getHeight() > hud.getHeight()) return false;
    return true;
  }

  void writeGalleryScreenshot() {
    writeScreenshot("artifacts/voxels/gallery.png");
  }

  int writeAvatarScreenshot(String name) {
    Pixmap pixels =
        Pixmap.createFromFrameBuffer(
            0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    try {
      var image = Gdx.files.local("docs/artifacts/avatar/voxel-" + name + ".png");
      image.parent().mkdirs();
      PixmapIO.writePNG(image, pixels, -1, true);
      int warmPixels = 0;
      int toolPixels = 0;
      int wallPixels = 0;
      int x0 = pixels.getWidth() / 12, x1 = pixels.getWidth() - x0;
      int y1 = (int) (pixels.getHeight() * .72f);
      for (int y = 0; y < y1; y++) {
        for (int x = x0; x < x1; x++) {
          int rgba = pixels.getPixel(x, y);
          int r = rgba >>> 24 & 255, g = rgba >>> 16 & 255, b = rgba >>> 8 & 255;
          if (r > 130 && g > 65 && r > g * 1.1 && g > b * 1.04) warmPixels++;
          if (x > pixels.getWidth() * 3 / 5
              && y > pixels.getHeight() / 20
              && r > 70
              && g > 30
              && b < 80
              && r > g * 1.2
              && g > b * 1.15) toolPixels++;
          if (x > pixels.getWidth() / 4
              && x < pixels.getWidth() * 3 / 4
              && y > pixels.getHeight() / 4
              && y < pixels.getHeight() * 3 / 4
              && Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 28
              && Math.max(r, Math.max(g, b)) < 180) wallPixels++;
        }
      }
      lastAvatarToolPixels = toolPixels;
      lastViewmodelWallPixels = wallPixels;
      return warmPixels;
    } finally {
      pixels.dispose();
    }
  }

  int lastAvatarToolPixels() {
    return lastAvatarToolPixels;
  }

  int lastViewmodelWallPixels() {
    return lastViewmodelWallPixels;
  }

  void renderRemoteAvatarForSmoke() {
    renderRemoteAvatarForSmoke(null);
  }

  void renderRemoteAvatarForSmoke(AvatarView localOverride) {
    Vector3 eye = fpsController.getEyePosition(new Vector3());
    PerspectiveCamera remote = remoteCameraComponent.camera;
    remote.viewportWidth = Gdx.graphics.getBackBufferWidth();
    remote.viewportHeight = Gdx.graphics.getBackBufferHeight();
    remote.position.set(eye).add(4, 2, 4);
    remote.direction.set(eye).sub(remote.position).nor();
    remote.up.set(Vector3.Y);
    remote.update();
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    Gdx.gl.glClearColor(.29f, .55f, .78f, 1f);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    chunks.setCullingEnabled(false);
    try {
      renderer.renderWorld(game, remoteCameraComponent, localOverride);
      renderer.render2D(game);
      drawCrosshair();
      stage.act(0);
      stage.draw();
    } finally {
      chunks.setCullingEnabled(true);
    }
  }

  void renderRemotePresetForSmoke(AvatarPreset preset) {
    remoteProofView.preset(preset);
    renderRemoteAvatarForSmoke(remoteProofView);
  }

  void writeClearBoomCullingDiagnostic() {
    chunks.setCullingEnabled(false);
    try {
      Gdx.gl.glViewport(
          0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
      Gdx.gl.glClearColor(.29f, .55f, .78f, 1f);
      Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
      Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
      renderer.renderWorld(game, cameraComponent, worldFullView);
      writeScreenshot("docs/artifacts/avatar/voxel-tpp-clear-culling-off.png");
    } finally {
      chunks.setCullingEnabled(true);
    }
  }

  private void writeScreenshot(String path) {
    Pixmap pixels =
        Pixmap.createFromFrameBuffer(
            0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    try {
      var image = Gdx.files.local(path);
      image.parent().mkdirs();
      PixmapIO.writePNG(image, pixels, -1, true);
    } finally {
      pixels.dispose();
    }
  }

  void finishSmoke(String proofJson) {
    dispose();
    int glError = Gdx.gl.glGetError();
    require(glError == GL20.GL_NO_ERROR, "OpenGL error after voxel scene cleanup: " + glError);
    Gdx.files.local("artifacts/voxels/proof.json").writeString(proofJson, false, "UTF-8");
    Gdx.files
        .local("docs/artifacts/avatar/voxel-proof.json")
        .writeString(proofJson, false, "UTF-8");
    Gdx.app.log(
        "Voxels", "SCENE PASS: FPS movement, camera controls, seam edits, resize and cleanup");
    Gdx.app.exit();
  }

  VoxelTerrain terrain() {
    return terrain;
  }

  VoxelWorld world() {
    return world;
  }

  int selectedBlockId() {
    return selectedBlockId;
  }

  int dirtyChunkCount() {
    return world.dirtyChunkCount();
  }

  int lastRebuilds() {
    return chunks.lastRebuilds();
  }

  int totalRebuilds() {
    return chunks.totalRebuilds();
  }

  int rebuildCount(ChunkPos position) {
    return chunks.rebuildCount(position);
  }

  int visibleChunkCount() {
    return chunks.visibleChunkCount();
  }

  int faceCount() {
    return chunks.faceCount();
  }

  int frameNumber() {
    return frames;
  }

  boolean isFreeFlyMode() {
    return freeFlyMode;
  }

  AvatarCameraMode avatarCameraMode() {
    return cameraRig.mode();
  }

  float cameraClearanceFraction() {
    return cameraRig.clearanceFraction();
  }

  float boomDistance() {
    return cameraRig.boomDistance();
  }

  Vector3 playerEye() {
    return fpsController.getEyePosition(new Vector3());
  }

  double locomotionTime() {
    return avatar.locomotionTime();
  }

  double locomotionParameter() {
    return avatar.locomotionParameter();
  }

  double useTime() {
    return avatar.useTime();
  }

  boolean clothingVisible() {
    return clothingVisible;
  }

  boolean toolVisible() {
    return toolVisible;
  }

  boolean rightHandSocketMatchesTool() {
    return avatar.rightHandSocketMatchesTool();
  }

  com.badlogic.gdx.math.Matrix4 rightArmPoseForSmoke() {
    return avatar.rightArmPose();
  }

  com.badlogic.gdx.math.Matrix4 leftLegPoseForSmoke() {
    return avatar.leftLegPose();
  }

  void setAvatarJumpForSmoke(boolean airborne) {
    avatar.updateJump(airborne);
  }

  double jumpTimeForSmoke() {
    return avatar.jumpTime();
  }

  double jumpWeightForSmoke() {
    return avatar.jumpWeight();
  }

  boolean jumpPlaybackActiveForSmoke() {
    return avatar.jumpPlaybackActive();
  }

  int avatarMarkerCountForSmoke() {
    return avatar.markerCount();
  }

  void beginAvatarUseForSmoke() {
    avatar.beginUse();
  }

  void setAvatarMoveForSmoke(float forward, float right) {
    fpsController.setMoveInput(forward, right);
  }

  void resetPlayerForSmoke() {
    cameraTransform.position.set(spawnFeet);
    previousPlayerFeet.set(spawnFeet);
    fpsController.setMoveInput(0, 0);
  }

  Game game() {
    return game;
  }

  boolean dispatchKeyDownForSmoke(int keycode) {
    boolean handled = inputMultiplexer.keyDown(keycode);
    inputMultiplexer.keyUp(keycode);
    return handled;
  }

  boolean dispatchRawKeyDownForSmoke(int keycode) {
    return inputMultiplexer.keyDown(keycode);
  }

  boolean dispatchRawKeyUpForSmoke(int keycode) {
    return inputMultiplexer.keyUp(keycode);
  }

  void applyFpsMouseLookForSmoke(float deltaX, float deltaY) {
    applyFpsMouseLook(deltaX, deltaY);
  }

  void syncFpsCameraForSmoke() {
    syncCameraFromFpsController();
  }

  VoxelFpsController fpsController() {
    return fpsController;
  }

  Vector3 playerFeet() {
    return cameraTransform.position;
  }

  Vector3 spawnFeet() {
    return spawnFeet.cpy();
  }

  void restoreFpsSpawnPose() {
    if (freeFlyMode) setFreeFlyMode(false);
    fpsController.setMoveInput(0, 0);
    fpsController.setSprint(false);
    fpsController.setJump(false);
    cameraTransform.position.set(spawnFeet);
  }
}
