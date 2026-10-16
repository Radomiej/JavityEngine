package org.javity.ui.inspector;

import com.badlogic.gdx.math.*;
import java.util.*;
import org.javity.core.*;

/**
 * Scene snapshots and typed transform edits. Selection and edit history are scoped to a Game
 * instance.
 */
public final class SceneInspection {
  private static final int HISTORY_LIMIT = 256;

  public enum Transform2DField {
    POSITION_X,
    POSITION_Y,
    ROTATION,
    SCALE_X,
    SCALE_Y
  }

  public enum Transform3DField {
    POSITION_X,
    POSITION_Y,
    POSITION_Z,
    ROTATION_X,
    ROTATION_Y,
    ROTATION_Z,
    ROTATION_W,
    SCALE_X,
    SCALE_Y,
    SCALE_Z
  }

  private enum EditKind {
    TRANSFORM_2D,
    TRANSFORM_3D,
    ROTATION_3D,
    TRANSFORM_2D_FULL,
    TRANSFORM_3D_FULL
  }

  /** Immutable raw local transform snapshot used by gizmo previews and atomic commits. */
  public record Transform3DState(
      float x,
      float y,
      float z,
      float qx,
      float qy,
      float qz,
      float qw,
      float scaleX,
      float scaleY,
      float scaleZ) {
    public Transform3DState {
      float[] values = {x, y, z, qx, qy, qz, qw, scaleX, scaleY, scaleZ};
      for (float value : values) requireFinite(value);
      float length2 = qx * qx + qy * qy + qz * qz + qw * qw;
      if (!Float.isFinite(length2) || length2 <= 1e-12f)
        throw new IllegalArgumentException("Rotation quaternion must have nonzero finite length");
    }
  }

  /** Immutable full local 2D transform used by gizmo previews and atomic commits. */
  public record Transform2DState(float x, float y, float rotation, float scaleX, float scaleY) {
    public Transform2DState {
      requireFinite(x);
      requireFinite(y);
      requireFinite(rotation);
      requireFinite(scaleX);
      requireFinite(scaleY);
    }
  }

  private record Edit(
      Game scene,
      GameObject object,
      Transform3D component,
      EditKind kind,
      Enum<?> field,
      float[] before,
      float[] after) {}

  /** Opaque identity/baseline token for a drag that spans multiple input frames. */
  public static final class Transform3DEditSession {
    private final Game scene;
    private final GameObject object;
    private final Transform3D component;
    private final Transform3D parent;
    private final float[] parentMatrix;
    private final Transform3DState before;
    private final long selectionEpoch;

    private Transform3DEditSession(
        Game scene,
        GameObject object,
        Transform3D component,
        Transform3D parent,
        float[] parentMatrix,
        Transform3DState before,
        long selectionEpoch) {
      this.scene = scene;
      this.object = object;
      this.component = component;
      this.parent = parent;
      this.parentMatrix = parentMatrix;
      this.before = before;
      this.selectionEpoch = selectionEpoch;
    }
  }

  public static final class Transform2DEditSession {
    private final Game scene;
    private final GameObject object;
    private final Transform2DState before;
    private final long selectionEpoch;

    private Transform2DEditSession(
        Game scene, GameObject object, Transform2DState before, long selectionEpoch) {
      this.scene = scene;
      this.object = object;
      this.before = before;
      this.selectionEpoch = selectionEpoch;
    }
  }

  public record Row(int id, String name, boolean active, int depth, int components) {}

  public record Snapshot(
      List<Row> objects, Integer selectedId, List<String> details, String status) {
    public Snapshot {
      objects = List.copyOf(objects);
      details = List.copyOf(details);
    }
  }

  private Game scene;
  private Integer selectedId;
  private long selectionEpoch;
  private final Deque<Edit> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();

  public void setScene(Game value) {
    if (scene != value) {
      scene = value;
      selectedId = null;
      selectionEpoch++;
      undo.clear();
      redo.clear();
    }
  }

  /** Whether the selected object's transform is available to inspector manipulation. */
  public boolean selectedTransformEditable() {
    if (scene == null || scene.isDisposed() || selectedId == null) return false;
    for (GameObject object : scene.getObjects())
      if (object.id == selectedId && !object.isDestroyed()) return allowsTransformEdit(object);
    return false;
  }

