package org.javity.render;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.GdxNativesLoader;
import java.util.concurrent.atomic.AtomicReference;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.presentation.CameraPresentation3D;
import org.junit.Test;

public class CameraRig3DTest {
  static {
    GdxNativesLoader.load();
  }

  @Test
  public void placesFirstPersonAndSweptThirdPersonWithoutMutatingOwner() {
    AtomicReference<GameObject> ignored = new AtomicReference<>();
    AtomicReference<Float> radius = new AtomicReference<>();
    Camera3D world = new Camera3D(67);
    PerspectiveCamera hands = new PerspectiveCamera(72, 1, 1);
    CameraRig3D rig =
        new CameraRig3D(
            world,
            hands,
            (from, to, r, owner) -> {
              assertEquals(new Vector3(2, 3, 4), from);
              assertEquals(new Vector3(-1.5f, 3, 4), to);
              radius.set(r);
              ignored.set(owner);
              return .5f;
            });
    try (Game game = new Game()) {
      GameObject owner = game.createObject("owner");
      Vector3 eye = new Vector3(2, 3, 4), look = new Vector3(1, 0, 0);
      rig.update(eye, look, owner);
      assertEquals(eye, world.camera.position);
      assertEquals(look, world.camera.direction);
      assertEquals(Vector3.Y, world.camera.up);
      assertNull(radius.get());
      rig.setMode(AvatarCameraMode.THIRD_PERSON);
      rig.update(eye, look, owner);
      assertEquals(owner, ignored.get());
      assertTrue(radius.get() > 0);
      assertEquals(0.5f, rig.clearanceFraction(), 0);
      assertEquals(.25f, world.camera.position.x, .0001f);
      assertEquals(look, world.camera.direction);
      assertEquals(Vector3.Y, world.camera.up);
      assertEquals(eye, new Vector3(2, 3, 4));
      assertEquals(Vector3.Zero, hands.position);
    }
  }

  @Test
  public void projectionResizeRecomputesCornerRadiusAndPresentationIsCopied() {
    AtomicReference<Float> radius = new AtomicReference<>();
    CameraRig3D rig =
        new CameraRig3D(
            new Camera3D(67),
            new PerspectiveCamera(72, 1, 1),
            (from, to, r, owner) -> {
              radius.set(r);
              return 1;
            });
    rig.setMode(AvatarCameraMode.THIRD_PERSON);
    rig.update(Vector3.Zero, Vector3.Z, null);
    float first = radius.get();
    rig.resize(1600, 600);
    rig.update(Vector3.Zero, Vector3.Z, null);
    assertTrue(radius.get() > first);
    rig.setWorldProjection(90, .3f, 400);
    rig.update(Vector3.Zero, Vector3.Z, null);
    assertTrue(radius.get() > first);
    rig.setRadius(.01f);
    rig.setWorldProjection(70, .5f, 400);
    rig.update(Vector3.Zero, Vector3.Z, null);
    float halfHeight = (float) Math.tan(Math.toRadians(35)) * .5f;
    float halfWidth = halfHeight * (1600f / 600f);
    assertTrue(
        radius.get() + .0001f >= Math.sqrt(.25f + halfHeight * halfHeight + halfWidth * halfWidth));
    assertThrows(IllegalArgumentException.class, () -> rig.setWorldProjection(70, .5f, .02f));
    Matrix4 supplied = new Matrix4().setToTranslation(1, 2, 3);
    rig.setPresentationOffset(supplied);
    supplied.setToTranslation(4, 5, 6);
    assertEquals(
        new Vector3(1, 2, 3), rig.presentationOffset(new Matrix4()).getTranslation(new Vector3()));
    assertThrows(IllegalArgumentException.class, () -> rig.setWorldProjection(180, .1f, 10));
    assertThrows(
        IllegalArgumentException.class,
        () -> rig.setPresentationOffset(new Matrix4().scl(Float.NaN)));
    assertThrows(
        IllegalArgumentException.class, () -> rig.setPresentationOffset(new Matrix4().scl(2)));
    assertThrows(
        IllegalArgumentException.class, () -> rig.setPresentationOffset(new Vector3(65, 0, 0)));
  }

