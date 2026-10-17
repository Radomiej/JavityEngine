package org.javity.postprocessing;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.javity.render.RecoverableFrameBuffer;

/**
 * Captures one explicit world pass and composites tint, desaturation and vignette before host UI.
 * The framebuffer, quad and shader belong to this object; the callback and all host renderers are
 * borrowed. Construct, resize, render and close on the thread that owns the live GL context.
 */
public final class WorldPostProcess implements Disposable {
  public static final int DEFAULT_MAX_DIMENSION = 4096;
  public static final long DEFAULT_ATTACHMENT_BUDGET_BYTES = 128L * 1024 * 1024;
  public static final int MAX_CONTROL_LEASES = 64;
  private static final int BYTES_PER_PIXEL = 6; // RGBA8888 plus a 16-bit depth attachment.
  private static final String VERTEX_SHADER =
      "attribute vec4 a_position;\n"
          + "attribute vec2 a_texCoord0;\n"
          + "varying vec2 v_uv;\n"
          + "void main(){ v_uv=a_texCoord0; gl_Position=a_position; }\n";
  private static final String FRAGMENT_SHADER =
      "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
          + "uniform sampler2D u_texture;\n"
          + "uniform vec3 u_tint;\n"
          + "uniform float u_desaturation;\n"
          + "uniform float u_vignette;\n"
          + "uniform int u_bypass;\n"
          + "varying vec2 v_uv;\n"
          + "void main(){\n"
          + " vec4 c=texture2D(u_texture,v_uv);\n"
          + " if(u_bypass==0){\n"
          + "  c.rgb*=u_tint;\n"
          + "  float y=dot(c.rgb,vec3(0.2126,0.7152,0.0722));\n"
          + "  c.rgb=mix(c.rgb,vec3(y),u_desaturation);\n"
          + "  float d=length((v_uv-vec2(0.5))*1.41421356);\n"
          + "  float edge=smoothstep(0.25,0.8,d);\n"
          + "  c.rgb*=1.0-u_vignette*edge;\n"
          + " }\n"
          + " gl_FragColor=c;\n"
          + "}\n";

  private final Thread ownerThread;
  private final int maxDimension;
  private final long attachmentBudgetBytes;
  private final ControlState controls = new ControlState();
  private final IntBuffer oneInt = BufferUtils.newIntBuffer(1);
  private final IntBuffer fourInts = BufferUtils.newIntBuffer(4);
  private final FloatBuffer fourFloats = BufferUtils.newFloatBuffer(4);
  private FrameBuffer target;
  private Mesh quad;
  private ShaderProgram shader;
  private int width;
  private int height;
  private boolean closed;
  private boolean rendering;

  public WorldPostProcess(int physicalWidth, int physicalHeight) {
    this(physicalWidth, physicalHeight, DEFAULT_MAX_DIMENSION, DEFAULT_ATTACHMENT_BUDGET_BYTES);
  }

  /** Exposed budget parameters make the allocation policy testable by headless callers. */
  public WorldPostProcess(
      int physicalWidth, int physicalHeight, int maxDimension, long attachmentBudgetBytes) {
    requireContext();
    if (maxDimension <= 0 || maxDimension > DEFAULT_MAX_DIMENSION)
      throw new IllegalArgumentException("maxDimension must be in [1,4096]");
    if (attachmentBudgetBytes <= 0)
      throw new IllegalArgumentException("attachmentBudgetBytes must be positive");
    validateTarget(physicalWidth, physicalHeight, maxDimension, attachmentBudgetBytes);
    ownerThread = Thread.currentThread();
    this.maxDimension = maxDimension;
    this.attachmentBudgetBytes = attachmentBudgetBytes;
    FrameBuffer createdTarget = null;
    Mesh createdQuad = null;
    ShaderProgram createdShader = null;
    GlState allocationState = new GlState(oneInt, fourInts, fourFloats);
    Throwable allocationFailure = null;
    try {
      createdTarget = makeTarget(physicalWidth, physicalHeight);
      createdQuad = makeQuad();
      createdShader = makeShader();
    } catch (RuntimeException | Error failure) {
      allocationFailure = failure;
      disposePreserving(failure, createdShader, createdQuad, createdTarget);
      throw failure;
    } finally {
      try {
        allocationState.restore();
      } catch (RuntimeException | Error restoreFailure) {
        if (allocationFailure != null) {
          if (restoreFailure != allocationFailure) allocationFailure.addSuppressed(restoreFailure);
        } else {
          disposePreserving(restoreFailure, createdShader, createdQuad, createdTarget);
          throw restoreFailure;
        }
      }
    }
    target = createdTarget;
    quad = createdQuad;
    shader = createdShader;
    width = physicalWidth;
    height = physicalHeight;
  }

