package org.javity.demo;

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.*;
import com.badlogic.gdx.utils.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.IntBuffer;
import java.util.Arrays;
import net.mgsx.gltf.scene3d.attributes.PBRColorAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute;
import org.javity.core.*;
import org.javity.core.Game;
import org.javity.gltf.*;
import org.javity.render.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

/** Real GL30 HDR and negative-control pixels; no window screenshot or CPU shader imitation. */
public final class HdrSmokeLauncher {
  private HdrSmokeLauncher() {}

  public static void main(String[] args) {
    Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("Javity HDR proof");
    config.setWindowedMode(96, 96);
    config.setInitialVisible(false);
    config.disableAudio(true);
    config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
    Probe probe = new Probe();
    new Lwjgl3Application(probe, config);
    if (probe.failure != null) throw new IllegalStateException("HDR smoke failed", probe.failure);
  }

  private static final class Probe extends ApplicationAdapter {
    Throwable failure;
    HdrWorldPass hdr;
    RecoverableFrameBuffer output, ldr;
    ShaderProgram source;
    Mesh quad;
    Game game;
    GdxRenderer renderer;
    Model model;
    Camera3D pbrCamera;
    long originalContext, replacementContext;
    GLCapabilities originalCapabilities;

    @Override
    public void create() {
      try {
        prove();
      } catch (Throwable problem) {
        failure = problem;
        problem.printStackTrace();
      } finally {
        for (Disposable resource :
            new Disposable[] {renderer, model, source, quad, hdr, ldr, output})
          if (resource != null)
            try {
              resource.dispose();
            } catch (Throwable problem) {
              if (failure == null) failure = problem;
              else if (problem != failure) failure.addSuppressed(problem);
            }
        if (game != null) game.close();
        if (replacementContext != 0) {
          GLFW.glfwMakeContextCurrent(originalContext);
          GL.setCapabilities(originalCapabilities);
          GLFW.glfwDestroyWindow(replacementContext);
        }
        Gdx.app.exit();
      }
    }

