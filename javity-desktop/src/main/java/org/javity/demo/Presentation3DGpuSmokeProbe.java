package org.javity.demo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Vector3;
import java.nio.ByteBuffer;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.presentation.CameraPresentation3D;
import org.javity.presentation.Presentation3D;
import org.javity.render.Camera3D;
import org.javity.render.CameraRig3D;
import org.javity.render.GdxRenderer;
import org.javity.render.ModelRenderer;
import org.javity.voxel.BlockDefinition;
import org.javity.voxel.BlockRegistry;
import org.javity.voxel.ChunkPos;
import org.javity.voxel.VoxelWorld;
import org.javity.voxel.controller.VoxelCameraObstruction;

/** Actual framebuffer proof: model pose, world camera, viewmodel camera and final camera rays. */
final class Presentation3DGpuSmokeProbe extends ApplicationAdapter {
  private static final int SIZE = 192;
  private Game game;
  private GdxRenderer renderer;
  private FrameBuffer fbo;
  private int framebufferTextureId;
  private Model model;
  private ModelInstance viewmodel;
  private Camera3D camera;
  private CameraRig3D rig;
  private CameraPresentation3D worldCameraPose, viewmodelCameraPose;
  private Presentation3D modelPose;
  private int checks;
  private Throwable failure;

  @Override
  public void create() {
    try {
      run();
      System.out.println(
          "presentation3dGpuSmoke: PASS (model/viewmodel pixels, presented camera ray,"
              + " framebuffer)");
    } catch (Throwable t) {
      failure = t;
      t.printStackTrace();
    } finally {
      Gdx.app.exit();
    }
  }

