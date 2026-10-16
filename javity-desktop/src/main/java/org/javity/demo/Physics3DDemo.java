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
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.util.List;
import org.javity.avatar.AvatarPreset;
import org.javity.avatar.AvatarView;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.physics3d.BoxCollider3D;
import org.javity.physics3d.BulletCharacterController3D;
import org.javity.physics3d.CompoundCollider3D;
import org.javity.physics3d.Contact3D;
import org.javity.physics3d.Physics3D;
import org.javity.physics3d.Physics3DCameraObstructionQuery;
import org.javity.physics3d.Physics3DDebugView;
import org.javity.physics3d.RigidBody3D;
import org.javity.physics3d.SphereCollider3D;
import org.javity.render.AvatarCameraMode;
import org.javity.render.Camera3D;
import org.javity.render.CameraRig3D;
import org.javity.render.GdxRenderer;
import org.javity.render.ModelRenderer;
import org.javity.ui.flex.Flex;
import org.javity.ui.flex.UiTheme;
import org.javity.voxel.AtlasLayout;
import org.javity.voxel.BlockDefinition;
import org.javity.voxel.BlockRegistry;
import org.javity.voxel.ChunkPos;
import org.javity.voxel.VoxelRaycaster;
import org.javity.voxel.VoxelWorld;
import org.javity.voxel.gpu.VoxelChunkRenderer;
import org.javity.voxel.physics3d.VoxelCollisionAdapter;

/** Explicit opt-in Bullet, voxel, and animated capsule integration scene. */
final class Physics3DDemo extends ApplicationAdapter {
  private static final int[] PATCH_COLORS = {1, 2, 3, 4, 5};
  private final boolean smoke;
  private Game game;
  private Physics3D physics;
  private VoxelWorld voxels;
  private VoxelCollisionAdapter collision;
  private VoxelChunkRenderer chunks;
  private Texture atlas;
  private GeneratedVoxelAvatar avatar;
  private AvatarView worldHiddenView, worldFullView, viewmodelView;
  private BulletCharacterController3D character;
  private Transform3D feetTransform;
  private GdxRenderer renderer;
  private Camera3D cameraComponent;
  private CameraRig3D cameraRig;
  private PerspectiveCamera viewmodelCamera;
  private PerspectiveCamera sphereProofCamera;
  private ModelBatch sphereProofBatch;
  private Physics3DDebugView debugView;
  private Stage stage;
  private UiTheme theme;
  private Flex hud;
  private com.badlogic.gdx.scenes.scene2d.ui.Label status;
  private Model sphereModel, crateModel, triggerModel, platformModel, cameraWallModel;
  private GameObject sphereObject, triggerObject, platformObject;
  private GameObject cameraWall;
  private RigidBody3D platformBody;
  private boolean triggerAction;
  private boolean focused = true,
      discardMouse = true,
      debugEnabled,
      debugToggleHeld,
      cameraToggleHeld;
  private boolean editToggleHeld;
  private boolean disposed;
  private float yaw = 0f, pitch = -.15f;
  private final Vector3 firstSpherePosition = new Vector3();
  private final Vector3 sphereProofScreenFirst = new Vector3();
  private float sphereTravel;
  private float spherePixelCentroidTravel, sphereProjectedPixelTravel;
  private int spherePixelsAtFirstPose, spherePixelsAtSecondPose, spherePixelsWithRendererDisabled;
  private int triggerRedPixelsAfterEnter;
  private boolean triggerInstanceMaterialChanged, triggerSourceMaterialUnchanged;
  private final Vector3 smokeSpawn = new Vector3();
  private float forwardYawAlignment, strafeYawAlignment;
  private Vector3 tppCamera = new Vector3();
  private float tppCameraDistance;
  private boolean debugPixelsMatch;
  private boolean debugViewDetached;
  private int frames, sceneCreations, triggerEnters;
  private int debugPixelDelta;
  private int voxelEditPixelDelta;
  private float firstTppClearance = -1,
      obstructedTppClearance = -1,
      restoredTppClearance = -1,
      secondTppClearance = -1;
  private float obstructedTppDistance = -1, restoredTppDistance = -1;
  private long editRevisionBefore, editRevisionAfter;
  private boolean editCollisionCommitted;
  private boolean editScreenshotSaved;
  private boolean socketMatches;
  private boolean f5EdgeChecked, f3EdgeChecked;
  private Vector3 socketWorld = new Vector3();
  private boolean recreated;
  private int[] previousFrame;
  private SpherePixels sphereFirstPixels;

  private record SpherePixels(int count, Vector3 centroid) {}

  private GL20 originalGl;
  private Physics3DGlBufferTracker glBuffers;
  private int sceneBufferStart, debugBufferStart, debugDeleteStart;
  private int debugProgramStart, debugShaderStart, debugProgramDeleteStart, debugShaderDeleteStart;
  private boolean sceneTracked;
  private java.util.Set<Integer> debugOwnedBuffers = java.util.Set.of();
  private java.util.Set<Integer> debugOwnedPrograms = java.util.Set.of();
  private java.util.Set<Integer> debugOwnedShaders = java.util.Set.of();
  private int debugBuffersDeleted, sceneBuffersDeleted, sceneBuffersLive;
  private int debugProgramsDeleted, debugShadersDeleted;

  Physics3DDemo(boolean smoke) {
    this.smoke = smoke;
  }

  @Override
  public void create() {
    if (smoke) {
      originalGl = Gdx.gl20;
      glBuffers = new Physics3DGlBufferTracker(originalGl);
      glBuffers.install();
    }
    try {
      createScene();
    } catch (RuntimeException | Error failure) {
      try {
        closeScene();
      } catch (RuntimeException | Error cleanup) {
        failure.addSuppressed(cleanup);
      }
      throw failure;
    }
  }

