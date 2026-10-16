package org.javity.physics3d;

/** Optional conservative admission policy for a character's complete swept world-space capsule. */
@FunctionalInterface
public interface CharacterReadiness3D {
  /** Returns true only when the supplied closed AABB is loaded and collision-ready. */
  boolean isReady(float minX, float minY, float minZ, float maxX, float maxY, float maxZ);
}