  private void run() {
    game = new Game();
    renderer = new GdxRenderer();
    renderer.resize(SIZE, SIZE);
    fbo = new FrameBuffer(Pixmap.Format.RGBA8888, SIZE, SIZE, true);
    framebufferTextureId = fbo.getColorBufferTexture().getTextureObjectHandle();
    model =
        new ModelBuilder()
            .createBox(
                .8f,
                .8f,
                .8f,
                new Material(ColorAttribute.createDiffuse(0.1f, 0.9f, 0.2f, 1)),
                Usage.Position | Usage.Normal);
    GameObject cameraObject = game.createObject("presented camera");
    camera = cameraObject.addComponent(new Camera3D(50));
    camera.camera.near = .1f;
    camera.camera.far = 20;
    GameObject modelObject = game.createObject("presented model");
    Transform3D transform = modelObject.addComponent(new Transform3D());
    ModelRenderer modelRenderer = modelObject.addComponent(new ModelRenderer(model));
    modelPose = modelObject.addComponent(new Presentation3D());
    worldCameraPose =
        cameraObject.addComponent(new CameraPresentation3D(CameraPresentation3D.Space.WORLD));
    viewmodelCameraPose =
        cameraObject.addComponent(new CameraPresentation3D(CameraPresentation3D.Space.VIEWMODEL));
    viewmodel = new ModelInstance(model);
    viewmodel.transform.setToTranslation(0, 0, -2);
    AtomicSweep sweep = new AtomicSweep();
    rig =
        new CameraRig3D(
            camera,
            new PerspectiveCamera(72, 1, 1),
            (from, to, radius, owner) -> sweep.fraction(from, to, radius));
    rig.setRadius(.01f);
    rig.resize(SIZE, SIZE);
    game.start();
    game.step(0);
    Presentation3D.Lease modelLease = modelPose.contribute();
    CameraPresentation3D.Lease cameraLease = worldCameraPose.contribute();
    CameraPresentation3D.Lease handsLease = viewmodelCameraPose.contribute();
    try {
      rig.update(
          new Vector3(0, 0, 4), new Vector3(0, 0, -1), null, worldCameraPose, viewmodelCameraPose);
      int[] base = renderWorldPixels("world-base.png");
      int baseCount = base[0], baseCx = base[1];
      require(baseCount > 20, "World model fixture produced too few colored pixels");

      modelLease.set(.8f, 0, 0, 0, 0, 0, 0, 0, 0);
      int[] movedModel = renderWorldPixels("model-offset.png");
      require(
          Math.abs(movedModel[1] - baseCx) > 8,
          "Presented model translation did not move actual pixels");
      require(
          transform.position.isZero() && transform.scale.equals(new Vector3(1, 1, 1)),
          "Canonical Transform3D changed during model presentation");
      modelLease.set(0, 0, 0, 0, 0, 0, 0, 0, 0);

      Vector3 centerRayBefore = camera.camera.getPickRay(SIZE / 2f, SIZE / 2f).direction.cpy();
      Vector3 sideRayBefore = camera.camera.getPickRay(SIZE * .75f, SIZE / 2f).direction.cpy();
      cameraLease.set(.5f, 0, 0, 0, 1, 0, 18);
      rig.update(
          new Vector3(0, 0, 4), new Vector3(0, 0, -1), null, worldCameraPose, viewmodelCameraPose);
      Vector3 centerRayAfter = camera.camera.getPickRay(SIZE / 2f, SIZE / 2f).direction.cpy();
      Vector3 sideRayAfter = camera.camera.getPickRay(SIZE * .75f, SIZE / 2f).direction.cpy();
      require(
          centerRayBefore.dst(centerRayAfter) > .2f,
          "Presented camera picking ray did not use its rotation contribution");
      require(
          sideRayBefore.dst(sideRayAfter) > .05f,
          "Picking ray did not use the final presented FOV");
      require(
          Math.abs(rig.presentedWorldFov() - 68) < 1e-4,
          "Authored and delta FOV were not composed");
      require(
          Math.abs(camera.camera.position.x - (sweep.wallX - sweep.radius)) < 1e-3,
          "Presented camera offset bypassed obstruction clearance");
      cameraLease.set(.5f, 0, 0, 0, .2f, 0, 18);
      rig.update(
          new Vector3(0, 0, 4), new Vector3(0, 0, -1), null, worldCameraPose, viewmodelCameraPose);
      int[] movedCamera = renderWorldPixels("world-camera-offset.png");
      require(
          Math.abs(movedCamera[1] - baseCx) > 2,
          "Presented world camera did not move actual pixels");
      cameraLease.set(.5f, 0, 0, 0, 1, 0, 18);
      rig.update(
          new Vector3(0, 0, 4), new Vector3(0, 0, -1), null, worldCameraPose, viewmodelCameraPose);
      require(
          camera.camera.frustum.boundsInFrustum(
              camera.camera.position.cpy().mulAdd(camera.camera.direction, 2),
              new Vector3(.1f, .1f, .1f)),
          "Final presented camera frustum omitted its forward view");
      require(
          !camera.camera.frustum.boundsInFrustum(
              new Vector3(0, 0, -2), new Vector3(.05f, .05f, .05f)),
          "Final presented camera frustum still contains the authored forward control point");
      require(
          sweep.radius > 0 && Math.abs(sweep.target.x - .5f) < 1e-4,
          "Presented camera did not sweep its offset with near-plane clearance");

      double baseHalfHeight = Math.tan(Math.toRadians(25)) * .1;
      double baseRadius = Math.sqrt(.01 + 2 * baseHalfHeight * baseHalfHeight);
      Vector3 fovOnlyEye = new Vector3((float) (sweep.wallX - baseRadius - .003), 0, 4);
      cameraLease.set(0, 0, 0, 0, 0, 0, 18);
      rig.update(fovOnlyEye, new Vector3(0, 0, -1), null, worldCameraPose, viewmodelCameraPose);
      require(
          rig.worldPresentationConstrained(), "FOV-only near-plane overlap was not constrained");
      require(
          Math.abs(rig.presentedWorldFov() - 50) < 1e-4,
          "Unsafe FOV-only contribution was not replaced by the authored base FOV");
      require(
          Math.abs(sweep.wallX - camera.camera.position.x) > baseRadius,
          "Base-FOV fallback did not restore safe near-plane clearance");

      cameraLease.set(0, 0, 0, 0, 0, 0, 0);
      verifyVoxelFovClearance(camera, worldCameraPose);

      handsLease.set(.35f, 0, 0, 0, 0, 0, 0);
      rig.update(
          new Vector3(0, 0, 4), new Vector3(0, 0, -1), null, worldCameraPose, viewmodelCameraPose);
      int[] handsBase = renderViewmodelPixels("viewmodel-offset.png");
      handsLease.set(0, 0, 0, 0, 0, 0, 0);
      rig.update(
          new Vector3(0, 0, 4), new Vector3(0, 0, -1), null, worldCameraPose, viewmodelCameraPose);
      int[] handsMoved = renderViewmodelPixels("viewmodel-zero.png");
      require(handsBase[0] > 10 && handsMoved[0] > 10, "Viewmodel fixture produced too few pixels");
      require(
          Math.abs(handsBase[1] - handsMoved[1]) > 2,
          "Viewmodel camera presentation did not move actual pixels");
      require(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "GL error during Presentation3D proof");
      System.out.println(
          "presentation3dGpuSmoke pixels: model="
              + baseCx
              + "->"
              + movedModel[1]
              + ", world-camera="
              + baseCx
              + "->"
              + movedCamera[1]
              + ", viewmodel="
              + handsBase[1]
              + "->"
              + handsMoved[1]
              + ", presentedFov="
              + rig.presentedWorldFov()
              + ", wallRadius="
              + sweep.radius);
      checks = 8;
    } finally {
      handsLease.close();
      cameraLease.close();
      modelLease.close();
      game.close();
      renderer.dispose();
      model.dispose();
      fbo.dispose();
      require(
          !Gdx.gl.glIsTexture(framebufferTextureId),
          "Framebuffer color texture leaked after disposal");
      require(
          Gdx.gl.glGetError() == GL20.GL_NO_ERROR,
          "GL error while releasing presentation fixture resources");
    }
    require(failure == null, "Probe failed: " + failure);
  }