  private void createScene() {
    sceneCreations++;
    if (glBuffers != null) {
      sceneBufferStart = glBuffers.generationCount();
      sceneTracked = true;
    }
    renderer = new GdxRenderer();
    game = new Game();
    physics = game.addSystem(new Physics3D(new Vector3(0, -9.81f, 0)));
    game.addSystem(new VoxelCollisionAdapter(createVoxelPatch(), physics, 16));
    voxels = patchWorld;
    collision = game.getSystem(VoxelCollisionAdapter.class);
    atlas = new Texture(Gdx.files.internal("org/javity/demo/voxels/atlas.png"), false);
    atlas.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
    chunks = new VoxelChunkRenderer(game, voxels, atlas, new AtlasLayout(80, 40, 16, 2, 4, 2));
    GameObject cameraObject = game.createObject("Bullet demo camera");
    cameraObject.addComponent(new Transform3D());
    cameraComponent = cameraObject.addComponent(new Camera3D(67));
    cameraComponent.camera.near = .1f;
    cameraComponent.camera.far = 180;
    viewmodelCamera = new PerspectiveCamera(72, 1, 1);
    cameraRig =
        new CameraRig3D(
            cameraComponent, viewmodelCamera, new Physics3DCameraObstructionQuery(physics));
    cameraRig.setWorldProjection(67, .1f, 180);
    cameraRig.setViewmodelProjection(72, .03f);
    cameraRig.setBoomDistance(4.5f);
    yaw = -1.2f;
    pitch = -.36f;

    avatar = GeneratedVoxelAvatar.create(game, 1f);
    feetTransform = avatar.object.getComponent(Transform3D.class);
    worldHiddenView = avatar.binding.view().parts(java.util.Set.of());
    worldFullView =
        avatar
            .binding
            .view()
            .preset(AvatarPreset.FULL_BODY)
            .slotVisible("shirt", true)
            .slotVisible("tool", true);
    viewmodelView =
        avatar
            .binding
            .view()
            .preset(AvatarPreset.ARMS_ONLY)
            .slotVisible("shirt", false)
            .slotVisible("tool", true);
    character =
        avatar.object.addComponent(
            new BulletCharacterController3D(.34f, 1.8f, .35f)
                .setSpeed(5.0f)
                .setSprintMultiplier(1.65f)
                .setJumpSpeed(6.5f)
                .setReadinessPolicy(collision.characterReadiness()));
    feetTransform.position.set(.5f, 2.1f, .5f);
    character.setLook(yaw, pitch);

    createPhysicsShowcase();
    game.start();
    stage = new Stage(new ScreenViewport());
    theme = new UiTheme();
    hud = new Flex(theme, "flex-col p-4 gap-1 bg-slate-900 rounded-lg border-2 border-slate-700");
    hud.add(theme.label("JAVITY / BULLET 3D", "text-xl text-cyan-400"), "h-8");
    status = theme.label("Loading collision chunks…", "text-sm text-emerald-500");
    hud.add(status, "h-6");
    hud.add(
        theme.label(
            "WASD move  Space jump  Shift sprint  F5 FPS/TPP\n"
                + "F3 debug draw  E use  LMB/RMB edit  Esc cursor",
            "text-sm text-slate-400",
            true),
        "h-10");
    stage.addActor(hud);
    InputMultiplexer mux =
        new InputMultiplexer(
            stage,
            new InputAdapter() {
              @Override
              public boolean keyDown(int key) {
                if (key == Input.Keys.F5) {
                  if (!cameraToggleHeld) {
                    cameraToggleHeld = true;
                    cameraRig.setMode(
                        cameraRig.mode() == AvatarCameraMode.FIRST_PERSON
                            ? AvatarCameraMode.THIRD_PERSON
                            : AvatarCameraMode.FIRST_PERSON);
                  }
                  return true;
                }
                if (key == Input.Keys.F3) {
                  if (!debugToggleHeld) {
                    debugToggleHeld = true;
                    setDebug(!debugEnabled);
                  }
                  return true;
                }
                if (key == Input.Keys.E) {
                  avatar.beginUse();
                  return true;
                }
                if (key == Input.Keys.ESCAPE) {
                  Gdx.input.setCursorCatched(!Gdx.input.isCursorCatched());
                  discardMouse = true;
                  return true;
                }
                return false;
              }

              @Override
              public boolean keyUp(int key) {
                if (key == Input.Keys.F5) {
                  cameraToggleHeld = false;
                  return true;
                }
                if (key == Input.Keys.F3) {
                  debugToggleHeld = false;
                  return true;
                }
                return false;
              }

              @Override
              public boolean touchDown(int x, int y, int pointer, int button) {
                if (!Gdx.input.isCursorCatched()) {
                  Gdx.input.setCursorCatched(true);
                  discardMouse = true;
                  return true;
                }
                editRay(button == Input.Buttons.LEFT ? 0 : 1);
                return true;
              }
            });
    Gdx.input.setInputProcessor(mux);
    resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
  }

  private VoxelWorld patchWorld;

  private VoxelWorld createVoxelPatch() {
    BlockRegistry registry = new BlockRegistry();
    int grass = registry.register(new BlockDefinition("grass", 1, 1, 2, 0, 1, 1));
    int dirt = registry.register(new BlockDefinition("dirt", 2));
    int stone = registry.register(new BlockDefinition("stone", 3));
    int wood = registry.register(new BlockDefinition("wood", 4));
    registry.register(new BlockDefinition("sand", 6));
    patchWorld = new VoxelWorld(registry, 54);
    for (int cy = -1; cy <= 3; cy++)
      for (int cz = -1; cz <= 1; cz++)
        for (int cx = -1; cx <= 1; cx++) patchWorld.loadChunk(new ChunkPos(cx, cy, cz));
    for (int z = -8; z <= 8; z++)
      for (int x = -8; x <= 8; x++) {
        patchWorld.setBlock(x, 0, z, (x + z) % 5 == 0 ? stone : grass);
        patchWorld.setBlock(x, -1, z, dirt);
      }
    return patchWorld;
  }

