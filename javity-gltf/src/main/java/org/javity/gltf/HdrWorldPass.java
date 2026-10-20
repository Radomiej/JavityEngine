package org.javity.gltf;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.GLVersion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import java.nio.IntBuffer;
import java.util.Objects;

/**
 * Opt-in linear RGBA16F world capture, exposure, tone mapping and explicit sRGB output. All
 * resources belong to this pass. The callback, its renderer and output framebuffer are borrowed.
 * Construct/use/close on the live GL30 owner thread; render UI after renderWorld returns.
 */
public final class HdrWorldPass implements Disposable {
  public static final int DEFAULT_MAX_DIMENSION = 4096;
  public static final long DEFAULT_ATTACHMENT_BUDGET_BYTES = 128L * 1024 * 1024;
  private static final int FRAMEBUFFER_SRGB = 0x8DB9;
  private static final String VERTEX =
      "attribute vec2 a_position; attribute vec2 a_texCoord0; varying vec2 v_uv; void main(){"
          + " v_uv=a_texCoord0; gl_Position=vec4(a_position,0.,1.); }";
  private static final String FRAGMENT =
      "#ifdef GL_ES\n"
          + "precision highp float;\n"
          + "#endif\n"
          + "uniform sampler2D u_texture; uniform float u_exposure; uniform int u_operator; varying"
          + " vec2 v_uv;vec3 srgb(vec3 x){ return"
          + " mix(12.92*x,1.055*pow(x,vec3(1./2.4))-.055,step(vec3(.0031308),x)); }void main(){"
          + " vec4 c=texture2D(u_texture,v_uv); vec3"
          + " x=max(c.rgb,vec3(0.))*u_exposure;if(u_operator==0)"
          + " x=clamp((x*(2.51*x+.03))/(x*(2.43*x+.59)+.14),0.,1.);else x=x/(vec3(1.)+x);"
          + " gl_FragColor=vec4(srgb(x),c.a); }";
  private final Thread owner = Thread.currentThread();
  private final int maxDimension;
  private final long budget;
  private RecoverableHdrFrameBuffer target;
  private Mesh quad;
  private ShaderProgram shader;
  private HdrSettings settings = HdrSettings.DEFAULT;
  private boolean closed, rendering;

  public HdrWorldPass(int width, int height) {
    this(width, height, DEFAULT_MAX_DIMENSION, DEFAULT_ATTACHMENT_BUDGET_BYTES);
  }

  public HdrWorldPass(int width, int height, int maxDimension, long attachmentBudgetBytes) {
    requireContext();
    validateTarget(width, height, maxDimension, attachmentBudgetBytes);
    this.maxDimension = maxDimension;
    this.budget = attachmentBudgetBytes;
    ShadowGlState state = new ShadowGlState();
    Throwable failure = null;
    try {
      target = new RecoverableHdrFrameBuffer(width, height);
      quad = makeQuad();
      shader = makeShader();
    } catch (RuntimeException | Error problem) {
      failure = problem;
      cleanup(problem, shader, quad, target);
      throw problem;
    } finally {
      try {
        restore(state, failure);
      } catch (RuntimeException | Error restoreFailure) {
        cleanup(restoreFailure, shader, quad, target);
        throw restoreFailure;
      }
    }
  }

  /** RGBA16F (8 bytes) plus depth16 (2); excludes driver bookkeeping and shader/mesh resources. */
  public static long validateTarget(int width, int height, int maxDimension, long budget) {
    if (maxDimension < 1 || maxDimension > DEFAULT_MAX_DIMENSION)
      throw new IllegalArgumentException("HDR maximum dimension must be in [1,4096]");
    if (width < 1 || height < 1 || width > maxDimension || height > maxDimension)
      throw new IllegalArgumentException("HDR viewport exceeds supported dimensions");
    long bytes = Math.multiplyExact(Math.multiplyExact((long) width, height), 10);
    if (budget <= 0 || bytes > budget)
      throw new IllegalArgumentException("HDR attachment budget exceeded");
    return bytes;
  }

  public static long validateResizeBudget(
      int oldWidth, int oldHeight, int width, int height, int maxDimension, long budget) {
    long peak =
        Math.addExact(
            validateTarget(oldWidth, oldHeight, maxDimension, budget),
            validateTarget(width, height, maxDimension, budget));
    if (peak > budget)
      throw new IllegalArgumentException("HDR transactional resize peak exceeds budget");
    return peak;
  }

  public int width() {
    check();
    return target.getWidth();
  }

  public int height() {
    check();
    return target.getHeight();
  }

  public HdrSettings settings() {
    check();
    return settings;
  }

  public void setSettings(HdrSettings settings) {
    checkIdle();
    this.settings = Objects.requireNonNull(settings, "settings");
  }