  private void verifyVoxelFovClearance(Camera3D worldCamera, CameraPresentation3D worldEffects) {
    BlockRegistry registry = new BlockRegistry();
    int wall = registry.register(new BlockDefinition("presentation wall", 0));
    VoxelWorld voxelWorld = new VoxelWorld(registry, 1);
    voxelWorld.loadChunk(new ChunkPos(0, 0, 0));
    voxelWorld.setBlock(2, 1, 1, wall);
    VoxelCameraObstruction obstruction = new VoxelCameraObstruction(voxelWorld);
    CameraRig3D voxelRig =
        new CameraRig3D(worldCamera, new PerspectiveCamera(72, 1, 1), obstruction);
    voxelRig.setRadius(.01f);
    voxelRig.resize(SIZE, SIZE);
    float baseHalfHeight = (float) Math.tan(Math.toRadians(25)) * .1f;
    float baseRadius = (float) Math.sqrt(.01f + 2f * baseHalfHeight * baseHalfHeight);
    float wideHalfHeight = (float) Math.tan(Math.toRadians(34)) * .1f;
    float wideRadius = (float) Math.sqrt(.01f + 2f * wideHalfHeight * wideHalfHeight);
    Vector3 eye = new Vector3(1.87f, 1.5f, 1.5f);
    require(
        obstruction.sweepFraction(eye, eye, baseRadius, null) == 1f,
        "Loaded Voxel wall should clear the authored near plane");
    require(
        obstruction.sweepFraction(eye, eye, wideRadius, null) == 0f,
        "Loaded Voxel wall should overlap the widened near plane");

    CameraPresentation3D.Lease fov = worldEffects.contribute();
    float constrainedFov = Float.NaN;
    float admittedFov = Float.NaN;
    try {
      fov.set(0, 0, 0, 0, 0, 0, 18);
      voxelRig.update(eye, new Vector3(0, 0, -1), null, worldEffects, null);
      require(
          voxelRig.worldPresentationConstrained(),
          "Voxel wall did not constrain the FOV-only effect");
      require(
          Math.abs(voxelRig.presentedWorldFov() - 50f) < 1e-4f,
          "Voxel fallback did not restore base FOV");
      constrainedFov = voxelRig.presentedWorldFov();
      require(
          worldCamera.camera.position.dst(eye) < 1e-5f,
          "Voxel fallback changed authored camera eye");

      voxelWorld.setBlock(2, 1, 1, 0);
      voxelRig.update(eye, new Vector3(0, 0, -1), null, worldEffects, null);
      require(
          !voxelRig.worldPresentationConstrained(),
          "Removing Voxel wall did not readmit FOV effect");
      require(
          Math.abs(voxelRig.presentedWorldFov() - 68f) < 1e-4f,
          "Removed wall did not restore presented FOV");
      admittedFov = voxelRig.presentedWorldFov();
      CameraOutputSnapshot lastCamera = new CameraOutputSnapshot(worldCamera.camera);
      float lastFov = voxelRig.presentedWorldFov();

      voxelWorld.setBlock(2, 1, 1, wall);
      Vector3 unsafeEye = new Vector3(1.9f, 1.5f, 1.5f);
      boolean rejected = false;
      try {
        voxelRig.update(unsafeEye, new Vector3(0, 0, -1), null, worldEffects, null);
      } catch (IllegalStateException expected) {
        rejected = true;
      }
      require(rejected, "Obstructed authored fallback was not rejected");
      require(
          lastCamera.matches(worldCamera.camera),
          "Rejected camera update changed the last valid camera output");
      require(
          Math.abs(voxelRig.presentedWorldFov() - lastFov) < 1e-4f,
          "Rejected camera update changed presented diagnostics");
      voxelWorld.setBlock(2, 1, 1, 0);
    } finally {
      fov.close();
    }
    System.out.println(
        "presentation3dGpuSmoke voxel FOV wall: baseRadius="
            + baseRadius
            + ", wideRadius="
            + wideRadius
            + ", constrainedFov="
            + constrainedFov
            + ", admittedFov="
            + admittedFov);
  }

