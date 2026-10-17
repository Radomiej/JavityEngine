package org.javity.demo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.postprocessing.WorldPostProcess;
import org.javity.render.Camera3D;
import org.javity.render.GdxRenderer;
import org.javity.render.ModelRenderer;

/** Numeric readback proof for world pass effects, HUD placement, resize and ownership. */
final class WorldPostProcessGpuSmokeProbe extends ApplicationAdapter {
  private WorldPostProcess post;
  private GdxRenderer callerRenderer;
  private Game callerGame;
  private Camera3D callerCamera;
  private Model frontModel;
  private Model backModel;
  private ModelInstance callerViewmodel;
  private Texture callerTexture;
  private int borrowedBuffer;
  private final List<String> artifactCases = new ArrayList<>();
  private Throwable failure;

  @Override
  public void create() {
    try {
      runProbe();
      WorldPostProcess.ResourceIds ids = post.resourceIds();
      assertOwnedIdsLive(ids);
      post.dispose();
      require(!Gdx.gl.glIsFramebuffer(ids.framebuffer()), "owned framebuffer survived close");
      require(!Gdx.gl.glIsTexture(ids.colorTexture()), "owned color texture survived close");
      require(
          !Gdx.gl.glIsRenderbuffer(ids.depthRenderbuffer()),
          "owned depth renderbuffer survived close");
      require(!Gdx.gl.glIsProgram(ids.shaderProgram()), "owned shader survived close");
      require(!Gdx.gl.glIsBuffer(ids.vertexBuffer()), "owned quad buffer survived close");
      require(
          ids.vertexArray() == 0 || !Gdx.gl30.glIsVertexArray(ids.vertexArray()),
          "owned quad vertex array survived close");
      require(
          Gdx.gl.glIsTexture(callerTexture.getTextureObjectHandle()),
          "borrowed texture was closed");
      require(Gdx.gl.glIsBuffer(borrowedBuffer), "borrowed array buffer was closed");
      Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, borrowedBuffer);
      Gdx.gl.glBufferData(
          GL20.GL_ARRAY_BUFFER, 16, BufferUtils.newByteBuffer(16), GL20.GL_STATIC_DRAW);
      callerRenderer.renderWorld(callerGame, callerCamera, null);
      require(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "borrowed host renderer failed after close");
      writeArtifacts();
      System.out.println(
          "worldPostProcessGpuSmoke: PASS (flat bypass, tint, desaturation, vignette, HUD"
              + " isolation, leases, resize, resource ownership)");
    } catch (Throwable problem) {
      failure = problem;
      problem.printStackTrace();
    } finally {
      try {
        if (post != null) post.dispose();
      } catch (Throwable cleanupFailure) {
        if (failure != null) failure.addSuppressed(cleanupFailure);
        else cleanupFailure.printStackTrace();
      }
      if (callerTexture != null) callerTexture.dispose();
      if (callerRenderer != null) callerRenderer.dispose();
      if (callerGame != null) callerGame.close();
      if (frontModel != null) frontModel.dispose();
      if (backModel != null) backModel.dispose();
      Gdx.app.exit();
    }
  }

  void assertSuccess() {
    if (failure != null)
      throw new IllegalStateException("World postprocess GPU smoke failed", failure);
  }

  private void runProbe() {
    proveFailedShaderPreflightRecovery();
    Pixmap callerPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    callerPixmap.setColor(.25f, .5f, .75f, 1f);
    callerPixmap.fill();
    callerTexture = new Texture(callerPixmap);
    callerPixmap.dispose();
    callerTexture.bind(3);
    Gdx.gl.glViewport(4, 5, 80, 78);
    int entryFramebuffer = getInt(GL20.GL_FRAMEBUFFER_BINDING);
    int entryActiveTexture = getInt(GL20.GL_ACTIVE_TEXTURE);
    int entryTextureBinding = getInt(GL20.GL_TEXTURE_BINDING_2D);
    int[] entryViewport = getFour(GL20.GL_VIEWPORT);
    post = new WorldPostProcess(64, 64);
    require(
        getInt(GL20.GL_FRAMEBUFFER_BINDING) == entryFramebuffer,
        "constructor changed framebuffer binding");
    require(
        getInt(GL20.GL_ACTIVE_TEXTURE) == entryActiveTexture, "constructor changed active texture");
    require(
        getInt(GL20.GL_TEXTURE_BINDING_2D) == entryTextureBinding,
        "constructor changed caller texture binding");
    require(
        java.util.Arrays.equals(entryViewport, getFour(GL20.GL_VIEWPORT)),
        "constructor changed viewport");
    Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
    Gdx.gl.glViewport(0, 0, 96, 96);
    callerRenderer = new GdxRenderer();
    callerRenderer.resize(64, 64);
    callerGame = new Game();
    GameObject cameraObject = callerGame.createObject("world postprocess proof camera");
    callerCamera = cameraObject.addComponent(new Camera3D(60));
    callerCamera.camera.position.set(0, 0, 2);
    callerCamera.camera.lookAt(0, 0, 0);
    callerCamera.camera.near = .1f;
    callerCamera.camera.far = 10f;
    ModelBuilder modelBuilder = new ModelBuilder();
    frontModel =
        modelBuilder.createBox(
            1.2f,
            1.2f,
            .2f,
            new Material(ColorAttribute.createDiffuse(Color.GREEN)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
    backModel =
        modelBuilder.createBox(
            1.2f,
            1.2f,
            .2f,
            new Material(ColorAttribute.createDiffuse(Color.RED)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
    callerViewmodel = new ModelInstance(frontModel);
    GameObject frontObject = callerGame.createObject("depth front green");
    frontObject.addComponent(new Transform3D()).position.z = .35f;
    frontObject.addComponent(new ModelRenderer(frontModel));
    GameObject backObject = callerGame.createObject("depth back red");
    backObject.addComponent(new Transform3D()).position.z = -.35f;
    backObject.addComponent(new ModelRenderer(backModel));
    callerGame.start();
    callerGame.step(0f);
    WorldPostProcess.Controls controls = post.controls();
    borrowedBuffer = generateBorrowedBuffer();

    Gdx.gl.glDepthMask(false);
    Gdx.gl.glClearDepthf(.37f);
    float[] capturedDepth = new float[1];
    post.renderWorld(
        () -> {
          FloatBuffer depth = BufferUtils.newFloatBuffer(1);
          Gdx.gl.glReadPixels(32, 32, 1, 1, GL20.GL_DEPTH_COMPONENT, GL20.GL_FLOAT, depth);
          capturedDepth[0] = depth.get(0);
          clearWorld(.8f, .2f, .1f);
        });
    int ownedQuadBuffer = post.resourceIds().vertexBuffer();
    require(ownedQuadBuffer > 0, "owned quad VBO ID was not recorded");
    require(Gdx.gl.glIsBuffer(ownedQuadBuffer), "owned quad VBO is not live");
    require(ownedQuadBuffer != borrowedBuffer, "quad VBO was confused with caller array buffer");
    Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, borrowedBuffer);
    post.renderWorld(() -> clearWorld(.8f, .2f, .1f));
    require(
        post.resourceIds().vertexBuffer() == ownedQuadBuffer,
        "quad VBO identity changed across cached VAO binds");
    require(
        getInt(GL20.GL_ARRAY_BUFFER_BINDING) == borrowedBuffer,
        "postprocess failed to restore deliberately bound caller array buffer");
    require(
        Math.abs(capturedDepth[0] - 1f) < .001f,
        "owned world depth clear inherited caller depth state");
    require(getInt(GL20.GL_DEPTH_WRITEMASK) == 0, "pass changed caller depth write mask");
    FloatBuffer callerClearDepth = BufferUtils.newFloatBuffer(1);
    Gdx.gl.glGetFloatv(GL20.GL_DEPTH_CLEAR_VALUE, callerClearDepth);
    require(
        Math.abs(callerClearDepth.get(0) - .37f) < .001f, "pass changed caller depth clear value");
    Gdx.gl.glDepthMask(true);
    Gdx.gl.glClearDepthf(1f);

    post.renderWorld(() -> callerRenderer.renderWorld(callerGame, callerCamera, null));
    int[] occluded = pixel(32, 32);
    require(
        occluded[1] > occluded[0] * 2,
        "front world model did not occlude the rear model: " + rgb(occluded));

    drawWorld(0.8f, 0.2f, 0.1f);
    int[] bypass = pixel(32, 32);
    require(
        near(bypass[0], 204, 2) && near(bypass[1], 51, 2) && near(bypass[2], 26, 2),
        "bypass changed flat world color: " + rgb(bypass));
    saveArtifact("identity.png", sample("center", new int[] {204, 51, 26, 255}, bypass, 2));

    controls.setBaseTint(.5f, 1f, 1f);
    drawWorld(0.8f, 0.2f, 0.1f);
    int[] tinted = pixel(32, 32);
    require(
        near(tinted[0], 102, 3) && near(tinted[1], 51, 2) && near(tinted[2], 26, 2),
        "tint numeric output mismatch: " + rgb(tinted));
    saveArtifact("tint.png", sample("center", new int[] {102, 51, 26, 255}, tinted, 3));

    controls.setBaseTint(1, 1, 1);
    controls.setBaseDesaturation(1);
    drawWorld(0.8f, 0.2f, 0.1f);
    int[] gray = pixel(32, 32);
    require(
        Math.abs(gray[0] - gray[1]) <= 2 && Math.abs(gray[1] - gray[2]) <= 2,
        "full desaturation was not neutral: " + rgb(gray));
    require(near(gray[0], 83, 4), "desaturation luma mismatch: " + rgb(gray));
    saveArtifact("desaturation.png", sample("center", new int[] {83, 83, 83, 255}, gray, 4));

    controls.setBaseDesaturation(0);
    controls.setBaseVignette(.8f);
    drawWorld(0.8f, 0.2f, 0.1f);
    int[] center = pixel(48, 48);
    int[] edge = pixel(2, 2);
    require(
        center[0] > edge[0] + 30,
        "vignette center/edge mismatch: center=" + rgb(center) + " edge=" + rgb(edge));
    saveArtifact(
        "vignette.png",
        sample("center", new int[] {204, 51, 26, 255}, center, 3),
        sample("edge", new int[] {41, 10, 5, 255}, edge, 3));

    controls.setBaseVignette(0);
    WorldPostProcess.ControlLease one = controls.acquire();
    WorldPostProcess.ControlLease two = controls.acquire();
    one.setTint(.5f, 1, 1);
    two.setDesaturation(1);
    one.close();
    drawWorld(0.8f, 0.2f, 0.1f);
    int[] afterCancel = pixel(32, 32);
    require(Math.abs(afterCancel[0] - afterCancel[1]) <= 2, "closing first lease cancelled second");
    two.close();
    drawWorld(0.6f, 0.3f, 0.15f);
    int[] restored = pixel(32, 32);
    require(
        near(restored[0], 153, 2) && near(restored[1], 77, 2),
        "cancel did not reveal current live world output: " + rgb(restored));

    // Viewmodel and HUD are drawn after composition on the caller framebuffer.
    controls.setBaseTint(1, 0, 0);
    post.renderWorld(
        () -> {
          callerRenderer.renderWorld(callerGame, callerCamera, null);
          clearWorld(0.2f, 0.2f, 0.8f);
        });
    callerRenderer.renderViewmodel(callerCamera.camera, callerViewmodel);
    int[] viewmodelPixel = pixel(48, 48);
    require(
        viewmodelPixel[1] > viewmodelPixel[0] * 2 && viewmodelPixel[1] > viewmodelPixel[2] * 2,
        "viewmodel was included in world tint: " + rgb(viewmodelPixel));
    Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
    Gdx.gl.glScissor(0, 0, 12, 12);
    Gdx.gl.glClearColor(0.1f, 0.9f, 0.2f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    int[] worldPixel = pixel(80, 80);
    int[] hudPixel = pixel(5, 5);
    require(
        near(worldPixel[0], 51, 3) && worldPixel[1] <= 2 && worldPixel[2] <= 2,
        "world tint output mismatch after viewmodel: " + rgb(worldPixel));
    require(
        near(hudPixel[0], 26, 3) && near(hudPixel[1], 230, 3),
        "HUD output mismatch: " + rgb(hudPixel));
    saveArtifact(
        "world-viewmodel-hud.png",
        sample("world", new int[] {51, 0, 0, 255}, worldPixel, 3),
        semanticSample("viewmodel", "green-dominant", viewmodelPixel),
        sample("hud", new int[] {26, 230, 51, 255}, hudPixel, 4));

    controls.setBaseTint(1, 1, 1);
    verifyAsymmetricOrientation();

    proveCallerMeshState(post);
    WorldPostProcess.ResourceIds oldIds = post.resourceIds();
    callerTexture.bind(2);
    Gdx.gl.glViewport(1, 2, 60, 61);
    int resizeFramebuffer = getInt(GL20.GL_FRAMEBUFFER_BINDING);
    int resizeActiveTexture = getInt(GL20.GL_ACTIVE_TEXTURE);
    int resizeTextureBinding = getInt(GL20.GL_TEXTURE_BINDING_2D);
    int[] resizeViewport = getFour(GL20.GL_VIEWPORT);
    post.resize(80, 48);
    require(
        getInt(GL20.GL_FRAMEBUFFER_BINDING) == resizeFramebuffer,
        "resize changed framebuffer binding");
    require(getInt(GL20.GL_ACTIVE_TEXTURE) == resizeActiveTexture, "resize changed active texture");
    require(
        getInt(GL20.GL_TEXTURE_BINDING_2D) == resizeTextureBinding,
        "resize changed caller texture binding");
    require(
        java.util.Arrays.equals(resizeViewport, getFour(GL20.GL_VIEWPORT)),
        "resize changed viewport");
    Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
    Gdx.gl.glViewport(0, 0, 80, 48);
    controls.setBaseTint(1, 1, 1);
    WorldPostProcess.ResourceIds resizedIds = post.resourceIds();
    require(post.width() == 80 && post.height() == 48, "resize dimensions were not published");
    require(oldIds.framebuffer() != resizedIds.framebuffer(), "resize reused old framebuffer");
    require(!Gdx.gl.glIsFramebuffer(oldIds.framebuffer()), "resize leaked previous framebuffer");
    try {
      post.resize(8192, 4096);
      throw new AssertionError("over-dimension resize unexpectedly succeeded");
    } catch (IllegalArgumentException expected) {
      require(post.width() == 80 && post.height() == 48, "rejected resize replaced live target");
      require(post.resourceIds().equals(resizedIds), "rejected resize changed live resource IDs");
    }
    drawWorld(.25f, .5f, .75f);
    int[] resizedOutput = pixel(40, 24);
    require(
        near(resizedOutput[0], 64, 2) && near(resizedOutput[2], 191, 2),
        "resized output mismatch: " + rgb(resizedOutput));

    // A failed world callback still restores the exact caller target and viewport plus key state.
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    Gdx.gl.glEnable(GL20.GL_BLEND);
    Gdx.gl.glDepthFunc(GL20.GL_GREATER);
    Gdx.gl.glDepthRangef(.2f, .8f);
    Gdx.gl.glCullFace(GL20.GL_FRONT);
    Gdx.gl.glEnable(GL20.GL_CULL_FACE);
    callerTexture.bind(3);
    Gdx.gl.glViewport(2, 3, 50, 51);
    int previousFramebuffer = getInt(GL20.GL_FRAMEBUFFER_BINDING);
    int previousActiveTexture = getInt(GL20.GL_ACTIVE_TEXTURE);
    int[] previousViewport = getFour(GL20.GL_VIEWPORT);
    try {
      post.renderWorld(
          () -> {
            throw new IllegalStateException("injected world callback failure");
          });
      throw new AssertionError("world callback failure was swallowed");
    } catch (IllegalStateException expected) {
      require(
          expected.getMessage().contains("injected world callback failure"),
          "wrong callback exception");
    }
    require(
        getInt(GL20.GL_FRAMEBUFFER_BINDING) == previousFramebuffer,
        "exception changed framebuffer");
    require(
        getInt(GL20.GL_ACTIVE_TEXTURE) == previousActiveTexture,
        "exception changed active texture");
    int[] restoredViewport = getFour(GL20.GL_VIEWPORT);
    require(
        java.util.Arrays.equals(previousViewport, restoredViewport), "exception changed viewport");
    require(Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST), "exception changed depth test state");
    require(Gdx.gl.glIsEnabled(GL20.GL_BLEND), "exception changed blend state");
    require(
        getInt(GL20.GL_DEPTH_FUNC) == GL20.GL_GREATER, "exception changed caller depth function");
    float[] exceptionDepthRange = getDepthRange();
    require(
        Math.abs(exceptionDepthRange[0] - .2f) < 1e-6f
            && Math.abs(exceptionDepthRange[1] - .8f) < 1e-6f,
        "exception changed caller depth range");
    require(getInt(GL20.GL_CULL_FACE_MODE) == GL20.GL_FRONT, "exception changed caller cull mode");
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    Gdx.gl.glDisable(GL20.GL_BLEND);
    Gdx.gl.glDisable(GL20.GL_CULL_FACE);
    Gdx.gl.glDepthFunc(GL20.GL_LESS);
    Gdx.gl.glDepthRangef(0, 1);
    Gdx.gl.glCullFace(GL20.GL_BACK);
    Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
    Gdx.gl.glViewport(0, 0, 96, 96);

    require(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "GL error after postprocess proof");
  }

  private void proveCallerMeshState(WorldPostProcess postprocess) {
    String vertex =
        "attribute vec3 a_position; attribute vec4 a_color; varying vec4 v_color;"
            + " void main(){ v_color=a_color; gl_Position=vec4(a_position,1.0); }";
    String fragment =
        "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
            + "varying vec4 v_color; void main(){ gl_FragColor=v_color; }";
    ShaderProgram callerShader = new ShaderProgram(vertex, fragment);
    Mesh callerMesh =
        new Mesh(
            true,
            3,
            0,
            new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
            new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, "a_color"));
    try {
      require(
          callerShader.isCompiled(), "caller mesh proof shader failed: " + callerShader.getLog());
      callerMesh.setVertices(triangleVertices(0));
      Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
      Gdx.gl.glEnable(GL20.GL_CULL_FACE);
      Gdx.gl.glDepthMask(true);
      Gdx.gl.glDepthFunc(GL20.GL_GREATER);
      Gdx.gl.glDepthRangef(.2f, .8f);
      Gdx.gl.glCullFace(GL20.GL_FRONT);
      Gdx.gl.glDisable(GL20.GL_BLEND);
      Gdx.gl.glColorMask(true, true, true, true);
      Gdx.gl.glClearColor(0, 0, 0, 1);
      Gdx.gl.glClearDepthf(1);
      Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
      callerShader.bind();
      callerMesh.bind(callerShader);
      int entryVertexArray =
          Gdx.gl30 == null ? 0 : getInt(com.badlogic.gdx.graphics.GL30.GL_VERTEX_ARRAY_BINDING);
      int position = callerShader.getAttributeLocation("a_position");
      int color = callerShader.getAttributeLocation("a_color");
      int[] positionState = getAttributeState(position);
      int[] colorState = getAttributeState(color);

      for (int repeat = 0; repeat < 2; repeat++) {
        postprocess.renderWorld(() -> callerRenderer.renderWorld(callerGame, callerCamera, null));
        assertDepthAndCullState(entryVertexArray, positionState, colorState, position, color);
      }

      // With depth GREATER and range [.2,.8], z=0 maps to .5: clear .6 rejects it. If the
      // RenderContext's LEQUAL leaked, this back-facing triangle (surviving FRONT culling) draws.
      drawCallerTriangle(callerMesh, callerShader, 0, .6f, false);
      // z=-.5 maps to .35 in [.2,.8], above clear .3. The default [0,1] range maps it to .25.
      drawCallerTriangle(callerMesh, callerShader, -.5f, .3f, true);
      // The same clockwise winding is culled if caller FRONT mode leaked as BACK.
      Gdx.gl.glDepthFunc(GL20.GL_ALWAYS);
      drawCallerCullTriangle(callerMesh, callerShader);

      Gdx.gl.glDepthFunc(GL20.GL_LESS);
      Gdx.gl.glDepthRangef(0, 1);
      Gdx.gl.glCullFace(GL20.GL_BACK);
      Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
      Gdx.gl.glDisable(GL20.GL_CULL_FACE);
      callerMesh.unbind(callerShader);
    } finally {
      callerMesh.dispose();
      callerShader.dispose();
      Gdx.gl.glViewport(0, 0, 96, 96);
    }
  }

  private void assertDepthAndCullState(
      int entryVertexArray, int[] positionState, int[] colorState, int position, int color) {
    require(
        getInt(GL20.GL_DEPTH_FUNC) == GL20.GL_GREATER,
        "actual GdxRenderer callback leaked depth function");
    float[] depthRange = getDepthRange();
    require(
        Math.abs(depthRange[0] - .2f) < 1e-6f && Math.abs(depthRange[1] - .8f) < 1e-6f,
        "actual GdxRenderer callback leaked depth range");
    require(
        getInt(GL20.GL_CULL_FACE_MODE) == GL20.GL_FRONT,
        "actual GdxRenderer callback leaked cull-face mode");
    require(
        getInt(GL30.GL_VERTEX_ARRAY_BINDING) == entryVertexArray,
        "postprocess changed caller VAO binding");
    require(
        java.util.Arrays.equals(positionState, getAttributeState(position)),
        "postprocess changed caller position attribute descriptor");
    require(
        java.util.Arrays.equals(colorState, getAttributeState(color)),
        "postprocess changed caller color attribute descriptor");
  }

  private void drawCallerTriangle(
      Mesh mesh, ShaderProgram shader, float z, float clearDepth, boolean expectGreen) {
    mesh.setVertices(triangleVertices(z));
    Gdx.gl.glDepthMask(true);
    Gdx.gl.glClearDepthf(clearDepth);
    Gdx.gl.glClearColor(0, 0, 0, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    Gdx.gl.glUseProgram(shader.getHandle());
    Gdx.gl.glDrawArrays(GL20.GL_TRIANGLES, 0, 3);
    int[] observed = pixel(48, 48);
    require(
        expectGreen ? observed[1] > 200 : observed[1] < 10,
        "caller draw did not honor restored depth/range/cull state: " + rgb(observed));
  }

  private void drawCallerCullTriangle(Mesh mesh, ShaderProgram shader) {
    mesh.setVertices(triangleVertices(0, false));
    Gdx.gl.glDepthMask(true);
    Gdx.gl.glDepthFunc(GL20.GL_ALWAYS);
    Gdx.gl.glClearDepthf(1);
    Gdx.gl.glClearColor(0, 0, 0, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    Gdx.gl.glUseProgram(shader.getHandle());
    Gdx.gl.glDrawArrays(GL20.GL_TRIANGLES, 0, 3);
    int[] observed = pixel(48, 48);
    require(
        observed[1] < 10,
        "caller front-face draw escaped restored FRONT culling: " + rgb(observed));
  }

  private static float[] triangleVertices(float z) {
    return triangleVertices(z, true);
  }

  private static float[] triangleVertices(float z, boolean clockwise) {
    // Clockwise winding is the surviving back face when GL_FRONT is the cull mode.
    if (clockwise)
      return new float[] {
        -.8f, -.8f, z, 0, 1, 0, 1, 0, .8f, z, 0, 1, 0, 1, .8f, -.8f, z, 0, 1, 0, 1
      };
    return new float[] {-.8f, -.8f, z, 0, 1, 0, 1, .8f, -.8f, z, 0, 1, 0, 1, 0, .8f, z, 0, 1, 0, 1};
  }

  private static float[] getDepthRange() {
    FloatBuffer range = BufferUtils.newFloatBuffer(2);
    Gdx.gl.glGetFloatv(GL20.GL_DEPTH_RANGE, range);
    return new float[] {range.get(0), range.get(1)};
  }

  private void verifyAsymmetricOrientation() {
    post.renderWorld(
        () -> {
          Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
          quadrant(0, 0, .9f, .1f, .1f);
          quadrant(32, 0, .1f, .9f, .1f);
          quadrant(0, 32, .1f, .1f, .9f);
          quadrant(32, 32, .9f, .9f, .1f);
          Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        });
    int[] lowerLeft = pixel(16, 16);
    int[] lowerRight = pixel(80, 16);
    int[] upperLeft = pixel(16, 80);
    int[] upperRight = pixel(80, 80);
    saveArtifact(
        "orientation.png",
        sample("lower_left_red", new int[] {230, 26, 26, 255}, lowerLeft, 3),
        sample("lower_right_green", new int[] {26, 230, 26, 255}, lowerRight, 3),
        sample("upper_left_blue", new int[] {26, 26, 230, 255}, upperLeft, 3),
        sample("upper_right_yellow", new int[] {230, 230, 26, 255}, upperRight, 3));
  }

  private static void quadrant(int x, int y, float red, float green, float blue) {
    Gdx.gl.glScissor(x, y, 32, 32);
    Gdx.gl.glClearColor(red, green, blue, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
  }

  private record ArtifactSample(
      String name, int[] expected, String expectedRule, int[] observed, int tolerance) {}

  private static ArtifactSample sample(String name, int[] expected, int[] observed, int tolerance) {
    for (int channel = 0; channel < 4; channel++)
      require(
          near(observed[channel], expected[channel], tolerance),
          name
              + " artifact sample mismatch expected="
              + rgb(expected)
              + " observed="
              + rgb(observed));
    return new ArtifactSample(name, expected, null, observed, tolerance);
  }

  private static ArtifactSample semanticSample(String name, String expectedRule, int[] observed) {
    return new ArtifactSample(name, null, expectedRule, observed, 0);
  }

  private void saveArtifact(String filename, ArtifactSample... samples) {
    try {
      Path directory = artifactDirectory();
      Files.createDirectories(directory);
      byte[] topLeftOriginPixels = ScreenUtils.getFrameBufferPixels(0, 0, 96, 96, true);
      Pixmap image = new Pixmap(96, 96, Pixmap.Format.RGBA8888);
      try {
        image.getPixels().put(topLeftOriginPixels);
        PixmapIO.writePNG(new FileHandle(directory.resolve(filename).toFile()), image);
      } finally {
        image.dispose();
      }
      StringBuilder samplesJson = new StringBuilder("[");
      for (int index = 0; index < samples.length; index++) {
        if (index > 0) samplesJson.append(',');
        ArtifactSample sample = samples[index];
        samplesJson
            .append("{\"name\":\"")
            .append(sample.name())
            .append("\",\"expected\":")
            .append(
                sample.expectedRule() == null
                    ? jsonArray(sample.expected())
                    : "\"" + sample.expectedRule() + "\"")
            .append(",\"observed\":")
            .append(jsonArray(sample.observed()))
            .append(",\"tolerance\":")
            .append(sample.tolerance())
            .append('}');
      }
      samplesJson.append(']');
      artifactCases.add("{\"image\":\"" + filename + "\",\"samples\":" + samplesJson + "}");
    } catch (java.io.IOException failure) {
      throw new IllegalStateException(
          "Unable to write postprocess image artifact " + filename, failure);
    }
  }

  private void writeArtifacts() {
    try {
      Path path = artifactDirectory().resolve("results.json");
      String json =
          "{\"display\":\"dummy Xorg :104\",\"renderer\":\"software OpenGL 3.2\","
              + "\"size\":[96,96],\"cases\":["
              + String.join(",", artifactCases)
              + "]}\n";
      Files.writeString(path, json);
    } catch (java.io.IOException failure) {
      throw new IllegalStateException("Unable to write postprocess numeric artifact", failure);
    }
  }

  private static String jsonArray(int[] values) {
    return "[" + values[0] + "," + values[1] + "," + values[2] + "," + values[3] + "]";
  }

  private static Path artifactDirectory() {
    Path directory = Path.of("").toAbsolutePath();
    while (directory != null) {
      if (Files.exists(directory.resolve("settings.gradle")))
        return directory.resolve("docs/artifacts/world-postprocess");
      directory = directory.getParent();
    }
    throw new IllegalStateException("Unable to locate repository root for postprocess artifacts");
  }

  private int generateBorrowedBuffer() {
    int handle = Gdx.gl.glGenBuffer();
    Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, handle);
    Gdx.gl.glBufferData(
        GL20.GL_ARRAY_BUFFER, 16, BufferUtils.newByteBuffer(16), GL20.GL_STATIC_DRAW);
    return handle;
  }

  private static void assertOwnedIdsLive(WorldPostProcess.ResourceIds ids) {
    require(ids.framebuffer() > 0, "owned framebuffer ID is zero");
    require(ids.colorTexture() > 0, "owned color texture ID is zero");
    require(ids.depthRenderbuffer() > 0, "owned depth renderbuffer ID is zero");
    require(ids.shaderProgram() > 0, "owned shader program ID is zero");
    require(ids.vertexBuffer() > 0, "owned quad VBO ID is zero");
    require(ids.vertexArray() > 0, "owned quad VAO ID is zero");
    require(Gdx.gl.glIsFramebuffer(ids.framebuffer()), "owned framebuffer is not live");
    require(Gdx.gl.glIsTexture(ids.colorTexture()), "owned color texture is not live");
    require(Gdx.gl.glIsRenderbuffer(ids.depthRenderbuffer()), "owned depth buffer is not live");
    require(Gdx.gl.glIsProgram(ids.shaderProgram()), "owned shader program is not live");
    require(Gdx.gl.glIsBuffer(ids.vertexBuffer()), "owned quad VBO is not live");
    require(Gdx.gl30.glIsVertexArray(ids.vertexArray()), "owned quad VAO is not live");
  }

  private static int[] getAttributeState(int location) {
    int[] state = new int[6];
    int[] keys = {
      GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED,
      GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE,
      GL20.GL_VERTEX_ATTRIB_ARRAY_TYPE,
      GL20.GL_VERTEX_ATTRIB_ARRAY_NORMALIZED,
      GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE,
      GL20.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING
    };
    for (int i = 0; i < keys.length; i++) {
      java.nio.IntBuffer value = BufferUtils.newIntBuffer(1);
      Gdx.gl.glGetVertexAttribiv(location, keys[i], value);
      state[i] = value.get(0);
    }
    return state;
  }

  private static void proveFailedShaderPreflightRecovery() {
    try {
      Method preflight =
          WorldPostProcess.class.getDeclaredMethod(
              "preflightShaderProgram", String.class, String.class);
      preflight.setAccessible(true);
      try {
        preflight.invoke(null, "invalid shader", "invalid shader");
        throw new AssertionError("invalid shader source passed preflight");
      } catch (InvocationTargetException failure) {
        require(
            failure.getCause() instanceof IllegalStateException,
            "invalid shader preflight returned unexpected failure: " + failure.getCause());
      }
      require(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "failed shader preflight left a GL error");
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("unable to invoke shader preflight failure proof", failure);
    }
  }

  private void drawWorld(float red, float green, float blue) {
    post.renderWorld(() -> clearWorld(red, green, blue));
  }

  private static void clearWorld(float red, float green, float blue) {
    Gdx.gl.glClearColor(red, green, blue, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
  }

  private int[] pixel(int x, int y) {
    ByteBuffer pixel = BufferUtils.newByteBuffer(4);
    Gdx.gl.glReadPixels(x, y, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixel);
    return new int[] {
      pixel.get(0) & 255, pixel.get(1) & 255, pixel.get(2) & 255, pixel.get(3) & 255
    };
  }

  private static boolean near(int actual, int expected, int tolerance) {
    return Math.abs(actual - expected) <= tolerance;
  }

  private static String rgb(int[] color) {
    return "[" + color[0] + "," + color[1] + "," + color[2] + "," + color[3] + "]";
  }

  private static int getInt(int parameter) {
    var value = BufferUtils.newIntBuffer(1);
    Gdx.gl.glGetIntegerv(parameter, value);
    return value.get(0);
  }

  private static int[] getFour(int parameter) {
    var value = BufferUtils.newIntBuffer(4);
    Gdx.gl.glGetIntegerv(parameter, value);
    return new int[] {value.get(0), value.get(1), value.get(2), value.get(3)};
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
