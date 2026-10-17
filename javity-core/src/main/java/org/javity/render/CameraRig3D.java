package org.javity.render;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import java.util.Objects;
import org.javity.core.GameObject;
import org.javity.presentation.CameraPresentation3D;

/**
 * Per-view FPS/TPP camera placement and near-plane clearance. It never changes player transforms.
 */
public final class CameraRig3D {
  private final Camera3D worldCamera;
  private final PerspectiveCamera viewmodelCamera;
  private final PerspectiveCamera worldCandidate = new PerspectiveCamera(67, 1, 1);
  private final PerspectiveCamera viewmodelCandidate = new PerspectiveCamera(72, 1, 1);
  private final CameraObstructionQuery obstruction;
  private final Vector3 pivot = new Vector3();
  private final Vector3 originalEye = new Vector3();
  private final Vector3 direction = new Vector3();
  private final Vector3 desired = new Vector3();
  private final Vector3 candidatePivot = new Vector3();
  private final Vector3 offsetTarget = new Vector3();
  private final Vector3 candidateDirection = new Vector3();
  private final Vector3 candidateUp = new Vector3();
  private final Vector3 candidateCross = new Vector3();
  private final Vector3 baseDirection = new Vector3();
  private final Vector3 baseUp = new Vector3();
  private final Vector3 baseRight = new Vector3();
  private final Vector3 nextPosition = new Vector3();
  private final Vector3 local = new Vector3();
  private final Vector3 offsetTranslation = new Vector3();
  private final Quaternion offsetRotation = new Quaternion();
  private final Quaternion worldPresentationRotation = new Quaternion();
  private final Quaternion viewmodelPresentationRotation = new Quaternion();
  private final Quaternion providerRotation = new Quaternion();
  private final Vector3 viewmodelDirection = new Vector3();
  private final Vector3 viewmodelUp = new Vector3();
  private final Vector3 viewmodelPosition = new Vector3();
  private final Vector3 rayNearScratch = new Vector3();
  private final Vector3 rayFarScratch = new Vector3();
  private final CameraPresentation3D.Snapshot worldPresentation =
      new CameraPresentation3D.Snapshot();
  private final CameraPresentation3D.Snapshot viewmodelPresentation =
      new CameraPresentation3D.Snapshot();
  private final Matrix4 presentationOffset = new Matrix4();
  private AvatarCameraMode mode = AvatarCameraMode.FIRST_PERSON;
  private float boomDistance = 3.5f;
  private float radius = .16f;
  private float viewmodelFov = 72f;
  private float viewmodelNear = .03f;
  private float worldFov = 67f;
  private float worldNear = .1f;
  private float far = 240f;
  private float clearance = 1f;
  private float presentedWorldFov = 67f;
  private float presentedViewmodelFov = 72f;
  private boolean worldPresentationConstrained;

  public CameraRig3D(
      Camera3D worldCamera, PerspectiveCamera viewmodelCamera, CameraObstructionQuery obstruction) {
    this.worldCamera = Objects.requireNonNull(worldCamera, "worldCamera");
    this.viewmodelCamera = Objects.requireNonNull(viewmodelCamera, "viewmodelCamera");
    this.obstruction = Objects.requireNonNull(obstruction, "obstruction");
    float authoredWorldFov = worldCamera.camera.fieldOfView;
    float authoredViewmodelFov = viewmodelCamera.fieldOfView;
    validateFov(authoredWorldFov, "world camera FOV");
    validateFov(authoredViewmodelFov, "viewmodel camera FOV");
    worldFov = authoredWorldFov;
    viewmodelFov = authoredViewmodelFov;
    presentedWorldFov = worldFov;
    presentedViewmodelFov = viewmodelFov;
    applyProjection();
  }

  public void setMode(AvatarCameraMode mode) {
    this.mode = Objects.requireNonNull(mode, "mode");
  }

  public AvatarCameraMode mode() {
    return mode;
  }

  public PerspectiveCamera viewmodelCamera() {
    return viewmodelCamera;
  }

