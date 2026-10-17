package org.javity.demo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.BufferUtils;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.javity.core.Game;
import org.javity.effects.Clock;
import org.javity.particles.ParticleBurstHandle3D;
import org.javity.particles.ParticleBurstPool3D;
import org.javity.particles.ParticleBurstRenderer3D;
import org.javity.particles.ParticleBurstSpec3D;

/** Pixel/depth/state/ownership proof for the bounded additive burst renderer. */
final class ParticleGpuSmokeProbe extends ApplicationAdapter {
  private static final int WIDTH = 256, HEIGHT = 256;
  private Game game;
  private ParticleBurstPool3D pool;
  private ParticleBurstRenderer3D particles;
  private Texture borrowedTexture;
  private ModelBatch modelBatch;
  private Model occluderModel;
  private ModelInstance occluder;
  private PerspectiveCamera camera;
  private ParticleBurstHandle3D hiddenBurst, visibleBurst;
  private int viewportOriginX = 7, viewportOriginY = 9;
  private Throwable failure;

  @Override
  public void create() {
    try {
      run();
      System.out.println(
          "particleGpuSmoke: PASS (real pixels, depth, state, leases, resize, borrowed texture,"
              + " cleanup)");
    } catch (Throwable error) {
      failure = error;
      error.printStackTrace();
    } finally {
      Gdx.app.exit();
    }
  }

  @Override
  public void render() {
    if (failure != null) throw new IllegalStateException("Particle GPU proof failed", failure);
  }