  /** Applies one validated scalar edit to the selected object's embedded 2D transform. */
  public void edit(Transform2DField field, float value) {
    Objects.requireNonNull(field);
    requireFinite(value);
    GameObject object = requireSelectedObject();
    requireEditable(object);
    float before = read(object, field);
    if (Float.compare(before, value) == 0) return;
    push(
        new Edit(
            scene,
            object,
            null,
            EditKind.TRANSFORM_2D,
            field,
            new float[] {before},
            new float[] {value}));
    write(object, field, value);
  }

  /** Replaces the five Transform2D values as one undoable edit. */
  public void editTransform2D(float x, float y, float rotation, float scaleX, float scaleY) {
    float[] values = {x, y, rotation, scaleX, scaleY};
    for (float value : values) requireFinite(value);
    GameObject object = requireSelectedObject();
    requireEditable(object);
    float[] before = {
      object.transform.position.x,
      object.transform.position.y,
      object.transform.rotation,
      object.transform.scale.x,
      object.transform.scale.y
    };
    if (Arrays.equals(before, values)) return;
    push(new Edit(scene, object, null, EditKind.TRANSFORM_2D, null, before, values));
    writeTransform2D(object, values);
  }

  /** Applies one validated position or scale edit to the selected object's Transform3D. */
  public void edit(Transform3DField field, float value) {
    Objects.requireNonNull(field);
    requireFinite(value);
    GameObject object = requireSelectedObject();
    requireEditable(object);
    Transform3D transform = object.getComponent(Transform3D.class);
    if (transform == null) throw new IllegalStateException("Selected object has no Transform3D");
    if (isRotation(field)) {
      float[] before = quaternion(transform), after = quaternion(transform);
      after[rotationIndex(field)] = value;
      normalize(after);
      if (Arrays.equals(before, after)) return;
      push(new Edit(scene, object, transform, EditKind.ROTATION_3D, field, before, after));
      writeRotation(transform, after);
    } else {
      float before = read(transform, field);
      if (Float.compare(before, value) == 0) return;
      push(
          new Edit(
              scene,
              object,
              transform,
              EditKind.TRANSFORM_3D,
              field,
              new float[] {before},
              new float[] {value}));
      write(transform, field, value);
    }
  }

  /** Sets a complete quaternion atomically; zero length and non-finite values are rejected. */
  public void editRotation3D(float x, float y, float z, float w) {
    requireFinite(x);
    requireFinite(y);
    requireFinite(z);
    requireFinite(w);
    float[] value = {x, y, z, w};
    normalize(value);
    GameObject object = requireSelectedObject();
    requireEditable(object);
    Transform3D transform = object.getComponent(Transform3D.class);
    if (transform == null) throw new IllegalStateException("Selected object has no Transform3D");
    float[] before = quaternion(transform);
    if (Arrays.equals(before, value)) return;
    push(new Edit(scene, object, transform, EditKind.ROTATION_3D, null, before, value));
    writeRotation(transform, value);
  }

  public Transform3DState selectedTransform3D() {
    Transform3D transform = selectedTransform3DComponent();
    return state(transform);
  }

  public Transform3DEditSession beginTransform3DEdit() {
    GameObject object = requireSelectedObject();
    Transform3D component = selectedTransform3DComponent();
    Transform3D parent = component.getParent();
    return new Transform3DEditSession(
        scene,
        object,
        component,
        parent,
        parent == null ? null : Arrays.copyOf(parent.worldMatrix().val, 16),
        state(component),
        selectionEpoch);
  }

  public void commitTransform3D(Transform3DEditSession session, Transform3DState next) {
    Objects.requireNonNull(session);
    Objects.requireNonNull(next);
    if (session.scene != scene
        || session.selectionEpoch != selectionEpoch
        || session.object != requireSelectedObject()
        || !session.object.containsComponent(session.component)
        || session.object.getComponent(Transform3D.class) != session.component)
      throw new IllegalStateException("Object or Transform3D changed during gizmo drag");
    if (session.component.getParent() != session.parent)
      throw new IllegalStateException("Parent changed during gizmo drag");
    if (session.parent != null
        && !Arrays.equals(session.parentMatrix, session.parent.worldMatrix().val))
      throw new IllegalStateException("Parent transform changed during gizmo drag");
    if (!Arrays.equals(values(session.before), values(state(session.component))))
      throw new IllegalStateException("Transform changed during gizmo drag");
    commitTransform3D(session.before, next);
  }