  private int[] renderWorldPixels(String artifact) {
    fbo.begin();
    clear();
    try {
      renderer.renderWorld(game, camera, null);
      saveArtifact(artifact);
      return centroid(.1f, .9f, .2f);
    } finally {
      fbo.end();
    }
  }

  private int[] renderViewmodelPixels(String artifact) {
    fbo.begin();
    clear();
    try {
      renderer.renderViewmodel(rig.viewmodelCamera(), viewmodel);
      saveArtifact(artifact);
      return centroid(.1f, .9f, .2f);
    } finally {
      fbo.end();
    }
  }

  private void clear() {
    Gdx.gl.glViewport(0, 0, SIZE, SIZE);
    Gdx.gl.glClearColor(0, 0, 0, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
  }

  private void saveArtifact(String name) {
    Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, SIZE, SIZE);
    try {
      FileHandle output = Gdx.files.local("docs/artifacts/presentation3d/" + name);
      output.parent().mkdirs();
      PixmapIO.writePNG(output, pixels);
    } finally {
      pixels.dispose();
    }
  }

  private int[] centroid(float r, float g, float b) {
    ByteBuffer pixels = ByteBuffer.allocateDirect(SIZE * SIZE * 4);
    Gdx.gl.glReadPixels(0, 0, SIZE, SIZE, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixels);
    int count = 0, sumX = 0;
    for (int y = 0; y < SIZE; y++)
      for (int x = 0; x < SIZE; x++) {
        int i = (y * SIZE + x) * 4;
        int pr = pixels.get(i) & 255, pg = pixels.get(i + 1) & 255, pb = pixels.get(i + 2) & 255;
        if (pg > pr * 1.5 && pg > pb * 1.3 && pg > 35) {
          count++;
          sumX += x;
        }
      }
    return new int[] {count, count == 0 ? 0 : sumX / count};
  }

