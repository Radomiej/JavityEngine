package org.javity.render;

import static org.junit.Assert.*;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.g3d.utils.DefaultTextureBinder;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.ObjectIntMap;
import java.lang.reflect.*;
import java.nio.IntBuffer;
import java.util.*;
import org.junit.Test;

/** Actual libGDX owners; recording GL deliberately relocates the simulated linked program. */
public final class RecoverableDefaultShaderProviderTest {
  @Test
  public void retainedDefaultShaderUsesNewUniformLightVertexAndInstanceLocations()
      throws Exception {
    try (Fixture f = new Fixture()) {
      DefaultShader shader = (DefaultShader) f.provider.getShader(f.renderable);
      ShaderProgram program = shader.program;
      int oldHandle = program.getHandle();
      f.draw(shader);
      assertTrue(f.gl.draws > 0);
      f.gl.phase = 1;
      f.relinkAndRefreshProgramMaps(program);
      f.provider.contextRestored();
      assertSame(shader, f.provider.getShader(f.renderable));
      assertSame(program, shader.program);
      assertNotEquals(oldHandle, program.getHandle());
      int lookups = f.gl.locationQueries;
      f.gl.resetDrawObservations();
      f.draw(shader);
      assertTrue("relocated shader must execute the actual mesh draw path", f.gl.draws > 0);
      assertFalse(f.gl.uniformUploads.isEmpty());
      for (int location : f.gl.uniformUploads)
        assertTrue(
            "stale uniform/light location " + location,
            location < 0 || f.gl.uniformLocations().containsValue(location));
      for (String alias : f.gl.attributeAliases) {
        assertTrue(
            "binding missing relocated attribute " + alias,
            f.gl.enabledAttributes.contains(f.gl.attributeLocation(alias)));
      }
      assertTrue(
          "generic bone fallback must use relocated attribute",
          f.gl.genericBoneAttributes.contains(f.gl.attributeLocation("a_boneWeight0")));
      assertEquals("draw must not query GL locations", lookups, f.gl.locationQueries);
      f.provider.dispose();
      assertEquals(
          "retained owner disposes only its current program once", 1, f.gl.deletedPrograms);
    }
  }

  @Test
  public void initiallyInactiveUniformsStayDisabledWhileOriginallyActiveUniformCanReturn()
      throws Exception {
    try (Fixture f = new Fixture()) {
      DefaultShader shader = (DefaultShader) f.provider.getShader(f.renderable);
      int validatorDisabled = shader.getUniformID("u_specularColor");
      int initiallyMissing = shader.getUniformID("u_fogColor");
      int active = shader.getUniformID("u_worldTrans");
      assertTrue(shader.program.fetchUniformLocation("u_specularColor", false) >= 0);
      assertEquals(-1, shader.loc(validatorDisabled));
      assertEquals(-1, shader.loc(initiallyMissing));
      assertTrue(shader.loc(active) >= 0);
      int originalWorldLocation = shader.loc(active);
      f.gl.phase = 1;
      f.gl.hideWorldTransform = true;
      f.relinkAndRefreshProgramMaps(shader.program);
      f.provider.contextRestored();
      assertEquals(-1, shader.loc(active));
      assertEquals(-1, shader.loc(validatorDisabled));
      assertTrue(shader.program.fetchUniformLocation("u_fogColor", false) >= 0);
      assertEquals(-1, shader.loc(initiallyMissing));
      f.gl.resetDrawObservations();
      f.draw(shader);
      assertFalse(
          "temporarily absent world uniform must not upload to its old location",
          f.gl.uniformUploads.contains(originalWorldLocation));
      f.gl.phase = 2;
      f.gl.hideWorldTransform = false;
      f.relinkAndRefreshProgramMaps(shader.program);
      f.provider.contextRestored();
      assertEquals(f.gl.uniformLocations().get("u_worldTrans").intValue(), shader.loc(active));
      assertEquals(-1, shader.loc(validatorDisabled));
      assertEquals(-1, shader.loc(initiallyMissing));
      assertThrows(
          "initialized shader must not be reinitialized",
          RuntimeException.class,
          () -> shader.register("u_afterInit"));
    }
  }

  @Test
  public void emptyOrDisposedProviderDoesNotQueryGlDuringRestoration() {
    try (Fixture f = new Fixture()) {
      int before = f.gl.locationQueries;
      f.provider.contextRestored();
      f.provider.dispose();
      f.provider.contextRestored();
      assertEquals(before, f.gl.locationQueries);
    }
  }