  @Test
  public void rejectsInvalidSweepResultsAndInputs() {
    CameraRig3D rig =
        new CameraRig3D(
            new Camera3D(67),
            new PerspectiveCamera(72, 1, 1),
            (from, to, radius, owner) -> Float.NaN);
    rig.setMode(AvatarCameraMode.THIRD_PERSON);
    assertThrows(IllegalArgumentException.class, () -> rig.update(Vector3.Zero, Vector3.Z, null));
    assertThrows(
        IllegalArgumentException.class, () -> rig.update(Vector3.Zero, Vector3.Zero, null));
  }

  @Test
  public void verticalLookHasStableUpBasisAndInvalidQueryDoesNotCommitPose() {
    Camera3D world = new Camera3D(67);
    CameraRig3D rig =
        new CameraRig3D(
            world, new PerspectiveCamera(72, 1, 1), (from, to, radius, owner) -> Float.NaN);
    rig.update(Vector3.Zero, Vector3.Y, null);
    assertTrue(world.camera.direction.cpy().crs(world.camera.up).len2() > .9f);
    assertEquals(Vector3.Y, world.camera.direction);
    Vector3 before = world.camera.position.cpy();
    rig.setMode(AvatarCameraMode.THIRD_PERSON);
    assertThrows(
        IllegalArgumentException.class, () -> rig.update(new Vector3(5, 6, 7), Vector3.Z, null));
    assertEquals(before, world.camera.position);
  }

  @Test
  public void presentationTranslationIsSweptBeforeFpsAndTppBoomPlacement() {
    java.util.List<Vector3> starts = new java.util.ArrayList<>();
    java.util.List<Vector3> ends = new java.util.ArrayList<>();
    java.util.concurrent.atomic.AtomicReference<Float> offsetFraction =
        new java.util.concurrent.atomic.AtomicReference<>(.25f);
    Camera3D world = new Camera3D(67);
    CameraRig3D rig =
        new CameraRig3D(
            world,
            new PerspectiveCamera(72, 1, 1),
            (from, to, radius, owner) -> {
              starts.add(from.cpy());
              ends.add(to.cpy());
              return starts.size() == 1 ? offsetFraction.get() : .5f;
            });
    // For a +Z looking camera, local +X maps to world -X.
    rig.setPresentationOffset(new Matrix4().setToTranslation(-2, 0, 0));
    rig.update(Vector3.Zero, Vector3.Z, null);
    assertEquals(new Vector3(.5f, 0, 0), world.camera.position);
    assertEquals(1, starts.size());
    assertEquals(Vector3.Zero, starts.get(0));
    assertEquals(new Vector3(2, 0, 0), ends.get(0));

    starts.clear();
    ends.clear();
    rig.setMode(AvatarCameraMode.THIRD_PERSON);
    rig.update(Vector3.Zero, Vector3.Z, null);
    assertEquals(2, starts.size());
    assertEquals(Vector3.Zero, starts.get(0));
    assertEquals(new Vector3(2, 0, 0), ends.get(0));
    assertEquals(new Vector3(.5f, 0, 0), starts.get(1));
    assertEquals(new Vector3(.5f, 0, -3.5f), ends.get(1));
    assertEquals(new Vector3(.5f, 0, -1.75f), world.camera.position);
    assertEquals(.25f, rig.clearanceFraction(), 0);

    starts.clear();
    ends.clear();
    offsetFraction.set(1f);
    rig.setMode(AvatarCameraMode.FIRST_PERSON);
    rig.update(Vector3.Zero, Vector3.Z, null);
    assertEquals(new Vector3(2, 0, 0), world.camera.position);
  }