  private void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static final class AtomicSweep {
    final Vector3 target = new Vector3();
    final float wallX = .5f;
    float radius;

    float fraction(Vector3 from, Vector3 to, float cameraRadius) {
      radius = cameraRadius;
      if (from.dst2(to) == 0f) return wallX - from.x < cameraRadius - 1e-4f ? 0f : 1f;
      target.set(to);
      if (to.x <= from.x || to.x <= wallX - cameraRadius) return 1f;
      return Math.max(0, Math.min(1, (wallX - cameraRadius - from.x) / (to.x - from.x)));
    }
  }

  private static final class CameraOutputSnapshot {
    final float viewportWidth;
    final float viewportHeight;
    final float fieldOfView;
    final float near;
    final float far;
    final Vector3 position;
    final Vector3 direction;
    final Vector3 up;
    final float[] projection;
    final float[] view;
    final float[] combined;
    final float[] inverse;
    final float[][] frustum = new float[6][4];
    final com.badlogic.gdx.math.collision.Ray center;
    final com.badlogic.gdx.math.collision.Ray corner;

    CameraOutputSnapshot(PerspectiveCamera camera) {
      viewportWidth = camera.viewportWidth;
      viewportHeight = camera.viewportHeight;
      fieldOfView = camera.fieldOfView;
      near = camera.near;
      far = camera.far;
      position = camera.position.cpy();
      direction = camera.direction.cpy();
      up = camera.up.cpy();
      projection = camera.projection.val.clone();
      view = camera.view.val.clone();
      combined = camera.combined.val.clone();
      inverse = camera.invProjectionView.val.clone();
      for (int i = 0; i < frustum.length; i++) {
        Vector3 normal = camera.frustum.planes[i].normal;
        frustum[i][0] = normal.x;
        frustum[i][1] = normal.y;
        frustum[i][2] = normal.z;
        frustum[i][3] = camera.frustum.planes[i].d;
      }
      center = copyRay(camera, viewportWidth * .5f, viewportHeight * .5f);
      corner = copyRay(camera, 0, 0);
    }

    boolean matches(PerspectiveCamera camera) {
      if (viewportWidth != camera.viewportWidth
          || viewportHeight != camera.viewportHeight
          || fieldOfView != camera.fieldOfView
          || near != camera.near
          || far != camera.far
          || !position.equals(camera.position)
          || !direction.equals(camera.direction)
          || !up.equals(camera.up)
          || !java.util.Arrays.equals(projection, camera.projection.val)
          || !java.util.Arrays.equals(view, camera.view.val)
          || !java.util.Arrays.equals(combined, camera.combined.val)
          || !java.util.Arrays.equals(inverse, camera.invProjectionView.val)) return false;
      for (int i = 0; i < frustum.length; i++) {
        Vector3 normal = camera.frustum.planes[i].normal;
        if (frustum[i][0] != normal.x
            || frustum[i][1] != normal.y
            || frustum[i][2] != normal.z
            || frustum[i][3] != camera.frustum.planes[i].d) return false;
      }
      var nextCenter = copyRay(camera, viewportWidth * .5f, viewportHeight * .5f);
      var nextCorner = copyRay(camera, 0, 0);
      return center.origin.equals(nextCenter.origin)
          && center.direction.equals(nextCenter.direction)
          && corner.origin.equals(nextCorner.origin)
          && corner.direction.equals(nextCorner.direction);
    }

    private static com.badlogic.gdx.math.collision.Ray copyRay(
        PerspectiveCamera camera, float x, float y) {
      com.badlogic.gdx.math.collision.Ray source = camera.getPickRay(x, y);
      return new com.badlogic.gdx.math.collision.Ray(source.origin.cpy(), source.direction.cpy());
    }
  }
}