  public Transform2DEditSession beginTransform2DEdit() {
    GameObject object = requireSelectedObject();
    return new Transform2DEditSession(scene, object, selectedTransform2D(), selectionEpoch);
  }

  public void commitTransform2D(Transform2DEditSession session, Transform2DState next) {
    Objects.requireNonNull(session);
    Objects.requireNonNull(next);
    if (session.scene != scene
        || session.selectionEpoch != selectionEpoch
        || session.object != requireSelectedObject())
      throw new IllegalStateException("Object changed during gizmo drag");
    commitTransform2D(session.before, next);
  }

  public Transform2DState selectedTransform2D() {
    GameObject object = requireSelectedObject();
    requireEditable(object);
    return new Transform2DState(
        object.transform.position.x,
        object.transform.position.y,
        object.transform.rotation,
        object.transform.scale.x,
        object.transform.scale.y);
  }

  /** Atomically records and applies one full 3D transform if the baseline is still current. */
  public void commitTransform3D(Transform3DState expected, Transform3DState next) {
    Objects.requireNonNull(expected);
    Objects.requireNonNull(next);
    GameObject object = requireSelectedObject();
    requireEditable(object);
    Transform3D transform = selectedTransform3DComponent();
    float[] before = values(expected), actual = values(state(transform)), after = values(next);
    if (!Arrays.equals(before, actual))
      throw new IllegalStateException("Transform changed during gizmo drag");
    if (Float.compare(before[3], after[3]) != 0
        || Float.compare(before[4], after[4]) != 0
        || Float.compare(before[5], after[5]) != 0
        || Float.compare(before[6], after[6]) != 0) {
      float[] rotation = Arrays.copyOfRange(after, 3, 7);
      normalize(rotation);
      System.arraycopy(rotation, 0, after, 3, rotation.length);
    }
    if (Arrays.equals(before, after)) return;
    push(new Edit(scene, object, transform, EditKind.TRANSFORM_3D_FULL, null, before, after));
    writeTransform3D(transform, after);
  }

  /** Atomically records and applies one full 2D transform if the baseline is still current. */
  public void commitTransform2D(Transform2DState expected, Transform2DState next) {
    Objects.requireNonNull(expected);
    Objects.requireNonNull(next);
    GameObject object = requireSelectedObject();
    requireEditable(object);
    float[] before = values(expected), actual = values(selectedTransform2D()), after = values(next);
    if (!Arrays.equals(before, actual))
      throw new IllegalStateException("Transform changed during gizmo drag");
    if (Arrays.equals(before, after)) return;
    push(new Edit(scene, object, null, EditKind.TRANSFORM_2D_FULL, null, before, after));
    writeTransform2D(object, after);
  }

  /** World/local axis translation with optional positive-step snapping; one history command. */
  public void translateSelected3D(Vector3 delta, boolean localSpace, float snapStep) {
    Objects.requireNonNull(delta);
    requireFinite(delta.x);
    requireFinite(delta.y);
    requireFinite(delta.z);
    requireSnapStep(snapStep);
    GameObject object = requireSelectedObject();
    Transform3D t = selectedTransform3DComponent();
    Transform3DState before = state(t);
    Transform3DState next =
        translatedTransform3D(before, t.getParent(), delta, localSpace, snapStep);
    commitTransform3D(before, next);
  }