  @Test
  public void worldAndViewmodelPresentationUseLiveFovAndObstructedPresentedPose() {
    AtomicReference<Vector3> sweepTarget = new AtomicReference<>();
    AtomicReference<Float> sweptRadius = new AtomicReference<>();
    Camera3D world = new Camera3D(67);
    PerspectiveCamera vm = new PerspectiveCamera(72, 1, 1);
    CameraRig3D rig =
        new CameraRig3D(
            world,
            vm,
            (from, to, r, owner) -> {
              if (from.dst2(to) > 0f) sweepTarget.set(new Vector3(to));
              sweptRadius.set(r);
              return from.dst2(to) == 0f ? 1f : .5f;
            });
    try (Game game = new Game()) {
      var owner = game.createObject("camera");
      var w = owner.addComponent(new CameraPresentation3D(CameraPresentation3D.Space.WORLD));
      var v = owner.addComponent(new CameraPresentation3D(CameraPresentation3D.Space.VIEWMODEL));
      game.start();
      var wc = w.openChannel();
      var vc = v.openChannel();
      wc.setPose(1, 0, 0, 0, (float) Math.PI / 2, 0, 30);
      vc.setPose(.25f, 0, 0, 0, 0, 0, -100);
      rig.resize(1200, 600);
      Vector3 eye = new Vector3(0, 0, 0), look = new Vector3(0, 0, 1);
      rig.update(eye, look, owner, w, v);
      assertEquals(Vector3.Zero, eye);
      assertEquals(Vector3.Z, look);
      assertEquals(new Vector3(-1, 0, 0), sweepTarget.get());
      assertEquals(97f, rig.presentedWorldFov(), 0);
      assertEquals(2f, rig.presentedViewmodelFov(), 0);
      assertEquals(97f, world.camera.fieldOfView, 0);
      assertEquals(2f, vm.fieldOfView, 0);
      assertEquals(new Vector3(-.5f, 0, 0), world.camera.position);
      assertEquals(new Vector3(.25f, 0, 0), vm.position);
      assertEquals(.5f, rig.clearanceFraction(), 0);
      assertTrue(sweptRadius.get() > 0);
      float baselinePresentedRadius = sweptRadius.get();
      assertEquals(new Vector3(1, 0, 0), world.camera.direction);
      wc.setFovDelta(500);
      rig.update(Vector3.Zero, Vector3.Z, owner, w, v);
      assertEquals(178f, rig.presentedWorldFov(), 0);
      assertEquals(178f, world.camera.fieldOfView, 0);
      assertTrue(sweptRadius.get() > baselinePresentedRadius);
      rig.setWorldProjection(80, .1f, 240);
      rig.update(eye, look, owner, w, v);
      assertEquals(178f, rig.presentedWorldFov(), 0);
      wc.close();
      rig.update(eye, look, owner, w, v);
      assertEquals(80f, rig.presentedWorldFov(), 0);
      assertEquals(80f, world.camera.fieldOfView, 0);
    }
  }

  @Test
  public void rigSeedsAuthoredCameraFovsAndRejectsUnsafeFovOnlyPresentation() {
    Camera3D world = new Camera3D(50);
    PerspectiveCamera hands = new PerspectiveCamera(83, 1, 1);
    AtomicReference<Float> stationaryRadius = new AtomicReference<>();
    CameraRig3D rig =
        new CameraRig3D(
            world,
            hands,
            (from, to, radius, owner) -> {
              if (from.dst2(to) == 0f)
                stationaryRadius.updateAndGet(
                    current -> current == null ? radius : Math.max(current, radius));
              return from.dst2(to) == 0f && radius > .15f ? 0f : 1f;
            });
    rig.setRadius(.01f);
    assertEquals(50f, world.camera.fieldOfView, 0);
    assertEquals(83f, hands.fieldOfView, 0);
    assertEquals(50f, rig.presentedWorldFov(), 0);
    assertEquals(83f, rig.presentedViewmodelFov(), 0);
    try (Game game = new Game()) {
      GameObject owner = game.createObject("view");
      CameraPresentation3D fx =
          owner.addComponent(new CameraPresentation3D(CameraPresentation3D.Space.WORLD));
      game.start();
      CameraPresentation3D.Channel channel = fx.openChannel();
      channel.setFovDelta(70);
      rig.update(Vector3.Zero, Vector3.Z, owner, fx, null);
      assertTrue(stationaryRadius.get() > .15f);
      assertTrue(rig.worldPresentationConstrained());
      assertEquals(50f, rig.presentedWorldFov(), 0);
      assertEquals(50f, world.camera.fieldOfView, 0);
      assertEquals(Vector3.Zero, world.camera.position);

      rig.setWorldProjection(60, .1f, 240);
      channel.setFovDelta(0);
      rig.update(Vector3.Zero, Vector3.Z, owner, fx, null);
      assertEquals(60f, rig.presentedWorldFov(), 0);
      assertFalse(rig.worldPresentationConstrained());
    }
  }

