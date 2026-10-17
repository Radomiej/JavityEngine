package org.javity.core;

import com.badlogic.gdx.math.*;

public final class Transform3D extends Component {
  public final Vector3 position = new Vector3();
  public final Quaternion rotation = new Quaternion();
  public final Vector3 scale = new Vector3(1, 1, 1);
  private final Matrix4 world = new Matrix4();
  private Transform3D parent;

  public Transform3D getParent() {
    return parent;
  }

  public void setParent(Transform3D parent) {
    for (Transform3D p = parent; p != null; p = p.parent)
      if (p == this) throw new IllegalArgumentException("Cyclic transform hierarchy");
    this.parent = parent;
  }

  /** Borrowed matrix; callers that retain it should copy it. */
  public Matrix4 worldMatrix() {
    world.set(position, rotation, scale);
    if (parent != null) world.mulLeft(parent.worldMatrix());
    return world;
  }
}
