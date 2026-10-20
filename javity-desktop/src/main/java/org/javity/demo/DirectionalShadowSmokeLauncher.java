package org.javity.demo;

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.IntBuffer;
import java.util.IdentityHashMap;
import net.mgsx.gltf.scene3d.attributes.*;
import org.javity.core.*;
import org.javity.core.Game;
import org.javity.gltf.*;
import org.javity.render.*;
import org.javity.render.lighting.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

/** Real GL30 pixel proof using ordinary engine objects, transforms, camera and scene sunlight. */
public final class DirectionalShadowSmokeLauncher {
  private DirectionalShadowSmokeLauncher() {}

  public static void main(String[] args) {
    Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("Javity directional shadow proof");
    config.setWindowedMode(256, 256);
    config.setInitialVisible(false);
    config.disableAudio(true);
    config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
    Probe probe = new Probe();
    new Lwjgl3Application(probe, config);
    if (probe.failure != null)
      throw new IllegalStateException("Directional shadow smoke failed", probe.failure);
  }

  private static final class Probe extends ApplicationAdapter {
    Throwable failure;
    Game game;
    GdxRenderer renderer;
    DirectionalShadowPass shadow;
    Model cube, floor;
    GltfAsset skinnedAsset;
    GltfInstance skinnedInstance;
    GameObject skinnedObject;
    Camera3D camera;
    Transform3D caster;
    ShadowPolicy3D receiving, casting;
    int checkedPasses;
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
        try {
          if (renderer != null) renderer.dispose();
        } catch (Throwable problem) {
          if (failure == null) failure = problem;
          else if (problem != failure) failure.addSuppressed(problem);
        }
        if (game != null) game.close();
        if (skinnedInstance != null) skinnedInstance.close();
        if (skinnedAsset != null) skinnedAsset.close();
        if (cube != null) cube.dispose();
        if (floor != null) floor.dispose();
        if (replacementContext != 0) {
          GLFW.glfwMakeContextCurrent(originalContext);
          GL.setCapabilities(originalCapabilities);
          GLFW.glfwDestroyWindow(replacementContext);
        }
        Gdx.app.exit();
      }
    }

    private void prove() {
      game = new Game();
      RenderEnvironment3D environment = game.addSystem(new RenderEnvironment3D());
      environment.setAmbient(.04f, .04f, .04f);
      GameObject cameraObject = game.createObject("shadow camera");
      camera = cameraObject.addComponent(new Camera3D(55));
      camera.camera.position.set(6, 8, 9);
      camera.camera.lookAt(0, 0, 0);
      camera.camera.near = .1f;
      camera.camera.far = 40;
      GameObject sunObject = game.createObject("selected scene sun");
      sunObject
          .addComponent(new Transform3D())
          .rotation
          .setFromCross(new Vector3(0, 0, -1), new Vector3(-.7f, -1, -.4f).nor());
      DirectionalLight3D sun = sunObject.addComponent(new DirectionalLight3D());
      sun.setIntensity(3);
      cube =
          new ModelBuilder()
              .createBox(
                  1.3f,
                  1.3f,
                  1.3f,
                  material(),
                  VertexAttributes.Usage.Position
                      | VertexAttributes.Usage.Normal
                      | VertexAttributes.Usage.TextureCoordinates);
      floor =
          new ModelBuilder()
              .createBox(
                  10,
                  .1f,
                  10,
                  material(),
                  VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
      GameObject ground = game.createObject("receiver");
      ground.addComponent(new Transform3D()).position.y = -.05f;
      ground.addComponent(new ModelRenderer(floor));
      receiving = ground.addComponent(new ShadowPolicy3D(false, true));
      GameObject box = game.createObject("caster");
      caster = box.addComponent(new Transform3D());
      caster.position.set(0, 1.4f, 0);
      ModelRenderer boxRenderer = box.addComponent(new ModelRenderer(cube));
      casting = box.addComponent(new ShadowPolicy3D(true, false));
      game.start();
      game.step(0);
      PbrShaderProvider pbr = new PbrShaderProvider(12, 2, 0, 0);
      renderer = pbr.createRenderer();
      renderer.resize(256, 256);
      glOkay("renderer construction");
      shadow =
          new DirectionalShadowPass(
              pbr,
              sun,
              new DirectionalShadowPass.Settings(512, 14, 14, 0, 25, 0, 0, 0, .001f),
              12,
              4);
      glOkay("shadow construction");
      PbrShaderProvider wrongProvider = new PbrShaderProvider(12, 2, 0, 0);
      try {
        try {
          shadow.validateShaderProvider(wrongProvider);
          throw new AssertionError("wrong PBR provider accepted");
        } catch (IllegalArgumentException expected) {
        }
      } finally {
        wrongProvider.dispose();
      }
      // Deliberately seed non-default pass state; each invocation verifies restoration before
      // color.
      renderer.setWorldRenderPass(
          new WorldRenderPass() {
            @Override
            public void validateShaderProvider(RecoverableShaderProvider provider) {
              shadow.validateShaderProvider(provider);
            }

            @Override
            public void render(
                Camera view,
                Array<Renderable> renderables,
                IdentityHashMap<Renderable, ModelRenderer> owners,
                Environment selected) {
              int[] before = fingerprint();
              try {
                shadow.render(view, renderables, owners, selected);
              } finally {
                require(
                    java.util.Arrays.equals(before, fingerprint()),
                    "shadow pass changed caller GL state");
              }
              for (Renderable renderable : renderables)
                require(renderable.shader == null, "depth shader leaked into color renderable");
              checkedPasses++;
            }

            @Override
            public void contextRestored() {
              shadow.contextRestored();
            }

            @Override
            public void dispose() {
              shadow.dispose();
            }
          });
      try {
        shadow.validateShaderProvider(pbr);
        throw new AssertionError("pass ownership transferred twice");
      } catch (IllegalStateException expected) {
      }

      receiving.receive = false;
      byte[] noReceive = image("receive-off");
      receiving.receive = true;
      byte[] shadowA = image("shadow-a");
      int darkA = darker(noReceive, shadowA);
      require(darkA > 60, "receiver did not darken under caster: " + darkA);
      casting.cast = false;
      byte[] noCast = image("cast-off");
      require(darker(noCast, shadowA) > 60, "cast=false did not remove shadow");
      casting.cast = true;
      caster.position.x = 2.4f;
      byte[] shadowB = image("shadow-moved");
      receiving.receive = false;
      byte[] noReceiveB = image("moved-receive-off");
      require(darker(noReceiveB, shadowB) > 60, "moved caster lost shadow");
      int moved = shadowMaskDifference(noReceive, shadowA, noReceiveB, shadowB);
      require(moved > 100, "shadow did not move with GameObject: " + moved);
      receiving.receive = true;
      proveMask(boxRenderer);
      proveSkin();
      failDrawAndProveRecovery();

      // Validation failure must neither change target/settings nor alter caller GL state.
      var original = shadow.resourceIds();
      var originalSettings = shadow.settings();
      try {
        new DirectionalShadowPass.Settings(512, Float.NaN, 14, 0, 25, 0, 0, 0, .001f);
        throw new AssertionError("invalid settings accepted");
      } catch (IllegalArgumentException expected) {
      }
      require(
          shadow.settings().equals(originalSettings) && shadow.resourceIds().equals(original),
          "invalid settings changed published shadow target");
      boxRenderer.instance.materials.first().set(new BlendingAttribute());
      int[] beforeFailure = fingerprint();
      try {
        renderer.renderWorld(game, camera, null);
        throw new AssertionError("BLEND caster accepted");
      } catch (IllegalArgumentException expected) {
        require(expected.getMessage().contains("BLEND"), "unexpected validation failure");
      }
      // Renderer camera/environment collection may change no GL state; no sky is configured here.
      require(
          java.util.Arrays.equals(beforeFailure, fingerprint()),
          "failed caster validation changed GL state");
      boxRenderer.instance.materials.first().remove(BlendingAttribute.Type);
      failReplacementAndProveRollback(original);
      shadow.configure(new DirectionalShadowPass.Settings(256, 14, 14, 0, 25, 0, 0, 0, .001f));
      glOkay("shadow resize");
      require(!Gdx.gl.glIsFramebuffer(original.framebuffer()), "old resized FBO survived");
      require(!Gdx.gl.glIsTexture(original.colorTexture()), "old resized texture survived");
      renderer.restoreContextShaderBindings();
      glOkay("provider recovery");
      byte[] preContext = image("provider-recreated");
      RecoverableFrameBuffer nested =
          new RecoverableFrameBuffer(Pixmap.Format.RGBA8888, 256, 256, true);
      try {
        nested.begin();
        image("nested-caller-fbo");
        IntBuffer binding = BufferUtils.newIntBuffer(1);
        Gdx.gl.glGetIntegerv(GL30.GL_DRAW_FRAMEBUFFER_BINDING, binding);
        require(
            binding.get(0) == nested.getFramebufferHandle(),
            "shadow pass lost nested caller target");
        nested.end();
      } finally {
        nested.dispose();
      }
      replaceContext();
      byte[] firstRecovered = image("context-first-identical-policy");
      require(
          java.util.Arrays.equals(preContext, firstRecovered),
          "first unchanged-policy world frame changed after unshared context recovery");
      receiving.receive = false;
      byte[] recoveredColor = image("context-recovered-receive-off");
      require(
          java.util.Arrays.equals(noReceiveB, recoveredColor),
          "unshadowed world color changed after unshared context recovery (including receive-only"
              + " floor)");
      receiving.receive = true;
      byte[] recovered = image("context-recovered");
      require(
          darker(recoveredColor, recovered) > 60,
          "shadow failed after unshared GL context replacement");
      caster.getGameObject().setActive(false);
      skinnedObject.setActive(true);
      receiving.receive = false;
      byte[] skinClear = image("skin-context-receive-off");
      receiving.receive = true;
      byte[] skinRecovered = image("skin-context-recovered");
      require(
          darker(skinClear, skinRecovered) > 100,
          "imported skinned caster failed after unshared GL context replacement");
      skinnedObject.setActive(false);
      caster.getGameObject().setActive(true);
      var closed = shadow.resourceIds();
      int[] depthPrograms = shadow.shaderProgramIds();
      require(depthPrograms.length > 0, "depth provider compiled no programs");
      for (int program : depthPrograms)
        require(Gdx.gl.glIsProgram(program), "depth program was not live");
      Texture borrowedTexture =
          skinnedInstance
              .modelInstance()
              .materials
              .first()
              .get(PBRTextureAttribute.class, PBRTextureAttribute.BaseColorTexture)
              .textureDescription
              .texture;
      int borrowedTextureId = borrowedTexture.getTextureObjectHandle();
      renderer.dispose();
      renderer.dispose();
      glOkay("renderer disposal");
      require(!Gdx.gl.glIsFramebuffer(closed.framebuffer()), "owned shadow FBO survived close");
      require(!Gdx.gl.glIsTexture(closed.colorTexture()), "owned shadow texture survived close");
      require(
          !Gdx.gl.glIsRenderbuffer(closed.depthRenderbuffer()),
          "owned shadow depth survived close");
      for (int program : depthPrograms)
        require(!Gdx.gl.glIsProgram(program), "owned depth program survived close");
      require(
          Gdx.gl.glIsTexture(borrowedTextureId),
          "borrowed imported texture was closed by renderer");
      require(cube.meshes.first().getNumVertices() > 0, "borrowed model was disposed");
      require(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "OpenGL error after proof");
      System.out.println(
          "directionalShadowSmoke: PASS darkPixels="
              + darkA
              + " movedMaskPixels="
              + moved
              + " stateCheckedPasses="
              + checkedPasses
              + " (policy, movement, MASK, skinned pose, BLEND rejection, failed resize rollback,"
              + " resize, unshared context replacement, disposal)");
    }

    private void proveMask(ModelRenderer model) {
      Pixmap pixels = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
      pixels.setColor(1, 1, 1, 0);
      pixels.fill();
      Texture mask = new Texture(pixels);
      Material material = model.instance.materials.first();
      try {
        material.set(PBRTextureAttribute.createBaseColorTexture(mask));
        material.set(FloatAttribute.createAlphaTest(.5f));
        material.set(new BlendingAttribute());
        receiving.receive = false;
        byte[] clear = image("mask-transparent-receive-off");
        receiving.receive = true;
        byte[] transparent = image("mask-transparent");
        require(darker(clear, transparent) < 10, "fully discarded MASK still cast a shadow");
        pixels.setColor(Color.WHITE);
        pixels.fill();
        mask.draw(pixels, 0, 0);
        receiving.receive = false;
        byte[] baseline = image("mask-opaque-receive-off");
        receiving.receive = true;
        byte[] opaque = image("mask-opaque");
        require(darker(baseline, opaque) > 60, "opaque MASK failed to cast a shadow");
        PBRColorAttribute factor =
            material.get(PBRColorAttribute.class, PBRColorAttribute.BaseColorFactor);
        factor.color.a = 0;
        rejectMask("base-color alpha");
        material.remove(PBRTextureAttribute.BaseColorTexture);
        rejectMask("base-color alpha");
        factor.color.a = 1;
        PBRTextureAttribute channel = PBRTextureAttribute.createBaseColorTexture(mask);
        channel.uvIndex = 1;
        material.set(channel);
        rejectMask("UV0");
        channel.uvIndex = 0;
        material.get(BlendingAttribute.class, BlendingAttribute.Type).opacity = .75f;
        rejectMask("opacity");
        material.get(BlendingAttribute.class, BlendingAttribute.Type).opacity = 1;
        material.remove(BlendingAttribute.Type);
        rejectMask("BlendingAttribute");
        material.set(new BlendingAttribute());

        Model colored =
            new ModelBuilder()
                .createBox(
                    1,
                    1,
                    1,
                    material,
                    VertexAttributes.Usage.Position
                        | VertexAttributes.Usage.Normal
                        | VertexAttributes.Usage.TextureCoordinates
                        | VertexAttributes.Usage.ColorPacked);
        GameObject coloredObject = game.createObject("unsupported vertex-colored MASK");
        try {
          coloredObject.addComponent(new Transform3D()).position.y = 1.4f;
          coloredObject.addComponent(new ModelRenderer(colored));
          coloredObject.addComponent(new ShadowPolicy3D(true, false));
          game.step(0);
          rejectMask("vertex color");
        } finally {
          coloredObject.destroy();
          game.step(0);
          colored.dispose();
        }
      } finally {
        material.get(PBRColorAttribute.class, PBRColorAttribute.BaseColorFactor).color.a = 1;
        material.remove(PBRTextureAttribute.BaseColorTexture);
        material.remove(FloatAttribute.AlphaTest);
        material.remove(BlendingAttribute.Type);
        mask.dispose();
        pixels.dispose();
        receiving.receive = true;
      }
    }

    private void rejectMask(String reason) {
      try {
        image("unsupported-mask");
        throw new AssertionError("unsupported MASK caster accepted: " + reason);
      } catch (IllegalArgumentException expected) {
        require(expected.getMessage().contains(reason), "wrong MASK failure: " + expected);
      }
    }

    private void proveSkin() {
      caster.getGameObject().setActive(false);
      skinnedAsset =
          GltfAsset.load(Gdx.files.classpath("org/javity/gltf/fixtures/skinned.glb"), 12);
      skinnedInstance = skinnedAsset.instantiate();
      skinnedObject = game.createObject("imported skinned caster");
      Transform3D transform = skinnedObject.addComponent(new Transform3D());
      transform.position.set(0, 2, 0);
      transform.scale.set(3, 3, 3);
      transform.rotation.set(Vector3.X, -90);
      ModelRenderer skin = skinnedObject.addComponent(skinnedInstance.createRenderer());
      skinnedObject.addComponent(new ShadowPolicy3D(true, false));
      game.step(0);
      skin.play("lift", false);
      skin.animation.update(.05f);
      receiving.receive = false;
      byte[] a = image("skin-base-receive-off");
      receiving.receive = true;
      byte[] sa = image("skin-base");
      require(darker(a, sa) > 100, "skinned mesh failed to cast a shadow");
      skin.animation.update(.8f);
      receiving.receive = false;
      byte[] b = image("skin-posed-receive-off");
      receiving.receive = true;
      byte[] sb = image("skin-posed");
      require(darker(b, sb) > 100, "posed skinned mesh lost its shadow");
      require(shadowMaskDifference(a, sa, b, sb) > 50, "bone pose did not alter the shadow");
      skinnedObject.setActive(false);
      caster.getGameObject().setActive(true);
    }

    private void failReplacementAndProveRollback(DirectionalShadowPass.ResourceIds previous) {
      GL20 saved = Gdx.gl, saved20 = Gdx.gl20;
      GL30 saved30 = Gdx.gl30;
      boolean[] injected = {false};
      GL30 failing =
          (GL30)
              Proxy.newProxyInstance(
                  GL30.class.getClassLoader(),
                  new Class<?>[] {GL30.class},
                  (proxy, method, args) -> {
                    if (method.getName().equals("glCheckFramebufferStatus") && !injected[0]) {
                      injected[0] = true;
                      return GL20.GL_FRAMEBUFFER_UNSUPPORTED;
                    }
                    try {
                      return method.invoke(saved30, args);
                    } catch (InvocationTargetException problem) {
                      throw problem.getCause();
                    }
                  });
      int[] before = fingerprint();
      try {
        Gdx.gl = Gdx.gl20 = failing;
        Gdx.gl30 = failing;
        try {
          shadow.configure(new DirectionalShadowPass.Settings(1024, 14, 14, 0, 25, 0, 0, 0, .001f));
          throw new AssertionError("incomplete framebuffer was accepted");
        } catch (IllegalStateException expected) {
          require(
              expected.getMessage().contains("unsupported"),
              "unexpected allocation failure: " + expected);
        }
      } finally {
        Gdx.gl = saved;
        Gdx.gl20 = saved20;
        Gdx.gl30 = saved30;
      }
      require(injected[0], "allocation failure was not injected");
      require(previous.equals(shadow.resourceIds()), "failed resize replaced the working target");
      require(
          java.util.Arrays.equals(before, fingerprint()), "failed resize changed caller GL state");
      glOkay("failed resize recovery");
    }

    private void failDrawAndProveRecovery() {
      GL20 saved = Gdx.gl, saved20 = Gdx.gl20;
      GL30 saved30 = Gdx.gl30;
      RuntimeException injected = new RuntimeException("injected depth draw failure");
      boolean[] didFail = {false};
      GL30 failing =
          (GL30)
              Proxy.newProxyInstance(
                  GL30.class.getClassLoader(),
                  new Class<?>[] {GL30.class},
                  (proxy, method, args) -> {
                    if ((method.getName().equals("glDrawElements")
                            || method.getName().equals("glDrawArrays"))
                        && !didFail[0]) {
                      didFail[0] = true;
                      throw injected;
                    }
                    try {
                      return method.invoke(saved30, args);
                    } catch (InvocationTargetException problem) {
                      throw problem.getCause();
                    }
                  });
      int[] before = fingerprint();
      try {
        Gdx.gl = Gdx.gl20 = failing;
        Gdx.gl30 = failing;
        try {
          renderer.renderWorld(game, camera, null);
          throw new AssertionError("injected draw succeeded");
        } catch (RuntimeException expected) {
          require(expected == injected, "depth failure was replaced");
        }
      } finally {
        Gdx.gl = saved;
        Gdx.gl20 = saved20;
        Gdx.gl30 = saved30;
      }
      require(didFail[0], "draw failure was not injected");
      require(
          java.util.Arrays.equals(before, fingerprint()),
          "failed depth draw changed caller GL state");
      glOkay("failed depth draw recovery");
      image("draw-failure-recovered");
    }

    private void replaceContext() {
      originalContext = GLFW.glfwGetCurrentContext();
      originalCapabilities = GL.getCapabilities();
      GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
      replacementContext = GLFW.glfwCreateWindow(256, 256, "shadow replacement context", 0, 0);
      require(replacementContext != 0, "could not create replacement GL context");
      GLFW.glfwMakeContextCurrent(replacementContext);
      GL.createCapabilities();
      // There is no sharing: existing GPU names are invalid in this replacement context.
      Texture.invalidateAllTextures(Gdx.app);
      com.badlogic.gdx.graphics.glutils.ShaderProgram.invalidateAllShaderPrograms(Gdx.app);
      Mesh.invalidateAllMeshes(Gdx.app);
      com.badlogic.gdx.graphics.glutils.GLFrameBuffer.invalidateAllFrameBuffers(Gdx.app);
      renderer.restoreContextShaderBindings();
      glOkay("unshared context recovery");
    }

    private byte[] image(String name) {
      Gdx.gl.glViewport(0, 0, 256, 256);
      Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
      Gdx.gl.glColorMask(true, true, true, true);
      Gdx.gl.glDepthMask(true);
      Gdx.gl.glClearColor(.03f, .04f, .06f, 1);
      Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
      renderer.renderWorld(game, camera, null);
      glOkay("world image " + name);
      byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, 256, 256, false);
      Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, 256, 256);
      try {
        PixmapIO.writePNG(Gdx.files.local("build/reports/shadows/" + name + ".png"), pixmap);
      } finally {
        pixmap.dispose();
      }
      return pixels;
    }

    private static Material material() {
      return new Material(
          PBRColorAttribute.createBaseColorFactor(Color.WHITE),
          PBRFloatAttribute.createMetallic(0),
          PBRFloatAttribute.createRoughness(.9f));
    }

    private static int darker(byte[] baseline, byte[] shadowed) {
      int result = 0;
      for (int i = 0; i < baseline.length; i += 4)
        if (luminance(baseline, i) - luminance(shadowed, i) > 35) result++;
      return result;
    }

    private static int shadowMaskDifference(byte[] a, byte[] sa, byte[] b, byte[] sb) {
      int result = 0;
      for (int i = 0; i < a.length; i += 4)
        if ((luminance(a, i) - luminance(sa, i) > 35) != (luminance(b, i) - luminance(sb, i) > 35))
          result++;
      return result;
    }

    private static int luminance(byte[] p, int i) {
      return ((p[i] & 255) + (p[i + 1] & 255) + (p[i + 2] & 255)) / 3;
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
        GL20.GL_TEXTURE_BINDING_CUBE_MAP,
        GL20.GL_DEPTH_FUNC,
        GL20.GL_DEPTH_WRITEMASK,
        GL20.GL_BLEND_SRC_RGB,
        GL20.GL_BLEND_DST_RGB,
        GL20.GL_BLEND_SRC_ALPHA,
        GL20.GL_BLEND_DST_ALPHA,
        GL20.GL_FRONT_FACE
      };
      int[] result = new int[names.length + 8 + 4];
      IntBuffer buffer = BufferUtils.newIntBuffer(4);
      for (int i = 0; i < names.length; i++) {
        buffer.clear();
        Gdx.gl.glGetIntegerv(names[i], buffer);
        result[i] = buffer.get(0);
      }
      int offset = names.length;
      for (int name : new int[] {GL20.GL_VIEWPORT, GL20.GL_SCISSOR_BOX}) {
        buffer.clear();
        Gdx.gl.glGetIntegerv(name, buffer);
        for (int i = 0; i < 4; i++) result[offset++] = buffer.get(i);
      }
      for (int name :
          new int[] {GL20.GL_DEPTH_TEST, GL20.GL_BLEND, GL20.GL_CULL_FACE, GL20.GL_SCISSOR_TEST})
        result[offset++] = Gdx.gl.glIsEnabled(name) ? 1 : 0;
      return result;
    }

    private static void require(boolean value, String message) {
      if (!value) throw new AssertionError(message);
    }

    private static void glOkay(String label) {
      int error = Gdx.gl.glGetError();
      require(error == GL20.GL_NO_ERROR, label + " GL error 0x" + Integer.toHexString(error));
    }
  }
}