  /**
   * Callback must output linear radiance, including values above one. Does not own the callback.
   */
  public void renderWorld(Runnable world) {
    checkIdle();
    Objects.requireNonNull(world, "world");
    ShadowGlState state = new ShadowGlState();
    int draw = integer(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    if (draw == target.getFramebufferHandle())
      throw new IllegalStateException("HDR cannot composite into its own captured texture");
    IntBuffer viewport = BufferUtils.newIntBuffer(4);
    Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, viewport);
    boolean desktop = Gdx.graphics.getGLVersion().getType() == GLVersion.Type.OpenGL;
    boolean framebufferSrgb = desktop && Gdx.gl.glIsEnabled(FRAMEBUFFER_SRGB);
    rendering = true;
    Throwable failure = null;
    try {
      state.prepareDepth();
      if (desktop) Gdx.gl.glDisable(FRAMEBUFFER_SRGB);
      target.begin();
      Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
      Gdx.gl.glColorMask(true, true, true, true);
      Gdx.gl.glDepthMask(true);
      Gdx.gl.glClearColor(0, 0, 0, 0);
      Gdx.gl.glClearDepthf(1);
      Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
      world.run();
      Gdx.gl.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
      Gdx.gl.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
      state.prepareDepth();
      if (desktop) Gdx.gl.glDisable(FRAMEBUFFER_SRGB);
      Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
      Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
      Gdx.gl.glDisable(GL20.GL_BLEND);
      Gdx.gl.glDisable(GL20.GL_CULL_FACE);
      Gdx.gl.glDepthMask(false);
      Gdx.gl.glColorMask(true, true, true, true);
      target.getColorBufferTexture().bind(0);
      shader.bind();
      shader.setUniformi("u_texture", 0);
      shader.setUniformf("u_exposure", settings.exposure());
      shader.setUniformi(
          "u_operator", settings.toneMapper() == HdrSettings.ToneMapper.ACES ? 0 : 1);
      quad.render(shader, GL20.GL_TRIANGLE_STRIP);
    } catch (RuntimeException | Error problem) {
      failure = problem;
      throw problem;
    } finally {
      try {
        restoreCapture(state, desktop, framebufferSrgb, failure);
      } finally {
        rendering = false;
      }
    }
  }

  /** Candidate is complete before publication. Old+candidate must fit the attachment budget. */
  public void resize(int width, int height) {
    checkIdle();
    if (width == width() && height == height()) return;
    validateResizeBudget(width(), height(), width, height, maxDimension, budget);
    ShadowGlState state = new ShadowGlState();
    Throwable failure = null;
    try {
      RecoverableHdrFrameBuffer candidate = new RecoverableHdrFrameBuffer(width, height);
      RecoverableHdrFrameBuffer previous = target;
      target = candidate;
      state.replaceOwnedTarget(
          previous.getFramebufferHandle(),
          previous.getColorBufferTexture().getTextureObjectHandle(),
          previous.getDepthBufferHandle(),
          candidate.getFramebufferHandle(),
          candidate.getColorBufferTexture().getTextureObjectHandle(),
          candidate.getDepthBufferHandle());
      previous.dispose();
    } catch (RuntimeException | Error problem) {
      failure = problem;
      throw problem;
    } finally {
      restore(state, failure);
    }
  }

  /**
   * After the host's managed GL invalidation, refresh shader location caches before the next draw.
   */
  public void contextRestored() {
    checkIdle();
    ShadowGlState state = new ShadowGlState();
    Throwable failure = null;
    ShaderProgram replacement = null;
    Mesh replacementQuad = null;
    try {
      replacement = makeShader();
      replacementQuad = makeQuad();
      ShaderProgram previous = shader;
      Mesh previousQuad = quad;
      shader = replacement;
      quad = replacementQuad;
      state.replaceOwnedProgram(previous.getHandle(), replacement.getHandle());
      RuntimeException retiredFailure =
          new RuntimeException("Failed disposing retired HDR program/quad");
      cleanup(retiredFailure, previous, previousQuad);
      if (retiredFailure.getSuppressed().length != 0) throw retiredFailure;
    } catch (RuntimeException | Error problem) {
      failure = problem;
      if (shader != replacement) cleanup(problem, replacementQuad, replacement);
      throw problem;
    } finally {
      restore(state, failure);
    }
  }

  public ResourceIds resourceIds() {
    check();
    return new ResourceIds(
        target.getFramebufferHandle(),
        target.getColorBufferTexture().getTextureObjectHandle(),
        target.getDepthBufferHandle(),
        shader.getHandle(),
        ((TrackedQuad) quad).vertexBuffer,
        ((TrackedQuad) quad).vertexArray);
  }

  public record ResourceIds(
      int framebuffer,
      int colorTexture,
      int depthRenderbuffer,
      int shaderProgram,
      int vertexBuffer,
      int vertexArray) {}

  @Override
  public void dispose() {
    checkThread();
    if (closed) return;
    checkIdle();
    closed = true;
    RuntimeException failure = new RuntimeException("Failed disposing HDR resources");
    cleanup(failure, shader, quad, target);
    shader = null;
    quad = null;
    target = null;
    if (failure.getSuppressed().length != 0) throw failure;
  }

