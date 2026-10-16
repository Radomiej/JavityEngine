package org.javity.demo;

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.*;
import com.badlogic.gdx.utils.BufferUtils;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.Path;
import java.util.*;

/** Real native sky contract gate; deliberately separate from the combined environment demo. */
public final class SkyGpuSmokeLauncher {
  public static void main(String[] args) throws Exception {
    boolean gl20 = args.length > 0;
    var config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("Sky pass contract");
    config.setWindowedMode(96, 96);
    config.setInitialVisible(false);
    config.setOpenGLEmulation(
        gl20
            ? Lwjgl3ApplicationConfiguration.GLEmulation.GL20
            : Lwjgl3ApplicationConfiguration.GLEmulation.GL30,
        gl20 ? 2 : 3,
        gl20 ? 0 : 2);
    Probe probe = new Probe();
    new Lwjgl3Application(probe, config);
    if (probe.failure != null) throw new AssertionError("sky contract failed", probe.failure);
    System.out.println("skySmoke PASS " + (gl20 ? "GL20" : "GL30"));
    if (!gl20) {
      int exit =
          new ProcessBuilder(
                  Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                  "--enable-native-access=ALL-UNNAMED",
                  "-cp",
                  System.getProperty("java.class.path"),
                  SkyGpuSmokeLauncher.class.getName(),
                  "--gl20")
              .inheritIO()
              .start()
              .waitFor();
      if (exit != 0) throw new AssertionError("GL20 child failed " + exit);
    }
  }

  private static final class Probe extends ApplicationAdapter {
    Throwable failure;
    Class<?> settingsType, passType;
    Object pass, gradient;
    FrameBuffer target;
    PerspectiveCamera camera;

    @Override
    public void create() {
      try {
        run();
      } catch (Throwable t) {
        failure = t;
        t.printStackTrace();
      } finally {
        Gdx.app.exit();
      }
    }

    void run() throws Exception {
      // Reflection lets this same native gate run RED before the new public API exists.
      String globalVertex = ShaderProgram.prependVertexCode,
          globalFragment = ShaderProgram.prependFragmentCode;
      boolean globalPedantic = ShaderProgram.pedantic;
      settingsType = Class.forName("org.javity.render.sky.SkySettings");
      passType = Class.forName("org.javity.render.sky.SkyboxRenderer");
      gradient =
          settingsType
              .getMethod("gradient", Color.class, Color.class, Color.class)
              .invoke(null, Color.BLUE, Color.GREEN, Color.RED);
      pass = passType.getConstructor().newInstance();
      target = new FrameBuffer(Pixmap.Format.RGBA8888, 96, 96, true);
      camera = new PerspectiveCamera(65, 96, 96);
      camera.near = .1f;
      camera.far = 100;
      camera.direction.set(0, 0, -1);
      camera.up.set(0, 1, 0);
      camera.update();
      try {
        target.begin();
        clear();
        draw(gradient);
        byte[] initial = frame();
        camera.position.set(200, 71, -80);
        camera.update();
        clear();
        draw(gradient);
        check(Arrays.equals(initial, frame()), "camera translation changes sky");
        camera.direction.set(0, 1, -.01f).nor();
        camera.up.set(0, 0, 1);
        camera.update();
        clear();
        draw(gradient);
        int[] pixel = pixel(48, 48);
        check(
            pixel[2] > 240 && pixel[0] < 10 && pixel[1] < 15,
            "upward zenith rotation " + Arrays.toString(pixel));
        camera.direction.set(0, -1, -.01f).nor();
        camera.up.set(0, 0, -1);
        camera.update();
        clear();
        draw(gradient);
        pixel = pixel(48, 48);
        check(pixel[0] > 240 && pixel[1] < 15, "downward nadir rotation");
        camera.position.setZero();
        camera.direction.set(0, 0, -1);
        camera.up.set(0, 1, 0);
        camera.update();
        orthographic();
        cubemap();
        depth();
        state();
        failures();
        localGlesDialect();
        inputState();
        passType.getMethod("dispose").invoke(pass);
        lifecycle();
        integrationAndRecovery();
        target.end();
      } finally {
        passType.getMethod("dispose").invoke(pass);
        passType.getMethod("dispose").invoke(pass);
        target.dispose();
      }
      check(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "GL error");
      check(
          Objects.equals(globalVertex, ShaderProgram.prependVertexCode)
              && Objects.equals(globalFragment, ShaderProgram.prependFragmentCode)
              && globalPedantic == ShaderProgram.pedantic,
          "global shader policy changed");
    }

