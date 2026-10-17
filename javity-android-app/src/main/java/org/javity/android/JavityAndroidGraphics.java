package org.javity.android;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Version;
import com.badlogic.gdx.backends.android.AndroidApplicationBase;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.backends.android.AndroidGraphics;
import com.badlogic.gdx.backends.android.surfaceview.ResolutionStrategy;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.VertexBufferObjectWithVAO;
import com.badlogic.gdx.graphics.glutils.VertexData;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.badlogic.gdx.utils.ObjectMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/** GDX 1.14.2 Android recovery shim; call only on GL thread after backend invalidation. */
public final class JavityAndroidGraphics extends AndroidGraphics {
  private final Application owner;

  public JavityAndroidGraphics(
      AndroidApplicationBase application,
      AndroidApplicationConfiguration config,
      ResolutionStrategy strategy) {
    super(application, config, strategy);
    this.owner = application;
  }

  @Override
  public void onSurfaceCreated(
      javax.microedition.khronos.opengles.GL10 gl,
      javax.microedition.khronos.egl.EGLConfig config) {
    super.onSurfaceCreated(gl, config);
    try {
      Object version = Version.class.getField("VERSION").get(null);
      if (!"1.14.2".equals(version))
        throw new IllegalStateException(
            "VAO recovery shim requires libGDX 1.14.2; found " + version);
      int meshes = clearManagedVaoLocations(owner);
      int shaders = refreshManagedShaderLocations(owner);
      owner.log("JavityGLRecovery", "VAO meshes=" + meshes + ", shader programs=" + shaders);
      if (owner.getApplicationListener() instanceof AndroidGameFixture fixture)
        fixture.onGraphicsContextRestored();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Unable to apply pinned libGDX 1.14.2 context recovery", e);
    }
  }

  private static int clearManagedVaoLocations(Application app) throws ReflectiveOperationException {
    Field registryField = field(Mesh.class, "meshes");
    @SuppressWarnings("unchecked")
    Map<Application, Array<Mesh>> registry =
        (Map<Application, Array<Mesh>>) registryField.get(null);
    Array<Mesh> managed = registry.get(app);
    if (managed == null) return 0;

    Field verticesField = field(Mesh.class, "vertices");
    Field locationsField = field(VertexBufferObjectWithVAO.class, "cachedLocations");
    int changed = 0;
    for (int i = 0; i < managed.size; i++) {
      VertexData data = (VertexData) verticesField.get(managed.get(i));
      if (data instanceof VertexBufferObjectWithVAO) {
        com.badlogic.gdx.utils.IntArray cached =
            (com.badlogic.gdx.utils.IntArray) locationsField.get(data);
        cached.clear();
        changed++;
      }
    }
    return changed;
  }

  private static int refreshManagedShaderLocations(Application app)
      throws ReflectiveOperationException {
    Field registryField = field(ShaderProgram.class, "shaders");
    @SuppressWarnings("unchecked")
    ObjectMap<Application, Array<ShaderProgram>> registry =
        (ObjectMap<Application, Array<ShaderProgram>>) registryField.get(null);
    Array<ShaderProgram> managed = registry.get(app);
    if (managed == null) return 0;

    String[] mapNames = {
      "uniforms", "uniformTypes", "uniformSizes", "attributes", "attributeTypes", "attributeSizes"
    };
    Field[] maps = new Field[mapNames.length];
    for (int i = 0; i < mapNames.length; i++) maps[i] = field(ShaderProgram.class, mapNames[i]);
    Method fetchAttributes = method(ShaderProgram.class, "fetchAttributes");
    Method fetchUniforms = method(ShaderProgram.class, "fetchUniforms");
    for (int i = 0; i < managed.size; i++) {
      ShaderProgram shader = managed.get(i);
      if (!shader.isCompiled())
        throw new IllegalStateException("Managed shader failed recompilation: " + shader.getLog());
      for (Field map : maps) ((ObjectIntMap<?>) map.get(shader)).clear();
      fetchAttributes.invoke(shader);
      fetchUniforms.invoke(shader);
    }
    return managed.size;
  }

  private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
    Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  private static Method method(Class<?> type, String name) throws ReflectiveOperationException {
    Method method = type.getDeclaredMethod(name);
    method.setAccessible(true);
    return method;
  }
}
