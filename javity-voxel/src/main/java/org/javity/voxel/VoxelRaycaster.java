package org.javity.voxel;

import com.badlogic.gdx.math.Vector3;
import java.util.Objects;
import java.util.Optional;

/** Bounded double-precision voxel traversal. The caller's vectors are never modified. */
public final class VoxelRaycaster {
  private VoxelRaycaster() {}

  public static Optional<VoxelHit> cast(
      VoxelWorld world, Vector3 origin, Vector3 direction, double maxDistance, int maxSteps) {
    Objects.requireNonNull(origin, "origin");
    Objects.requireNonNull(direction, "direction");
    return cast(
        world,
        origin.x,
        origin.y,
        origin.z,
        direction.x,
        direction.y,
        direction.z,
        maxDistance,
        maxSteps);
  }

  public static Optional<VoxelHit> cast(
      VoxelWorld world,
      double originX,
      double originY,
      double originZ,
      double directionX,
      double directionY,
      double directionZ,
      double maxDistance,
      int maxSteps) {
    Objects.requireNonNull(world, "world");
    if (!Double.isFinite(originX)
        || !Double.isFinite(originY)
        || !Double.isFinite(originZ)
        || !Double.isFinite(directionX)
        || !Double.isFinite(directionY)
        || !Double.isFinite(directionZ)) {
      throw new IllegalArgumentException("Ray coordinates and direction must be finite");
    }
    if (!Double.isFinite(maxDistance) || maxDistance < 0)
      throw new IllegalArgumentException("maxDistance must be finite and non-negative");
    if (maxSteps <= 0) throw new IllegalArgumentException("maxSteps must be positive");
    double scale =
        Math.max(Math.abs(directionX), Math.max(Math.abs(directionY), Math.abs(directionZ)));
    if (scale == 0) throw new IllegalArgumentException("Direction must be non-zero and finite");
    directionX /= scale;
    directionY /= scale;
    directionZ /= scale;
    double magnitude = Math.hypot(Math.hypot(directionX, directionY), directionZ);
    directionX /= magnitude;
    directionY /= magnitude;
    directionZ /= magnitude;

    long cellX = floor(originX), cellY = floor(originY), cellZ = floor(originZ);
    if (!inWorld(cellX) || !inWorld(cellY) || !inWorld(cellZ))
      throw new IllegalArgumentException("Origin is outside the world block range");
    if (world.getBlock((int) cellX, (int) cellY, (int) cellZ) != 0) {
      return Optional.of(
          new VoxelHit(new BlockPos((int) cellX, (int) cellY, (int) cellZ), null, null, 0));
    }

    int stepX = sign(directionX), stepY = sign(directionY), stepZ = sign(directionZ);
    double deltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / directionX);
    double deltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / directionY);
    double deltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / directionZ);
    double maxX = initialBoundary(originX, cellX, directionX, stepX);
    double maxY = initialBoundary(originY, cellY, directionY, stepY);
    double maxZ = initialBoundary(originZ, cellZ, directionZ, stepZ);

    for (int steps = 1; steps < maxSteps; steps++) {
      double distance;
      BlockFace enteredFace;
      if (maxX <= maxY && maxX <= maxZ) {
        distance = maxX;
        maxX += deltaX;
        cellX += stepX;
        enteredFace = stepX > 0 ? BlockFace.NEG_X : BlockFace.POS_X;
      } else if (maxY <= maxZ) {
        distance = maxY;
        maxY += deltaY;
        cellY += stepY;
        enteredFace = stepY > 0 ? BlockFace.NEG_Y : BlockFace.POS_Y;
      } else {
        distance = maxZ;
        maxZ += deltaZ;
        cellZ += stepZ;
        enteredFace = stepZ > 0 ? BlockFace.NEG_Z : BlockFace.POS_Z;
      }
      if (distance > maxDistance) return Optional.empty();
      if (!inWorld(cellX) || !inWorld(cellY) || !inWorld(cellZ)) return Optional.empty();
      int x = (int) cellX, y = (int) cellY, z = (int) cellZ;
      if (world.getBlock(x, y, z) != 0) {
        BlockPos hit = new BlockPos(x, y, z);
        BlockPos placement =
            new BlockPos(x + enteredFace.dx(), y + enteredFace.dy(), z + enteredFace.dz());
        return Optional.of(new VoxelHit(hit, enteredFace, placement, distance));
      }
    }
    return Optional.empty();
  }

  private static long floor(double value) {
    double floor = Math.floor(value);
    if (floor < Long.MIN_VALUE || floor > Long.MAX_VALUE)
      throw new IllegalArgumentException("Origin outside representable range");
    return (long) floor;
  }

  private static boolean inWorld(long value) {
    return value >= BlockPos.MIN_COORDINATE && value <= BlockPos.MAX_COORDINATE;
  }

  private static int sign(double value) {
    return value > 0 ? 1 : value < 0 ? -1 : 0;
  }

  private static double initialBoundary(double origin, long cell, double direction, int step) {
    if (step == 0) return Double.POSITIVE_INFINITY;
    double boundary = step > 0 ? cell + 1.0 : cell;
    return (boundary - origin) / direction;
  }
}