  public void setBoomDistance(float distance) {
    requirePositive(distance, "distance");
    if (distance > 64) throw new IllegalArgumentException("boom distance exceeds 64 world units");
    boomDistance = distance;
  }

  public float boomDistance() {
    return boomDistance;
  }

  public void setRadius(float radius) {
    requirePositive(radius, "radius");
    if (radius > 4) throw new IllegalArgumentException("camera radius exceeds 4 world units");
    this.radius = radius;
  }

  public float radius() {
    return radius;
  }

  public void setWorldProjection(float fov, float near, float far) {
    validateProjection(fov, near, far);
    if (viewmodelNear >= far) throw new IllegalArgumentException("far must exceed viewmodel near");
    worldFov = fov;
    worldNear = near;
    this.far = far;
    applyProjection();
  }

  public void setViewmodelProjection(float fov, float near) {
    if (!Float.isFinite(fov) || fov <= 1 || fov >= 179)
      throw new IllegalArgumentException("fov must be in (1,179)");
    requirePositive(near, "near");
    if (near >= far) throw new IllegalArgumentException("near must be below far");
    viewmodelFov = fov;
    viewmodelNear = near;
    applyProjection();
  }

  public void resize(int width, int height) {
    if (width <= 0 || height <= 0) throw new IllegalArgumentException("viewport must be positive");
    worldCamera.camera.viewportWidth = width;
    worldCamera.camera.viewportHeight = height;
    viewmodelCamera.viewportWidth = width;
    viewmodelCamera.viewportHeight = height;
    applyProjection();
  }

  /** Copies a camera-local presentation offset; caller owns the supplied vector. */
  public void setPresentationOffset(Vector3 offset) {
    Objects.requireNonNull(offset, "offset");
    if (!Float.isFinite(offset.x) || !Float.isFinite(offset.y) || !Float.isFinite(offset.z))
      throw new IllegalArgumentException("presentation offset must be finite");
    if (offset.len2() > 64f * 64f)
      throw new IllegalArgumentException("presentation offset translation exceeds 64 world units");
    presentationOffset.setToTranslation(offset);
  }

  /** Copies a camera-local presentation matrix; caller owns the supplied matrix. */
  public void setPresentationOffset(Matrix4 offset) {
    Objects.requireNonNull(offset, "offset");
    validateRigid(offset);
    offset.getTranslation(offsetTranslation);
    if (offsetTranslation.len2() > 64f * 64f)
      throw new IllegalArgumentException("presentation offset translation exceeds 64 world units");
    presentationOffset.set(offset);
  }

  public Matrix4 presentationOffset(Matrix4 out) {
    return out.set(presentationOffset);
  }

  public float clearanceFraction() {
    return clearance;
  }

  public float presentedWorldFov() {
    return presentedWorldFov;
  }

  public float presentedViewmodelFov() {
    return presentedViewmodelFov;
  }

  /** True when this update had to discard the requested world presentation to keep clearance. */
  public boolean worldPresentationConstrained() {
    return worldPresentationConstrained;
  }

  /** Positions both cameras from post-simulation eye/look data without writing gameplay state. */
  public void update(Vector3 eye, Vector3 lookDirection, GameObject ignoredOwner) {
    update(eye, lookDirection, ignoredOwner, null, null);
  }

