package org.javity.core;

import com.badlogic.gdx.math.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A local 3D pose. Unattached transforms may form hierarchies, but all attached transforms in a
 * connected hierarchy must belong to the same scene.
 */
public final class Transform3D extends Component {
  public final Vector3 position = new Vector3();
  public final Quaternion rotation = new Quaternion();
  public final Vector3 scale = new Vector3(1, 1, 1);
  private final Matrix4 world = new Matrix4();
  private final List<Transform3D> children = new ArrayList<>();
  private Transform3D parent;

  /**
   * Returns the current parent. Removal or destruction of either endpoint detaches the link, even
   * before deferred lifecycle cleanup runs.
   */
  public Transform3D getParent() {
    if (parent != null && (unavailable() || parent.unavailable())) detachParent();
    return parent;
  }

  /**
   * Changes the parent while preserving local position, rotation and scale. Cycles, unavailable
   * endpoints and hierarchies spanning multiple scenes are rejected before the link changes.
   */
  public void setParent(Transform3D parent) {
    if (parent == null) {
      detachParent();
      return;
    }
    if (unavailable() || parent.unavailable())
      throw new IllegalArgumentException("Transform is removed or destroyed");
    for (Transform3D p = parent; p != null; p = p.getParent())
      if (p == this) throw new IllegalArgumentException("Cyclic transform hierarchy");
    parent.root().subtreeScene(subtreeScene(null));
    detachParent();
    this.parent = parent;
    parent.children.add(this);
  }

  @Override
  protected void validateAttachment(GameObject owner) {
    root().subtreeScene(owner.game);
  }

  private Transform3D root() {
    Transform3D root = this;
    while (root.getParent() != null) root = root.parent;
    return root;
  }

  /** Validates a subtree, including attached descendants behind unattached intermediates. */
  private Game subtreeScene(Game scene) {
    Game ownScene = getGame();
    if (ownScene != null) {
      if (scene != null && scene != ownScene)
        throw new IllegalArgumentException("Transform hierarchy belongs to another scene");
      scene = ownScene;
    }
    for (int i = 0; i < children.size(); ) {
      Transform3D child = children.get(i);
      if (child.unavailable()) child.detachParent();
      else {
        scene = child.subtreeScene(scene);
        i++;
      }
    }
    return scene;
  }

  private boolean unavailable() {
    GameObject owner = getGameObject();
    return removed
        || destroyed
        || (owner != null && (owner.isDestroyed() || owner.game.isDisposed()));
  }

  private void detachParent() {
    if (parent == null) return;
    parent.children.remove(this);
    parent = null;
  }

  @Override
  protected void onRelease() {
    detachParent();
    for (Transform3D child : children) child.parent = null;
    children.clear();
  }

  /** Borrowed matrix; callers that retain it should copy it. */
  public Matrix4 worldMatrix() {
    world.set(position, rotation, scale);
    Transform3D parent = getParent();
    if (parent != null) world.mulLeft(parent.worldMatrix());
    return world;
  }
}
