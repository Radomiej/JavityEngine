package org.javity.render;

import static org.junit.Assert.*;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.VertexBufferObjectWithVAO;
import com.badlogic.gdx.utils.*;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/** Actual pinned Mesh/VBO bookkeeping with deterministic GL name allocation, without a context. */
public class ManagedMeshBindingsRecoveryTest {
  @Test
  public void restoredApplicationClearsStaleVaoLocationsWithoutMutatingOtherOwnersOrGeometry()
      throws Exception {
    GdxNativesLoader.load();
    Application previousApp = Gdx.app;
    GL20 previousGl = Gdx.gl, previous20 = Gdx.gl20;
    GL30 previous30 = Gdx.gl30;
    Application app = application(), otherApp = application();
    Mesh current = null, other = null;
    try {
      AtomicInteger names = new AtomicInteger(1);
      GL30 gl =
          (GL30)
              Proxy.newProxyInstance(
                  GL30.class.getClassLoader(),
                  new Class<?>[] {GL30.class},
                  (proxy, method, args) -> {
                    if (method.getName().equals("glGenBuffer")) return names.getAndIncrement();
                    if (method.getName().equals("glGenVertexArrays")) {
                      IntBuffer output = (IntBuffer) args[1];
                      for (int i = 0; i < (Integer) args[0]; i++)
                        output.put(i, names.getAndIncrement());
                    }
                    return primitiveDefault(method.getReturnType());
                  });
      Gdx.gl = Gdx.gl20 = gl;
      Gdx.gl30 = gl;
      Gdx.app = app;
      current = new Mesh(false, 1, 0, VertexAttribute.Position());
      current.setVertices(new float[] {2, 3, 4});
      Gdx.app = otherApp;
      other = new Mesh(false, 1, 0, VertexAttribute.Position());
      other.setVertices(new float[] {5, 6, 7});
      Object currentData = vertices(current), otherData = vertices(other);
      assertTrue(currentData instanceof VertexBufferObjectWithVAO);
      locations(currentData).add(0);
      locations(otherData).add(0);
      bound(currentData, true);
      bound(otherData, true);
      Mesh.invalidateAllMeshes(app);
      // libGDX allocates a fresh VAO but keeps the old equal-location cache: this is the defect.
      assertEquals(1, locations(currentData).size);
      assertEquals(1, ManagedMeshBindingsRecovery.contextRestored(app));
      assertEquals(0, locations(currentData).size);
      assertFalse(bound(currentData));
      assertEquals(1, locations(otherData).size);
      assertTrue(bound(otherData));
      float[] actual = new float[3];
      current.getVertices(actual);
      assertArrayEquals(new float[] {2, 3, 4}, actual, 0);
      other.getVertices(actual);
      assertArrayEquals(new float[] {5, 6, 7}, actual, 0);
      // Repeated hooks are harmless and do not unregister either borrowed mesh.
      ManagedMeshBindingsRecovery.contextRestored(app);
      assertEquals(0, locations(currentData).size);
      assertEquals(1, locations(otherData).size);
    } finally {
      Gdx.app = app;
      if (current != null) current.dispose();
      Gdx.app = otherApp;
      if (other != null) other.dispose();
      Mesh.clearAllMeshes(app);
      Mesh.clearAllMeshes(otherApp);
      Gdx.app = previousApp;
      Gdx.gl = previousGl;
      Gdx.gl20 = previous20;
      Gdx.gl30 = previous30;
    }
  }

  private static Application application() {
    return (Application)
        Proxy.newProxyInstance(
            Application.class.getClassLoader(),
            new Class<?>[] {Application.class},
            (proxy, method, args) -> {
              if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
              if (method.getName().equals("equals")) return proxy == args[0];
              return primitiveDefault(method.getReturnType());
            });
  }

  private static Object primitiveDefault(Class<?> type) {
    if (type == boolean.class) return false;
    if (type == int.class) return 0;
    if (type == long.class) return 0L;
    if (type == float.class) return 0f;
    return null;
  }

  private static Object vertices(Mesh mesh) throws Exception {
    Field field = Mesh.class.getDeclaredField("vertices");
    field.setAccessible(true);
    return field.get(mesh);
  }

  private static IntArray locations(Object data) throws Exception {
    Field field = VertexBufferObjectWithVAO.class.getDeclaredField("cachedLocations");
    field.setAccessible(true);
    return (IntArray) field.get(data);
  }

  private static boolean bound(Object data) throws Exception {
    Field field = VertexBufferObjectWithVAO.class.getDeclaredField("isBound");
    field.setAccessible(true);
    return field.getBoolean(data);
  }

  private static void bound(Object data, boolean value) throws Exception {
    Field field = VertexBufferObjectWithVAO.class.getDeclaredField("isBound");
    field.setAccessible(true);
    field.setBoolean(data, value);
  }
}