  /**
   * Composes copied per-view contributions before obstruction, projection, drawing and picking.
   * Providers are independent; world presentation never changes the viewmodel provider.
   */
  public void update(
      Vector3 eye,
      Vector3 lookDirection,
      GameObject ignoredOwner,
      CameraPresentation3D worldEffects,
      CameraPresentation3D viewmodelEffects) {
    Objects.requireNonNull(eye, "eye");
    Objects.requireNonNull(lookDirection, "lookDirection");
    GameObject cameraOwner = worldCamera.getGameObject();
    if (cameraOwner != null
        && (!cameraOwner.containsComponent(worldCamera)
            || cameraOwner.isDestroyed()
            || !cameraOwner.isActive()
            || !worldCamera.isEnabled()))
      throw new IllegalStateException("CameraRig3D world camera is no longer active on its view");
    if (!finite(eye) || !finite(lookDirection) || lookDirection.isZero(1e-6f))
      throw new IllegalArgumentException(
          "eye/lookDirection must be finite and look direction nonzero");
    if (worldEffects != null && worldEffects.space() != CameraPresentation3D.Space.WORLD)
      throw new IllegalArgumentException("world camera requires a WORLD presentation provider");
    if (viewmodelEffects != null
        && viewmodelEffects.space() != CameraPresentation3D.Space.VIEWMODEL)
      throw new IllegalArgumentException(
          "viewmodel camera requires a VIEWMODEL presentation provider");
    validateCameraOwner(worldEffects);
    validateCameraOwner(viewmodelEffects);
    if (worldEffects != null) worldEffects.snapshot(worldPresentation);
    else worldPresentation.clear();
    if (viewmodelEffects != null) viewmodelEffects.snapshot(viewmodelPresentation);
    else viewmodelPresentation.clear();
    float nextPresentedWorldFov = clampFov((double) worldFov + worldPresentation.fovDelta());
    float nextPresentedViewmodelFov =
        clampFov((double) viewmodelFov + viewmodelPresentation.fovDelta());
    boolean nextWorldPresentationConstrained = false;
    originalEye.set(eye);
    candidatePivot.set(eye);
    baseDirection.set(lookDirection).nor();
    baseUp.set(Vector3.Y);
    if (Math.abs(baseDirection.dot(baseUp)) > .999f) baseUp.set(Vector3.Z);
    baseRight.set(baseDirection).crs(baseUp).nor();
    baseUp.set(baseRight).crs(baseDirection).nor();
    rotationVector(
        worldPresentation.rotationX(),
        worldPresentation.rotationY(),
        worldPresentation.rotationZ(),
        providerRotation);
    presentationOffset.getRotation(offsetRotation, true);
    worldPresentationRotation.set(offsetRotation).mul(providerRotation);
    presentationOffset.getTranslation(offsetTranslation);
    candidatePivot
        .mulAdd(baseRight, offsetTranslation.x + worldPresentation.translationX())
        .mulAdd(baseUp, offsetTranslation.y + worldPresentation.translationY())
        .mulAdd(baseDirection, -(offsetTranslation.z + worldPresentation.translationZ()));
    local.set(0, 0, -1);
    worldPresentationRotation.transform(local);
    candidateDirection
        .setZero()
        .mulAdd(baseRight, local.x)
        .mulAdd(baseUp, local.y)
        .mulAdd(baseDirection, -local.z)
        .nor();
    local.set(0, 1, 0);
    worldPresentationRotation.transform(local);
    candidateUp
        .setZero()
        .mulAdd(baseRight, local.x)
        .mulAdd(baseUp, local.y)
        .mulAdd(baseDirection, -local.z)
        .nor();
    if (!finite(candidatePivot)
        || !finite(candidateDirection)
        || !finite(candidateUp)
        || candidateDirection.isZero(1e-6f)
        || candidateCross.set(candidateDirection).crs(candidateUp).len2() < 1e-6f)
      throw new IllegalArgumentException("presentation offset produced an invalid camera pose");
    float cameraRadius =
        nearPlaneRadius(
            nextPresentedWorldFov,
            worldNear,
            worldCamera.camera.viewportWidth,
            worldCamera.camera.viewportHeight,
            radius);
    float nextClearance = 1f;
    if (candidatePivot.dst2(originalEye) > 0f) {
      offsetTarget.set(candidatePivot);
      float translationClearance =
          validateFraction(
              obstruction.sweepFraction(originalEye, offsetTarget, cameraRadius, ignoredOwner));
      candidatePivot.set(originalEye).lerp(offsetTarget, translationClearance);
      nextClearance = translationClearance;
    }
    PerspectiveCamera world = worldCamera.camera;
    nextPosition.set(candidatePivot);
    if (mode == AvatarCameraMode.FIRST_PERSON) {
      nextPosition.set(candidatePivot);
    } else {
      desired.set(candidatePivot).mulAdd(candidateDirection, -boomDistance);
      float boomClearance =
          validateFraction(
              obstruction.sweepFraction(candidatePivot, desired, cameraRadius, ignoredOwner));
      nextClearance = Math.min(nextClearance, boomClearance);
      nextPosition.set(candidatePivot).lerp(desired, boomClearance);
    }
    if (worldEffects != null && hasWorldContribution(worldPresentation)) {
      float finalClearance =
          validateFraction(
              obstruction.sweepFraction(nextPosition, nextPosition, cameraRadius, ignoredOwner));
      if (finalClearance < 1f) {
        // Reject this frame's camera contribution atomically. The authored eye/aim and base FOV
        // are the safe fallback; do not publish a wider or rotated camera that starts in a wall.
        nextWorldPresentationConstrained = true;
        nextPresentedWorldFov = worldFov;
        cameraRadius =
            nearPlaneRadius(
                nextPresentedWorldFov,
                worldNear,
                worldCamera.camera.viewportWidth,
                worldCamera.camera.viewportHeight,
                radius);
        candidatePivot.set(originalEye);
        candidateDirection.set(baseDirection);
        candidateUp.set(baseUp);
        nextPosition.set(candidatePivot);
        nextClearance = 1f;
        if (mode == AvatarCameraMode.THIRD_PERSON) {
          desired.set(candidatePivot).mulAdd(candidateDirection, -boomDistance);
          float baseBoomClearance =
              validateFraction(
                  obstruction.sweepFraction(candidatePivot, desired, cameraRadius, ignoredOwner));
          nextClearance = baseBoomClearance;
          nextPosition.set(candidatePivot).lerp(desired, baseBoomClearance);
        }
        float safeBaseClearance =
            validateFraction(
                obstruction.sweepFraction(nextPosition, nextPosition, cameraRadius, ignoredOwner));
        if (safeBaseClearance < 1f)
          throw new IllegalStateException(
              "authored world camera pose is obstructed; no safe presentation fallback exists");
      }
    }
    rotationVector(
        viewmodelPresentation.rotationX(),
        viewmodelPresentation.rotationY(),
        viewmodelPresentation.rotationZ(),
        providerRotation);
    viewmodelPresentationRotation.set(providerRotation);
    viewmodelDirection.set(0, 0, -1);
    viewmodelUp.set(0, 1, 0);
    viewmodelPresentationRotation.transform(viewmodelDirection).nor();
    viewmodelPresentationRotation.transform(viewmodelUp).nor();
    viewmodelPosition.set(
        viewmodelPresentation.translationX(),
        viewmodelPresentation.translationY(),
        viewmodelPresentation.translationZ());
    prepareCamera(
        worldCandidate,
        world,
        nextPresentedWorldFov,
        worldNear,
        far,
        nextPosition,
        candidateDirection,
        candidateUp);
    prepareCamera(
        viewmodelCandidate,
        viewmodelCamera,
        nextPresentedViewmodelFov,
        viewmodelNear,
        far,
        viewmodelPosition,
        viewmodelDirection,
        viewmodelUp);

    commitCamera(world, worldCandidate);
    commitCamera(viewmodelCamera, viewmodelCandidate);
    pivot.set(candidatePivot);
    direction.set(candidateDirection);
    clearance = nextClearance;
    presentedWorldFov = nextPresentedWorldFov;
    presentedViewmodelFov = nextPresentedViewmodelFov;
    worldPresentationConstrained = nextWorldPresentationConstrained;
  }

