package org.javity.voxel;

/**
 * A DDA hit. A null face and placement cell indicate that the ray started inside the solid block.
 */
public record VoxelHit(
    BlockPos block, BlockFace enteredFace, BlockPos placementCell, double distance) {
  public VoxelHit {
    if (block == null) throw new NullPointerException("block");
    if ((enteredFace == null) != (placementCell == null)) {
      throw new IllegalArgumentException(
          "Face and placement cell must both be present or both absent");
    }
    if (!Double.isFinite(distance) || distance < 0)
      throw new IllegalArgumentException("Invalid hit distance");
  }
}