  /** Pure transform calculation shared by UI drag preview/commit and typed operation tests. */
  public static Transform3DState translatedTransform3D(
      Transform3DState before,
      Transform3D parent,
      Vector3 delta,
      boolean localSpace,
      float snapStep) {
    Objects.requireNonNull(before);
    Objects.requireNonNull(delta);
    requireFinite(delta.x);
    requireFinite(delta.y);
    requireFinite(delta.z);
    requireSnapStep(snapStep);
    Vector3 localTarget;
    Vector3 snappedDelta =
        new Vector3(snap(delta.x, snapStep), snap(delta.y, snapStep), snap(delta.z, snapStep));
    if (localSpace) {
      Vector3 parentSpaceDelta =
          new Quaternion(before.qx(), before.qy(), before.qz(), before.qw())
              .transform(snappedDelta);
      localTarget = new Vector3(before.x(), before.y(), before.z()).add(parentSpaceDelta);
    } else {
      Vector3 worldPosition = new Vector3(before.x(), before.y(), before.z());
      if (parent != null) worldPosition.mul(parent.worldMatrix());
      worldPosition.add(snappedDelta);
      if (parent != null) {
        Matrix4 parentWorld = parent.worldMatrix().cpy();
        float determinant = parentWorld.det();
        if (!Float.isFinite(determinant) || Math.abs(determinant) < 1e-8f)
          throw new IllegalStateException("Cannot translate through a singular parent transform");
        parentWorld.inv();
        localTarget = worldPosition.mul(parentWorld);
      } else localTarget = worldPosition;
    }
    return new Transform3DState(
        localTarget.x,
        localTarget.y,
        localTarget.z,
        before.qx(),
        before.qy(),
        before.qz(),
        before.qw(),
        before.scaleX(),
        before.scaleY(),
        before.scaleZ());
  }

  /**
   * Applies a local-axis rotation. World rotation below a parent is rejected as non-representable
   * in general.
   */
  public void rotateSelected3D(Vector3 axis, float radians, boolean localSpace, float snapStep) {
    Objects.requireNonNull(axis);
    requireFinite(radians);
    requireSnapStep(snapStep);
    if (!Float.isFinite(axis.len2()) || axis.len2() <= 1e-12f)
      throw new IllegalArgumentException("Rotation axis must be nonzero");
    Transform3D t = selectedTransform3DComponent();
    if (!localSpace && t.getParent() != null)
      throw new IllegalStateException(
          "World rotation below a parent is unsupported; use local space");
    Transform3DState before = state(t);
    Quaternion delta =
        new Quaternion(axis.cpy().nor(), snap(radians, snapStep) * MathUtils.radiansToDegrees);
    Quaternion after = new Quaternion(before.qx(), before.qy(), before.qz(), before.qw());
    if (localSpace) after.mul(delta);
    else after.mulLeft(delta);
    commitTransform3D(
        before,
        new Transform3DState(
            before.x(),
            before.y(),
            before.z(),
            after.x,
            after.y,
            after.z,
            after.w,
            before.scaleX(),
            before.scaleY(),
            before.scaleZ()));
  }

  /**
   * Local-axis scale. World scale beneath parents is rejected rather than silently approximated.
   */
  public void scaleSelected3D(Vector3 factors, boolean localSpace, float snapStep) {
    Objects.requireNonNull(factors);
    requireFinite(factors.x);
    requireFinite(factors.y);
    requireFinite(factors.z);
    requireSnapStep(snapStep);
    Transform3D t = selectedTransform3DComponent();
    if (!localSpace && (t.getParent() != null || !isIdentityRotation(t.rotation)))
      throw new IllegalStateException(
          "World scale under a parent or rotated object may introduce shear; use local space");
    Transform3DState before = state(t);
    commitTransform3D(
        before,
        new Transform3DState(
            before.x(),
            before.y(),
            before.z(),
            before.qx(),
            before.qy(),
            before.qz(),
            before.qw(),
            before.scaleX() * (1 + snap(factors.x - 1, snapStep)),
            before.scaleY() * (1 + snap(factors.y - 1, snapStep)),
            before.scaleZ() * (1 + snap(factors.z - 1, snapStep))));
  }

  /** Applies a screen-independent 2D translation/rotation/scale operation as one edit. */
  public void manipulateSelected2D(
      float dx, float dy, float radians, float sx, float sy, float snapStep) {
    requireFinite(dx);
    requireFinite(dy);
    requireFinite(radians);
    requireFinite(sx);
    requireFinite(sy);
    requireSnapStep(snapStep);
    Transform2DState before = selectedTransform2D();
    commitTransform2D(
        before,
        new Transform2DState(
            before.x() + snap(dx, snapStep),
            before.y() + snap(dy, snapStep),
            before.rotation() + snap(radians, snapStep),
            before.scaleX() * (1 + snap(sx - 1, snapStep)),
            before.scaleY() * (1 + snap(sy - 1, snapStep))));
  }

