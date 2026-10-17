package org.javity.voxel;

/**
 * Regular texture atlas measured from the PNG's top-left corner. UVs include a half-texel inset.
 */
public final class AtlasLayout {
  private final int textureWidth;
  private final int textureHeight;
  private final int tileSize;
  private final int gutter;
  private final int cellStride;
  private final int columns;
  private final int rows;
  private final int tileCount;

  public AtlasLayout(
      int textureWidth, int textureHeight, int tileSize, int gutter, int columns, int rows) {
    if (textureWidth <= 0
        || textureHeight <= 0
        || tileSize <= 0
        || gutter < 0
        || columns <= 0
        || rows <= 0) {
      throw new IllegalArgumentException(
          "Atlas dimensions, tile size, rows and columns must be positive; gutter non-negative");
    }
    long stride = (long) tileSize + 2L * gutter;
    if (stride > textureWidth / columns || stride > textureHeight / rows) {
      throw new IllegalArgumentException("Atlas cells do not fit the texture dimensions");
    }
    long tileCount = (long) columns * rows;
    if (tileCount > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Atlas tile count exceeds integer range");
    }
    this.textureWidth = textureWidth;
    this.textureHeight = textureHeight;
    this.tileSize = tileSize;
    this.gutter = gutter;
    this.cellStride = (int) stride;
    this.columns = columns;
    this.rows = rows;
    this.tileCount = (int) tileCount;
  }

  public float u0(int tile) {
    checkTile(tile);
    return (column(tile) * stride() + gutter + 0.5f) / textureWidth;
  }

  public float v0(int tile) {
    checkTile(tile);
    return (row(tile) * stride() + gutter + 0.5f) / textureHeight;
  }

  public float u1(int tile) {
    checkTile(tile);
    return (column(tile) * stride() + gutter + tileSize - 0.5f) / textureWidth;
  }

  public float v1(int tile) {
    checkTile(tile);
    return (row(tile) * stride() + gutter + tileSize - 0.5f) / textureHeight;
  }

  public int tileCount() {
    return tileCount;
  }

  private int column(int tile) {
    return tile % columns;
  }

  private int row(int tile) {
    return tile / columns;
  }

  private int stride() {
    return cellStride;
  }

  private void checkTile(int tile) {
    if (tile < 0 || tile >= tileCount())
      throw new IllegalArgumentException("Tile outside atlas: " + tile);
  }
}
