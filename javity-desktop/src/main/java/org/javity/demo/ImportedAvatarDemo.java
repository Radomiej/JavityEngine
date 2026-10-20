package org.javity.demo;

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.*;
import org.javity.animation.*;
import org.javity.assets.AssetLease;
import org.javity.avatar.*;
import org.javity.core.*;
import org.javity.core.Game;
import org.javity.gltf.*;
import org.javity.render.*;
import org.javity.render.lighting.*;
import org.javity.serialization.AssetId;

/**
 * Original imported humanoid: shared resource, independent poses, full body and arms/tool views.
 */
public final class ImportedAvatarDemo extends ApplicationAdapter {
  private final boolean smoke;
  private Game game;
  private GdxRenderer renderer;
  private GltfAsset asset;
  private GltfInstance first, second;
  private AvatarBinding bodyBinding, armsBinding;
  private AvatarView arms;
  private ModelRenderer bodyRenderer, armsRenderer;
  private ModelPoseAnimator bodyAnimator, armsAnimator;
  private Camera3D camera;
  private CameraRig3D rig;
  private boolean firstPerson;
  private FrameBuffer framebuffer;
  private Throwable failure;
  private boolean cleaned;
  private int originalMeshCount = -1;

  public ImportedAvatarDemo(boolean smoke) {
    this.smoke = smoke;
  }