  private static Mesh makeQuad() {
    Mesh mesh =
        new TrackedQuad(
            true,
            4,
            0,
            new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"),
            new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0"));
    try {
      mesh.setVertices(new float[] {-1, -1, 0, 0, 1, -1, 1, 0, -1, 1, 0, 1, 1, 1, 1, 1});
      return mesh;
    } catch (RuntimeException | Error problem) {
      cleanup(problem, mesh);
      throw problem;
    }
  }

  private static final class TrackedQuad extends Mesh {
    int vertexBuffer, vertexArray;
    final IntBuffer binding = BufferUtils.newIntBuffer(1);

    TrackedQuad(boolean isStatic, int vertices, int indices, VertexAttribute... attributes) {
      super(isStatic, vertices, indices, attributes);
    }

    @Override
    public void bind(ShaderProgram shader) {
      super.bind(shader);
      binding.clear();
      Gdx.gl.glGetVertexAttribiv(
          shader.getAttributeLocation("a_position"),
          GL20.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING,
          binding);
      vertexBuffer = binding.get(0);
      vertexArray = integer(GL30.GL_VERTEX_ARRAY_BINDING);
    }
  }

  private static ShaderProgram makeShader() {
    // Preflight raw compile/link so libGDX never owns a failed shader handle.
    int vertex = 0, fragment = 0, program = 0;
    try {
      vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX);
      fragment = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
      program = Gdx.gl.glCreateProgram();
      if (program == 0) throw new IllegalStateException("HDR program allocation failed");
      Gdx.gl.glAttachShader(program, vertex);
      Gdx.gl.glAttachShader(program, fragment);
      Gdx.gl.glLinkProgram(program);
      IntBuffer status = BufferUtils.newIntBuffer(1);
      Gdx.gl.glGetProgramiv(program, GL20.GL_LINK_STATUS, status);
      if (status.get(0) == 0)
        throw new IllegalStateException("HDR shader link: " + Gdx.gl.glGetProgramInfoLog(program));
    } finally {
      if (program != 0) Gdx.gl.glDeleteProgram(program);
      if (fragment != 0) Gdx.gl.glDeleteShader(fragment);
      if (vertex != 0) Gdx.gl.glDeleteShader(vertex);
    }
    ShaderProgram result = new ShaderProgram(VERTEX, FRAGMENT);
    if (!result.isCompiled()) {
      String log = result.getLog();
      result.dispose();
      throw new IllegalStateException("HDR shader: " + log);
    }
    return result;
  }

  private static int compile(int type, String source) {
    int shader = Gdx.gl.glCreateShader(type);
    if (shader == 0) throw new IllegalStateException("HDR shader allocation failed");
    try {
      Gdx.gl.glShaderSource(shader, source);
      Gdx.gl.glCompileShader(shader);
      IntBuffer status = BufferUtils.newIntBuffer(1);
      Gdx.gl.glGetShaderiv(shader, GL20.GL_COMPILE_STATUS, status);
      if (status.get(0) == 0)
        throw new IllegalStateException("HDR shader compile: " + Gdx.gl.glGetShaderInfoLog(shader));
      return shader;
    } catch (RuntimeException | Error failure) {
      Gdx.gl.glDeleteShader(shader);
      throw failure;
    }
  }

  private static int integer(int parameter) {
    IntBuffer result = BufferUtils.newIntBuffer(1);
    Gdx.gl.glGetIntegerv(parameter, result);
    return result.get(0);
  }

  private static void cleanup(Throwable failure, Disposable... resources) {
    for (Disposable resource : resources)
      if (resource != null)
        try {
          resource.dispose();
        } catch (RuntimeException | Error problem) {
          if (problem != failure) failure.addSuppressed(problem);
        }
  }

  private static void restoreCapture(
      ShadowGlState state, boolean desktop, boolean framebufferSrgb, Throwable original) {
    Throwable first = original;
    try {
      state.restore();
    } catch (RuntimeException | Error problem) {
      if (first == null) first = problem;
      else if (problem != first) first.addSuppressed(problem);
    }
    try {
      if (desktop) {
        if (framebufferSrgb) Gdx.gl.glEnable(FRAMEBUFFER_SRGB);
        else Gdx.gl.glDisable(FRAMEBUFFER_SRGB);
      }
    } catch (RuntimeException | Error problem) {
      if (first == null) first = problem;
      else if (problem != first) first.addSuppressed(problem);
    }
    if (original == null && first != null) {
      if (first instanceof RuntimeException runtime) throw runtime;
      throw (Error) first;
    }
  }

  private static void restore(ShadowGlState state, Throwable failure) {
    try {
      state.restore();
    } catch (RuntimeException | Error problem) {
      if (failure == null) throw problem;
      if (problem != failure) failure.addSuppressed(problem);
    }
  }

  private static void requireContext() {
    if (Gdx.gl30 == null || Gdx.graphics == null)
      throw new UnsupportedOperationException(
          "HDR requires a live GL30 context and complete RGBA16F/depth16 target");
  }

  private void checkThread() {
    if (Thread.currentThread() != owner)
      throw new IllegalStateException("HDR requires its GL owner thread");
  }

  private void check() {
    checkThread();
    if (closed) throw new IllegalStateException("HDR pass is closed");
    requireContext();
  }

  private void checkIdle() {
    check();
    if (rendering) throw new IllegalStateException("HDR capture is not reentrant");
  }
}
