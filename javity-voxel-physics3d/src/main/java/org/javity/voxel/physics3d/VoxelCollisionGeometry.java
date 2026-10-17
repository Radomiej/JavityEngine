package org.javity.voxel.physics3d;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;
import java.util.List;
import org.javity.physics3d.CompoundCollider3D;
import org.javity.voxel.VoxelWorld;

/** Deterministic CPU greedy merge of occupied chunk cells into bounded axis-aligned boxes. */
public final class VoxelCollisionGeometry {
  public static final int MAX_BOXES = 4096;

  private VoxelCollisionGeometry() {}

  public static List<CompoundCollider3D.Child> merge(VoxelWorld.CollisionSnapshot snapshot) {
    boolean[] occupied = new boolean[VoxelWorld.BLOCK_COUNT];
    boolean[] used = new boolean[VoxelWorld.BLOCK_COUNT];
    for (int y = 0; y < 16; y++)
      for (int z = 0; z < 16; z++)
        for (int x = 0; x < 16; x++) {
          int index = index(x, y, z);
          occupied[index] = snapshot.getBlock(x, y, z) != 0;
        }
    List<CompoundCollider3D.Child> result = new ArrayList<>();
    for (int y = 0; y < 16; y++)
      for (int z = 0; z < 16; z++)
        for (int x = 0; x < 16; x++) {
          int start = index(x, y, z);
          if (!occupied[start] || used[start]) continue;
          int width = 1;
          while (x + width < 16 && free(occupied, used, x + width, y, z)) width++;
          int depth = 1;
          while (z + depth < 16 && rowFree(occupied, used, x, x + width, y, z + depth)) depth++;
          int height = 1;
          while (y + height < 16
              && planeFree(occupied, used, x, x + width, y + height, z, z + depth)) height++;
          for (int yy = y; yy < y + height; yy++)
            for (int zz = z; zz < z + depth; zz++)
              for (int xx = x; xx < x + width; xx++) used[index(xx, yy, zz)] = true;
          if (result.size() == MAX_BOXES)
            throw new IllegalStateException("Voxel collision box budget exceeded");
          result.add(
              new CompoundCollider3D.Box(
                  new Vector3(width, height, depth),
                  new Vector3(x + width * .5f - 8, y + height * .5f - 8, z + depth * .5f - 8),
                  new Quaternion()));
        }
    return List.copyOf(result);
  }

  private static boolean free(boolean[] occupied, boolean[] used, int x, int y, int z) {
    int i = index(x, y, z);
    return occupied[i] && !used[i];
  }

  private static boolean rowFree(boolean[] occupied, boolean[] used, int x0, int x1, int y, int z) {
    for (int x = x0; x < x1; x++) if (!free(occupied, used, x, y, z)) return false;
    return true;
  }

  private static boolean planeFree(
      boolean[] occupied, boolean[] used, int x0, int x1, int y, int z0, int z1) {
    for (int z = z0; z < z1; z++) if (!rowFree(occupied, used, x0, x1, y, z)) return false;
    return true;
  }

  private static int index(int x, int y, int z) {
    return (y * 16 + z) * 16 + x;
  }
}