  public static void main(String[] args) {
    boolean smoke = java.util.Arrays.asList(args).contains("--smoke");
    var config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("Imported avatar — full body / explicit arms + tool (Space: FPS, T: tool)");
    config.setWindowedMode(960, 640);
    config.setInitialVisible(!smoke);
    config.setForegroundFPS(60);
    if (java.util.Arrays.asList(args).contains("--gl20"))
      config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL20, 2, 0);
    else config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
    new Lwjgl3Application(new ImportedAvatarDemo(smoke), config);
  }

  @Override
  public void create() {
    try {
      System.out.println(
          "importedAvatarSmoke: native GL="
              + Gdx.gl.glGetString(GL20.GL_VERSION)
              + ", renderer="
              + Gdx.gl.glGetString(GL20.GL_RENDERER)
              + ", engine GL30="
              + (Gdx.gl30 != null));
      originalMeshCount = managedMeshCount();
      setup();
      if (smoke) {
        verify();
        cleanup();
        System.out.println(
            "importedAvatarSmoke: PASS (real CC0 humanoid GLB, exact metadata, canonical"
                + " palettes/socket/pose, full body vs arms/tool pixels, independent instances,"
                + " deferred lease disposal)");
        Gdx.app.exit();
      }
    } catch (Throwable next) {
      failure = next;
      next.printStackTrace();
      Gdx.app.exit();
    }
  }

  private void setup() {
    game = new Game();
    var assets = game.addSystem(new GltfAssets(12));
    AssetId<GltfAsset> id = new AssetId<>("avatar/cc0-block-humanoid");
    assets.register(id, Gdx.files.classpath("org/javity/gltf/fixtures/avatar.glb"));
    try (AssetLease<GltfAsset> lease = assets.request(id)) {
      require(lease.state() == AssetLease.State.READY, "Import failed: " + lease.failure());
      asset = lease.get();
      first = asset.instantiate();
      second = asset.instantiate();
    }
    require(
        asset.isClosing() && !asset.isDisposed(), "Instance handles did not pin released lease");
    var catalog = ImportedAvatarProfile.inspect(first.modelInstance());
    var profile =
        catalog
            .profile()
            .part(AvatarPart.TORSO, new PartKey("Avatar/Torso", 0))
            .part(AvatarPart.HEAD, new PartKey("Avatar/Head", 0), new PartKey("Avatar/Visor", 0))
            .part(AvatarPart.LEFT_ARM, new PartKey("Avatar/LeftArm", 0))
            .part(AvatarPart.RIGHT_ARM, new PartKey("Avatar/RightArm", 0))
            .part(
                AvatarPart.LEGS,
                new PartKey("Avatar/LeftLeg", 0),
                new PartKey("Avatar/RightLeg", 0))
            .slot("tool", new PartKey("Avatar/Hips/Spine/RightArmBone/RightHand/Tool", 0))
            .socket("grip", "Avatar/Hips/Spine/RightArmBone/RightHand", new Matrix4())
            .clip("salute", "tool-salute")
            .build();
    require(
        catalog.parts().size() == 8 && catalog.clips().size() == 1,
        "Unexpected imported canonical catalog: " + catalog);
    require(
        catalog.parts().stream().filter(p -> !p.bonePaths().isEmpty()).count() == 7,
        "Imported skin palette paths missing");
    game.addSystem(new RenderEnvironment3D()).setAmbient(.45f, .45f, .45f);
    var light = game.createObject("soft sun");
    light.addComponent(new Transform3D());
    light.addComponent(new DirectionalLight3D()).setIntensity(2);
    renderer = new PbrShaderProvider(12, 2, 1, 0).createRenderer();
    camera = game.createObject("camera").addComponent(new Camera3D(42));
    rig = new CameraRig3D(camera, new PerspectiveCamera(62, 960, 640), (a, b, r, o) -> 1);
    rig.setWorldProjection(42, .1f, 100);
    rig.setMode(AvatarCameraMode.THIRD_PERSON);
    rig.setBoomDistance(5);
    var body = game.createObject("third-person full body");
    body.addComponent(new Transform3D()).position.set(-.9f, 0, 0);
    bodyRenderer = body.addComponent(first.createRenderer());
    bodyAnimator = body.addComponent(new ModelPoseAnimator());
    bodyBinding = profile.definition().bind(bodyRenderer);
    var fps = game.createObject("same rig, arms and tool");
    fps.addComponent(new Transform3D()).position.set(.9f, 0, 0);
    armsRenderer = fps.addComponent(second.createRenderer());
    armsAnimator = fps.addComponent(new ModelPoseAnimator());
    armsBinding = profile.definition().bind(armsRenderer);
    arms = armsBinding.view().preset(AvatarPreset.ARMS_ONLY).slotVisible("tool", true);
    game.start();
    bodyAnimator.play(profile.clip("salute"), PlayMode.LOOP).seekNormalized(.1).setSpeed(0);
    armsAnimator.play(profile.clip("salute"), PlayMode.LOOP).seekNormalized(.5).setSpeed(0);
    game.step(0);
    resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
  }

  private void verify() {
    require(
        first.modelInstance().model == second.modelInstance().model, "Imported model not shared");
    var bodyPart = first.modelInstance().getNode("RightArm").parts.first();
    var armsPart = second.modelInstance().getNode("RightArm").parts.first();
    require(bodyPart.bones != armsPart.bones, "Independent instances share bone arrays");
    var canonical = second.modelInstance().getNode("RightArmBone");
    require(
        second.modelInstance().animations.first().nodeAnimations.first().node == canonical,
        "Imported animation channel detached from canonical instance node");
    require(
        first.modelInstance().animations.first().nodeAnimations.first().node != canonical,
        "Independent imported instances share animation target nodes");
    Matrix4 pose = new Matrix4(canonical.globalTransform);
    Matrix4 socket = armsBinding.socketTransform("grip", new Matrix4(), new Matrix4());
    Matrix4 otherSocket = bodyBinding.socketTransform("grip", new Matrix4(), new Matrix4());
    require(!same(socket, otherSocket, .001f), "Independent animation times lost");
    var emitted = gather(arms);
    require(emitted.size == 3, "FPS selection must contain two arms + tool");
    require(gather(bodyBinding.view()).size == 8, "TPP selection lost body parts");
    var toolNode = second.modelInstance().getNode("Tool");
    boolean foundCanonicalTool = false;
    for (var draw : emitted)
      if (draw.bones == null
          && draw.meshPart.mesh == toolNode.parts.first().meshPart.mesh
          && same(draw.worldTransform, toolNode.globalTransform, .00001f))
        foundCanonicalTool = true;
    require(foundCanonicalTool, "Rigid tool did not retain the canonical animated hand transform");
    require(
        emitted.get(1).bones == armsPart.bones
            || emitted.get(0).bones == armsPart.bones
            || emitted.get(2).bones == armsPart.bones,
        "Avatar view copied canonical palette");
    framebuffer = new RecoverableFrameBuffer(Pixmap.Format.RGBA8888, 640, 480, true);
    var writeFailure = Gdx.files.local("build/imported-avatar/write-failure");
    writeFailure.mkdirs();
    int bindingBeforeFailure = framebufferBinding();
    boolean rejectedWrite = false;
    try {
      capture("write-failure", false); // Directory target forces an actual PNG-write failure.
    } catch (GdxRuntimeException expected) {
      rejectedWrite = true;
    } finally {
      writeFailure.deleteDirectory();
    }
    require(
        rejectedWrite && framebufferBinding() == bindingBeforeFailure,
        "Failed capture did not restore caller framebuffer");
    Pixels armsAndTool = capture("arms-and-tool.png", false);
    arms.slotVisible("tool", false);
    Pixels armsOnly = capture("arms-only.png", false);
    require(armsAndTool.count > armsOnly.count + 10, "Tool slot did not change real pixels");
    arms.slotVisible("tool", true);
    arms.preset(AvatarPreset.FULL_BODY);
    Pixels twoBodies = capture("two-full-bodies.png", false);
    require(
        twoBodies.count > armsAndTool.count + 300,
        "Full-body selection did not restore body pixels");
    arms.preset(AvatarPreset.ARMS_ONLY);
    Pixels fps = capture("first-person.png", true);
    require(fps.count > 100, "CameraRig3D viewmodel path produced no arms/tool pixels");
    require(same(pose, canonical.globalTransform, .00001f), "View selection changed skeleton pose");
    require(
        same(socket, armsBinding.socketTransform("grip", new Matrix4(), new Matrix4()), .00001f),
        "Hidden-body view changed socket");
    require(armsAnimator.current().normalizedTime() == .5, "Rendering advanced animation clock");
    System.out.println(
        "importedAvatarSmoke: pixel counts arms+tool="
            + armsAndTool.count
            + ", arms="
            + armsOnly.count
            + ", full="
            + twoBodies.count
            + ", fps="
            + fps.count);
    firstPerson = false;
    // Asset close is already requested by the released typed lease. Detach before handles close.
    bodyRenderer.getGameObject().removeComponent(bodyRenderer);
    first.close();
    require(!asset.isDisposed(), "First handle disposed resources used by second instance");
    require(gather(arms).size == 3, "Releasing first instance invalidated second view");
    require(
        capture("surviving-instance.png", false).count > 200,
        "Remaining instance could not draw after sibling handle closed");
    require(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "Native imported avatar GL error");
  }

  private static Array<Renderable> gather(AvatarView view) {
    Array<Renderable> result = new Array<>();
    view.getRenderables(
        result,
        new Pool<>() {
          @Override
          protected Renderable newObject() {
            return new Renderable();
          }
        });
    return result;
  }

  private Pixels capture(String name, boolean fps) {
    firstPerson = fps;
    framebuffer.begin();
    try {
      draw(640, 480);
      Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, 640, 480);
      try {
        int count = 0;
        for (int y = 0; y < pixels.getHeight(); y++)
          for (int x = 0; x < pixels.getWidth(); x++) {
            int p = pixels.getPixel(x, y);
            // Geometry differs substantially from the dark blue background.
            if (((p >>> 24) & 255) > 55 || ((p >>> 16) & 255) > 65 || ((p >>> 8) & 255) > 80)
              count++;
          }
        PixmapIO.writePNG(Gdx.files.local("build/imported-avatar/" + name), pixels, -1, true);
        return new Pixels(count);
      } finally {
        pixels.dispose();
      }
    } finally {
      framebuffer.end();
    }
  }

  private void draw(int width, int height) {
    Gdx.gl.glViewport(0, 0, width, height);
    Gdx.gl.glClearColor(.055f, .08f, .12f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    rig.resize(width, height);
    renderer.resize(width, height);
    if (firstPerson) {
      rig.setMode(AvatarCameraMode.FIRST_PERSON);
      rig.update(new Vector3(0, 1.3f, 0), new Vector3(0, 0, -1), null);
      arms.setBaseTransform(new Matrix4().setToTranslation(0, -1.4f, -1.7f));
      renderer.renderViewmodel(rig.viewmodelCamera(), arms);
      arms.setBaseTransform(new Matrix4());
    } else {
      rig.setMode(AvatarCameraMode.THIRD_PERSON);
      rig.update(new Vector3(0, 1.15f, 0), new Vector3(0, 0, -1), null);
      renderer.renderWorld(game, camera, arms);
    }
  }

  @Override
  public void render() {
    if (smoke || failure != null) return;
    if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) firstPerson = !firstPerson;
    if (Gdx.input.isKeyJustPressed(Input.Keys.T)) {
      toolVisible = !toolVisible;
      arms.slotVisible("tool", toolVisible);
    }
    if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) Gdx.app.exit();
    bodyAnimator.current().setSpeed(1);
    armsAnimator.current().setSpeed(1);
    game.step(Math.min(Gdx.graphics.getDeltaTime(), .05f));
    draw(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
  }

  private boolean toolVisible = true;

  @Override
  public void resize(int width, int height) {
    if (rig != null && width > 0 && height > 0) {
      rig.resize(width, height);
      renderer.resize(width, height);
    }
  }

  private void cleanup() {
    if (cleaned) return;
    cleaned = true;
    ResourceScope cleanup = new ResourceScope();
    if (framebuffer != null) cleanup.own(framebuffer::dispose);
    if (renderer != null) cleanup.own(renderer::dispose);
    if (second != null) cleanup.own(second);
    if (first != null) cleanup.own(first);
    if (game != null) cleanup.own(game::close);
    cleanup.close();
    if (asset != null) require(asset.isDisposed(), "Imported asset survived final instance handle");
    if (smoke && originalMeshCount >= 0)
      require(
          originalMeshCount == managedMeshCount(),
          "Native imported mesh ownership leaked: " + Mesh.getManagedStatus());
  }

  @Override
  public void dispose() {
    try {
      cleanup();
    } catch (Throwable next) {
      if (failure == null) failure = next;
      else if (failure != next) failure.addSuppressed(next);
    }
    if (failure != null) throw new IllegalStateException("Imported avatar demo failed", failure);
  }

  private static int framebufferBinding() {
    var value = BufferUtils.newIntBuffer(1);
    Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING, value);
    return value.get(0);
  }

  private static int managedMeshCount() {
    var matcher = java.util.regex.Pattern.compile("[0-9]+").matcher(Mesh.getManagedStatus());
    int count = 0;
    while (matcher.find()) count += Integer.parseInt(matcher.group());
    return count;
  }

  private static boolean same(Matrix4 a, Matrix4 b, float epsilon) {
    for (int i = 0; i < 16; i++) if (Math.abs(a.val[i] - b.val[i]) > epsilon) return false;
    return true;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private record Pixels(int count) {}
}
