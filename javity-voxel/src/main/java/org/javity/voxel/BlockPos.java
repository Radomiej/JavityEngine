package org.javity.voxel;

/** Integer world coordinates of a block. */
public record BlockPos(int x, int y, int z) {
  public static final int MIN_COORDINATE = -1_048_576;
  public static final int MAX_COORDINATE = 1_048_575;

  public BlockPos {
    validateCoordinates(x, y, z);
  }

  static void validateCoordinates(int x, int y, int z) {
    if (!inRange(x) || !inRange(y) || !inRange(z)) {
      throw new IllegalArgumentException("Block coordinates must be in [-1048576, 1048575]");
    }
  }

  static boolean inRange(int value) {
    return value >= MIN_COORDINATE && value <= MAX_COORDINATE;
  }
}
