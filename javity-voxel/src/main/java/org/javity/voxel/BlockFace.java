package org.javity.voxel;

/** The six axis-aligned outward directions of a block face. */
public enum BlockFace {
  NEG_X(-1, 0, 0),
  POS_X(1, 0, 0),
  NEG_Y(0, -1, 0),
  POS_Y(0, 1, 0),
  NEG_Z(0, 0, -1),
  POS_Z(0, 0, 1);

  private final int dx;
  private final int dy;
  private final int dz;

  BlockFace(int dx, int dy, int dz) {
    this.dx = dx;
    this.dy = dy;
    this.dz = dz;
  }

  public int dx() {
    return dx;
  }

  public int dy() {
    return dy;
  }

  public int dz() {
    return dz;
  }
}