  /** Validates the physical size and total known attachment budget without touching GL. */
  public static long validateTarget(
      int width, int height, int maxDimension, long attachmentBudgetBytes) {
    if (width <= 0 || height <= 0) throw new IllegalArgumentException("viewport must be positive");
    if (maxDimension <= 0 || width > maxDimension || height > maxDimension)
      throw new IllegalArgumentException("viewport exceeds maximum supported dimension");
    long bytes = bytesFor(width, height);
    if (bytes > attachmentBudgetBytes)
      throw new IllegalArgumentException("framebuffer attachments exceed configured byte budget");
    return bytes;
  }

  /** Validates the temporary old+candidate attachment peak for a transactional resize. */
  public static long validateResizeBudget(
      int oldWidth,
      int oldHeight,
      int newWidth,
      int newHeight,
      int maxDimension,
      long attachmentBudgetBytes) {
    long oldBytes = validateTarget(oldWidth, oldHeight, maxDimension, attachmentBudgetBytes);
    long newBytes = validateTarget(newWidth, newHeight, maxDimension, attachmentBudgetBytes);
    long peak = Math.addExact(oldBytes, newBytes);
    if (peak > attachmentBudgetBytes)
      throw new IllegalArgumentException(
          "transactional resize peak exceeds configured byte budget");
    return peak;
  }

  public int width() {
    return width;
  }

  public int height() {
    return height;
  }

  public Controls controls() {
    ensureOpen();
    return controls.view();
  }

  /**
   * Captures and composites the callback as a world-only pass. Call viewmodel and HUD rendering
   * after this method returns, on the caller's original framebuffer.
   */
  public void renderWorld(Runnable worldPass) {
    checkOwner();
    ensureOpen();
    Objects.requireNonNull(worldPass, "worldPass");
    if (rendering) throw new IllegalStateException("WorldPostProcess render is not reentrant");
    GlState state =
        new GlState(
            oneInt,
            fourInts,
            fourFloats,
            shader.getAttributeLocation(ShaderProgram.POSITION_ATTRIBUTE),
            shader.getAttributeLocation(ShaderProgram.TEXCOORD_ATTRIBUTE + "0"));
    rendering = true;
    Throwable passFailure = null;
    try {
      target.begin();
      Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
      Gdx.gl.glColorMask(true, true, true, true);
      Gdx.gl.glDepthMask(true);
      Gdx.gl.glClearColor(0, 0, 0, 0);
      Gdx.gl.glClearDepthf(1f);
      Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
      worldPass.run();
      target.end();
      state.restoreFrameAndViewport();
      Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
      Gdx.gl.glColorMask(true, true, true, true);
      composite(state);
    } catch (RuntimeException | Error failure) {
      passFailure = failure;
      try {
        target.end();
      } catch (RuntimeException | Error cleanupFailure) {
        if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
      }
      throw failure;
    } finally {
      try {
        state.restore();
      } catch (RuntimeException | Error restoreFailure) {
        if (passFailure != null) {
          if (restoreFailure != passFailure) passFailure.addSuppressed(restoreFailure);
        } else throw restoreFailure;
      } finally {
        rendering = false;
      }
    }
  }

