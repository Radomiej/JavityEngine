package org.javity.voxel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Mutable during setup and immutable once a world is created. ID zero is reserved for air. */
public final class BlockRegistry {
  private final List<BlockDefinition> definitions = new ArrayList<>();
  private boolean frozen;

  public int register(BlockDefinition definition) {
    if (frozen) throw new IllegalStateException("Block registry is frozen");
    Objects.requireNonNull(definition, "definition");
    if (definitions.size() >= 65_535)
      throw new IllegalStateException("Block ID capacity exhausted");
    definitions.add(definition);
    return definitions.size();
  }

  /** Returns null for the reserved air ID zero. */
  public BlockDefinition get(int id) {
    if (id == 0) return null;
    if (id < 0 || id > definitions.size())
      throw new IllegalArgumentException("Unknown block ID: " + id);
    return definitions.get(id - 1);
  }

  public int size() {
    return definitions.size();
  }

  public boolean isFrozen() {
    return frozen;
  }

  void freeze() {
    frozen = true;
  }
}
