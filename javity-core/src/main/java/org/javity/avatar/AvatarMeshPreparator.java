package org.javity.avatar;

import com.badlogic.gdx.graphics.g3d.Model;
import java.util.Map;
import java.util.Objects;

/** Entry point for one-time authored triangle-region preparation of a single skinned NodePart. */
public final class AvatarMeshPreparator {
  private AvatarMeshPreparator() {}

  /**
   * Validates and partitions one indexed weighted triangle part. Region triangle ordinals are
   * relative to the selected MeshPart. Call on the GL thread outside any active ModelBatch.
   */
  public static PreparedAvatarGeometry prepare(
      Model source, PartKey target, Map<String, int[]> triangleRegions) {
    return PreparedAvatarGeometry.prepare(
        Objects.requireNonNull(source, "source"),
        Objects.requireNonNull(target, "target"),
        Objects.requireNonNull(triangleRegions, "triangleRegions"));
  }
}
