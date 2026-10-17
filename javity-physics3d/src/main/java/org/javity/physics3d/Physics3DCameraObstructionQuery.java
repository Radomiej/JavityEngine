package org.javity.physics3d;

import com.badlogic.gdx.math.Vector3;
import java.util.Objects;
import org.javity.core.GameObject;
import org.javity.render.CameraObstructionQuery;

/** Adapts copied Bullet sphere sweeps to the renderer's camera clearance seam. */
public final class Physics3DCameraObstructionQuery implements CameraObstructionQuery {
  private final Physics3D physics;
  private final int group, mask;

  public Physics3DCameraObstructionQuery(Physics3D physics) {
    this(physics, 0xffff, 0xffff);
  }

  public Physics3DCameraObstructionQuery(Physics3D physics, int group, int mask) {
    this.physics = Objects.requireNonNull(physics, "physics");
    PhysicsFilter3D.validateBits(group, "group");
    PhysicsFilter3D.validateBits(mask, "mask");
    this.group = group;
    this.mask = mask;
  }

  @Override
  public float sweepFraction(Vector3 from, Vector3 to, float radius, GameObject ignoredOwner) {
    if (!Float.isFinite(radius) || radius < 0)
      throw new IllegalArgumentException("Camera sweep radius must be finite and nonnegative");
    PhysicsFilter3D filter =
        new PhysicsFilter3D(group, mask, false, ignoredOwner == null ? null : ignoredOwner.id);
    if (radius == 0)
      return physics.raycast(from, to, filter).map(PhysicsHit3D::fraction).orElse(1f);
    return physics.sweepSphere(radius, from, to, filter).map(PhysicsHit3D::fraction).orElse(1f);
  }
}