    void orthographic() throws Exception {
      OrthographicCamera orthographic = new OrthographicCamera(200, 200);
      orthographic.direction.set(0, 0, -1);
      orthographic.up.set(0, 1, 0);
      orthographic.near = .1f;
      orthographic.far = 100;
      orthographic.update();
      clear();
      passType.getMethod("render", Camera.class, settingsType).invoke(pass, orthographic, gradient);
      check(
          Arrays.equals(pixel(5, 5), pixel(90, 90)),
          "orthographic parallel rays must have one sky direction");
    }

    void cubemap() throws Exception {
      Pixmap[] faces = new Pixmap[6];
      Color[] colors = {
        Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.MAGENTA, Color.CYAN
      };
      for (int i = 0; i < 6; i++) {
        faces[i] = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
        faces[i].setColor(colors[i]);
        faces[i].fill();
      }
      Cubemap cube = new Cubemap(faces[0], faces[1], faces[2], faces[3], faces[4], faces[5]);
      try {
        Object settings = settingsType.getMethod("cubemap", Cubemap.class).invoke(null, cube);
        check(
            settingsType.getMethod("mode").invoke(settings).toString().equals("CUBEMAP"),
            "exclusive cubemap mode");
        clear();
        draw(settings);
        int[] p = pixel(48, 48);
        check(p[1] > 250 && p[2] > 250 && p[0] < 3, "negative Z cubemap face");
        byte[] before = frame();
        camera.position.set(90, -50, 400);
        camera.update();
        clear();
        draw(settings);
        check(Arrays.equals(before, frame()), "cubemap parallax");
        camera.direction.set(1, 0, 0);
        camera.update();
        clear();
        draw(settings);
        p = pixel(48, 48);
        check(p[0] > 250 && p[1] < 3, "positive X cubemap rotation");
        Object other = passType.getConstructor().newInstance();
        passType.getMethod("render", Camera.class, settingsType).invoke(other, camera, settings);
        passType.getMethod("dispose").invoke(other);
        passType.getMethod("dispose").invoke(other);
        check(Gdx.gl.glIsTexture(cube.getTextureObjectHandle()), "pass disposed borrowed cubemap");
      } finally {
        cube.dispose();
        for (Pixmap face : faces) face.dispose();
      }
      camera.position.setZero();
      camera.direction.set(0, 0, -1);
      camera.update();
    }

    void depth() throws Exception {
      ShaderProgram shader =
          new ShaderProgram(
              "attribute vec4 a_position; void main(){gl_Position=a_position;}",
              "#ifdef GL_ES\n"
                  + "precision mediump float;\n"
                  + "#endif\n"
                  + "void main(){gl_FragColor=vec4(1,1,1,1);}");
      check(shader.isCompiled(), shader.getLog());
      Mesh mesh = new Mesh(true, 3, 0, VertexAttribute.Position());
      mesh.setVertices(new float[] {-.5f, -.5f, 0, .5f, -.5f, 0, 0, .5f, 0});
      try {
        clear();
        draw(gradient);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LESS);
        Gdx.gl.glDepthMask(true);
        shader.bind();
        mesh.render(shader, GL20.GL_TRIANGLES);
        check(pixel(48, 48)[0] > 250, "sky wrote depth / occluded geometry");
        draw(gradient);
        check(pixel(48, 48)[0] > 250, "sky overwrote nearer depth");
      } finally {
        mesh.dispose();
        shader.dispose();
      }
    }