  /** Computes a snapped 2D scale change in local or world axes without introducing shear. */
  public static Transform2DState scaledTransform2D(
      Transform2DState before, int axis, float factor, boolean localSpace, float snapStep) {
    Objects.requireNonNull(before);
    if (axis < 0 || axis > 1) throw new IllegalArgumentException("2D scale axis must be X or Y");
    requireFinite(factor);
    requireSnapStep(snapStep);
    float snappedFactor = 1 + snap(factor - 1, snapStep);
    float scaleX = before.scaleX(), scaleY = before.scaleY();
    if (localSpace) {
      if (axis == 0) scaleX *= snappedFactor;
      else scaleY *= snappedFactor;
    } else {
      double quarterTurns = before.rotation() / (Math.PI / 2);
      if (Math.abs(quarterTurns - Math.rint(quarterTurns)) > 1e-5)
        throw new IllegalStateException(
            "World scale on a rotated 2D object may introduce shear; choose LOCAL");
      boolean swapsAxes = (Math.round(quarterTurns) & 1) != 0;
      if ((axis == 0) != swapsAxes) scaleX *= snappedFactor;
      else scaleY *= snappedFactor;
    }
    return new Transform2DState(before.x(), before.y(), before.rotation(), scaleX, scaleY);
  }

  private static void requireSnapStep(float value) {
    if (!Float.isFinite(value) || value < 0)
      throw new IllegalArgumentException(
          "Snap step must be finite and nonnegative (0 disables snapping)");
  }

  private static float snap(float value, float step) {
    return step == 0 ? value : (float) Math.floor(value / step + .5f) * step;
  }

  private static boolean isIdentityRotation(Quaternion q) {
    return Math.abs(Math.abs(q.w) - 1f) < 1e-5f
        && Math.abs(q.x) < 1e-5f
        && Math.abs(q.y) < 1e-5f
        && Math.abs(q.z) < 1e-5f;
  }

  private Transform3D selectedTransform3DComponent() {
    GameObject object = requireSelectedObject();
    requireEditable(object);
    Transform3D transform = object.getComponent(Transform3D.class);
    if (transform == null) throw new IllegalStateException("Selected object has no Transform3D");
    return transform;
  }

  private static Transform3DState state(Transform3D t) {
    return new Transform3DState(
        t.position.x,
        t.position.y,
        t.position.z,
        t.rotation.x,
        t.rotation.y,
        t.rotation.z,
        t.rotation.w,
        t.scale.x,
        t.scale.y,
        t.scale.z);
  }

  private static float[] values(Transform3DState s) {
    return new float[] {
      s.x(), s.y(), s.z(), s.qx(), s.qy(), s.qz(), s.qw(), s.scaleX(), s.scaleY(), s.scaleZ()
    };
  }

  private static float[] values(Transform2DState s) {
    return new float[] {s.x(), s.y(), s.rotation(), s.scaleX(), s.scaleY()};
  }

  public boolean canUndo() {
    return !undo.isEmpty();
  }

  public boolean canRedo() {
    return !redo.isEmpty();
  }

  public boolean undo() {
    return move(undo, redo, false);
  }

  public boolean redo() {
    return move(redo, undo, true);
  }

  private boolean move(Deque<Edit> source, Deque<Edit> destination, boolean forward) {
    if (source.isEmpty()) return false;
    Edit edit = source.removeLast();
    boolean live = isLive(edit);
    boolean matches = matches(edit, forward ? edit.before() : edit.after());
    if (!live || !matches) {
      undo.clear();
      redo.clear();
      return false;
    }
    apply(edit, forward);
    destination.addLast(edit);
    return true;
  }

  private void push(Edit edit) {
    undo.addLast(edit);
    if (undo.size() > HISTORY_LIMIT) undo.removeFirst();
    redo.clear();
  }