  /** Allocates a replacement first; a failure leaves the previous target available. */
  public void resize(int physicalWidth, int physicalHeight) {
    checkOwner();
    ensureOpen();
    if (rendering) throw new IllegalStateException("Cannot resize during world capture");
    if (physicalWidth == width && physicalHeight == height) return;
    validateResizeBudget(
        width, height, physicalWidth, physicalHeight, maxDimension, attachmentBudgetBytes);
    GlState allocationState = new GlState(oneInt, fourInts, fourFloats);
    Throwable allocationFailure = null;
    try {
      FrameBuffer candidate = makeTarget(physicalWidth, physicalHeight);
      FrameBuffer previous = target;
      target = candidate;
      width = physicalWidth;
      height = physicalHeight;
      previous.dispose();
    } catch (RuntimeException | Error failure) {
      allocationFailure = failure;
      throw failure;
    } finally {
      restoreAfterAllocation(allocationState, allocationFailure);
    }
  }

  /** Returns IDs owned by this pass for live-context lifecycle probes. */
  public ResourceIds resourceIds() {
    checkOwner();
    ensureOpen();
    return new ResourceIds(
        target.getFramebufferHandle(),
        target.getColorBufferTexture().getTextureObjectHandle(),
        target.getDepthBufferHandle(),
        shader.getHandle(),
        quad instanceof TrackedQuad tracked ? tracked.vertexBuffer : 0,
        quad instanceof TrackedQuad tracked ? tracked.vertexArray : 0);
  }

  @Override
  public void dispose() {
    checkOwner();
    if (closed) return;
    if (rendering) throw new IllegalStateException("Cannot close during world capture");
    closed = true;
    controls.close();
    RuntimeException first = null;
    for (Disposable resource : new Disposable[] {shader, quad, target}) {
      try {
        resource.dispose();
      } catch (RuntimeException | Error failure) {
        if (first == null)
          first = new RuntimeException("Failed closing world postprocess", failure);
        else first.addSuppressed(failure);
      }
    }
    shader = null;
    quad = null;
    target = null;
    if (first != null) throw first;
  }

  private void composite(GlState state) {
    Resolved resolved = controls.resolve();
    boolean bypass = resolved.isIdentity();
    state.bindOriginalDrawFramebuffer();
    Gdx.gl.glViewport(state.viewport[0], state.viewport[1], state.viewport[2], state.viewport[3]);
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    Gdx.gl.glDepthMask(false);
    Gdx.gl.glDisable(GL20.GL_BLEND);
    Gdx.gl.glDisable(GL20.GL_CULL_FACE);
    Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    Gdx.gl.glColorMask(true, true, true, true);
    Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
    target.getColorBufferTexture().bind(0);
    shader.bind();
    shader.setUniformi("u_texture", 0);
    shader.setUniformi("u_bypass", bypass ? 1 : 0);
    shader.setUniformf("u_tint", resolved.red, resolved.green, resolved.blue);
    shader.setUniformf("u_desaturation", resolved.desaturation);
    shader.setUniformf("u_vignette", resolved.vignette);
    quad.render(shader, GL20.GL_TRIANGLE_STRIP);
  }

  private static FrameBuffer makeTarget(int width, int height) {
    return new RecoverableFrameBuffer(Pixmap.Format.RGBA8888, width, height, true);
  }