  @Test
  public void cameraContributionBoundsKeepEverySurvivingSubsetRepresentable() {
    try (Game game = new Game()) {
      GameObject owner = game.createObject("bounded camera provider");
      CameraPresentation3D provider =
          owner.addComponent(new CameraPresentation3D(CameraPresentation3D.Space.WORLD));
      game.start();
      CameraPresentation3D.Lease positive = provider.contribute();
      CameraPresentation3D.Lease negative = provider.contribute();
      assertThrows(
          IllegalArgumentException.class, () -> positive.set(Float.MAX_VALUE, 0, 0, 0, 0, 0, 0));
      float bounded = Float.MAX_VALUE / 128f;
      positive.set(bounded, 0, 0, 0, 0, 0, 0);
      negative.set(-bounded, 0, 0, 0, 0, 0, 0);
      CameraPresentation3D.Snapshot snapshot = new CameraPresentation3D.Snapshot();
      provider.snapshot(snapshot);
      assertEquals(0, snapshot.translationX(), 0);
      negative.close();
      provider.snapshot(snapshot);
      assertEquals(bounded, snapshot.translationX(), 0);
      positive.close();
      provider.snapshot(snapshot);
      assertEquals(0, snapshot.translationX(), 0);
    }
  }

  @Test
  public void nonfinitePresentedViewmodelIsRejectedBeforeEitherCameraOrDiagnosticsCommit() {
    Camera3D world = new Camera3D(67);
    PerspectiveCamera hands = new PerspectiveCamera(72, 1, 1);
    CameraRig3D rig = new CameraRig3D(world, hands, (from, to, radius, owner) -> 1f);
    rig.resize(800, 600);
    try (Game game = new Game()) {
      GameObject owner = game.createObject("overflow view");
      CameraPresentation3D viewmodel =
          owner.addComponent(new CameraPresentation3D(CameraPresentation3D.Space.VIEWMODEL));
      game.start();
      rig.update(Vector3.Zero, Vector3.Z, owner);
      CameraState worldBefore = new CameraState(world.camera);
      CameraState handsBefore = new CameraState(hands);
      float worldFovBefore = rig.presentedWorldFov();
      float handsFovBefore = rig.presentedViewmodelFov();
      float clearanceBefore = rig.clearanceFraction();
      boolean constrainedBefore = rig.worldPresentationConstrained();

      CameraPresentation3D.Lease[] leases = new CameraPresentation3D.Lease[64];
      float contribution = Float.MAX_VALUE / 128f;
      for (int i = 0; i < leases.length; i++) {
        leases[i] = viewmodel.contribute();
        leases[i].set(contribution, 0, 0, 0, 0, 0, i == 0 ? -70 : 0);
      }
      assertThrows(
          IllegalArgumentException.class,
          () -> rig.update(Vector3.Zero, Vector3.Z, owner, null, viewmodel));
      worldBefore.assertUnchanged(world.camera);
      handsBefore.assertUnchanged(hands);
      assertEquals(worldFovBefore, rig.presentedWorldFov(), 0);
      assertEquals(handsFovBefore, rig.presentedViewmodelFov(), 0);
      assertEquals(clearanceBefore, rig.clearanceFraction(), 0);
      assertEquals(constrainedBefore, rig.worldPresentationConstrained());

      for (CameraPresentation3D.Lease lease : leases) lease.close();
      CameraPresentation3D.Lease large = viewmodel.contribute();
      large.set(1024, 0, 0, 0, 0, 0, -5);
      rig.update(Vector3.Zero, Vector3.Z, owner, null, viewmodel);
      assertEquals(1024, hands.position.x, 0);
      assertEquals(67, rig.presentedViewmodelFov(), 0);
      assertFinite(hands.projection);
      assertFinite(hands.view);
      assertFinite(hands.combined);
      assertFinite(hands.invProjectionView);
      assertTrue(Float.isFinite(pickRay(hands, 400, 300).direction.x));
    }
  }

