package org.javity.render;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Version;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.glutils.VertexBufferObjectWithVAO;
import com.badlogic.gdx.graphics.glutils.VertexData;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;

/** Restores libGDX managed mesh vertex bindings after host-managed context recovery. */
public final class ManagedMeshBindingsRecovery {
  private ManagedMeshBindingsRecovery() {}

  /**
   * GL owner thread, after Mesh.invalidateAllMeshes(application), before any draw. Pinned to
   * libGDX1.14.2: its VAO invalidate path allocates a new VAO but retains old attribute locations.
   * Repairs only CPU binding bookkeeping for that app; geometry and ownership do not change.
   */
  public static int contextRestored(Application application) {
    Objects.requireNonNull(application, "application");
    Access access = Holder.ACCESS;
    Array<Mesh> meshes = access.meshes(application);
    if (meshes == null) return 0;
    int changed = 0;
    for (int i = 0; i < meshes.size; i++) {
      Object vertices = access.read(access.vertices, meshes.get(i));
      if (vertices instanceof VertexBufferObjectWithVAO) {
        ((IntArray) access.read(access.locations, vertices)).clear();
        try {
          access.bound.setBoolean(vertices, false);
        } catch (IllegalAccessException failure) {
          throw new IllegalStateException(
              "Pinned VAO binding bookkeeping is inaccessible", failure);
        }
        changed++;
      }
    }
    return changed;
  }

  private static final class Holder {
    static final Access ACCESS = new Access();
  }

  private static final class Access {
    final Field registry, vertices, locations, bound;

    Access() {
      try {
        Object version = Version.class.getField("VERSION").get(null);
        if (!"1.14.2".equals(version))
          throw new IllegalStateException(
              "Managed mesh binding recovery requires libGDX1.14.2; found " + version);
        registry = field(Mesh.class, "meshes", Map.class);
        vertices = field(Mesh.class, "vertices", VertexData.class);
        locations = field(VertexBufferObjectWithVAO.class, "cachedLocations", IntArray.class);
        bound = field(VertexBufferObjectWithVAO.class, "isBound", boolean.class);
      } catch (ReflectiveOperationException failure) {
        throw new IllegalStateException("Missing pinned libGDX VAO fields/keep rules", failure);
      }
    }

    private static Field field(Class<?> owner, String name, Class<?> type)
        throws ReflectiveOperationException {
      Field field = owner.getDeclaredField(name);
      if (field.getType() != type) throw new NoSuchFieldException(owner.getName() + "." + name);
      field.setAccessible(true);
      return field;
    }

    Object read(Field field, Object owner) {
      try {
        return field.get(owner);
      } catch (IllegalAccessException failure) {
        throw new IllegalStateException("Pinned mesh bookkeeping is inaccessible", failure);
      }
    }

    @SuppressWarnings("unchecked")
    Array<Mesh> meshes(Application application) {
      return ((Map<Application, Array<Mesh>>) read(registry, null)).get(application);
    }
  }
}