  private static Mesh makeQuad() {
    Mesh mesh =
        new TrackedQuad(
            true,
            4,
            0,
            new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"),
            new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0"));
    mesh.setVertices(
        new float[] {
          -1, -1, 0, 0,
          1, -1, 1, 0,
          -1, 1, 0, 1,
          1, 1, 1, 1
        });
    return mesh;
  }

  private static ShaderProgram makeShader() {
    preflightShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
    ShaderProgram candidate = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
    if (!candidate.isCompiled()) {
      String log = candidate.getLog();
      candidate.dispose();
      throw new IllegalStateException("World postprocess shader failed to compile: " + log);
    }
    return candidate;
  }

  private static void preflightShaderProgram(String vertexSource, String fragmentSource) {
    int vertex = 0;
    int fragment = 0;
    int program = 0;
    try {
      vertex = compileRaw(GL20.GL_VERTEX_SHADER, vertexSource, "vertex");
      fragment = compileRaw(GL20.GL_FRAGMENT_SHADER, fragmentSource, "fragment");
      program = Gdx.gl.glCreateProgram();
      if (program == 0)
        throw new IllegalStateException("Unable to allocate postprocess shader program");
      Gdx.gl.glAttachShader(program, vertex);
      Gdx.gl.glAttachShader(program, fragment);
      Gdx.gl.glLinkProgram(program);
      IntBuffer status = BufferUtils.newIntBuffer(1);
      Gdx.gl.glGetProgramiv(program, GL20.GL_LINK_STATUS, status);
      if (status.get(0) == 0)
        throw new IllegalStateException(
            "World postprocess shader link failed: " + Gdx.gl.glGetProgramInfoLog(program));
    } finally {
      if (program != 0) Gdx.gl.glDeleteProgram(program);
      if (fragment != 0) Gdx.gl.glDeleteShader(fragment);
      if (vertex != 0) Gdx.gl.glDeleteShader(vertex);
    }
  }

  private static int compileRaw(int type, String source, String label) {
    int shader = Gdx.gl.glCreateShader(type);
    if (shader == 0)
      throw new IllegalStateException("Unable to allocate postprocess " + label + " shader");
    try {
      Gdx.gl.glShaderSource(shader, source);
      Gdx.gl.glCompileShader(shader);
      IntBuffer status = BufferUtils.newIntBuffer(1);
      Gdx.gl.glGetShaderiv(shader, GL20.GL_COMPILE_STATUS, status);
      if (status.get(0) == 0)
        throw new IllegalStateException(
            "World postprocess "
                + label
                + " shader failed to compile: "
                + Gdx.gl.glGetShaderInfoLog(shader));
      return shader;
    } catch (RuntimeException | Error failure) {
      Gdx.gl.glDeleteShader(shader);
      throw failure;
    }
  }

  private static void restoreAfterAllocation(GlState state, Throwable originalFailure) {
    try {
      state.restore();
    } catch (RuntimeException | Error restoreFailure) {
      if (originalFailure != null) {
        if (restoreFailure != originalFailure) originalFailure.addSuppressed(restoreFailure);
      } else throw restoreFailure;
    }
  }

  private static void disposePreserving(Throwable original, Disposable... resources) {
    for (Disposable resource : resources) {
      if (resource == null) continue;
      try {
        resource.dispose();
      } catch (RuntimeException | Error cleanupFailure) {
        if (cleanupFailure != original) original.addSuppressed(cleanupFailure);
      }
    }
  }

  private void checkOwner() {
    if (Thread.currentThread() != ownerThread)
      throw new IllegalStateException("WorldPostProcess must be used on its GL owner thread");
    requireContext();
  }

  private void ensureOpen() {
    if (closed) throw new IllegalStateException("WorldPostProcess is closed");
  }

  private static void requireContext() {
    if (Gdx.gl == null || Gdx.graphics == null)
      throw new IllegalStateException("WorldPostProcess requires a live libGDX GL context");
  }

  public record ResourceIds(
      int framebuffer,
      int colorTexture,
      int depthRenderbuffer,
      int shaderProgram,
      int vertexBuffer,
      int vertexArray) {}

  /** Small host-facing control API with deterministic independent leases. */
  public static final class Controls {
    private final ControlState state;

    private Controls(ControlState state) {
      this.state = state;
    }

    public void setBaseTint(float red, float green, float blue) {
      state.setBaseTint(red, green, blue);
    }

    public void setBaseDesaturation(float amount) {
      state.setBaseDesaturation(amount);
    }

    public void setBaseVignette(float amount) {
      state.setBaseVignette(amount);
    }

    public ControlLease acquire() {
      return state.acquire();
    }

    public Resolved resolve() {
      return state.resolve();
    }
  }

  /** Each lease composes independently and closing it reveals the current persistent base. */
  public static final class ControlLease implements AutoCloseable {
    private final ControlState owner;
    private final long id;
    private volatile float intensity = 1;
    private volatile float red = 1, green = 1, blue = 1;
    private volatile float desaturation;
    private volatile float vignette;
    private volatile boolean active = true;

    private ControlLease(ControlState owner, long id) {
      this.owner = owner;
      this.id = id;
    }

    public boolean isActive() {
      return active && owner.contains(id);
    }

    public void setIntensity(float value) {
      requireActive();
      intensity = unit(value, "intensity");
    }

    /** Multiplicative per-channel tint contribution. */
    public void setTint(float red, float green, float blue) {
      requireActive();
      float validatedRed = tint(red, "red");
      float validatedGreen = tint(green, "green");
      float validatedBlue = tint(blue, "blue");
      this.red = validatedRed;
      this.green = validatedGreen;
      this.blue = validatedBlue;
    }

    public void setDesaturation(float amount) {
      requireActive();
      desaturation = unit(amount, "desaturation");
    }

    public void setVignette(float amount) {
      requireActive();
      vignette = unit(amount, "vignette");
    }

    @Override
    public void close() {
      if (!active) return;
      owner.remove(id);
    }

    private void requireActive() {
      if (!isActive()) throw new IllegalStateException("Control lease is no longer active");
    }
  }

  public record Resolved(float red, float green, float blue, float desaturation, float vignette) {
    public boolean isIdentity() {
      return red == 1f && green == 1f && blue == 1f && desaturation == 0f && vignette == 0f;
    }
  }

  static final class ControlState {
    private final Map<Long, ControlLease> leases = new LinkedHashMap<>();
    private long nextId = 1;
    private float baseRed = 1, baseGreen = 1, baseBlue = 1;
    private float baseDesaturation;
    private float baseVignette;
    private boolean closed;

    Controls view() {
      return new Controls(this);
    }

    synchronized void setBaseTint(float red, float green, float blue) {
      ensureOpen();
      float validatedRed = tint(red, "red");
      float validatedGreen = tint(green, "green");
      float validatedBlue = tint(blue, "blue");
      baseRed = validatedRed;
      baseGreen = validatedGreen;
      baseBlue = validatedBlue;
    }

    synchronized void setBaseDesaturation(float amount) {
      ensureOpen();
      baseDesaturation = unit(amount, "desaturation");
    }

    synchronized void setBaseVignette(float amount) {
      ensureOpen();
      baseVignette = unit(amount, "vignette");
    }

    synchronized ControlLease acquire() {
      ensureOpen();
      if (leases.size() >= MAX_CONTROL_LEASES)
        throw new IllegalStateException(
            "control lease capacity of " + MAX_CONTROL_LEASES + " reached");
      long id = nextId++;
      if (id <= 0) throw new IllegalStateException("control lease generation exhausted");
      ControlLease lease = new ControlLease(this, id);
      leases.put(id, lease);
      return lease;
    }

    synchronized boolean contains(long id) {
      return leases.containsKey(id);
    }

    synchronized void remove(long id) {
      ControlLease removed = leases.remove(id);
      if (removed != null) removed.active = false;
    }

    synchronized Resolved resolve() {
      ensureOpen();
      double red = baseRed, green = baseGreen, blue = baseBlue;
      double desaturation = baseDesaturation, vignette = baseVignette;
      for (ControlLease lease : leases.values()) {
        float intensity = lease.intensity;
        red *= 1d + (lease.red - 1d) * intensity;
        green *= 1d + (lease.green - 1d) * intensity;
        blue *= 1d + (lease.blue - 1d) * intensity;
        desaturation += lease.desaturation * intensity;
        vignette += lease.vignette * intensity;
      }
      return new Resolved(
          (float) Math.max(0, Math.min(4, red)),
          (float) Math.max(0, Math.min(4, green)),
          (float) Math.max(0, Math.min(4, blue)),
          (float) Math.min(1, desaturation),
          (float) Math.min(1, vignette));
    }

    synchronized void close() {
      if (closed) return;
      closed = true;
      for (ControlLease lease : leases.values()) lease.active = false;
      leases.clear();
    }

    private void ensureOpen() {
      if (closed) throw new IllegalStateException("Control state is closed");
    }
  }

  private static float unit(float value, String name) {
    if (!Float.isFinite(value) || value < 0 || value > 1)
      throw new IllegalArgumentException(name + " must be finite and in [0,1]");
    return value;
  }

  private static long bytesFor(int width, int height) {
    return Math.multiplyExact(Math.multiplyExact((long) width, height), BYTES_PER_PIXEL);
  }

  private static float tint(float value, String name) {
    if (!Float.isFinite(value) || value < 0 || value > 4)
      throw new IllegalArgumentException(name + " tint must be finite and in [0,4]");
    return value;
  }

  private static final class GlState {
    private final IntBuffer one;
    private final IntBuffer four;
    private final FloatBuffer floatFour;
    private final int framebuffer;
    private final int readFramebuffer;
    private final int drawFramebuffer;
    private final boolean splitFramebufferBindings;
    private final int vertexArrayBinding;
    private final int[] viewport = new int[4];
    private final int activeTexture;
    private final int activeTextureBinding;
    private final int texture0Binding;
    private final int renderbufferBinding;
    private final int currentProgram;
    private final int arrayBuffer;
    private final int elementArrayBuffer;
    private final AttributeState[] vertexAttributes;
    private final boolean depthTest;
    private final boolean blend;
    private final boolean cullFace;
    private final boolean scissorTest;
    private final boolean depthMask;
    private final int[] scissorBox = new int[4];
    private final int[] colorMask = new int[4];
    private final int blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha;
    private final int blendEquationRgb, blendEquationAlpha;
    private final int depthFunction;
    private final int cullFaceMode;
    private final float[] clearColor = new float[4];
    private final float[] depthRange = new float[2];
    private final float clearDepth;

    GlState(IntBuffer one, IntBuffer four, FloatBuffer floatFour, int... attributeLocations) {
      this.one = one;
      this.four = four;
      this.floatFour = floatFour;
      framebuffer = getInt(GL20.GL_FRAMEBUFFER_BINDING);
      splitFramebufferBindings = Gdx.gl30 != null;
      if (splitFramebufferBindings) {
        readFramebuffer = getInt(GL30.GL_READ_FRAMEBUFFER_BINDING);
        drawFramebuffer = getInt(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        vertexArrayBinding = getInt(GL30.GL_VERTEX_ARRAY_BINDING);
      } else {
        readFramebuffer = framebuffer;
        drawFramebuffer = framebuffer;
        vertexArrayBinding = 0;
      }
      getFour(GL20.GL_VIEWPORT, viewport);
      activeTexture = getInt(GL20.GL_ACTIVE_TEXTURE);
      activeTextureBinding = getInt(GL20.GL_TEXTURE_BINDING_2D);
      renderbufferBinding = getInt(GL20.GL_RENDERBUFFER_BINDING);
      Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
      texture0Binding = getInt(GL20.GL_TEXTURE_BINDING_2D);
      Gdx.gl.glActiveTexture(activeTexture);
      currentProgram = getInt(GL20.GL_CURRENT_PROGRAM);
      arrayBuffer = getInt(GL20.GL_ARRAY_BUFFER_BINDING);
      elementArrayBuffer = getInt(GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING);
      vertexAttributes = captureVertexAttributes(attributeLocations);
      depthTest = Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST);
      blend = Gdx.gl.glIsEnabled(GL20.GL_BLEND);
      cullFace = Gdx.gl.glIsEnabled(GL20.GL_CULL_FACE);
      scissorTest = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
      depthMask = getInt(GL20.GL_DEPTH_WRITEMASK) != 0;
      getFour(GL20.GL_SCISSOR_BOX, scissorBox);
      getFour(GL20.GL_COLOR_WRITEMASK, colorMask);
      blendSrcRgb = getInt(GL20.GL_BLEND_SRC_RGB);
      blendDstRgb = getInt(GL20.GL_BLEND_DST_RGB);
      blendSrcAlpha = getInt(GL20.GL_BLEND_SRC_ALPHA);
      blendDstAlpha = getInt(GL20.GL_BLEND_DST_ALPHA);
      blendEquationRgb = getInt(GL20.GL_BLEND_EQUATION_RGB);
      blendEquationAlpha = getInt(GL20.GL_BLEND_EQUATION_ALPHA);
      depthFunction = getInt(GL20.GL_DEPTH_FUNC);
      cullFaceMode = getInt(GL20.GL_CULL_FACE_MODE);
      floatFour.clear();
      Gdx.gl.glGetFloatv(GL20.GL_COLOR_CLEAR_VALUE, floatFour);
      for (int i = 0; i < clearColor.length; i++) clearColor[i] = floatFour.get(i);
      floatFour.clear();
      Gdx.gl.glGetFloatv(GL20.GL_DEPTH_RANGE, floatFour);
      depthRange[0] = floatFour.get(0);
      depthRange[1] = floatFour.get(1);
      floatFour.clear();
      Gdx.gl.glGetFloatv(GL20.GL_DEPTH_CLEAR_VALUE, floatFour);
      clearDepth = floatFour.get(0);
    }

    void restoreFrameAndViewport() {
      if (splitFramebufferBindings) {
        Gdx.gl.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        Gdx.gl.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        Gdx.gl30.glBindVertexArray(vertexArrayBinding);
      } else Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, framebuffer);
      Gdx.gl.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
    }

    void bindOriginalDrawFramebuffer() {
      Gdx.gl.glBindFramebuffer(
          splitFramebufferBindings ? GL30.GL_DRAW_FRAMEBUFFER : GL20.GL_FRAMEBUFFER,
          drawFramebuffer);
    }

    private AttributeState[] captureVertexAttributes(int[] locations) {
      if (splitFramebufferBindings || locations.length == 0) return new AttributeState[0];
      AttributeState[] attributes = new AttributeState[locations.length];
      for (int index = 0; index < locations.length; index++) {
        int location = locations[index];
        if (location < 0)
          throw new IllegalStateException("World postprocess shader attribute is inactive");
        int enabled = getAttribute(location, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED);
        int size = getAttribute(location, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE);
        int type = getAttribute(location, GL20.GL_VERTEX_ATTRIB_ARRAY_TYPE);
        int normalized = getAttribute(location, GL20.GL_VERTEX_ATTRIB_ARRAY_NORMALIZED);
        int stride = getAttribute(location, GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE);
        int buffer = getAttribute(location, GL20.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING);
        if (enabled != 0 || buffer != 0)
          throw new IllegalStateException(
              "GL20 world postprocess rejects prebound caller vertex attributes");
        ByteBuffer pointerBuffer =
            BufferUtils.newByteBuffer(Long.BYTES).order(ByteOrder.nativeOrder());
        try {
          Gdx.gl.glGetVertexAttribPointerv(
              location, GL20.GL_VERTEX_ATTRIB_ARRAY_POINTER, pointerBuffer);
        } catch (UnsupportedOperationException unsupported) {
          // With no enabled array or captured buffer, the descriptor is unbound; restoring offset
          // zero is equivalent and avoids changing any live caller mesh input.
        }
        long pointer = pointerBuffer.getLong(0);
        if (buffer == 0 && pointer != 0)
          throw new IllegalStateException(
              "World postprocess cannot preserve client-side vertex attribute pointers");
        attributes[index] =
            new AttributeState(location, enabled, size, type, normalized, stride, buffer, pointer);
      }
      return attributes;
    }

    private int getAttribute(int location, int parameter) {
      one.clear();
      Gdx.gl.glGetVertexAttribiv(location, parameter, one);
      return one.get(0);
    }

    void restore() {
      restoreFrameAndViewport();
      Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
      Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, texture0Binding);
      Gdx.gl.glActiveTexture(activeTexture);
      Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, activeTextureBinding);
      Gdx.gl.glBindRenderbuffer(GL20.GL_RENDERBUFFER, renderbufferBinding);
      Gdx.gl.glUseProgram(currentProgram);
      Gdx.gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, elementArrayBuffer);
      for (AttributeState attribute : vertexAttributes) {
        Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, attribute.buffer);
        Gdx.gl.glVertexAttribPointer(
            attribute.location,
            attribute.size,
            attribute.type,
            attribute.normalized != 0,
            attribute.stride,
            (int) attribute.pointer);
        if (attribute.enabled != 0) Gdx.gl.glEnableVertexAttribArray(attribute.location);
        else Gdx.gl.glDisableVertexAttribArray(attribute.location);
      }
      Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, arrayBuffer);
      if (depthTest) Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
      else Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
      if (blend) Gdx.gl.glEnable(GL20.GL_BLEND);
      else Gdx.gl.glDisable(GL20.GL_BLEND);
      if (cullFace) Gdx.gl.glEnable(GL20.GL_CULL_FACE);
      else Gdx.gl.glDisable(GL20.GL_CULL_FACE);
      if (scissorTest) Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
      else Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
      Gdx.gl.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
      Gdx.gl.glColorMask(
          colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0);
      Gdx.gl.glDepthMask(depthMask);
      Gdx.gl.glDepthFunc(depthFunction);
      Gdx.gl.glDepthRangef(depthRange[0], depthRange[1]);
      Gdx.gl.glCullFace(cullFaceMode);
      Gdx.gl.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
      Gdx.gl.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
      Gdx.gl.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
      Gdx.gl.glClearDepthf(clearDepth);
    }

    private int getInt(int parameter) {
      one.clear();
      Gdx.gl.glGetIntegerv(parameter, one);
      return one.get(0);
    }

    private record AttributeState(
        int location,
        int enabled,
        int size,
        int type,
        int normalized,
        int stride,
        int buffer,
        long pointer) {}

    private void getFour(int parameter, int[] out) {
      four.clear();
      Gdx.gl.glGetIntegerv(parameter, four);
      for (int i = 0; i < out.length; i++) out[i] = four.get(i);
    }
  }

  private static final class TrackedQuad extends Mesh {
    private int vertexBuffer;
    private int vertexArray;
    private final IntBuffer binding = BufferUtils.newIntBuffer(1);

    TrackedQuad(boolean isStatic, int maxVertices, int maxIndices, VertexAttribute... attributes) {
      super(isStatic, maxVertices, maxIndices, attributes);
    }

    @Override
    public void bind(ShaderProgram shader) {
      super.bind(shader);
      int position = shader.getAttributeLocation(ShaderProgram.POSITION_ATTRIBUTE);
      binding.clear();
      Gdx.gl.glGetVertexAttribiv(position, GL20.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING, binding);
      int actualVertexBuffer = binding.get(0);
      if (actualVertexBuffer == 0)
        throw new IllegalStateException("World postprocess quad position has no owned VBO");
      if (vertexBuffer != 0 && vertexBuffer != actualVertexBuffer)
        throw new IllegalStateException("World postprocess quad VBO identity changed unexpectedly");
      vertexBuffer = actualVertexBuffer;
      if (Gdx.gl30 != null) {
        binding.clear();
        Gdx.gl.glGetIntegerv(GL30.GL_VERTEX_ARRAY_BINDING, binding);
        vertexArray = binding.get(0);
      }
    }
  }
}