    void state() throws Exception {
      ShaderProgram sentinel =
          new ShaderProgram(
              "attribute vec4 a_position;void main(){gl_Position=a_position;}",
              "void main(){gl_FragColor=vec4(1);}");
      check(sentinel.isCompiled(), sentinel.getLog());
      Pixmap face = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
      face.setColor(Color.BLUE);
      face.fill();
      Cubemap cube = new Cubemap(face, face, face, face, face, face);
      int cube0 = Gdx.gl.glGenTexture(), cube3 = Gdx.gl.glGenTexture();
      try {
        sentinel.bind();
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_CUBE_MAP, cube0);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE3);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_CUBE_MAP, cube3);
        Gdx.gl.glViewport(3, 4, 81, 77);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_GREATER);
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glDepthRangef(.2f, .8f);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFuncSeparate(
            GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ONE, GL20.GL_ZERO);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_FRONT);
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(9, 8, 17, 16);
        Gdx.gl.glColorMask(false, true, false, true);
        Gdx.gl.glEnable(GL20.GL_STENCIL_TEST);
        Object cubemapSettings =
            settingsType.getMethod("cubemap", Cubemap.class).invoke(null, cube);
        for (Object settings : new Object[] {gradient, cubemapSettings}) {
          int[] before = stateValues();
          draw(settings);
          check(Arrays.equals(before, stateValues()), "caller state changed");
          try (GlProbe gl = new GlProbe()) {
            gl.throwAfterDraw = true;
            boolean rejected = false;
            try {
              draw(settings);
            } catch (InvocationTargetException expected) {
              rejected = expected.getCause() instanceof IllegalStateException;
            }
            check(rejected, "real draw did not trigger injected failure");
            check(Arrays.equals(before, stateValues()), "exception changed caller state");
          }
        }
      } finally {
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_CUBE_MAP, 0);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE3);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_CUBE_MAP, 0);
        Gdx.gl.glDeleteTexture(cube0);
        Gdx.gl.glDeleteTexture(cube3);
        cube.dispose();
        face.dispose();
        sentinel.dispose();
        Gdx.gl.glViewport(0, 0, 96, 96);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glDisable(GL20.GL_STENCIL_TEST);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDepthFunc(GL20.GL_LESS);
        Gdx.gl.glDepthRangef(0, 1);
        Gdx.gl.glColorMask(true, true, true, true);
      }
    }

    int[] stateValues() {
      int[] names = {
        GL20.GL_DEPTH_FUNC,
        GL20.GL_DEPTH_WRITEMASK,
        GL20.GL_CULL_FACE_MODE,
        GL20.GL_ACTIVE_TEXTURE,
        GL20.GL_CURRENT_PROGRAM,
        GL20.GL_ARRAY_BUFFER_BINDING,
        GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING,
        GL20.GL_TEXTURE_BINDING_CUBE_MAP,
        GL20.GL_BLEND_SRC_RGB,
        GL20.GL_BLEND_DST_RGB,
        GL20.GL_BLEND_SRC_ALPHA,
        GL20.GL_BLEND_DST_ALPHA,
        GL20.GL_BLEND_EQUATION_RGB,
        GL20.GL_BLEND_EQUATION_ALPHA,
        GL20.GL_FRAMEBUFFER_BINDING
      };
      java.util.ArrayList<Integer> result = new java.util.ArrayList<>();
      IntBuffer b = BufferUtils.newIntBuffer(4);
      for (int name : names) {
        Gdx.gl.glGetIntegerv(name, b);
        result.add(b.get(0));
      }
      for (int name : new int[] {GL20.GL_VIEWPORT, GL20.GL_SCISSOR_BOX, GL20.GL_COLOR_WRITEMASK}) {
        Gdx.gl.glGetIntegerv(name, b);
        for (int i = 0; i < 4; i++) result.add(b.get(i));
      }
      for (int name :
          new int[] {
            GL20.GL_DEPTH_TEST,
            GL20.GL_BLEND,
            GL20.GL_CULL_FACE,
            GL20.GL_SCISSOR_TEST,
            GL20.GL_STENCIL_TEST
          }) result.add(Gdx.gl.glIsEnabled(name) ? 1 : 0);
      Gdx.gl.glGetIntegerv(GL20.GL_ACTIVE_TEXTURE, b);
      int active = b.get(0);
      Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
      Gdx.gl.glGetIntegerv(GL20.GL_TEXTURE_BINDING_CUBE_MAP, b);
      result.add(b.get(0));
      Gdx.gl.glActiveTexture(active);
      FloatBuffer range = BufferUtils.newFloatBuffer(2);
      Gdx.gl.glGetFloatv(GL20.GL_DEPTH_RANGE, range);
      result.add(Float.floatToIntBits(range.get(0)));
      result.add(Float.floatToIntBits(range.get(1)));
      if (Gdx.gl30 != null) {
        Gdx.gl.glGetIntegerv(GL30.GL_VERTEX_ARRAY_BINDING, b);
        result.add(b.get(0));
      }
      return result.stream().mapToInt(Integer::intValue).toArray();
    }

    void failures() throws Exception {
      Method validate = passType.getDeclaredMethod("validate", String.class, String.class);
      validate.setAccessible(true);
      String vertex =
          "#version 120\n"
              + "attribute vec4 a_position;varying vec3 mismatch;void"
              + " main(){mismatch=a_position.xyz;gl_Position=a_position;}";
      String fragment =
          "#version 120\nvarying vec3 mismatch;void main(){gl_FragColor=vec4(mismatch,1);}";
      for (String[] sources :
          new String[][] {
            {"bad vertex", fragment},
            {vertex, "bad fragment"},
            {
              vertex,
              fragment
                  .replace("vec3 mismatch", "vec2 mismatch")
                  .replace("vec4(mismatch,1)", "vec4(mismatch,0,1)")
            }
          }) {
        try (GlProbe gl = new GlProbe()) {
          int[] before = stateValues();
          boolean rejected = false;
          try {
            validate.invoke(null, sources[0], sources[1]);
          } catch (InvocationTargetException expected) {
            rejected = expected.getCause() instanceof IllegalArgumentException;
          }
          check(rejected, "raw invalid shader/link accepted");
          check(Arrays.equals(before, stateValues()), "compile failure changed state");
          gl.assertReleased();
        }
      }
      // A real GL draw followed by a Java exception still restores all caller state.
      Gdx.gl.glEnable(GL20.GL_BLEND);
      Gdx.gl.glDepthMask(false);
      Gdx.gl.glDepthFunc(GL20.GL_GREATER);
      Gdx.gl.glActiveTexture(GL20.GL_TEXTURE3);
      int[] before = stateValues();
      try (GlProbe gl = new GlProbe()) {
        gl.throwAfterDraw = true;
        boolean rejected = false;
        try {
          draw(gradient);
        } catch (InvocationTargetException expected) {
          rejected = expected.getCause() instanceof IllegalStateException;
        }
        check(rejected, "draw failure was not injected");
        check(Arrays.equals(before, stateValues()), "exception changed caller state");
      }
      Gdx.gl.glDisable(GL20.GL_BLEND);
      Gdx.gl.glDepthMask(true);
      Gdx.gl.glDepthFunc(GL20.GL_LESS);
    }

    void localGlesDialect() throws Exception {
      Graphics graphics = Gdx.graphics;
      String[] sources;
      try {
        Gdx.graphics =
            (Graphics)
                Proxy.newProxyInstance(
                    Graphics.class.getClassLoader(),
                    new Class<?>[] {Graphics.class},
                    (proxy, method, args) -> {
                      if (method.getName().equals("getGLVersion"))
                        return new GLVersion(
                            Application.ApplicationType.Android,
                            "OpenGL ES 3.2",
                            "local dialect probe",
                            "local dialect probe");
                      try {
                        return method.invoke(graphics, args);
                      } catch (InvocationTargetException e) {
                        throw e.getCause();
                      }
                    });
        Method sourceMethod = passType.getDeclaredMethod("sources");
        sourceMethod.setAccessible(true);
        sources = (String[]) sourceMethod.invoke(null);
      } finally {
        Gdx.graphics = graphics;
      }
      check(
          sources[0].startsWith("#version 300 es") && sources[1].startsWith("#version 300 es"),
          "GLES3 local dialect missing");
      Method validate = passType.getDeclaredMethod("validate", String.class, String.class);
      validate.setAccessible(true);
      try (GlProbe resources = new GlProbe()) {
        validate.invoke(null, sources[0], sources[1]);
        resources.assertReleased();
      }
      System.out.println(
          "GLES300 sky sources compile/link on desktop Mesa compiler (no device/context claim)");
    }

    void inputState() throws Exception {
      ShaderProgram shader = (ShaderProgram) field(pass, "shader");
      int location = Gdx.gl.glGetAttribLocation(shader.getHandle(), "a_position");
      if (Gdx.gl30 == null) {
        int buffer = Gdx.gl.glGenBuffer();
        Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, buffer);
        Gdx.gl.glBufferData(
            GL20.GL_ARRAY_BUFFER, 32, BufferUtils.newByteBuffer(32), GL20.GL_STATIC_DRAW);
        Gdx.gl.glVertexAttribPointer(location, 2, GL20.GL_FLOAT, false, 8, 8);
        Gdx.gl.glEnableVertexAttribArray(location);
        int[] before = stateValues();
        boolean rejected = false;
        try {
          draw(gradient);
        } catch (InvocationTargetException expected) {
          rejected = expected.getCause() instanceof IllegalStateException;
        }
        check(rejected, "GL20 configured sky slot accepted");
        check(Arrays.equals(before, stateValues()), "GL20 rejection changed state");
        IntBuffer value = BufferUtils.newIntBuffer(1);
        Gdx.gl.glGetVertexAttribiv(location, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED, value);
        check(value.get(0) == 1, "rejected input disabled");
        Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, 0);
        Gdx.gl.glVertexAttribPointer(location, 4, GL20.GL_FLOAT, false, 0, 0);
        Gdx.gl.glDisableVertexAttribArray(location);
        Gdx.gl.glDeleteBuffer(buffer);
      } else {
        IntBuffer ids = BufferUtils.newIntBuffer(1);
        Gdx.gl30.glGenVertexArrays(1, ids);
        int vao = ids.get(0);
        Gdx.gl30.glBindVertexArray(vao);
        int buffer = Gdx.gl.glGenBuffer();
        Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, buffer);
        Gdx.gl.glBufferData(
            GL20.GL_ARRAY_BUFFER, 32, BufferUtils.newByteBuffer(32), GL20.GL_STATIC_DRAW);
        Gdx.gl.glVertexAttribPointer(location, 2, GL20.GL_FLOAT, false, 8, 8);
        Gdx.gl.glEnableVertexAttribArray(location);
        int[] before = stateValues();
        draw(gradient);
        check(Arrays.equals(before, stateValues()), "GL30 caller input state changed");
        Gdx.gl.glGetIntegerv(GL30.GL_VERTEX_ARRAY_BINDING, ids);
        check(ids.get(0) == vao, "caller VAO changed");
        Gdx.gl.glGetVertexAttribiv(location, GL20.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING, ids);
        check(ids.get(0) == buffer, "caller VAO descriptor changed");
        Gdx.gl30.glBindVertexArray(0);
        ids.put(0, vao);
        Gdx.gl30.glDeleteVertexArrays(1, ids);
        Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, 0);
        Gdx.gl.glDeleteBuffer(buffer);
      }
    }

    void lifecycle() throws Exception {
      try (GlProbe resources = new GlProbe()) {
        resources.failAfterMesh = true;
        int[] before = stateValues();
        boolean rejected = false;
        try {
          passType.getConstructor().newInstance();
        } catch (InvocationTargetException expected) {
          rejected = expected.getCause() instanceof IllegalStateException;
        }
        check(rejected, "constructor failure after mesh creation not injected");
        check(Arrays.equals(before, stateValues()), "constructor failure changed caller state");
        resources.assertReleased();
      }
      try (GlProbe resources = new GlProbe()) {
        Object owned = passType.getConstructor().newInstance();
        passType.getMethod("render", Camera.class, settingsType).invoke(owned, camera, gradient);
        check(!resources.buffers.isEmpty(), "owned sky mesh did not allocate a VBO");
        passType.getMethod("dispose").invoke(owned);
        passType.getMethod("dispose").invoke(owned);
        resources.assertReleased();
        boolean rejected = false;
        try {
          passType.getMethod("render", Camera.class, settingsType).invoke(owned, camera, gradient);
        } catch (InvocationTargetException expected) {
          rejected = expected.getCause() instanceof IllegalStateException;
        }
        check(rejected, "disposed sky still draws");
      }
    }

    void integrationAndRecovery() throws Exception {
      org.javity.render.GdxRenderer renderer;
      GlProbe resources = new GlProbe();
      try {
        renderer = new org.javity.render.GdxRenderer();
      } finally {
        resources.close();
      }
      try (org.javity.core.Game scene = new org.javity.core.Game()) {
        var environment = scene.addSystem(new org.javity.render.lighting.RenderEnvironment3D());
        environment.setSky(
            org.javity.render.sky.SkySettings.gradient(Color.BLUE, Color.GREEN, Color.RED));
        var view = scene.createObject("camera").addComponent(new org.javity.render.Camera3D(65));
        view.camera.position.set(0, 0, 3);
        view.camera.lookAt(0, 0, 0);
        view.camera.update();
        scene.start();
        renderer.resize(96, 96);
        try (GlProbe skyResources = new GlProbe()) {
          clear();
          renderer.renderWorld(scene, view, null);
          resources.programs.addAll(skyResources.programs);
          resources.shaders.addAll(skyResources.shaders);
        }
        byte[] before = frame();
        check(pixel(48, 48)[1] > 240, "world renderer omitted sky");
        clear();
        renderer.renderWorld(scene, view, null);
        check(Arrays.equals(before, frame()), "second world frame changed sky");
        Object ownedSky = field(renderer, "sky");
        ShaderProgram program = (ShaderProgram) field(ownedSky, "shader");
        int oldPosition = Gdx.gl.glGetAttribLocation(program.getHandle(), "a_position"),
            oldUniform = Gdx.gl.glGetUniformLocation(program.getHandle(), "u_inverse");
        // Simulate managed program recreation with real driver compilation and deliberately moved
        // locations. The stale ShaderProgram maps remain untouched throughout this test.
        Gdx.gl.glUseProgram(0);
        for (int id : resources.programs) if (Gdx.gl.glIsProgram(id)) Gdx.gl.glDeleteProgram(id);
        for (int id : resources.shaders) if (Gdx.gl.glIsShader(id)) Gdx.gl.glDeleteShader(id);
        try (GlProbe relink = new GlProbe()) {
          relink.relocation = true;
          ShaderProgram.invalidateAllShaderPrograms(Gdx.app);
        }
        check(program.isCompiled(), "managed relink compile: " + program.getLog());
        int newPosition = Gdx.gl.glGetAttribLocation(program.getHandle(), "a_position"),
            newUniform = Gdx.gl.glGetUniformLocation(program.getHandle(), "u_inverse");
        check(
            oldPosition != newPosition && oldUniform != newUniform,
            "driver locations did not move");
        check(
            program.getUniformLocation("u_inverse") != newUniform,
            "alias cache unexpectedly refreshed");
        renderer.restoreContextShaderBindings();
        clear();
        renderer.renderWorld(scene, view, null);
        check(Arrays.equals(before, frame()), "relinked sky differs after renderer recovery hook");
        System.out.println(
            "sky recovery raw positions "
                + oldPosition
                + " -> "
                + newPosition
                + ", uniform "
                + oldUniform
                + " -> "
                + newUniform
                + "; stale aliases retained");
        // Background marker survives the viewmodel pass; actual white hands geometry still draws.
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(0, 0, 12, 12);
        Gdx.gl.glClearColor(1, 0, 1, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        var model =
            new com.badlogic.gdx.graphics.g3d.utils.ModelBuilder()
                .createBox(
                    1,
                    1,
                    1,
                    new com.badlogic.gdx.graphics.g3d.Material(
                        com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(
                            Color.WHITE)),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        try {
          renderer.renderViewmodel(
              view.camera, new com.badlogic.gdx.graphics.g3d.ModelInstance(model));
          check(pixel(48, 48)[0] > 80, "viewmodel not drawn");
          check(
              pixel(5, 5)[0] > 250 && pixel(5, 5)[2] > 250,
              "sky redrawn over postprocess marker by viewmodel");
        } finally {
          model.dispose();
        }
        var hud = scene.createObject("SCREEN sentinel");
        hud.transform.position.set(75, 75);
        var shape = hud.addComponent(org.javity.render.ShapeRenderer2D.rectangle(20, 20));
        shape.space = org.javity.render.ShapeRenderer2D.Space.SCREEN;
        shape.color.set(Color.YELLOW);
        scene.step(0);
        renderer.render2D(scene);
        check(
            pixel(75, 75)[0] > 250 && pixel(75, 75)[1] > 250 && pixel(75, 75)[2] < 3,
            "SCREEN sentinel changed");
        screenshot();
        environment.clearSky();
        clear();
        renderer.renderWorld(scene, view, null);
        check(pixel(48, 48)[0] == 0 && pixel(48, 48)[1] == 0, "cleared sky retained");
      } finally {
        renderer.dispose();
      }
    }

    void screenshot() throws Exception {
      Pixmap image = new Pixmap(96, 96, Pixmap.Format.RGBA8888);
      byte[] pixels = frame();
      for (int y = 0; y < 96; y++)
        for (int x = 0; x < 96; x++) {
          int offset = (y * 96 + x) * 4;
          image.drawPixel(
              x,
              95 - y,
              ((pixels[offset] & 255) << 24)
                  | ((pixels[offset + 1] & 255) << 16)
                  | ((pixels[offset + 2] & 255) << 8)
                  | 255);
        }
      try {
        PixmapIO.writePNG(
            Gdx.files.local("docs/sky-pass-task2-" + (Gdx.gl30 == null ? "gl20" : "gl30") + ".png"),
            image);
      } finally {
        image.dispose();
      }
    }

    Object field(Object value, String name) throws Exception {
      Field f = value.getClass().getDeclaredField(name);
      f.setAccessible(true);
      return f.get(value);
    }

    final class GlProbe implements AutoCloseable, InvocationHandler {
      final GL20 original = Gdx.gl, original20 = Gdx.gl20;
      final GL30 original30 = Gdx.gl30;
      final Set<Integer> programs = new HashSet<>(),
          shaders = new HashSet<>(),
          skyShaders = new HashSet<>(),
          buffers = new HashSet<>(),
          vaos = new HashSet<>();
      boolean throwAfterDraw, relocation, failAfterMesh;

      GlProbe() {
        GL20 proxy =
            (GL20)
                Proxy.newProxyInstance(
                    GL20.class.getClassLoader(),
                    new Class<?>[] {original30 == null ? GL20.class : GL30.class},
                    this);
        Gdx.gl = proxy;
        Gdx.gl20 = proxy;
        if (original30 != null) Gdx.gl30 = (GL30) proxy;
      }

      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        if (relocation
            && name.equals("glShaderSource")
            && ((String) args[1]).contains("v_direction")) {
          skyShaders.add((int) args[0]);
          String source = (String) args[1];
          source =
              source.replace(
                  "#version 120\n",
                  "#version 120\n"
                      + "#extension GL_ARB_explicit_uniform_location : require\n"
                      + "#extension GL_ARB_explicit_attrib_location : require\n");
          source =
              source.replace("uniform mat4 u_inverse", "layout(location=8) uniform mat4 u_inverse");
          args[1] = source;
        }
        if (relocation && name.equals("glAttachShader") && skyShaders.contains((int) args[1]))
          original.glBindAttribLocation((int) args[0], 13, "a_position");
        Object result;
        try {
          result = method.invoke(original30 == null ? original20 : original30, args);
        } catch (InvocationTargetException e) {
          throw e.getCause();
        }
        if (failAfterMesh
            && !buffers.isEmpty()
            && name.equals("glGetUniformLocation")
            && args[1].equals("u_inverse")) {
          failAfterMesh = false;
          return -1;
        }
        if (name.equals("glCreateProgram")) programs.add((int) result);
        if (name.equals("glCreateShader")) shaders.add((int) result);
        if (name.equals("glGenBuffer")) buffers.add((int) result);
        if (name.equals("glGenVertexArrays")) {
          IntBuffer ids = (IntBuffer) args[1];
          for (int i = 0; i < (int) args[0]; i++) vaos.add(ids.get(ids.position() + i));
        }
        if (throwAfterDraw && name.equals("glDrawArrays")) {
          throwAfterDraw = false;
          throw new IllegalStateException("injected after real sky draw");
        }
        return result;
      }

      void assertReleased() {
        for (int id : programs) check(!original.glIsProgram(id), "leaked program " + id);
        for (int id : shaders) check(!original.glIsShader(id), "leaked shader " + id);
        for (int id : buffers) check(!original.glIsBuffer(id), "leaked buffer " + id);
        for (int id : vaos) check(!original30.glIsVertexArray(id), "leaked VAO " + id);
      }

      @Override
      public void close() {
        Gdx.gl = original;
        Gdx.gl20 = original20;
        Gdx.gl30 = original30;
      }
    }

    void draw(Object settings) throws Exception {
      passType.getMethod("render", Camera.class, settingsType).invoke(pass, camera, settings);
    }

    void clear() {
      Gdx.gl.glDepthMask(true);
      Gdx.gl.glClearColor(0, 0, 0, 1);
      Gdx.gl.glClearDepthf(1);
      Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    }

    int[] pixel(int x, int y) {
      ByteBuffer b = BufferUtils.newByteBuffer(4);
      Gdx.gl.glReadPixels(x, y, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, b);
      return new int[] {b.get(0) & 255, b.get(1) & 255, b.get(2) & 255, b.get(3) & 255};
    }

    byte[] frame() {
      ByteBuffer b = BufferUtils.newByteBuffer(96 * 96 * 4);
      Gdx.gl.glReadPixels(0, 0, 96, 96, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, b);
      byte[] result = new byte[b.capacity()];
      b.get(result);
      return result;
    }

    void check(boolean ok, String message) {
      if (!ok) throw new AssertionError(message);
    }
  }
}