  private void prepareCamera(
      PerspectiveCamera candidate,
      PerspectiveCamera authored,
      float fov,
      float near,
      float far,
      Vector3 position,
      Vector3 direction,
      Vector3 up) {
    candidate.viewportWidth = authored.viewportWidth;
    candidate.viewportHeight = authored.viewportHeight;
    candidate.fieldOfView = fov;
    candidate.near = near;
    candidate.far = far;
    candidate.position.set(position);
    candidate.direction.set(direction);
    candidate.up.set(up);
    candidate.update();
    validateCamera(candidate);
  }

  private static void commitCamera(PerspectiveCamera target, PerspectiveCamera candidate) {
    target.viewportWidth = candidate.viewportWidth;
    target.viewportHeight = candidate.viewportHeight;
    target.fieldOfView = candidate.fieldOfView;
    target.near = candidate.near;
    target.far = candidate.far;
    target.position.set(candidate.position);
    target.direction.set(candidate.direction);
    target.up.set(candidate.up);
    target.projection.set(candidate.projection);
    target.view.set(candidate.view);
    target.combined.set(candidate.combined);
    target.invProjectionView.set(candidate.invProjectionView);
    target.frustum.update(target.invProjectionView);
  }

  private void validateCamera(PerspectiveCamera camera) {
    if (!finite(camera.position)
        || !finite(camera.direction)
        || !finite(camera.up)
        || !Float.isFinite(camera.fieldOfView)
        || !Float.isFinite(camera.near)
        || !Float.isFinite(camera.far)
        || !Float.isFinite(camera.viewportWidth)
        || !Float.isFinite(camera.viewportHeight)
        || camera.viewportWidth <= 0
        || camera.viewportHeight <= 0)
      throw new IllegalArgumentException("presented camera pose/projection is not representable");
    requireFinite(camera.projection, "presented camera projection matrix is not finite");
    requireFinite(camera.view, "presented camera view matrix is not finite");
    requireFinite(camera.combined, "presented camera combined matrix is not finite");
    requireFinite(camera.invProjectionView, "presented camera inverse matrix is not finite");
    for (var plane : camera.frustum.planes)
      if (!finite(plane.normal) || !Float.isFinite(plane.d))
        throw new IllegalArgumentException("presented camera frustum is not finite");
    validatePickRay(camera, camera.viewportWidth * .5f, camera.viewportHeight * .5f);
    validatePickRay(camera, 0, 0);
  }