  private static boolean matches(Edit edit, float[] expected) {
    float[] actual;
    switch (edit.kind()) {
      case TRANSFORM_2D -> {
        if (edit.field() != null)
          actual = new float[] {read(edit.object(), (Transform2DField) edit.field())};
        else
          actual =
              new float[] {
                edit.object().transform.position.x,
                edit.object().transform.position.y,
                edit.object().transform.rotation,
                edit.object().transform.scale.x,
                edit.object().transform.scale.y
              };
      }
      case TRANSFORM_3D ->
          actual = new float[] {read(edit.component(), (Transform3DField) edit.field())};
      case ROTATION_3D -> actual = quaternion(edit.component());
      case TRANSFORM_2D_FULL ->
          actual =
              new float[] {
                edit.object().transform.position.x,
                edit.object().transform.position.y,
                edit.object().transform.rotation,
                edit.object().transform.scale.x,
                edit.object().transform.scale.y
              };
      case TRANSFORM_3D_FULL -> actual = values(state(edit.component()));
      default -> throw new AssertionError(edit.kind());
    }
    return Arrays.equals(actual, expected);
  }

  private boolean isLive(Edit edit) {
    if (scene != edit.scene() || scene == null || scene.isDisposed() || edit.object().isDestroyed())
      return false;
    boolean present = false;
    for (GameObject candidate : scene.getObjects())
      if (candidate == edit.object()) {
        present = true;
        break;
      }
    return present
        && (edit.component() == null || edit.object().containsComponent(edit.component()))
        && allowsTransformEdit(edit.object());
  }

  private GameObject requireSelectedObject() {
    if (scene == null || scene.isDisposed() || selectedId == null)
      throw new IllegalStateException("Select an object in a live scene first");
    for (GameObject object : scene.getObjects())
      if (object.id == selectedId && !object.isDestroyed()) return object;
    selectedId = null;
    throw new IllegalStateException("Selected object is no longer in the scene");
  }

  private static void requireEditable(GameObject object) {
    if (!allowsTransformEdit(object))
      throw new IllegalStateException("A component owns this object's transform");
  }

  private static boolean allowsTransformEdit(GameObject object) {
    for (Component component : object.getComponents())
      if (component instanceof TransformEditPolicy policy && !policy.allowsInspectorTransformEdit())
        return false;
    return true;
  }

  private static void requireFinite(float value) {
    if (!Float.isFinite(value))
      throw new IllegalArgumentException("Transform values must be finite");
  }

  private static float read(GameObject object, Transform2DField field) {
    return switch (field) {
      case POSITION_X -> object.transform.position.x;
      case POSITION_Y -> object.transform.position.y;
      case ROTATION -> object.transform.rotation;
      case SCALE_X -> object.transform.scale.x;
      case SCALE_Y -> object.transform.scale.y;
    };
  }

  private static void write(GameObject object, Transform2DField field, float value) {
    switch (field) {
      case POSITION_X -> object.transform.position.x = value;
      case POSITION_Y -> object.transform.position.y = value;
      case ROTATION -> object.transform.rotation = value;
      case SCALE_X -> object.transform.scale.x = value;
      case SCALE_Y -> object.transform.scale.y = value;
    }
  }

  private static float read(Transform3D transform, Transform3DField field) {
    return switch (field) {
      case POSITION_X -> transform.position.x;
      case POSITION_Y -> transform.position.y;
      case POSITION_Z -> transform.position.z;
      case ROTATION_X, ROTATION_Y, ROTATION_Z, ROTATION_W ->
          throw new IllegalArgumentException("Use rotation edit");
      case SCALE_X -> transform.scale.x;
      case SCALE_Y -> transform.scale.y;
      case SCALE_Z -> transform.scale.z;
    };
  }

  private static void write(Transform3D transform, Transform3DField field, float value) {
    switch (field) {
      case POSITION_X -> transform.position.x = value;
      case POSITION_Y -> transform.position.y = value;
      case POSITION_Z -> transform.position.z = value;
      case ROTATION_X, ROTATION_Y, ROTATION_Z, ROTATION_W ->
          throw new IllegalArgumentException("Use rotation edit");
      case SCALE_X -> transform.scale.x = value;
      case SCALE_Y -> transform.scale.y = value;
      case SCALE_Z -> transform.scale.z = value;
    }
  }

  private static void writeRotation(Transform3D transform, float[] q) {
    transform.rotation.set(q[0], q[1], q[2], q[3]);
  }

  private static boolean isRotation(Transform3DField field) {
    return field == Transform3DField.ROTATION_X
        || field == Transform3DField.ROTATION_Y
        || field == Transform3DField.ROTATION_Z
        || field == Transform3DField.ROTATION_W;
  }