  private void createPhysicsShowcase() {
    ModelBuilder builder = new ModelBuilder();
    sphereModel =
        builder.createSphere(
            1.15f,
            1.15f,
            1.15f,
            24,
            16,
            new Material(ColorAttribute.createDiffuse(new Color(.92f, .43f, .12f, 1))),
            com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);
    if (smoke) {
      sphereProofBatch = new ModelBatch();
      sphereProofCamera =
          new PerspectiveCamera(
              60, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
      sphereProofCamera.position.set(3.2f, 5.5f, 9f);
      sphereProofCamera.lookAt(3.2f, 5.5f, 0);
      sphereProofCamera.up.set(Vector3.Y);
      sphereProofCamera.near = .1f;
      sphereProofCamera.far = 100f;
      sphereProofCamera.update();
    }
    triggerModel = createTriggerCage(builder);
    platformModel =
        builder.createBox(
            4f,
            .45f,
            3f,
            new Material(ColorAttribute.createDiffuse(new Color(.27f, .43f, .88f, 1))),
            com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);
    sphereObject = modelObject("falling spinning sphere", sphereModel, new Vector3(3.2f, 7, 0));
    sphereObject.addComponent(
        new RigidBody3D(RigidBody3D.Type.DYNAMIC)
            .setMass(2)
            .setAngularVelocity(new Vector3(2.2f, 0, 1.4f)));
    sphereObject.addComponent(new SphereCollider3D(.575f));
    GameObject compound = game.createObject("compound crate");
    compound.addComponent(new Transform3D()).position.set(6.2f, 5, -2.5f);
    crateModel =
        builder.createBox(
            1.3f,
            1.8f,
            .8f,
            new Material(ColorAttribute.createDiffuse(new Color(.68f, .39f, .16f, 1))),
            com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);
    compound.addComponent(new ModelRenderer(crateModel));
    compound.addComponent(new RigidBody3D(RigidBody3D.Type.DYNAMIC).setMass(4));
    compound.addComponent(
        new CompoundCollider3D(
            List.of(
                new CompoundCollider3D.Box(
                    new Vector3(1.2f, .55f, .7f),
                    new Vector3(0, 0, 0),
                    new com.badlogic.gdx.math.Quaternion()),
                new CompoundCollider3D.Box(
                    new Vector3(.5f, 1.1f, .5f),
                    new Vector3(0, .7f, 0),
                    new com.badlogic.gdx.math.Quaternion()))));
    triggerObject = modelObject("visible trigger", triggerModel, new Vector3(3.2f, 1.3f, 0));
    var trigger = triggerObject.addComponent(new BoxCollider3D(1.4f, 1.4f, 1.4f).setTrigger(true));
    trigger.onContact(
        event -> {
          if (event.phase() == Contact3D.Phase.ENTER) {
            triggerAction = true;
            triggerEnters++;
            ModelRenderer renderedTrigger = triggerObject.getComponent(ModelRenderer.class);
            ((ColorAttribute)
                    renderedTrigger.instance.materials.first().get(ColorAttribute.Diffuse))
                .color.set(.94f, .17f, .22f, 1f);
          }
        });
    platformObject = modelObject("kinematic platform", platformModel, new Vector3(6.2f, 2, -2.5f));
    platformBody = platformObject.addComponent(new RigidBody3D(RigidBody3D.Type.KINEMATIC));
    platformObject.addComponent(new BoxCollider3D(4, .45f, 3));
  }

  private void createCameraObstruction() {
    cameraWallModel =
        new ModelBuilder()
            .createBox(
                3.2f,
                4f,
                .55f,
                new Material(ColorAttribute.createDiffuse(new Color(.56f, .18f, .72f, 1))),
                com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                    | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);
    cameraWall =
        modelObject(
            "temporary camera obstruction", cameraWallModel, new Vector3(-1.55f, 3.65f, 1.3f));
    cameraWall.getComponent(Transform3D.class).rotation.setFromAxis(Vector3.Y, 68);
    cameraWall.addComponent(new RigidBody3D(RigidBody3D.Type.STATIC));
    cameraWall.addComponent(new BoxCollider3D(3.2f, 4f, .55f));
  }

  private GameObject modelObject(String name, Model model, Vector3 position) {
    GameObject object = game.createObject(name);
    object.addComponent(new Transform3D()).position.set(position);
    object.addComponent(new ModelRenderer(model));
    return object;
  }

  private static Model createTriggerCage(ModelBuilder builder) {
    builder.begin();
    var lines =
        builder.part(
            "trigger-cage",
            GL20.GL_LINES,
            com.badlogic.gdx.graphics.VertexAttributes.Usage.Position,
            new Material(ColorAttribute.createDiffuse(new Color(.2f, .9f, .62f, 1f))));
    float h = .7f;
    for (int x = -1; x <= 1; x += 2)
      for (int y = -1; y <= 1; y += 2)
        for (int z = -1; z <= 1; z += 2) {
          float px = x * h, py = y * h, pz = z * h;
          lines.line(px, py, pz, -x * h, py, pz);
          if (y < 0) lines.line(px, py, pz, px, -y * h, pz);
          if (z < 0) lines.line(px, py, pz, px, py, -z * h);
        }
    return builder.end();
  }

  private void setDebug(boolean enabled) {
    if (enabled == debugEnabled) return;
    if (enabled) debugView = physics.createDebugView();
    else {
      debugView.close();
      debugView = null;
      debugViewDetached = !physics.isDebugViewAttached();
    }
    debugEnabled = enabled;
  }

  private void editRay(int mode) {
    var hit =
        VoxelRaycaster.cast(
            voxels, cameraComponent.camera.position, cameraComponent.camera.direction, 8, 512);
    if (hit.isEmpty()) return;
    var block = hit.get().block();
    if (mode == 0) voxels.setBlock(block.x(), block.y(), block.z(), 0);
    else if (hit.get().placementCell() != null) {
      var cell = hit.get().placementCell();
      voxels.setBlock(
          cell.x(),
          cell.y(),
          cell.z(),
          PATCH_COLORS[Math.floorMod(cell.x() + cell.z(), PATCH_COLORS.length)]);
    }
  }

  @Override
  public void render() {
    float dt = smoke ? 1f / 60f : Math.min(Gdx.graphics.getDeltaTime(), .1f);
    if (!smoke) readControls();
    boolean active = !game.isWorldFrozen() && dt > 0f;
    if (active && platformBody != null) {
      float y = 2f + .7f * MathUtils.sin(frames * .012f);
      platformBody.moveKinematic(
          new Vector3(6.2f, y, -2.5f), new com.badlogic.gdx.math.Quaternion());
    }
    game.step(dt);
    boolean worldAdvanced = !game.isWorldFrozen() && game.deltaTime > 0f;
    Vector3 feet =
        character.isBoundToPhysics() ? character.feet() : new Vector3(feetTransform.position);
    Vector3 look = lookDirection();
    if (worldAdvanced && character.isBoundToPhysics()) {
      Vector3 horizontalVelocity = character.velocity();
      horizontalVelocity.y = 0;
      avatar.setWalking(MathUtils.clamp(horizontalVelocity.len() / 7f, 0, 1));
      avatar.updateUse();
      avatar.updateJump(!character.isGrounded());
    }
    cameraRig.update(feet.cpy().add(0, 1.55f, 0), look, avatar.object);
    chunks.update(cameraComponent.camera, 2);
    renderSceneComposite(dt);
    frames++;
    if (smoke) smokeProofFrame();
  }

  private void renderSceneComposite(float stageDelta) {
    ScreenUtils.clear(.24f, .39f, .52f, 1, true);
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    worldFullView.setBaseTransform(
        new com.badlogic.gdx.math.Matrix4()
            .setToRotation(Vector3.Y, yaw * MathUtils.radiansToDegrees));
    renderer.renderWorld(
        game,
        cameraComponent,
        cameraRig.mode() == AvatarCameraMode.FIRST_PERSON ? worldHiddenView : worldFullView);
    if (debugEnabled) debugView.render(cameraComponent.camera);
    if (cameraRig.mode() == AvatarCameraMode.FIRST_PERSON) {
      com.badlogic.gdx.math.Matrix4 viewBase =
          new com.badlogic.gdx.math.Matrix4()
              .translate(.05f, -.8f, -1.35f)
              .rotate(Vector3.X, 15)
              .scale(.65f, .65f, .65f);
      viewmodelView.setBaseTransform(viewBase);
      renderer.renderViewmodel(cameraRig.viewmodelCamera(), viewmodelView);
    }
    renderer.render2D(game);
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    updateHud();
    stage.act(stageDelta);
    stage.draw();
  }

  private Vector3 lookDirection() {
    float cp = MathUtils.cos(pitch);
    return new Vector3(-cp * MathUtils.sin(yaw), MathUtils.sin(pitch), -cp * MathUtils.cos(yaw))
        .nor();
  }

  private void readControls() {
    if (!focused) {
      character.clearInput();
      return;
    }
    float forward = key(Input.Keys.W, Input.Keys.S), right = key(Input.Keys.D, Input.Keys.A);
    character
        .setMoveAxes(right, forward)
        .setSprint(
            Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT));
    character.setJumpHeld(Gdx.input.isKeyPressed(Input.Keys.SPACE));
    if (Gdx.input.isCursorCatched()) {
      if (discardMouse) discardMouse = false;
      else {
        yaw -= Gdx.input.getDeltaX() * .0025f;
        pitch = MathUtils.clamp(pitch - Gdx.input.getDeltaY() * .0025f, -1.48f, 1.48f);
      }
      character.setLook(yaw, pitch);
    }
  }

