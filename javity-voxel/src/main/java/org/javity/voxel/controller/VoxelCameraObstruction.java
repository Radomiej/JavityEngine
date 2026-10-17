package org.javity.voxel.controller;

import com.badlogic.gdx.math.Vector3;
import java.util.Objects;
import org.javity.core.GameObject;
import org.javity.render.CameraObstructionQuery;
import org.javity.voxel.BlockPos;
import org.javity.voxel.ChunkPos;
import org.javity.voxel.VoxelWorld;

/** Continuous conservative camera sweep against radius-expanded solid voxel AABBs. */
public final class VoxelCameraObstruction implements CameraObstructionQuery {
  public static final int MAX_CANDIDATE_CELLS = 8192;
  private static final float CONTACT_SKIN = .025f;
  private final VoxelWorld world;

  public VoxelCameraObstruction(VoxelWorld world) {
    this.world = Objects.requireNonNull(world, "world");
  }

  /**
   * Sweeps a sphere conservatively by expanding each solid unit-cell AABB by radius on every axis.
   * This is intentionally a cube-expanded bound (it may stop early at corners), never fixed-step
   * sampling. The terrain-only query has no player body to ignore; the typed owner is unused.
   */
  @Override
  public float sweepFraction(Vector3 from, Vector3 to, float radius, GameObject ignoredOwner) {
    requireVector(from, "from");
    requireVector(to, "to");
    if (!Float.isFinite(radius) || radius < 0 || radius > 4)
      throw new IllegalArgumentException("radius must be finite and in [0,4]");
    float dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
    float length2 = dx * dx + dy * dy + dz * dz;
    if (!Float.isFinite(length2) || length2 > 64f * 64f)
      throw new IllegalArgumentException("camera sweep length must be at most 64");
    float coordinateMargin = 65f + radius;
    if (outsideRange(from.x, coordinateMargin)
        || outsideRange(from.y, coordinateMargin)
        || outsideRange(from.z, coordinateMargin)
        || outsideRange(to.x, coordinateMargin)
        || outsideRange(to.y, coordinateMargin)
        || outsideRange(to.z, coordinateMargin)) return 0f;

    int minX = floor(Math.min(from.x, to.x) - radius),
        maxX = floor(Math.max(from.x, to.x) + radius);
    int minY = floor(Math.min(from.y, to.y) - radius),
        maxY = floor(Math.max(from.y, to.y) + radius);
    int minZ = floor(Math.min(from.z, to.z) - radius),
        maxZ = floor(Math.max(from.z, to.z) + radius);
    long candidates =
        ((long) maxX - minX + 1) * ((long) maxY - minY + 1) * ((long) maxZ - minZ + 1);
    if (candidates <= 0 || candidates > MAX_CANDIDATE_CELLS) return 0f;
    float earliest = 1f;
    boolean collided = false;
    for (int y = minY; y <= maxY; y++) {
      for (int z = minZ; z <= maxZ; z++) {
        for (int x = minX; x <= maxX; x++) {
          boolean outside =
              x < BlockPos.MIN_COORDINATE
                  || x > BlockPos.MAX_COORDINATE
                  || y < BlockPos.MIN_COORDINATE
                  || y > BlockPos.MAX_COORDINATE
                  || z < BlockPos.MIN_COORDINATE
                  || z > BlockPos.MAX_COORDINATE;
          boolean blocked = outside;
          if (!outside) {
            if (!world.isLoaded(ChunkPos.fromBlock(x, y, z))) blocked = true;
            else blocked = world.getBlock(x, y, z) != 0;
          }
          if (!blocked) continue;
          float hit =
              segmentBox(
                  from,
                  dx,
                  dy,
                  dz,
                  x - radius,
                  y - radius,
                  z - radius,
                  x + 1f + radius,
                  y + 1f + radius,
                  z + 1f + radius);
          if (hit >= 0f) {
            collided = true;
            earliest = Math.min(earliest, hit);
          }
        }
      }
    }
    if (!collided) return 1f;
    float length = (float) Math.sqrt(length2);
    if (length == 0f || earliest == 0f) return 0f;
    return Math.max(0f, earliest - CONTACT_SKIN / length);
  }

  private static float segmentBox(
      Vector3 p,
      float dx,
      float dy,
      float dz,
      float minX,
      float minY,
      float minZ,
      float maxX,
      float maxY,
      float maxZ) {
    float lo = 0f, hi = 1f;
    boolean startInside =
        p.x > minX && p.x < maxX && p.y > minY && p.y < maxY && p.z > minZ && p.z < maxZ;
    if (startInside) return 0f;
    if (Math.abs(dx) < 1e-8f) {
      if (p.x < minX || p.x > maxX) return -1f;
    } else {
      float a = (minX - p.x) / dx, b = (maxX - p.x) / dx;
      if (a > b) {
        float s = a;
        a = b;
        b = s;
      }
      lo = Math.max(lo, a);
      hi = Math.min(hi, b);
      if (lo > hi) return -1f;
    }
    if (Math.abs(dy) < 1e-8f) {
      if (p.y < minY || p.y > maxY) return -1f;
    } else {
      float a = (minY - p.y) / dy, b = (maxY - p.y) / dy;
      if (a > b) {
        float s = a;
        a = b;
        b = s;
      }
      lo = Math.max(lo, a);
      hi = Math.min(hi, b);
      if (lo > hi) return -1f;
    }
    if (Math.abs(dz) < 1e-8f) {
      if (p.z < minZ || p.z > maxZ) return -1f;
    } else {
      float a = (minZ - p.z) / dz, b = (maxZ - p.z) / dz;
      if (a > b) {
        float s = a;
        a = b;
        b = s;
      }
      lo = Math.max(lo, a);
      hi = Math.min(hi, b);
      if (lo > hi) return -1f;
    }
    // A zero-width interval at t=0 is a touching camera moving away (or tangent), not penetration.
    if (hi <= 1e-7f) return -1f;
    return lo;
  }

  private static boolean outsideRange(float coordinate, float margin) {
    return coordinate < BlockPos.MIN_COORDINATE - margin
        || coordinate > BlockPos.MAX_COORDINATE + 1f + margin;
  }

  private static int floor(float value) {
    if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
      return value < 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
    return (int) Math.floor(value);
  }

  private static void requireVector(Vector3 value, String name) {
    Objects.requireNonNull(value, name);
    if (!Float.isFinite(value.x) || !Float.isFinite(value.y) || !Float.isFinite(value.z))
      throw new IllegalArgumentException(name + " must be finite");
  }
}
