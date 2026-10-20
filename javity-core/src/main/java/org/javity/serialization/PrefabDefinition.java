package org.javity.serialization;

import java.util.List;
import java.util.Objects;

/** Immutable serialized prefab asset with an application-level format version. */
public record PrefabDefinition(
    PrefabId id, int version, String sceneJson, List<AssetId<?>> assets) {
  public PrefabDefinition(PrefabId id, int version, String sceneJson) {
    this(id, version, sceneJson, List.of());
  }

  public PrefabDefinition {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(sceneJson, "sceneJson");
    if (version < 1) throw new IllegalArgumentException("Prefab version must be positive");
    assets = List.copyOf(assets);
  }
}