  private void validatePickRay(PerspectiveCamera camera, float screenX, float screenY) {
    float ndcX = 2f * screenX / camera.viewportWidth - 1f;
    float ndcY = 1f - 2f * screenY / camera.viewportHeight;
    rayNearScratch.set(ndcX, ndcY, -1).prj(camera.invProjectionView);
    rayFarScratch.set(ndcX, ndcY, 1).prj(camera.invProjectionView);
    if (!finite(rayNearScratch) || !finite(rayFarScratch))
      throw new IllegalArgumentException("presented camera picking ray origin is not finite");
    double dx = (double) rayFarScratch.x - rayNearScratch.x;
    double dy = (double) rayFarScratch.y - rayNearScratch.y;
    double dz = (double) rayFarScratch.z - rayNearScratch.z;
    double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (!Double.isFinite(length)
        || length < 1e-8
        || !Double.isFinite(dx / length)
        || !Double.isFinite(dy / length)
        || !Double.isFinite(dz / length))
      throw new IllegalArgumentException("presented camera picking ray direction is not finite");
  }

  private static void requireFinite(Matrix4 matrix, String message) {
    for (float component : matrix.val)
      if (!Float.isFinite(component)) throw new IllegalArgumentException(message);
  }

  private void validateCameraOwner(CameraPresentation3D provider) {
    if (provider == null || worldCamera.getGameObject() == null) return;
    if (provider.getGameObject() != worldCamera.getGameObject())
      throw new IllegalArgumentException("camera presentation provider must belong to this view");
  }

  private static Quaternion rotationVector(float x, float y, float z, Quaternion out) {
    double angle = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
    if (!Double.isFinite(angle))
      throw new IllegalArgumentException("camera rotation is not finite");
    if (angle < 1e-8)
      return out.set((float) (x * .5), (float) (y * .5), (float) (z * .5), 1f).nor();
    double half = angle * .5, factor = Math.sin(half) / angle;
    return out.set(
            (float) (x * factor),
            (float) (y * factor),
            (float) (z * factor),
            (float) Math.cos(half))
        .nor();
  }