  private static void assertFinite(Matrix4 matrix) {
    for (float value : matrix.val) assertTrue(Float.isFinite(value));
  }

  private static final class CameraState {
    private final float width;
    private final float height;
    private final float fov;
    private final float near;
    private final float far;
    private final Vector3 position;
    private final Vector3 direction;
    private final Vector3 up;
    private final float[] projection;
    private final float[] view;
    private final float[] combined;
    private final float[] inverse;
    private final Vector3[] planeNormals = new Vector3[6];
    private final float[] planeDistances = new float[6];
    private final Ray center;
    private final Ray corner;

    private CameraState(PerspectiveCamera camera) {
      width = camera.viewportWidth;
      height = camera.viewportHeight;
      fov = camera.fieldOfView;
      near = camera.near;
      far = camera.far;
      position = camera.position.cpy();
      direction = camera.direction.cpy();
      up = camera.up.cpy();
      projection = camera.projection.val.clone();
      view = camera.view.val.clone();
      combined = camera.combined.val.clone();
      inverse = camera.invProjectionView.val.clone();
      for (int i = 0; i < planeNormals.length; i++) {
        planeNormals[i] = camera.frustum.planes[i].normal.cpy();
        planeDistances[i] = camera.frustum.planes[i].d;
      }
      center = pickRay(camera, width * .5f, height * .5f);
      corner = pickRay(camera, 0, 0);
    }

    private void assertUnchanged(PerspectiveCamera camera) {
      assertEquals(width, camera.viewportWidth, 0);
      assertEquals(height, camera.viewportHeight, 0);
      assertEquals(fov, camera.fieldOfView, 0);
      assertEquals(near, camera.near, 0);
      assertEquals(far, camera.far, 0);
      assertEquals(position, camera.position);
      assertEquals(direction, camera.direction);
      assertEquals(up, camera.up);
      assertArrayEquals(projection, camera.projection.val, 0);
      assertArrayEquals(view, camera.view.val, 0);
      assertArrayEquals(combined, camera.combined.val, 0);
      assertArrayEquals(inverse, camera.invProjectionView.val, 0);
      for (int i = 0; i < planeNormals.length; i++) {
        assertEquals(planeNormals[i], camera.frustum.planes[i].normal);
        assertEquals(planeDistances[i], camera.frustum.planes[i].d, 0);
      }
      var nextCenter = pickRay(camera, width * .5f, height * .5f);
      var nextCorner = pickRay(camera, 0, 0);
      assertEquals(center.origin, nextCenter.origin);
      assertEquals(center.direction, nextCenter.direction);
      assertEquals(corner.origin, nextCorner.origin);
      assertEquals(corner.direction, nextCorner.direction);
    }
  }

  private static Ray pickRay(PerspectiveCamera camera, float screenX, float screenY) {
    float ndcX = 2f * screenX / camera.viewportWidth - 1f;
    float ndcY = 1f - 2f * screenY / camera.viewportHeight;
    Vector3 near = new Vector3(ndcX, ndcY, -1).prj(camera.invProjectionView);
    Vector3 far = new Vector3(ndcX, ndcY, 1).prj(camera.invProjectionView);
    return new Ray(near, far.sub(near).nor());
  }
}