  @Test
  public void suggestedExternalShaderIsNeitherRecoveredNorDisposedByProvider() {
    try (Fixture f = new Fixture()) {
      int[] disposed = {0};
      Shader external =
          new Shader() {
            public void init() {}

            public boolean canRender(Renderable renderable) {
              return true;
            }

            public int compareTo(Shader shader) {
              return 0;
            }

            public void begin(Camera camera, RenderContext context) {}

            public void render(Renderable renderable) {}

            public void end() {}

            public void dispose() {
              disposed[0]++;
            }
          };
      f.renderable.shader = external;
      assertSame(external, f.provider.getShader(f.renderable));
      int lookups = f.gl.locationQueries;
      f.provider.contextRestored();
      f.provider.dispose();
      assertEquals(lookups, f.gl.locationQueries);
      assertEquals(0, disposed[0]);
      external.dispose();
      assertEquals(1, disposed[0]);
    }
  }

  private static final class Fixture implements AutoCloseable {
    final Application previousApp = Gdx.app;
    final Graphics previousGraphics = Gdx.graphics;
    final GL20 previousGl = Gdx.gl, previousGl20 = Gdx.gl20;
    final GL30 previousGl30 = Gdx.gl30;
    final RecordingGl gl = new RecordingGl();
    final Application app;
    final Mesh mesh;
    final Renderable renderable = new Renderable();
    final RecoverableDefaultShaderProvider provider;

    Fixture() {
      GdxNativesLoader.load();
      app =
          (Application)
              Proxy.newProxyInstance(
                  Application.class.getClassLoader(),
                  new Class<?>[] {Application.class},
                  (proxy, method, args) ->
                      method.getName().equals("getType")
                          ? Application.ApplicationType.Desktop
                          : defaults(proxy, method, args));
      Gdx.app = app;
      Gdx.graphics =
          (Graphics)
              Proxy.newProxyInstance(
                  Graphics.class.getClassLoader(),
                  new Class<?>[] {Graphics.class},
                  (proxy, method, args) ->
                      method.getName().equals("getDeltaTime")
                          ? 1f / 60
                          : defaults(proxy, method, args));
      GL30 api =
          (GL30)
              Proxy.newProxyInstance(GL30.class.getClassLoader(), new Class<?>[] {GL30.class}, gl);
      Gdx.gl = Gdx.gl20 = api;
      Gdx.gl30 = api;
      mesh =
          new Mesh(
              Mesh.VertexDataType.VertexArray,
              false,
              3,
              0,
              VertexAttribute.Position(),
              VertexAttribute.Normal(),
              VertexAttribute.BoneWeight(0));
      mesh.setVertices(
          new float[] {0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1});
      mesh.enableInstancedRendering(
          false, 1, new VertexAttribute(VertexAttributes.Usage.Generic, 4, "a_instanceRow"));
      mesh.setInstanceData(new float[] {1, 0, 0, 0});
      renderable.meshPart.set("triangle", mesh, 0, 3, GL20.GL_TRIANGLES);
      renderable.material = new Material(ColorAttribute.createDiffuse(1, .5f, .2f, 1));
      renderable.environment = new Environment();
      renderable.environment.set(new ColorAttribute(ColorAttribute.AmbientLight, .4f, .4f, .4f, 1));
      renderable.environment.add(new DirectionalLight().set(1, 1, 1, -1, -1, -1));
      renderable.bones = new Matrix4[] {new Matrix4()};
      DefaultShader.Config config = new DefaultShader.Config("void main(){}", "void main(){}");
      config.numBones = 1;
      config.numBoneWeights = 1;
      config.numDirectionalLights = 2;
      config.numPointLights = 2;
      config.numSpotLights = 2;
      provider = new RecoverableDefaultShaderProvider(config);
    }

    void draw(DefaultShader shader) {
      PerspectiveCamera camera = new PerspectiveCamera(67, 16, 16);
      camera.update();
      RenderContext context =
          new RenderContext(new DefaultTextureBinder(DefaultTextureBinder.LRU, 0));
      context.begin();
      try {
        shader.begin(camera, context);
        try {
          shader.render(renderable);
        } finally {
          shader.end();
        }
      } finally {
        context.end();
      }
    }

    void relinkAndRefreshProgramMaps(ShaderProgram program) throws Exception {
      ShaderProgram.invalidateAllShaderPrograms(app);
      for (String name :
          List.of(
              "uniforms",
              "uniformTypes",
              "uniformSizes",
              "attributes",
              "attributeTypes",
              "attributeSizes")) {
        Field field = ShaderProgram.class.getDeclaredField(name);
        field.setAccessible(true);
        ((ObjectIntMap<?>) field.get(program)).clear();
      }
      for (String name : List.of("fetchAttributes", "fetchUniforms")) {
        Method method = ShaderProgram.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(program);
      }
    }

    @Override
    public void close() {
      try {
        provider.dispose();
        mesh.dispose();
        ShaderProgram.clearAllShaderPrograms(app);
      } finally {
        Gdx.app = previousApp;
        Gdx.graphics = previousGraphics;
        Gdx.gl = previousGl;
        Gdx.gl20 = previousGl20;
        Gdx.gl30 = previousGl30;
      }
    }
  }