  private float key(int positive, int negative) {
    return (Gdx.input.isKeyPressed(positive) ? 1 : 0) - (Gdx.input.isKeyPressed(negative) ? 1 : 0);
  }

  private void updateHud() {
    status.setText(
        "Bullet capsule "
            + (character.isBoundToPhysics()
                ? (character.isGrounded() ? "grounded" : "airborne")
                : "waiting for loaded collision")
            + " | "
            + cameraRig.mode()
            + " clear="
            + String.format(java.util.Locale.ROOT, "%.2f", cameraRig.clearanceFraction())
            + " | trigger "
            + (triggerAction ? "ACTION" : "armed")
            + " | voxel chunks "
            + collision.getReadyChunkCount()
            + " | debug "
            + (debugEnabled ? "on" : "off"));
  }

  private void smokeProofFrame() {
    if (frames == 5) {
      Transform3D sphereTransform = sphereObject.getComponent(Transform3D.class);
      firstSpherePosition.set(sphereTransform.position);
      sphereProofScreenFirst.set(projectSphereProof(sphereTransform.position));
      int[] firstPose = renderSphereProof(sphereTransform.worldMatrix());
      sphereFirstPixels = measureSpherePixels(firstPose, sphereProofScreenFirst);
      spherePixelsAtFirstPose = sphereFirstPixels.count();
      saveScreenshot("physics3d-sphere-proof-start.png");
      ModelRenderer sphereRenderer = sphereObject.getComponent(ModelRenderer.class);
      sphereRenderer.setEnabled(false);
      int[] missingRenderer = renderSphereProof(sphereTransform.worldMatrix());
      spherePixelsWithRendererDisabled =
          measureSpherePixels(missingRenderer, sphereProofScreenFirst).count();
      sphereRenderer.setEnabled(true);
      smokeSpawn.set(character.feet());
      yaw = pitch = 0;
      character.setLook(yaw, pitch).setMoveAxes(0, 1);
    }
    if (frames == 18) {
      Vector3 displacement = character.feet().sub(smokeSpawn);
      forwardYawAlignment = displacement.nor().dot(lookDirection());
      character.setMoveAxes(0, 0).warp(smokeSpawn);
    }
    if (frames == 19) {
      yaw = .73f;
      character.setLook(yaw, 0).setMoveAxes(1, 0);
    }
    if (frames == 31) {
      Vector3 displacement = character.feet().sub(smokeSpawn);
      Vector3 expectedRight = lookDirection().crs(Vector3.Y).nor();
      strafeYawAlignment = displacement.nor().dot(expectedRight);
      character.setMoveAxes(0, 0).warp(smokeSpawn);
    }
    if (frames == 32) {
      yaw = -1.2f;
      pitch = -.36f;
      character.setLook(yaw, pitch);
    }
    if (frames == 45) {
      Transform3D sphereTransform = sphereObject.getComponent(Transform3D.class);
      sphereTravel = sphereTransform.position.dst(firstSpherePosition);
      Vector3 sphereProofScreenNow = projectSphereProof(sphereTransform.position);
      int[] secondPose = renderSphereProof(sphereTransform.worldMatrix());
      SpherePixels secondPixels = measureSpherePixels(secondPose, sphereProofScreenNow);
      spherePixelsAtSecondPose = secondPixels.count();
      sphereProjectedPixelTravel = sphereProofScreenFirst.dst(sphereProofScreenNow);
      spherePixelCentroidTravel = sphereFirstPixels.centroid().dst(secondPixels.centroid());
      saveScreenshot("physics3d-sphere-proof-end.png");
      firstTppClearance = cameraRig.clearanceFraction();
      createCameraObstruction();
      f5EdgeChecked = toggleKeyEdge(Input.Keys.F5, AvatarCameraMode.THIRD_PERSON);
    }
    if (frames == 49) {
      obstructedTppClearance = cameraRig.clearanceFraction();
      obstructedTppDistance =
          cameraComponent.camera.position.dst(character.feet().cpy().add(0, 1.55f, 0));
      saveScreenshot("physics3d-third-person-obstructed.png");
      cameraWall.destroy();
    }
    if (frames == 55) {
      restoredTppClearance = cameraRig.clearanceFraction();
      restoredTppDistance =
          cameraComponent.camera.position.dst(character.feet().cpy().add(0, 1.55f, 0));
    }
    if (frames == 94) {
      secondTppClearance = cameraRig.clearanceFraction();
      tppCamera.set(cameraComponent.camera.position);
      tppCameraDistance = tppCamera.dst(character.feet().add(0, 1.55f, 0));
      saveScreenshot("physics3d-third-person.png");
      f5EdgeChecked &= toggleKeyEdge(Input.Keys.F5, AvatarCameraMode.FIRST_PERSON);
      debugBufferStart = glBuffers == null ? 0 : glBuffers.generationCount();
      debugProgramStart = glBuffers == null ? 0 : glBuffers.programGenerationCount();
      debugShaderStart = glBuffers == null ? 0 : glBuffers.shaderGenerationCount();
      f3EdgeChecked = toggleKeyEdge(Input.Keys.F3, null);
    }
    if (frames == 100 && !recreated) {
      debugPixelDelta = debugPassComparison();
      if (glBuffers != null) {
        debugOwnedBuffers = glBuffers.liveGenerated(debugBufferStart, glBuffers.generationCount());
        require(
            !debugOwnedBuffers.isEmpty(), "Bullet debug drawer created no bound live GL buffers");
        debugOwnedPrograms =
            glBuffers.livePrograms(debugProgramStart, glBuffers.programGenerationCount());
        debugOwnedShaders =
            glBuffers.createdShaders(debugShaderStart, glBuffers.shaderGenerationCount());
        require(!debugOwnedPrograms.isEmpty(), "Bullet debug drawer created no live GL program");
        require(!debugOwnedShaders.isEmpty(), "Bullet debug drawer created no GL shader objects");
      }
    }
    if (frames == 106) {
      debugDeleteStart = glBuffers == null ? 0 : glBuffers.deletionCount();
      debugProgramDeleteStart = glBuffers == null ? 0 : glBuffers.programDeletionCount();
      debugShaderDeleteStart = glBuffers == null ? 0 : glBuffers.shaderDeletionCount();
      f3EdgeChecked &= toggleKeyEdge(Input.Keys.F3, null);
      if (glBuffers != null) {
        glBuffers.requireDeleted(
            debugOwnedBuffers, debugDeleteStart, glBuffers.deletionCount(), "debug view close");
        debugBuffersDeleted = debugOwnedBuffers.size();
        glBuffers.requireProgramsDeleted(
            debugOwnedPrograms,
            debugProgramDeleteStart,
            glBuffers.programDeletionCount(),
            "debug view close");
        glBuffers.requireShadersDeleted(
            debugOwnedShaders,
            debugShaderDeleteStart,
            glBuffers.shaderDeletionCount(),
            "debug view close");
        debugProgramsDeleted = debugOwnedPrograms.size();
        debugShadersDeleted = debugOwnedShaders.size();
      }
      triggerInstanceMaterialChanged = triggerMaterialChanged();
      triggerSourceMaterialUnchanged = triggerSourceIsGreen();
      renderSceneComposite(0f);
      triggerRedPixelsAfterEnter = countTriggerRedPixels(capture());
      saveScreenshot("physics3d-physics.png");
    }
    if (frames == 112) {
      editRevisionBefore = voxels.contentRevision(new ChunkPos(0, 0, 0));
      previousFrame = capture();
      voxels.setBlock(3, 1, 0, 3);
      editRevisionAfter = voxels.contentRevision(new ChunkPos(0, 0, 0));
    }
    if (frames >= 118 && editRevisionAfter > editRevisionBefore) {
      editCollisionCommitted = collision.isChunkCollisionReady(new ChunkPos(0, 0, 0));
      if (editCollisionCommitted && !editScreenshotSaved) {
        int[] before = previousFrame;
        int[] after = capture();
        voxelEditPixelDelta = differenceAtWorldRoi(before, after, new Vector3(3.5f, 1.5f, .5f), 32);
        saveScreenshot("physics3d-voxel-edit.png");
        previousFrame = after;
        editScreenshotSaved = true;
      }
    }
    if (frames == 175 && !recreated) {
      closeScene();
      recreated = true;
      try {
        createScene();
      } catch (RuntimeException | Error failure) {
        try {
          closeScene();
        } catch (RuntimeException | Error cleanup) {
          failure.addSuppressed(cleanup);
        }
        throw failure;
      }
      frames = 0;
    }
    if (recreated && frames == 100) {
      require(
          triggerEnters > 0 && triggerInstanceMaterialChanged && triggerSourceMaterialUnchanged,
          "Bullet sphere did not activate the visible trigger action");
      require(
          spherePixelsAtFirstPose > 100 && spherePixelsAtSecondPose > 100,
          "sphere-specific orange pixels are absent at one of the native poses");
      require(
          spherePixelsWithRendererDisabled == 0,
          "missing-renderer negative control unexpectedly produced sphere pixels");
      require(
          sphereProjectedPixelTravel > 20
              && spherePixelCentroidTravel > sphereProjectedPixelTravel * .5f
              && spherePixelCentroidTravel < sphereProjectedPixelTravel * 1.5f,
          "sphere pixel centroid did not follow its projected native movement");
      require(triggerRedPixelsAfterEnter > 0, "post-ENTER trigger cage produced no red pixels");
      require(debugPixelDelta > 10, "Bullet debug overlay did not change the framebuffer");
      require(
          sphereTravel > .5f, "native sphere did not move far enough for the rendered-body proof");
      require(
          forwardYawAlignment > .95f && strafeYawAlignment > .95f,
          "native W/D movement does not align with the rendered camera at two yaws");
      require(
          secondTppClearance > 0 && tppCameraDistance > 3f,
          "F5 did not place the third-person camera through the obstruction query");
      require(
          obstructedTppClearance < .99f
              && obstructedTppDistance < restoredTppDistance
              && restoredTppClearance > .99f,
          "third-person camera did not contract against a native wall and recover after removal");
      require(
          f5EdgeChecked && f3EdgeChecked,
          "F5/F3 key repeat did not preserve edge-triggered toggles");
      require(
          editCollisionCommitted && editRevisionAfter > editRevisionBefore,
          "voxel edit was not committed to Bullet collision");
      require(
          voxelEditPixelDelta > 0,
          "voxel edit produced no rendered pixel change in the showcase region");
      require(sceneCreations == 2, "scene was not recreated under a live GL context");
      socketMatches = renderedSocketMatches();
      com.badlogic.gdx.math.Matrix4 socket =
          avatar.binding.socketTransform(
              "right-hand",
              new com.badlogic.gdx.math.Matrix4(feetTransform.worldMatrix())
                  .mul(worldFullView.baseTransform()),
              new com.badlogic.gdx.math.Matrix4());
      socket.getTranslation(socketWorld);
      require(socketMatches, "animated avatar hand socket diverged from its attached tool");
      saveScreenshot("physics3d-recreated.png");
      closeScene();
      if (glBuffers != null) {
        glBuffers.restore(originalGl);
        require(
            Gdx.gl.glGetError() == GL20.GL_NO_ERROR,
            "OpenGL reported an error after scene cleanup");
      }
      writeReport();
      Gdx.app.exit();
    }
  }