    private void prove() {
      output = new RecoverableFrameBuffer(Pixmap.Format.RGBA8888, 64, 64, true);
      ldr = new RecoverableFrameBuffer(Pixmap.Format.RGBA8888, 64, 64, true);
      source =
          new ShaderProgram(
              "attribute vec2 a_position; varying vec2 uv; void"
                  + " main(){uv=a_position*.5+.5;gl_Position=vec4(a_position,0.,1.);}",
              "#ifdef GL_ES\n"
                  + "precision highp float;\n"
                  + "#endif\n"
                  + "varying vec2 uv;void main(){float"
                  + " x=uv.x<.5?1.:4.;gl_FragColor=vec4(x,x,x,1.);}");
      require(source.isCompiled(), source.getLog());
      quad =
          new Mesh(
              true, 4, 0, new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"));
      quad.setVertices(new float[] {-1, -1, 1, -1, -1, 1, 1, 1});
      hdr = new HdrWorldPass(64, 64);
      hdr.setSettings(new HdrSettings(1, HdrSettings.ToneMapper.REINHARD));
      ldr.begin();
      drawSource();
      int[] clipped = pair();
      ldr.end();
      require(
          Math.abs(clipped[0] - clipped[1]) <= 1 && clipped[0] == 255,
          "LDR negative control did not clamp: " + Arrays.toString(clipped));
      output.begin();
      hdr.renderWorld(this::drawSource);
      int[] retained = pair();
      // Independent reference values from the sRGB standard: encode(1/2)=188, encode(4/5)=231.
      require(
          Math.abs(retained[0] - 188) <= 2 && Math.abs(retained[1] - 231) <= 2,
          "HDR radiance or sRGB conversion incorrect: " + Arrays.toString(retained));
      callbackSamplerRegression(retained);
      image("reinhard-1-vs-4");
      hdr.setSettings(new HdrSettings(.25f, HdrSettings.ToneMapper.REINHARD));
      hdr.renderWorld(this::drawSource);
      int[] exposed = pair();
      require(
          Math.abs(exposed[0] - 124) <= 2 && Math.abs(exposed[1] - 188) <= 2,
          "linear exposure wrong: " + Arrays.toString(exposed));
      hdr.setSettings(HdrSettings.DEFAULT);
      hdr.renderWorld(this::drawSource);
      int[] aces = pair();
      require(aces[1] > aces[0] + 10, "ACES did not retain highlight distinction");
      image("aces-1-vs-4");
      // UI written after composition stays in display color space and is never tone mapped.
      Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
      Gdx.gl.glScissor(0, 0, 8, 8);
      Gdx.gl.glClearColor(1, 0, 0, 1);
      Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
      byte[] ui = ScreenUtils.getFrameBufferPixels(2, 2, 1, 1, false);
      require((ui[0] & 255) == 255 && (ui[1] & 255) == 0, "UI was unexpectedly transformed");
      Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);

      // Seed hostile caller state, including sampler bindings and explicit sRGB framebuffer
      // conversion.
      IntBuffer samplerBuffer = BufferUtils.newIntBuffer(1);
      Gdx.gl30.glGenSamplers(1, samplerBuffer);
      int sampler = samplerBuffer.get(0);
      Gdx.gl30.glBindSampler(0, sampler);
      Gdx.gl.glEnable(GL20.GL_BLEND);
      Gdx.gl.glEnable(GL20.GL_STENCIL_TEST);
      Gdx.gl.glEnable(GL30.GL_RASTERIZER_DISCARD);
      Gdx.gl.glEnable(0x8DB9);
      Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
      Gdx.gl.glScissor(3, 4, 17, 18);
      Gdx.gl.glColorMask(false, true, false, true);
      Gdx.gl.glDepthMask(false);
      Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0 + 3);
      int[] before = fingerprint();
      hdr.renderWorld(this::drawSource);
      require(Arrays.equals(before, fingerprint()), "successful HDR capture changed caller state");
      RuntimeException sentinel = new RuntimeException("expected borrowed callback failure");
      try {
        hdr.renderWorld(
            () -> {
              drawSource();
              throw sentinel;
            });
        throw new AssertionError("callback error swallowed");
      } catch (RuntimeException expected) {
        require(expected == sentinel, "primary failure changed");
      }
      require(Arrays.equals(before, fingerprint()), "failed HDR capture changed caller state");
      try {
        hdr.renderWorld(() -> hdr.resize(32, 32));
        throw new AssertionError("reentrant resize accepted");
      } catch (IllegalStateException expected) {
      }
      require(Arrays.equals(before, fingerprint()), "reentrant failure changed state");
      Gdx.gl.glDisable(0x8DB9);
      Gdx.gl.glDisable(GL20.GL_STENCIL_TEST);
      Gdx.gl.glDisable(GL30.GL_RASTERIZER_DISCARD);
      Gdx.gl.glDisable(GL20.GL_BLEND);
      Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
      Gdx.gl.glColorMask(true, true, true, true);
      Gdx.gl.glDepthMask(true);
      Gdx.gl30.glBindSampler(0, 0);
      samplerBuffer.put(0, sampler);
      Gdx.gl30.glDeleteSamplers(1, samplerBuffer);
      Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
      var old = hdr.resourceIds();
      Gdx.gl.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, old.framebuffer());
      Gdx.gl.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, old.framebuffer());
      Gdx.gl.glBindRenderbuffer(GL20.GL_RENDERBUFFER, old.depthRenderbuffer());
      Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0 + 2);
      Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, old.colorTexture());
      hdr.resize(32, 48);
      var resized = hdr.resourceIds();
      require(
          integer(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == resized.framebuffer()
              && integer(GL30.GL_READ_FRAMEBUFFER_BINDING) == resized.framebuffer()
              && integer(GL20.GL_RENDERBUFFER_BINDING) == resized.depthRenderbuffer()
              && integer(GL20.GL_TEXTURE_BINDING_2D) == resized.colorTexture(),
          "resize restored retired owned names");
      int[] boundOwnedState = fingerprint();
      try {
        hdr.renderWorld(this::drawSource);
        throw new AssertionError("HDR texture feedback accepted");
      } catch (IllegalStateException expected) {
      }
      require(Arrays.equals(boundOwnedState, fingerprint()), "feedback rejection changed GL state");
      output.begin();
      require(hdr.width() == 32 && hdr.height() == 48, "resize dimensions lost");
      require(
          !Gdx.gl.glIsFramebuffer(old.framebuffer())
              && !Gdx.gl.glIsTexture(old.colorTexture())
              && !Gdx.gl.glIsRenderbuffer(old.depthRenderbuffer()),
          "retired target survived resize");
      hdr.setSettings(new HdrSettings(1, HdrSettings.ToneMapper.REINHARD));
      hdr.renderWorld(this::drawSource);
      require(Arrays.equals(retained, pair()), "resized capture lost HDR values");
      rejectIncompleteTarget();
      rejectUnexpectedAllocationThrow();
      var valid = hdr.resourceIds();
      try {
        hdr.resize(4096, 4096);
        throw new AssertionError("overbudget target accepted");
      } catch (IllegalArgumentException expected) {
      }
      require(hdr.resourceIds().equals(valid), "rejected resize changed published target");
      output.end();
      pbrProof();
      recoveryProof();
      var closed = hdr.resourceIds();
      require(
          closed.vertexBuffer() != 0 && closed.vertexArray() != 0,
          "HDR quad resource tracking missing");
      hdr.dispose();
      hdr.dispose();
      require(
          !Gdx.gl.glIsFramebuffer(closed.framebuffer())
              && !Gdx.gl.glIsTexture(closed.colorTexture())
              && !Gdx.gl.glIsRenderbuffer(closed.depthRenderbuffer())
              && !Gdx.gl.glIsProgram(closed.shaderProgram())
              && !Gdx.gl.glIsBuffer(closed.vertexBuffer())
              && !Gdx.gl30.glIsVertexArray(closed.vertexArray()),
          "owned HDR GL resource survived close");
      require(
          Gdx.gl.glIsTexture(output.getColorBufferTexture().getTextureObjectHandle())
              && source.isCompiled(),
          "HDR disposed borrowed resource");
      require(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "GL error after HDR proof");
      System.out.println(
          "hdrSmoke: PASS RGBA8="
              + Arrays.toString(clipped)
              + " RGBA16F="
              + Arrays.toString(retained)
              + " exposure="
              + Arrays.toString(exposed)
              + " ACES="
              + Arrays.toString(aces)
              + " (linear PBR, UI, callback sampler, state/throw, resize/budget, managed recovery,"
              + " owned cleanup)");
    }

    private void callbackSamplerRegression(int[] expectedPixels) {
      int previousActiveTexture = integer(GL20.GL_ACTIVE_TEXTURE);
      IntBuffer handle = BufferUtils.newIntBuffer(1);
      Gdx.gl30.glGenSamplers(1, handle);
      int sampler = handle.get(0);
      try {
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        Gdx.gl30.glBindSampler(0, 0);
        require(integer(0x8919) == 0, "sampler regression must enter with unit0 sampler unbound");
        Gdx.gl30.glSamplerParameteri(
            sampler, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_LINEAR_MIPMAP_LINEAR);
        hdr.renderWorld(
            () -> {
              drawSource();
              // This sampler requires mipmaps; the HDR target deliberately has only level0.
              Gdx.gl30.glBindSampler(0, sampler);
            });
        int[] actual = pair();
        require(
            Arrays.equals(actual, expectedPixels),
            "callback sampler corrupted HDR composition: " + Arrays.toString(actual));
        require(integer(0x8919) == 0, "HDR failed restoring entry unit0 sampler binding");
        require(
            Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "GL error during callback sampler regression");
      } finally {
        Gdx.gl30.glBindSampler(0, 0);
        handle.put(0, sampler);
        Gdx.gl30.glDeleteSamplers(1, handle);
        Gdx.gl.glActiveTexture(previousActiveTexture);
      }
    }

    private void pbrProof() {
      game = new Game();
      GameObject cameraObject = game.createObject("HDR camera");
      Camera3D camera = cameraObject.addComponent(new Camera3D(55));
      pbrCamera = camera;
      camera.camera.position.set(0, 0, 3);
      camera.camera.lookAt(0, 0, 0);
      camera.camera.near = .1f;
      camera.camera.far = 10;
      ColorAttribute emissive = ColorAttribute.createEmissive(1, 1, 1, 1);
      Material material =
          new Material(
              PBRColorAttribute.createBaseColorFactor(Color.BLACK),
              emissive,
              PBRFloatAttribute.createEmissiveIntensity(1));
      model =
          new ModelBuilder()
              .createBox(
                  2,
                  2,
                  .1f,
                  material,
                  VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
      GameObject object = game.createObject("linear emissive model");
      object.addComponent(new Transform3D());
      ModelRenderer component = object.addComponent(new ModelRenderer(model));
      game.start();
      game.step(0);
      PbrShaderProvider provider =
          new PbrShaderProvider(12, 1, 0, 0, PbrShaderProvider.OutputColorSpace.LINEAR);
      renderer = provider.createRenderer();
      renderer.resize(64, 64);
      output.begin();
      hdr.renderWorld(() -> renderer.renderWorld(game, camera, null));
      int one = center();
      component
              .instance
              .materials
              .first()
              .get(PBRFloatAttribute.class, PBRFloatAttribute.EmissiveIntensity)
              .value =
          4;
      hdr.renderWorld(() -> renderer.renderWorld(game, camera, null));
      int four = center();
      image("pbr-emissive-4");
      output.end();
      require(
          Math.abs(one - 188) <= 3 && Math.abs(four - 231) <= 3,
          "PBR was not linear HDR: " + one + "," + four);
    }

    private void rejectIncompleteTarget() {
      var previous = hdr.resourceIds();
      int[] before = fingerprint();
      GL20 saved = Gdx.gl, saved20 = Gdx.gl20;
      GL30 saved30 = Gdx.gl30;
      boolean[] injected = {false};
      GL30 wrapped =
          (GL30)
              Proxy.newProxyInstance(
                  GL30.class.getClassLoader(),
                  new Class<?>[] {GL30.class},
                  (proxy, method, arguments) -> {
                    if (method.getName().equals("glCheckFramebufferStatus")) {
                      injected[0] = true;
                      return GL20.GL_FRAMEBUFFER_UNSUPPORTED;
                    }
                    try {
                      return method.invoke(saved30, arguments);
                    } catch (InvocationTargetException failure) {
                      throw failure.getCause();
                    }
                  });
      try {
        Gdx.gl = wrapped;
        Gdx.gl20 = wrapped;
        Gdx.gl30 = wrapped;
        try {
          hdr.resize(40, 40);
          throw new AssertionError("incomplete float target accepted");
        } catch (IllegalStateException expected) {
          require(
              expected.getMessage().contains("unsupported"), "unexpected target allocation error");
        }
      } finally {
        Gdx.gl = saved;
        Gdx.gl20 = saved20;
        Gdx.gl30 = saved30;
      }
      require(
          injected[0] && previous.equals(hdr.resourceIds()),
          "incomplete target replaced working HDR target");
      require(
          Arrays.equals(before, fingerprint()), "incomplete target allocation changed GL state");
    }

    private void rejectUnexpectedAllocationThrow() {
      var previous = hdr.resourceIds();
      int[] before = fingerprint();
      GL20 saved = Gdx.gl, saved20 = Gdx.gl20;
      GL30 saved30 = Gdx.gl30;
      int[] allocated = new int[3];
      RuntimeException sentinel = new RuntimeException("injected GL observer failure");
      GL30 wrapped =
          (GL30)
              Proxy.newProxyInstance(
                  GL30.class.getClassLoader(),
                  new Class<?>[] {GL30.class},
                  (proxy, method, arguments) -> {
                    if (method.getName().equals("glCheckFramebufferStatus")) throw sentinel;
                    Object result;
                    try {
                      result = method.invoke(saved30, arguments);
                    } catch (InvocationTargetException failure) {
                      throw failure.getCause();
                    }
                    if (method.getName().equals("glGenFramebuffer"))
                      allocated[0] = (Integer) result;
                    if (method.getName().equals("glGenRenderbuffer"))
                      allocated[1] = (Integer) result;
                    if (method.getName().equals("glGenTexture")) allocated[2] = (Integer) result;
                    return result;
                  });
      try {
        Gdx.gl = wrapped;
        Gdx.gl20 = wrapped;
        Gdx.gl30 = wrapped;
        try {
          hdr.resize(40, 40);
          throw new AssertionError("unexpected allocation exception missing");
        } catch (RuntimeException problem) {
          require(problem == sentinel, "allocation failure identity changed");
        }
      } finally {
        Gdx.gl = saved;
        Gdx.gl20 = saved20;
        Gdx.gl30 = saved30;
      }
      require(
          allocated[0] != 0 && allocated[1] != 0 && allocated[2] != 0,
          "allocation exception fixture did not allocate complete candidate");
      require(
          !Gdx.gl.glIsFramebuffer(allocated[0])
              && !Gdx.gl.glIsRenderbuffer(allocated[1])
              && !Gdx.gl.glIsTexture(allocated[2]),
          "unexpected allocation exception leaked owned candidate objects");
      require(
          previous.equals(hdr.resourceIds()),
          "unexpected allocation exception replaced working HDR target");
      require(
          Arrays.equals(before, fingerprint()),
          "unexpected allocation exception changed caller GL state");
    }

    private void recoveryProof() {
      String registry = GLFrameBuffer.getManagedStatus();
      originalContext = GLFW.glfwGetCurrentContext();
      originalCapabilities = GL.getCapabilities();
      GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
      replacementContext = GLFW.glfwCreateWindow(96, 96, "HDR unshared replacement", 0, 0);
      require(replacementContext != 0, "replacement GL context unavailable");
      GLFW.glfwMakeContextCurrent(replacementContext);
      GL.createCapabilities();
      // No shared objects: old texture/FBO/mesh/program names belong to the original context.
      Texture.invalidateAllTextures(Gdx.app);
      ShaderProgram.invalidateAllShaderPrograms(Gdx.app);
      Mesh.invalidateAllMeshes(Gdx.app);
      GLFrameBuffer.invalidateAllFrameBuffers(Gdx.app);
      Gdx.gl.glUseProgram(hdr.resourceIds().shaderProgram());
      hdr.contextRestored();
      require(
          integer(GL20.GL_CURRENT_PROGRAM) == hdr.resourceIds().shaderProgram(),
          "shader recovery restored retired program");
      renderer.restoreContextShaderBindings();
      require(
          registry.equals(GLFrameBuffer.getManagedStatus()),
          "managed target registration grew on rebuild");
      output.begin();
      hdr.renderWorld(this::drawSource);
      require(
          Math.abs(pair()[1] - 231) <= 2,
          "recovered HDR capture incorrect: " + Arrays.toString(pair()));
      image("recovered");
      hdr.renderWorld(() -> renderer.renderWorld(game, pbrCamera, null));
      require(
          Math.abs(center() - 231) <= 3,
          "borrowed PBR model did not recover linear HDR radiance: " + center());
      image("pbr-recovered");
      output.end();
    }

    private void drawSource() {
      Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
      Gdx.gl.glDisable(GL20.GL_BLEND);
      Gdx.gl.glDisable(GL20.GL_CULL_FACE);
      source.bind();
      quad.render(source, GL20.GL_TRIANGLE_STRIP);
    }

    private int[] pair() {
      byte[] p = ScreenUtils.getFrameBufferPixels(0, 0, 64, 64, false);
      return new int[] {p[(32 * 64 + 16) * 4] & 255, p[(32 * 64 + 48) * 4] & 255};
    }

    private int center() {
      return ScreenUtils.getFrameBufferPixels(32, 32, 1, 1, false)[0] & 255;
    }

    private void image(String name) {
      Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, 64, 64);
      try {
        PixmapIO.writePNG(Gdx.files.local("build/reports/hdr/" + name + ".png"), pixels);
      } finally {
        pixels.dispose();
      }
    }

    private static int integer(int name) {
      IntBuffer buffer = BufferUtils.newIntBuffer(1);
      Gdx.gl.glGetIntegerv(name, buffer);
      return buffer.get(0);
    }

    private static int[] fingerprint() {
      int[] names = {
        GL30.GL_DRAW_FRAMEBUFFER_BINDING,
        GL30.GL_READ_FRAMEBUFFER_BINDING,
        GL20.GL_RENDERBUFFER_BINDING,
        GL30.GL_VERTEX_ARRAY_BINDING,
        GL20.GL_ARRAY_BUFFER_BINDING,
        GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING,
        GL20.GL_CURRENT_PROGRAM,
        GL20.GL_ACTIVE_TEXTURE,
        GL20.GL_TEXTURE_BINDING_2D,
        GL20.GL_DEPTH_WRITEMASK,
        GL20.GL_DEPTH_FUNC,
        GL20.GL_BLEND_SRC_RGB,
        GL20.GL_BLEND_DST_RGB,
        0x8919
      };
      int[] result = new int[names.length + 12 + 7];
      IntBuffer buffer = BufferUtils.newIntBuffer(4);
      int offset = 0;
      for (int name : names) {
        buffer.clear();
        Gdx.gl.glGetIntegerv(name, buffer);
        result[offset++] = buffer.get(0);
      }
      for (int name : new int[] {GL20.GL_VIEWPORT, GL20.GL_SCISSOR_BOX, GL20.GL_COLOR_WRITEMASK}) {
        buffer.clear();
        Gdx.gl.glGetIntegerv(name, buffer);
        for (int i = 0; i < 4; i++) result[offset++] = buffer.get(i);
      }
      for (int name :
          new int[] {
            GL20.GL_DEPTH_TEST,
            GL20.GL_BLEND,
            GL20.GL_CULL_FACE,
            GL20.GL_SCISSOR_TEST,
            GL20.GL_STENCIL_TEST,
            GL30.GL_RASTERIZER_DISCARD,
            0x8DB9
          }) result[offset++] = Gdx.gl.glIsEnabled(name) ? 1 : 0;
      return result;
    }

    private static void require(boolean condition, String message) {
      if (!condition) throw new AssertionError(message);
    }
  }
}
