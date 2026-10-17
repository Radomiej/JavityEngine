package org.javity.demo;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/** Camera-local framing only. The canonical avatar pose and animation clocks stay untouched. */
final class VoxelViewmodelPresentation {
  // The hanging TPP grip points toward the ground. Turn only this view around X so its
  // sleeve enters from below and the head sits above the hand. Bounds are projection-tested
  // at both 16:9 and 4:3 with the viewmodel's 72-degree FOV and .03 near plane.
  private final Matrix4 base =
      new Matrix4()
          .translate(.30f, .12f, -1.35f)
          .rotate(Vector3.X, 170)
          .scale(.75f, .75f, .75f);

  /** Writes the authored framing into caller-owned scratch storage without accumulating edits. */
  Matrix4 writeBase(Matrix4 target) {
    return target.set(base);
  }
}