  private static float clampFov(double fov) {
    if (!Double.isFinite(fov)) throw new IllegalArgumentException("presented FOV must be finite");
    return (float) Math.max(2d, Math.min(178d, fov));
  }

  private static boolean hasWorldContribution(CameraPresentation3D.Snapshot pose) {
    return pose.translationX() != 0
        || pose.translationY() != 0
        || pose.translationZ() != 0
        || pose.rotationX() != 0
        || pose.rotationY() != 0
        || pose.rotationZ() != 0
        || pose.fovDelta() != 0;
  }

  private static float nearPlaneRadius(
      float fov, float near, float width, float height, float padding) {
    float aspect = Math.max(1f, width) / Math.max(1f, height);
    float halfHeight = MathUtils.tan(fov * MathUtils.degreesToRadians * .5f) * near;
    float halfWidth = halfHeight * aspect;
    float cornerDistance =
        (float) Math.sqrt(near * near + halfWidth * halfWidth + halfHeight * halfHeight);
    return Math.max(padding, cornerDistance);
  }

  private static void validateRigid(Matrix4 value) {
    float[] m = value.val;
    for (float component : m)
      if (!Float.isFinite(component))
        throw new IllegalArgumentException("presentation offset must be finite");
    if (Math.abs(m[Matrix4.M30]) > 1e-5f
        || Math.abs(m[Matrix4.M31]) > 1e-5f
        || Math.abs(m[Matrix4.M32]) > 1e-5f
        || Math.abs(m[Matrix4.M33] - 1f) > 1e-5f)
      throw new IllegalArgumentException("presentation offset must be an affine rigid transform");
    Vector3 x = new Vector3(m[Matrix4.M00], m[Matrix4.M10], m[Matrix4.M20]);
    Vector3 y = new Vector3(m[Matrix4.M01], m[Matrix4.M11], m[Matrix4.M21]);
    Vector3 z = new Vector3(m[Matrix4.M02], m[Matrix4.M12], m[Matrix4.M22]);
    if (Math.abs(x.len2() - 1) > 1e-4f
        || Math.abs(y.len2() - 1) > 1e-4f
        || Math.abs(z.len2() - 1) > 1e-4f
        || Math.abs(x.dot(y)) > 1e-4f
        || Math.abs(x.dot(z)) > 1e-4f
        || Math.abs(y.dot(z)) > 1e-4f
        || x.crs(y).dot(z) < .999f)
      throw new IllegalArgumentException("presentation offset basis must be rigid and nonsingular");
  }

  private static float validateFraction(float value) {
    if (!Float.isFinite(value) || value < 0 || value > 1)
      throw new IllegalArgumentException("camera obstruction fraction must be finite and in [0,1]");
    return value;
  }

  private void applyProjection() {
    worldCamera.camera.fieldOfView = worldFov;
    worldCamera.camera.near = worldNear;
    worldCamera.camera.far = far;
    viewmodelCamera.fieldOfView = viewmodelFov;
    viewmodelCamera.near = viewmodelNear;
    viewmodelCamera.far = far;
    worldCamera.camera.update();
    viewmodelCamera.update();
  }

  private static void validateProjection(float fov, float near, float far) {
    validateFov(fov, "fov");
    requirePositive(near, "near");
    if (!Float.isFinite(far) || far <= near)
      throw new IllegalArgumentException("far must exceed near");
  }

  private static void validateFov(float fov, String name) {
    if (!Float.isFinite(fov) || fov <= 1 || fov >= 179)
      throw new IllegalArgumentException(name + " must be finite and in (1,179)");
  }

  private static void requirePositive(float value, String name) {
    if (!Float.isFinite(value) || value <= 0)
      throw new IllegalArgumentException(name + " must be finite and positive");
  }

  private static boolean finite(Vector3 v) {
    return Float.isFinite(v.x) && Float.isFinite(v.y) && Float.isFinite(v.z);
  }
}
