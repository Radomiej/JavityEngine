package org.javity.render;

import com.badlogic.gdx.math.Vector3;
import org.javity.core.GameObject;

/** Supplies a bounded, conservative clearance fraction along a camera boom. */
@FunctionalInterface
public interface CameraObstructionQuery {
  /**
   * Returns the unobstructed fraction of the segment from {@code from} to {@code to}, in [0,1]. A
   * zero-length segment is a stationary clearance query: return 1 only when the sphere is clear at
   * that point and 0 when it overlaps an obstacle. The owner is a typed self-ignore hint for
   * adapters that model bodies; voxel terrain adapters query blocks only and consequently ignore
   * the owner by construction. Inputs are read-only.
   */
  float sweepFraction(Vector3 from, Vector3 to, float radius, GameObject ignoredOwner);
}