  private int[] capture() {
    Pixmap pix =
        Pixmap.createFromFrameBuffer(
            0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    try {
      int[] data = new int[pix.getWidth() * pix.getHeight()];
      int i = 0;
      for (int y = 0; y < pix.getHeight(); y++)
        for (int x = 0; x < pix.getWidth(); x++) data[i++] = pix.getPixel(x, y);
      return data;
    } finally {
      pix.dispose();
    }
  }

  private Vector3 projectSphereProof(Vector3 nativePosition) {
    return sphereProofCamera.project(nativePosition.cpy());
  }

  /** Isolated pass of the actual ModelRenderer at a copied native transform and a fixed camera. */
  private int[] renderSphereProof(com.badlogic.gdx.math.Matrix4 nativePose) {
    int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
    Gdx.gl.glViewport(0, 0, width, height);
    Gdx.gl.glClearColor(.24f, .39f, .52f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    ModelRenderer sphereRenderer = sphereObject.getComponent(ModelRenderer.class);
    if (sphereRenderer != null && sphereRenderer.isEnabled()) {
      sphereRenderer.instance.transform.set(nativePose);
      sphereProofBatch.begin(sphereProofCamera);
      try {
        sphereProofBatch.render(sphereRenderer.instance);
      } finally {
        sphereProofBatch.end();
      }
    }
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    Gdx.gl.glFinish();
    return capture();
  }

  private SpherePixels measureSpherePixels(int[] pixels, Vector3 projectedCenter) {
    int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
    if (pixels == null || pixels.length != width * height)
      throw new IllegalStateException("Invalid sphere proof framebuffer");
    if (projectedCenter.x < 0
        || projectedCenter.x >= width
        || projectedCenter.y < 0
        || projectedCenter.y >= height
        || projectedCenter.z < 0
        || projectedCenter.z > 1)
      throw new IllegalStateException("Native sphere projection is outside the proof camera");
    int radius = 64, count = 0;
    double sumX = 0, sumY = 0;
    for (int y = Math.max(0, (int) projectedCenter.y - radius);
        y < Math.min(height, (int) projectedCenter.y + radius);
        y++) {
      for (int x = Math.max(0, (int) projectedCenter.x - radius);
          x < Math.min(width, (int) projectedCenter.x + radius);
          x++) {
        float dx = x - projectedCenter.x, dy = y - projectedCenter.y;
        if (dx * dx + dy * dy > radius * radius) continue;
        int color = pixels[y * width + x];
        int red = color >>> 24 & 255, green = color >>> 16 & 255, blue = color >>> 8 & 255;
        if (red > 130 && red > green * 1.25f && red > blue * 1.35f) {
          count++;
          sumX += x;
          sumY += y;
        }
      }
    }
    Vector3 centroid =
        count == 0
            ? new Vector3(Float.NaN, Float.NaN, 0)
            : new Vector3((float) (sumX / count), (float) (sumY / count), 0);
    return new SpherePixels(count, centroid);
  }

  private int differenceAtWorldRoi(int[] before, int[] after, Vector3 world, int radius) {
    if (before == null || before.length != after.length)
      throw new IllegalStateException("Missing voxel captures");
    Vector3 screen = cameraComponent.camera.project(world.cpy());
    int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
    java.util.BitSet roi = new java.util.BitSet(before.length);
    markRoi(roi, width, height, (int) screen.x, (int) screen.y, radius);
    markRoi(roi, width, height, (int) screen.x, height - (int) screen.y, radius);
    int changed = 0;
    for (int i = roi.nextSetBit(0); i >= 0; i = roi.nextSetBit(i + 1))
      if (before[i] != after[i]) changed++;
    return changed;
  }

  private static void markRoi(java.util.BitSet bits, int w, int h, int cx, int cy, int r) {
    int x0 = Math.max(0, cx - r),
        x1 = Math.min(w, cx + r),
        y0 = Math.max(0, cy - r),
        y1 = Math.min(h, cy + r);
    for (int y = y0; y < y1; y++) bits.set(y * w + x0, y * w + x1);
  }

  private int debugPassComparison() {
    int[] with = renderWorldProof(true);
    saveScreenshot("physics3d-debug.png");
    int[] without = renderWorldProof(false);
    saveScreenshot("physics3d-debug-off.png");
    return differenceAll(with, without);
  }

  private boolean toggleKeyEdge(int key, AvatarCameraMode expectedMode) {
    var processor = Gdx.input.getInputProcessor();
    processor.keyDown(key);
    if (expectedMode != null && cameraRig.mode() != expectedMode) return false;
    Object debugBefore = debugView;
    processor.keyDown(key);
    boolean repeatDidNotToggle =
        expectedMode == null ? debugView == debugBefore : cameraRig.mode() == expectedMode;
    processor.keyUp(key);
    return repeatDidNotToggle;
  }

  private int[] renderWorldProof(boolean debug) {
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    Gdx.gl.glClearColor(.24f, .39f, .52f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    worldFullView.setBaseTransform(
        new com.badlogic.gdx.math.Matrix4()
            .setToRotation(Vector3.Y, yaw * MathUtils.radiansToDegrees));
    renderer.renderWorld(
        game,
        cameraComponent,
        cameraRig.mode() == AvatarCameraMode.FIRST_PERSON ? worldHiddenView : worldFullView);
    if (debug) debugView.render(cameraComponent.camera);
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    Gdx.gl.glFinish();
    return capture();
  }

  private static int differenceAll(int[] a, int[] b) {
    if (a == null || b == null || a.length != b.length)
      throw new IllegalStateException("Invalid debug comparison captures");
    int n = 0;
    for (int i = 0; i < a.length; i++) if (a[i] != b[i]) n++;
    return n;
  }

  private boolean renderedSocketMatches() {
    var hand = avatar.renderer.instance.getNode("right-arm");
    var base =
        new com.badlogic.gdx.math.Matrix4(feetTransform.worldMatrix())
            .mul(worldFullView.baseTransform());
    var socket =
        avatar.binding.socketTransform("right-hand", base, new com.badlogic.gdx.math.Matrix4());
    var grip =
        new com.badlogic.gdx.math.Matrix4(base)
            .mul(hand.globalTransform)
            .translate(-.005f, .3f - 1.27f, .25f);
    for (int i = 0; i < 16; i++) if (Math.abs(socket.val[i] - grip.val[i]) > .0001f) return false;
    return true;
  }

  private boolean triggerMaterialChanged() {
    ModelRenderer rendered = triggerObject.getComponent(ModelRenderer.class);
    Color color =
        ((ColorAttribute) rendered.instance.materials.first().get(ColorAttribute.Diffuse)).color;
    return color.r > .9f && color.g < .25f && color.b < .3f && color.a > .9f;
  }

  private boolean triggerSourceIsGreen() {
    Color color =
        ((ColorAttribute) triggerModel.materials.first().get(ColorAttribute.Diffuse)).color;
    return color.g > .8f && color.r < .3f && color.b > .5f;
  }

  private int countTriggerRedPixels(int[] pixels) {
    int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
    if (pixels == null || pixels.length != width * height)
      throw new IllegalStateException("Invalid trigger-color framebuffer");
    Vector3 center =
        cameraComponent.camera.project(
            triggerObject.getComponent(Transform3D.class).position.cpy());
    if (center.x < 0 || center.x >= width || center.y < 0 || center.y >= height)
      throw new IllegalStateException("Trigger projection is outside the game camera");
    int radius = 110, count = 0;
    for (int y = Math.max(0, (int) center.y - radius);
        y < Math.min(height, (int) center.y + radius);
        y++) {
      for (int x = Math.max(0, (int) center.x - radius);
          x < Math.min(width, (int) center.x + radius);
          x++) {
        float dx = x - center.x, dy = y - center.y;
        if (dx * dx + dy * dy > radius * radius) continue;
        int color = pixels[y * width + x];
        int red = color >>> 24 & 255, green = color >>> 16 & 255, blue = color >>> 8 & 255;
        if (red > 180 && green < 90 && blue < 110) count++;
      }
    }
    return count;
  }

  private void saveScreenshot(String name) {
    Pixmap pix =
        Pixmap.createFromFrameBuffer(
            0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    try {
      var file = Gdx.files.local("docs/artifacts/physics3d/" + name);
      file.parent().mkdirs();
      PixmapIO.writePNG(file, pix, -1, true);
    } finally {
      pix.dispose();
    }
  }

  private void writeReport() {
    String json =
        "{\n  \"sceneCreations\": "
            + sceneCreations
            + ",\n  \"triggerEnterEvents\": "
            + triggerEnters
            + ",\n  \"triggerMaterialChangedOnEnter\": "
            + triggerInstanceMaterialChanged
            + ",\n  \"triggerSourceMaterialUnchanged\": "
            + triggerSourceMaterialUnchanged
            + ",\n  \"triggerRedCagePixelsAfterEnter\": "
            + triggerRedPixelsAfterEnter
            + ",\n  \"nativeForwardCameraAlignmentYaw0\": "
            + forwardYawAlignment
            + ",\n  \"nativeStrafeCameraAlignmentYaw0_73\": "
            + strafeYawAlignment
            + ",\n  \"sphereTravelWorldUnits\": "
            + sphereTravel
            + ",\n  \"spherePixelsAtFirstNativePose\": "
            + spherePixelsAtFirstPose
            + ",\n  \"spherePixelsAtSecondNativePose\": "
            + spherePixelsAtSecondPose
            + ",\n  \"spherePixelsWithRendererDisabled\": "
            + spherePixelsWithRendererDisabled
            + ",\n  \"sphereProjectedNativePixelTravel\": "
            + sphereProjectedPixelTravel
            + ",\n  \"sphereColorCentroidPixelTravel\": "
            + spherePixelCentroidTravel
            + ",\n  \"debugOverlayPixelDeltaSamePose\": "
            + debugPixelDelta
            + ",\n  \"debugDrawerLiveBoundBufferCount\": "
            + debugOwnedBuffers.size()
            + ",\n  \"debugDrawerBufferDeletedOnClose\": "
            + debugBuffersDeleted
            + ",\n  \"debugDrawerLiveProgramCount\": "
            + debugOwnedPrograms.size()
            + ",\n  \"debugDrawerProgramDeletedOnClose\": "
            + debugProgramsDeleted
            + ",\n  \"debugDrawerShaderObjectCount\": "
            + debugOwnedShaders.size()
            + ",\n  \"debugDrawerShaderDeletedOnClose\": "
            + debugShadersDeleted
            + ",\n  \"sceneLiveBoundBufferCountBeforeClose\": "
            + sceneBuffersLive
            + ",\n  \"sceneBufferDeletedOnClose\": "
            + sceneBuffersDeleted
            + ",\n  \"voxelEditPixelDeltaInShowcaseRegion\": "
            + voxelEditPixelDelta
            + ",\n  \"firstPersonClearance\": "
            + firstTppClearance
            + ",\n  \"obstructedThirdPersonClearance\": "
            + obstructedTppClearance
            + ",\n  \"obstructedThirdPersonDistance\": "
            + obstructedTppDistance
            + ",\n  \"restoredThirdPersonClearance\": "
            + restoredTppClearance
            + ",\n  \"restoredThirdPersonDistance\": "
            + restoredTppDistance
            + ",\n  \"thirdPersonClearance\": "
            + secondTppClearance
            + ",\n  \"thirdPersonCameraDistance\": "
            + tppCameraDistance
            + ",\n  \"voxelRevisionBefore\": "
            + editRevisionBefore
            + ",\n  \"voxelRevisionAfter\": "
            + editRevisionAfter
            + ",\n  \"voxelCollisionCommitted\": "
            + editCollisionCommitted
            + ",\n  \"avatarRenderedSocketMatchesTool\": "
            + socketMatches
            + ",\n  \"avatarRightHandSocketWorld\": ["
            + socketWorld.x
            + ", "
            + socketWorld.y
            + ", "
            + socketWorld.z
            + "],\n  \"debugViewDetachedBeforeWorld\": "
            + debugViewDetached
            + ",\n  \"sceneRecreatedWithGl\": "
            + (sceneCreations == 2)
            + ",\n  \"glError\": "
            + Gdx.gl.glGetError()
            + "\n}\n";
    Gdx.files.local("docs/artifacts/physics3d/verification.json").writeString(json, false);
  }

  @Override
  public void resize(int width, int height) {
    if (renderer != null) renderer.resize(width, height);
    if (cameraRig != null && width > 0 && height > 0) cameraRig.resize(width, height);
    if (sphereProofCamera != null && width > 0 && height > 0) {
      sphereProofCamera.viewportWidth = width;
      sphereProofCamera.viewportHeight = height;
      sphereProofCamera.update();
    }
    if (stage != null) {
      stage.getViewport().update(width, height, false);
      if (hud != null) {
        hud.setBounds(
            16, Math.max(16, height - 142), Math.min(700, Math.max(340, width - 32)), 126);
        hud.invalidateHierarchy();
      }
    }
  }

  @Override
  public void pause() {
    focused = false;
    if (character != null) character.clearInput();
    if (Gdx.input != null) Gdx.input.setCursorCatched(false);
    discardMouse = true;
  }

  @Override
  public void resume() {
    focused = true;
    discardMouse = true;
  }

  void onFocusLost() {
    pause();
  }

  private void closeScene() {
    Throwable failure = null;
    boolean verifySceneBuffers = glBuffers != null && sceneTracked;
    java.util.Set<Integer> liveSceneBuffers =
        !verifySceneBuffers
            ? java.util.Set.of()
            : glBuffers.liveGenerated(sceneBufferStart, glBuffers.generationCount());
    int deletionStart = verifySceneBuffers ? glBuffers.deletionCount() : 0;
    Physics3DDebugView ownedDebug = debugView;
    debugView = null;
    debugEnabled = false;
    if (ownedDebug != null) failure = clean(failure, ownedDebug::close);
    VoxelChunkRenderer ownedChunks = chunks;
    chunks = null;
    if (ownedChunks != null) failure = clean(failure, ownedChunks::close);
    GeneratedVoxelAvatar ownedAvatar = avatar;
    avatar = null;
    if (ownedAvatar != null) failure = clean(failure, ownedAvatar::close);
    Game ownedGame = game;
    game = null;
    if (ownedGame != null) failure = clean(failure, ownedGame::close);
    GdxRenderer ownedRenderer = renderer;
    renderer = null;
    if (ownedRenderer != null) failure = clean(failure, ownedRenderer::dispose);
    ModelBatch ownedSphereProofBatch = sphereProofBatch;
    sphereProofBatch = null;
    sphereProofCamera = null;
    if (ownedSphereProofBatch != null) failure = clean(failure, ownedSphereProofBatch::dispose);
    Model ownedSphere = sphereModel;
    sphereModel = null;
    if (ownedSphere != null) failure = clean(failure, ownedSphere::dispose);
    Model ownedCrate = crateModel;
    crateModel = null;
    if (ownedCrate != null) failure = clean(failure, ownedCrate::dispose);
    Model ownedTrigger = triggerModel;
    triggerModel = null;
    if (ownedTrigger != null) failure = clean(failure, ownedTrigger::dispose);
    Model ownedPlatform = platformModel;
    platformModel = null;
    if (ownedPlatform != null) failure = clean(failure, ownedPlatform::dispose);
    Model ownedCameraWall = cameraWallModel;
    cameraWallModel = null;
    cameraWall = null;
    if (ownedCameraWall != null) failure = clean(failure, ownedCameraWall::dispose);
    Texture ownedAtlas = atlas;
    atlas = null;
    if (ownedAtlas != null) failure = clean(failure, ownedAtlas::dispose);
    Stage ownedStage = stage;
    stage = null;
    if (ownedStage != null) failure = clean(failure, ownedStage::dispose);
    Flex ownedHud = hud;
    hud = null;
    if (ownedHud != null) failure = clean(failure, ownedHud::close);
    UiTheme ownedTheme = theme;
    theme = null;
    if (ownedTheme != null) failure = clean(failure, ownedTheme::dispose);
    physics = null;
    collision = null;
    voxels = null;
    cameraRig = null;
    cameraComponent = null;
    platformBody = null;
    if (verifySceneBuffers) {
      try {
        require(
            !liveSceneBuffers.isEmpty(), "scene created no bound live GL buffers before cleanup");
        glBuffers.requireDeleted(
            liveSceneBuffers,
            deletionStart,
            glBuffers.deletionCount(),
            "scene " + sceneCreations + " close");
        sceneBuffersLive += liveSceneBuffers.size();
        sceneBuffersDeleted += liveSceneBuffers.size();
      } catch (RuntimeException | Error cleanup) {
        failure = combine(failure, cleanup);
      }
      sceneTracked = false;
    }
    if (failure instanceof RuntimeException e) throw e;
    if (failure instanceof Error e) throw e;
  }

  private static Throwable clean(Throwable prior, Runnable cleanup) {
    try {
      cleanup.run();
    } catch (RuntimeException | Error e) {
      return combine(prior, e);
    }
    return prior;
  }

  private static Throwable combine(Throwable a, Throwable b) {
    if (a == null) return b;
    a.addSuppressed(b);
    return a;
  }

  private static void require(boolean ok, String message) {
    if (!ok) throw new IllegalStateException("Physics3D smoke: " + message);
  }

  @Override
  public void dispose() {
    if (disposed) return;
    disposed = true;
    closeScene();
  }
}
