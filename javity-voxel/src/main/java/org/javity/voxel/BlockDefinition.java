package org.javity.voxel;

import java.util.Objects;

/** Immutable opaque cube definition with a texture tile for each outward face. */
public final class BlockDefinition {
  private final String name;
  private final int[] tiles;

  public BlockDefinition(String name, int tile) {
    this(name, tile, tile, tile, tile, tile, tile);
  }

  public BlockDefinition(String name, int negX, int posX, int negY, int posY, int negZ, int posZ) {
    this.name = Objects.requireNonNull(name, "name");
    if (name.isBlank()) throw new IllegalArgumentException("Block name must not be blank");
    this.tiles = new int[] {negX, posX, negY, posY, negZ, posZ};
    for (int tile : tiles)
      if (tile < 0) throw new IllegalArgumentException("Tile IDs must be non-negative");
  }

  public String name() {
    return name;
  }

  public int tile(BlockFace face) {
    return tiles[Objects.requireNonNull(face, "face").ordinal()];
  }
}