  private void run() throws Exception {
    camera = new PerspectiveCamera(60, 220, 210);
    camera.position.set(0, 0, 6);
    camera.lookAt(0, 0, 0);
    camera.near = .1f;
    camera.far = 40;
    camera.update();
    modelBatch = new ModelBatch();
    Environment environment = new Environment();
    environment.set(ColorAttribute.createAmbientLight(.18f, .18f, .18f, 1));
    occluderModel =
        new ModelBuilder()
            .createBox(
                1.3f,
                1.3f,
                .2f,
                new Material(ColorAttribute.createDiffuse(new Color(.04f, .04f, .04f, 1))),
                com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                    | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);
    occluder = new ModelInstance(occluderModel);
    Pixmap sprite = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
    sprite.setBlending(Pixmap.Blending.None);
    for (int y = 0; y < 16; y++)
      for (int x = 0; x < 16; x++) {
        float dx = (x + .5f - 8) / 8f, dy = (y + .5f - 8) / 8f;
        float alpha = Math.max(0, Math.min(1, (1 - (float) Math.sqrt(dx * dx + dy * dy)) * 5));
        sprite.setColor(1, 1, 1, alpha);
        sprite.drawPixel(x, y);
      }
    borrowedTexture = new Texture(sprite);
    borrowedTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    sprite.dispose();
    game = new Game();
    pool = game.addSystem(new ParticleBurstPool3D(Clock.WORLD, 8, 4));
    game.start();
    particles = new ParticleBurstRenderer3D(8);
    hiddenBurst = pool.burst(spec(0, -.35f, -0.6f, new Color(1, .15f, .03f, .95f), 501));
    visibleBurst = pool.burst(spec(1.45f, 0, -.6f, new Color(.03f, .35f, 1, .95f), 902));
    game.step(.016f);
    int particleProgram = particles.getShaderProgramId();
    int vertexBuffer = particles.getMeshVertexBufferId(),
        indexBuffer = particles.getMeshIndexBufferId();
    int particleVertexArray = particles.getMeshVertexArrayId();
    int textureId = borrowedTexture.getTextureObjectHandle();
    assertTrue("particle VBO must be a live owned buffer before close", vertexBuffer != 0);
    assertTrue("particle IBO must be a live owned buffer before close", indexBuffer != 0);
    assertTrue("particle VAO must be live before close", particleVertexArray != 0);
    assertTrue("particle VBO must exist before close", Gdx.gl.glIsBuffer(vertexBuffer));
    assertTrue("particle IBO must exist before close", Gdx.gl.glIsBuffer(indexBuffer));
    assertTrue(
        "particle VAO must exist before close", Gdx.gl30.glIsVertexArray(particleVertexArray));

    Gdx.gl.glViewport(7, 9, 220, 210);
    clearAndDrawWorld(environment);
    int centerX = projectX(0), centerY = projectY(0);
    float occludedDepthBefore = readDepth(centerX, centerY);
    float visibleDepthBefore = readDepth(projectX(1.45f), projectY(0));
    int occludedRed = readRed(centerX, centerY);
    int visibleBlueBefore = readBlue(projectX(1.45f), projectY(0));
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    Gdx.gl.glDisable(GL20.GL_BLEND);
    Gdx.gl.glDepthMask(true);
    IntBuffer viewport = BufferUtils.newIntBuffer(4);
    Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, viewport);
    int[] priorViewport = {viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3)};
    boolean priorDepth = Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST),
        priorBlend = Gdx.gl.glIsEnabled(GL20.GL_BLEND);
    ByteBuffer mask = BufferUtils.newByteBuffer(1);
    Gdx.gl.glGetBooleanv(GL20.GL_DEPTH_WRITEMASK, mask);
    boolean priorDepthMask = mask.get(0) != 0;
    particles.draw(pool, camera, borrowedTexture, 7, 9, 220, 210);
    float occludedDepthAfter = readDepth(centerX, centerY);
    float visibleDepthAfter = readDepth(projectX(1.45f), projectY(0));
    int visibleBlue = readBlue(projectX(1.45f), projectY(0));
    System.out.println(
        "particle pixels center="
            + occludedRed
            + " sideBlue="
            + visibleBlueBefore
            + "->"
            + visibleBlue
            + " at "
            + projectX(1.45f)
            + ","
            + projectY(0));
    assertTrue("opaque box must cover the rear particle", occludedRed < 35);
    assertTrue(
        "visible burst must add its blue sprite pixels", visibleBlue > visibleBlueBefore + 35);
    assertNear(occludedDepthBefore, occludedDepthAfter, 1e-6f, "depth writes at occluder");
    assertNear(visibleDepthBefore, visibleDepthAfter, 1e-6f, "depth writes at particle");
    assertViewport(priorViewport);
    assertEquals(priorDepth, Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST));
    assertEquals(priorBlend, Gdx.gl.glIsEnabled(GL20.GL_BLEND));
    mask.clear();
    Gdx.gl.glGetBooleanv(GL20.GL_DEPTH_WRITEMASK, mask);
    assertEquals(priorDepthMask, mask.get(0) != 0);
    capture("docs/artifacts/particles3d/particles-occlusion.png");
    verifyCallerMeshState(environment);

    occluder.transform.setToTranslation(20, 0, 0);
    hiddenBurst.cancel();
    Gdx.gl.glViewport(0, 0, 192, 160);
    viewportOriginX = 0;
    viewportOriginY = 0;
    camera.viewportWidth = 192;
    camera.viewportHeight = 160;
    camera.update();
    clearAndDrawWorld(environment);
    particles.draw(pool, camera, borrowedTexture, 0, 0, 192, 160);
    assertViewport(new int[] {0, 0, 192, 160});
    int centerAfterCancel = readRed(projectX(0), projectY(0));
    int sideAfterCancel = readBlue(projectX(1.45f), projectY(0));
    assertTrue("cancelled burst must disappear", centerAfterCancel < 8);
    assertTrue("independent burst must remain visible", sideAfterCancel > 35);
    capture("docs/artifacts/particles3d/particles-independent-resize.png");

    visibleBurst.cancel();
    assertEquals(0, pool.getActiveParticleCount());
    particles.close();
    assertFalse("owned vertex buffer must be deleted", Gdx.gl.glIsBuffer(vertexBuffer));
    assertFalse("owned index buffer must be deleted", Gdx.gl.glIsBuffer(indexBuffer));
    assertFalse("owned VAO must be deleted", Gdx.gl30.glIsVertexArray(particleVertexArray));
    assertFalse("owned shader program must be deleted", Gdx.gl.glIsProgram(particleProgram));
    assertTrue("borrowed texture must survive renderer close", Gdx.gl.glIsTexture(textureId));
    borrowedTexture.bind();
    writeReport(
        String.format(
            Locale.ROOT,
            "{\n"
                + "  \"status\": \"PASS\",\n"
                + "  \"profile\": \"%s\",\n"
                + "  \"occludedCenterRed\": %d,\n"
                + "  \"visibleSideBlueBefore\": %d,\n"
                + "  \"visibleSideBlueAfter\": %d,\n"
                + "  \"cancelledCenterRed\": %d,\n"
                + "  \"independentSideBlue\": %d,\n"
                + "  \"depthAtOccluderBefore\": %.8f,\n"
                + "  \"depthAtOccluderAfter\": %.8f,\n"
                + "  \"depthAtParticleBefore\": %.8f,\n"
                + "  \"depthAtParticleAfter\": %.8f,\n"
                + "  \"resizeViewport\": [192, 160],\n"
                + "  \"borrowedTextureAliveAfterClose\": true,\n"
                + "  \"ownedVertexBufferDeleted\": true,\n"
                + "  \"ownedIndexBufferDeleted\": true,\n"
                + "  \"ownedVertexArrayDeleted\": true,\n"
                + "  \"ownedVertexBufferWasLiveBeforeClose\": true,\n"
                + "  \"ownedIndexBufferWasLiveBeforeClose\": true,\n"
                + "  \"ownedVertexArrayWasLiveBeforeClose\": true,\n"
                + "  \"callerMeshBufferSurvivedParticleDraw\": true,\n"
                + "  \"callerMeshIndexBufferPreserved\": true,\n"
                + "  \"callerVertexArrayRestored\": true,\n"
                + "  \"ownedShaderProgramDeleted\": true\n"
                + "}\n",
            Gdx.graphics.getGLVersion().getVersionString()
                + " "
                + Gdx.graphics.getGLVersion().getRendererString(),
            occludedRed,
            visibleBlueBefore,
            visibleBlue,
            centerAfterCancel,
            sideAfterCancel,
            occludedDepthBefore,
            occludedDepthAfter,
            visibleDepthBefore,
            visibleDepthAfter));
    game.dispose();
    borrowedTexture.dispose();
    occluderModel.dispose();
    modelBatch.dispose();
  }

  private void verifyCallerMeshState(Environment environment) {
    String vertex =
        "attribute vec3 a_position; uniform mat4 u_projView;"
            + " void main(){ gl_Position=u_projView*vec4(a_position,1.0); }";
    String fragment =
        "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
            + "void main(){ gl_FragColor=vec4(0.0,1.0,0.0,1.0); }";
    ShaderProgram callerShader = new ShaderProgram(vertex, fragment);
    if (!callerShader.isCompiled())
      throw new AssertionError("caller fixture shader: " + callerShader.getLog());
    Mesh callerMesh =
        new Mesh(
            true,
            3,
            3,
            new VertexAttribute(
                VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE));
    callerMesh.setVertices(new float[] {-.35f, -.3f, 5f, .35f, -.3f, 5f, 0f, .35f, 5f});
    callerMesh.setIndices(new short[] {0, 1, 2});
    int callerProgram = callerShader.getHandle();
    callerShader.bind();
    callerShader.setUniformMatrix("u_projView", camera.combined);
    callerMesh.bind(callerShader);
    int position = callerShader.getAttributeLocation(ShaderProgram.POSITION_ATTRIBUTE);
    int callerBuffer = queryAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING);
    int callerVertexArray = queryInt(GL30.GL_VERTEX_ARRAY_BINDING);
    int callerIndexBuffer = queryInt(GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING);
    if (callerVertexArray == 0 || callerBuffer == 0 || callerIndexBuffer == 0)
      throw new AssertionError("caller fixture must own a nonempty VAO, VBO and IBO");
    if (queryAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED) == 0)
      throw new AssertionError("caller mesh fixture did not leave a vertex attribute enabled");
    particles.draw(
        pool,
        camera,
        borrowedTexture,
        viewportOriginX,
        viewportOriginY,
        (int) camera.viewportWidth,
        (int) camera.viewportHeight);
    IntBuffer currentProgram = BufferUtils.newIntBuffer(1);
    Gdx.gl.glGetIntegerv(GL20.GL_CURRENT_PROGRAM, currentProgram);
    if (currentProgram.get(0) != callerProgram)
      throw new AssertionError("particle pass did not restore caller shader program");
    if (queryInt(GL30.GL_VERTEX_ARRAY_BINDING) != callerVertexArray)
      throw new AssertionError("particle pass did not restore caller VAO");
    if (!Gdx.gl.glIsBuffer(callerBuffer))
      throw new AssertionError("particle pass deleted the borrowed caller mesh buffer");
    if (queryInt(GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING) != callerIndexBuffer
        || !Gdx.gl.glIsBuffer(callerIndexBuffer))
      throw new AssertionError("particle pass did not preserve caller mesh index buffer");
    if (queryAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED) == 0
        || queryAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING) != callerBuffer)
      throw new AssertionError("particle pass did not restore caller vertex attribute state");
    callerShader.setUniformMatrix("u_projView", camera.combined);
    callerMesh.render(callerShader, GL20.GL_TRIANGLES, 0, 3);
    int green = readGreen(projectX(0), projectY(0));
    if (green < 150)
      throw new AssertionError("prebound caller mesh could not render after particles: " + green);
    callerShader.end();
    callerMesh.dispose();
    callerShader.dispose();
  }

  private int queryAttrib(int index, int property) {
    IntBuffer value = BufferUtils.newIntBuffer(1);
    Gdx.gl.glGetVertexAttribiv(index, property, value);
    return value.get(0);
  }

  private int queryInt(int property) {
    IntBuffer value = BufferUtils.newIntBuffer(1);
    Gdx.gl.glGetIntegerv(property, value);
    return value.get(0);
  }

  private ParticleBurstSpec3D spec(float x, float y, float z, Color color, long seed) {
    return new ParticleBurstSpec3D(
        new Vector3(x, y, z), new Vector3(), new Vector3(), new Vector3(), 2, .72f, color, 1, seed);
  }

  private void clearAndDrawWorld(Environment environment) {
    Gdx.gl.glViewport(
        viewportOriginX, viewportOriginY, (int) camera.viewportWidth, (int) camera.viewportHeight);
    Gdx.gl.glClearColor(0, 0, 0, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    modelBatch.begin(camera);
    modelBatch.render(occluder, environment);
    modelBatch.end();
  }

  private int projectX(float x) {
    Vector3 p =
        camera.project(
            new Vector3(x, 0, -0.6f),
            viewportOriginX,
            viewportOriginY,
            camera.viewportWidth,
            camera.viewportHeight);
    return Math.max(0, Math.min(WIDTH - 1, Math.round(p.x)));
  }

  private int projectY(float y) {
    Vector3 p =
        camera.project(
            new Vector3(0, y, -0.6f),
            viewportOriginX,
            viewportOriginY,
            camera.viewportWidth,
            camera.viewportHeight);
    return Math.max(0, Math.min(HEIGHT - 1, Math.round(p.y)));
  }

  private int readRed(int x, int y) {
    ByteBuffer px = BufferUtils.newByteBuffer(4);
    Gdx.gl.glReadPixels(x, y, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, px);
    return px.get(0) & 255;
  }

  private int readGreen(int x, int y) {
    ByteBuffer px = BufferUtils.newByteBuffer(4);
    Gdx.gl.glReadPixels(x, y, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, px);
    return px.get(1) & 255;
  }

  private int readBlue(int x, int y) {
    ByteBuffer px = BufferUtils.newByteBuffer(4);
    Gdx.gl.glReadPixels(x, y, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, px);
    return px.get(2) & 255;
  }

  private float readDepth(int x, int y) {
    FloatBuffer px = BufferUtils.newFloatBuffer(1);
    Gdx.gl.glReadPixels(x, y, 1, 1, GL20.GL_DEPTH_COMPONENT, GL20.GL_FLOAT, px);
    return px.get(0);
  }

  private void capture(String relative) throws Exception {
    int w = WIDTH, h = HEIGHT;
    Pixmap pixmap = new Pixmap(w, h, Pixmap.Format.RGBA8888);
    Gdx.gl.glReadPixels(0, 0, w, h, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixmap.getPixels());
    PixmapIO.writePNG(Gdx.files.absolute(Path.of(relative).toAbsolutePath().toString()), pixmap);
    pixmap.dispose();
  }

  private void assertViewport(int[] expected) {
    IntBuffer v = BufferUtils.newIntBuffer(4);
    Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, v);
    for (int i = 0; i < 4; i++)
      if (v.get(i) != expected[i]) throw new AssertionError("viewport restore mismatch");
  }

  private void assertTrue(String message, boolean value) {
    if (!value) throw new AssertionError(message);
  }

  private void assertFalse(String message, boolean value) {
    if (value) throw new AssertionError(message);
  }

  private void assertEquals(boolean expected, boolean actual) {
    if (expected != actual) throw new AssertionError("expected " + expected + " but was " + actual);
  }

  private void assertEquals(int expected, int actual) {
    if (expected != actual) throw new AssertionError("expected " + expected + " but was " + actual);
  }

  private void assertNear(float a, float b, float epsilon, String label) {
    if (Math.abs(a - b) > epsilon) throw new AssertionError(label + ": " + a + " vs " + b);
  }

  private void writeReport(String report) throws Exception {
    Files.writeString(Path.of("docs/artifacts/particles3d/verification.json"), report);
  }
}