  private static Object defaults(Object proxy, Method method, Object[] args) {
    return switch (method.getName()) {
      case "hashCode" -> System.identityHashCode(proxy);
      case "equals" -> proxy == args[0];
      case "toString" -> "recording GL fixture";
      default ->
          method.getReturnType() == int.class
              ? 0
              : method.getReturnType() == boolean.class
                  ? false
                  : method.getReturnType() == float.class
                      ? 0f
                      : method.getReturnType() == String.class ? "" : null;
    };
  }

  private static final class RecordingGl implements InvocationHandler {
    int phase, names = 10, locationQueries, draws, deletedPrograms;
    boolean hideWorldTransform;
    final Set<Integer> uniformUploads = new HashSet<>(),
        enabledAttributes = new HashSet<>(),
        genericBoneAttributes = new HashSet<>();
    final List<String> attributeAliases =
        List.of("a_position", "a_normal", "a_boneWeight0", "a_instanceRow");
    final List<String> allUniforms =
        List.of(
            "u_projViewTrans",
            "u_worldTrans",
            "u_normalMatrix",
            "u_diffuseColor",
            "u_bones",
            "u_specularColor",
            "u_dirLights[0].color",
            "u_dirLights[0].direction",
            "u_dirLights[1].color",
            "u_dirLights[1].direction",
            "u_pointLights[0].color",
            "u_pointLights[0].position",
            "u_pointLights[0].intensity",
            "u_pointLights[1].color",
            "u_pointLights[1].position",
            "u_pointLights[1].intensity",
            "u_spotLights[0].color",
            "u_spotLights[0].position",
            "u_spotLights[0].intensity",
            "u_spotLights[0].direction",
            "u_spotLights[0].cutoffAngle",
            "u_spotLights[0].exponent",
            "u_spotLights[1].color",
            "u_spotLights[1].position",
            "u_spotLights[1].intensity",
            "u_spotLights[1].direction",
            "u_spotLights[1].cutoffAngle",
            "u_spotLights[1].exponent",
            "u_fogColor");

    Map<String, Integer> uniformLocations() {
      Map<String, Integer> locations = new LinkedHashMap<>();
      for (int i = 0; i < allUniforms.size(); i++) {
        String alias = allUniforms.get(i);
        if ((phase == 0 && alias.equals("u_fogColor"))
            || (hideWorldTransform && alias.equals("u_worldTrans"))) continue;
        locations.put(alias, 3 + phase * 200 + i * 3);
      }
      return locations;
    }

    int attributeLocation(String alias) {
      int index = attributeAliases.indexOf(alias);
      return index < 0 ? -1 : 5 + phase * 100 + index;
    }

    void resetDrawObservations() {
      uniformUploads.clear();
      enabledAttributes.clear();
      genericBoneAttributes.clear();
      draws = 0;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
      String name = method.getName();
      switch (name) {
        case "glCreateShader", "glCreateProgram", "glGenBuffer" -> {
          return ++names;
        }
        case "glGetShaderiv" -> {
          ((IntBuffer) args[2]).put(0, 1);
          return null;
        }
        case "glGetProgramiv" -> {
          int what = (int) args[1];
          ((IntBuffer) args[2])
              .put(
                  0,
                  what == GL20.GL_LINK_STATUS
                      ? 1
                      : what == GL20.GL_ACTIVE_UNIFORMS
                          ? uniformLocations().size()
                          : what == GL20.GL_ACTIVE_ATTRIBUTES ? attributeAliases.size() : 0);
          return null;
        }
        case "glGetActiveUniform" -> {
          ((IntBuffer) args[2]).put(0, 1);
          ((IntBuffer) args[3]).put(0, GL20.GL_FLOAT);
          return new ArrayList<>(uniformLocations().keySet()).get((int) args[1]);
        }
        case "glGetActiveAttrib" -> {
          ((IntBuffer) args[2]).put(0, 1);
          ((IntBuffer) args[3]).put(0, GL20.GL_FLOAT_VEC3);
          return attributeAliases.get((int) args[1]);
        }
        case "glGetUniformLocation" -> {
          locationQueries++;
          return uniformLocations().getOrDefault((String) args[1], -1);
        }
        case "glGetAttribLocation" -> {
          locationQueries++;
          return attributeLocation((String) args[1]);
        }
        case "glGetIntegerv" -> {
          ((IntBuffer) args[1]).put(0, (int) args[0] == GL20.GL_MAX_TEXTURE_IMAGE_UNITS ? 16 : 0);
          return null;
        }
        case "glEnableVertexAttribArray" -> {
          enabledAttributes.add((int) args[0]);
          return null;
        }
        case "glVertexAttrib2f" -> {
          genericBoneAttributes.add((int) args[0]);
          return null;
        }
        case "glDeleteProgram" -> {
          deletedPrograms++;
          return null;
        }
        default -> {
          if (name.startsWith("glUniform")) uniformUploads.add((int) args[0]);
          if (name.startsWith("glDraw")) draws++;
          return defaults(proxy, method, args);
        }
      }
    }
  }
}