  private static int rotationIndex(Transform3DField field) {
    return switch (field) {
      case ROTATION_X -> 0;
      case ROTATION_Y -> 1;
      case ROTATION_Z -> 2;
      case ROTATION_W -> 3;
      default -> throw new IllegalArgumentException("Not a quaternion field");
    };
  }

  private static float[] quaternion(Transform3D transform) {
    return new float[] {
      transform.rotation.x, transform.rotation.y, transform.rotation.z, transform.rotation.w
    };
  }

  private static void normalize(float[] q) {
    float length2 = q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3];
    if (!Float.isFinite(length2) || length2 <= 1e-12f)
      throw new IllegalArgumentException("Rotation quaternion must have nonzero finite length");
    float inverseLength = 1f / (float) Math.sqrt(length2);
    for (int i = 0; i < q.length; i++) q[i] *= inverseLength;
  }

  private static void apply(Edit edit, boolean forward) {
    float[] values = forward ? edit.after() : edit.before();
    switch (edit.kind()) {
      case TRANSFORM_2D -> {
        if (edit.field() == null) writeTransform2D(edit.object(), values);
        else write(edit.object(), (Transform2DField) edit.field(), values[0]);
      }
      case TRANSFORM_3D -> write(edit.component(), (Transform3DField) edit.field(), values[0]);
      case ROTATION_3D -> writeRotation(edit.component(), values);
      case TRANSFORM_2D_FULL -> writeTransform2D(edit.object(), values);
      case TRANSFORM_3D_FULL -> writeTransform3D(edit.component(), values);
    }
  }

  private static void writeTransform2D(GameObject object, float[] values) {
    object.transform.position.set(values[0], values[1]);
    object.transform.rotation = values[2];
    object.transform.scale.set(values[3], values[4]);
  }

  private static void writeTransform3D(Transform3D t, float[] v) {
    t.position.set(v[0], v[1], v[2]);
    t.rotation.set(v[3], v[4], v[5], v[6]);
    t.scale.set(v[7], v[8], v[9]);
  }

  public boolean select(int id) {
    if (scene == null || scene.isDisposed()) return false;
    for (GameObject object : scene.getObjects())
      if (object.id == id) {
        if (!Objects.equals(selectedId, id)) selectionEpoch++;
        selectedId = id;
        return true;
      }
    return false;
  }

  /** Pure tree projection: search retains ancestors; collapsed parents still hide descendants. */
  public List<Row> visibleRows(String query, Set<Integer> collapsed) {
    Objects.requireNonNull(collapsed);
    List<Row> all = capture().objects();
    String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    Map<Integer, GameObject> objects = new HashMap<>();
    if (scene != null && !scene.isDisposed())
      for (GameObject object : scene.getObjects()) objects.put(object.id, object);
    Set<Integer> included = new HashSet<>();
    Deque<Row> ancestors = new ArrayDeque<>();
    for (Row row : all) {
      while (!ancestors.isEmpty() && ancestors.peekLast().depth() >= row.depth())
        ancestors.removeLast();
      GameObject object = objects.get(row.id());
      boolean matches = needle.isEmpty()
          || row.name().toLowerCase(Locale.ROOT).contains(needle)
          || Integer.toString(row.id()).contains(needle)
          || (object != null && object.getTags().stream()
              .anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(needle)));
      if (matches) {
        included.add(row.id());
        for (Row ancestor : ancestors) included.add(ancestor.id());
      }
      ancestors.addLast(row);
    }
    List<Row> visible = new ArrayList<>();
    int hiddenBelow = Integer.MAX_VALUE;
    for (Row row : all) {
      if (row.depth() > hiddenBelow) continue;
      hiddenBelow = Integer.MAX_VALUE;
      if (included.contains(row.id())) visible.add(row);
      if (collapsed.contains(row.id())) hiddenBelow = row.depth();
    }
    return List.copyOf(visible);
  }

  public Snapshot capture() {
    if (scene == null || scene.isDisposed()) {
      selectedId = null;
      return new Snapshot(List.of(), null, List.of("No live scene"), "No live scene");
    }
    List<GameObject> objects = scene.getObjects();
    Map<Integer, GameObject> byId = new LinkedHashMap<>();
    IdentityHashMap<Transform3D, GameObject> transforms = new IdentityHashMap<>();
    for (GameObject object : objects) {
      byId.put(object.id, object);
      Transform3D transform = object.getComponent(Transform3D.class);
      if (transform != null) transforms.put(transform, object);
    }
    Map<Integer, List<GameObject>> children = new HashMap<>();
    List<GameObject> roots = new ArrayList<>();
    for (GameObject object : objects) {
      Transform3D transform = object.getComponent(Transform3D.class);
      GameObject parent = transform == null ? null : transforms.get(transform.getParent());
      if (parent == null) roots.add(object);
      else children.computeIfAbsent(parent.id, unused -> new ArrayList<>()).add(object);
    }
    List<Row> rows = new ArrayList<>();
    Set<Integer> visited = new HashSet<>();
    // Iterative traversal also handles very deep user-created transform hierarchies.
    record Pending(GameObject object, int depth) {}
    Deque<Pending> pending = new ArrayDeque<>();
    for (int i = roots.size() - 1; i >= 0; i--) pending.push(new Pending(roots.get(i), 0));
    while (!pending.isEmpty()) {
      Pending item = pending.pop();
      GameObject object = item.object();
      if (!visited.add(object.id)) continue;
      rows.add(row(object, item.depth()));
      List<GameObject> descendants = children.getOrDefault(object.id, List.of());
      for (int i = descendants.size() - 1; i >= 0; i--)
        pending.push(new Pending(descendants.get(i), item.depth() + 1));
    }
    for (GameObject object : objects) if (visited.add(object.id)) rows.add(row(object, 0));
    GameObject selected = selectedId == null ? null : byId.get(selectedId);
    if (selected == null) selectedId = null;
    List<String> details = selected == null ? List.of("Select a GameObject") : describe(selected);
    return new Snapshot(
        rows,
        selectedId,
        details,
        objects.size()
            + " objects / "
            + (scene.isPaused() ? "WORLD paused" : "WORLD running")
            + " / typed transforms");
  }

  private static Row row(GameObject object, int depth) {
    return new Row(
        object.id,
        object.name == null ? "(unnamed)" : object.name,
        object.isActive(),
        depth,
        object.getComponents().size());
  }

  private static List<String> describe(GameObject object) {
    List<String> result = new ArrayList<>();
    result.add("#" + object.id + "  " + object.name);
    result.add("Active: " + object.isActive() + "  Tags: " + object.getTags());
    Transform2D t = object.transform;
    result.add(format("Transform2D  position %.2f, %.2f", t.position.x, t.position.y));
    result.add(format("rotation %.3f rad  scale %.2f, %.2f", t.rotation, t.scale.x, t.scale.y));
    Transform3D spatial = object.getComponent(Transform3D.class);
    if (spatial != null) {
      result.add(
          format(
              "Transform3D LOCAL position %.2f, %.2f, %.2f",
              spatial.position.x, spatial.position.y, spatial.position.z));
      result.add(
          format(
              "rotation quaternion %.3f, %.3f, %.3f, %.3f",
              spatial.rotation.x, spatial.rotation.y, spatial.rotation.z, spatial.rotation.w));
      result.add(
          format("scale %.2f, %.2f, %.2f", spatial.scale.x, spatial.scale.y, spatial.scale.z));
      Transform3D parent = spatial.getParent();
      if (parent != null)
        result.add(
            "Parent: "
                + (parent.getGameObject() == null
                    ? "unattached transform"
                    : "#" + parent.getGameObject().id + " " + parent.getGameObject().name));
    }
    result.add("Components (including duplicate types):");
    int index = 0;
    for (Component c : object.getComponents()) {
      String type = c.getClass().getSimpleName();
      if (type.isEmpty()) {
        String full = c.getClass().getName();
        type =
            c.getClass().getSuperclass().getSimpleName()
                + " ["
                + full.substring(full.lastIndexOf('.') + 1)
                + "]";
      }
      result.add(
          ++index
              + ". "
              + type
              + " / "
              + (c.isEnabled() ? "enabled" : "disabled")
              + " / "
              + c.updateMode
              + " / "
              + (c.isInitialized() ? "ready" : "pending"));
    }
    return result;
  }

  private static String format(String pattern, Object... values) {
    return String.format(Locale.ROOT, pattern, values);
  }
}
