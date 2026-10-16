package org.javity.particles;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.BufferUtils;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Objects;

/**
 * Render-thread GPU adapter for additive billboard bursts. It owns its mesh/shader and borrows the
 * sprite texture, simulation pool and explicit presented camera.
 */
public final class ParticleBurstRenderer3D implements AutoCloseable {
  private final Thread renderThread;
  private final int capacity;
  private final Mesh mesh;
  private final ShaderProgram shader;
  private final float[] packed;
  private final float[] vertices;
  private final short[] indices;
  private boolean disposed;

  private static final String VERTEX =
      """
      attribute vec3 a_position;
      attribute vec4 a_color;
      attribute vec2 a_texCoord0;
      uniform mat4 u_projView;
      varying vec4 v_color;
      varying vec2 v_uv;
      void main() { v_color = a_color; v_uv = a_texCoord0; gl_Position = u_projView * vec4(a_position, 1.0); }
      """;
  private static final String FRAGMENT =
      """
      #ifdef GL_ES
      precision mediump float;
      #endif
      varying vec4 v_color;
      varying vec2 v_uv;
      uniform sampler2D u_texture;
      void main() { gl_FragColor = texture2D(u_texture, v_uv) * v_color; }
      """;

  public ParticleBurstRenderer3D(int capacity) {
    requireGlThread();
    requireGl30Support(Gdx.gl30 != null);
    if (capacity < 1 || capacity > 16383)
      throw new IllegalArgumentException("Capacity must be in [1,16383]");
    this.capacity = capacity;
    renderThread = Thread.currentThread();
    packed = new float[capacity * ParticleBurstPool3D.RENDER_FLOATS_PER_PARTICLE];
    vertices = new float[capacity * 4 * 9];
    indices = new short[capacity * 6];
    for (int i = 0; i < capacity; i++) {
      int v = i * 4, k = i * 6;
      indices[k] = (short) v;
      indices[k + 1] = (short) (v + 1);
      indices[k + 2] = (short) (v + 2);
      indices[k + 3] = (short) (v + 2);
      indices[k + 4] = (short) (v + 3);
      indices[k + 5] = (short) v;
    }
    ShaderProgram compiled = null;
    Mesh allocated = null;
    try {
      validateRawProgram(VERTEX, FRAGMENT);
      compiled = new ShaderProgram(VERTEX, FRAGMENT);
      if (!compiled.isCompiled())
        throw new IllegalStateException("Particle shader failed: " + compiled.getLog());
      allocated =
          new Mesh(
              true,
              capacity * 4,
              capacity * 6,
              new VertexAttribute(
                  VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
              new VertexAttribute(
                  VertexAttributes.Usage.Generic,
                  4,
                  GL20.GL_FLOAT,
                  false,
                  ShaderProgram.COLOR_ATTRIBUTE),
              new VertexAttribute(
                  VertexAttributes.Usage.TextureCoordinates,
                  2,
                  ShaderProgram.TEXCOORD_ATTRIBUTE + "0"));
      allocated.setIndices(indices);
      shader = compiled;
      mesh = allocated;
    } catch (RuntimeException | Error failure) {
      if (allocated != null)
        try {
          allocated.dispose();
        } catch (RuntimeException | Error cleanup) {
          failure.addSuppressed(cleanup);
        }
      if (compiled != null)
        try {
          compiled.dispose();
        } catch (RuntimeException | Error cleanup) {
          failure.addSuppressed(cleanup);
        }
      throw failure;
    }
  }

  /** Draw into the currently bound framebuffer, after opaque geometry and before viewmodel/HUD. */
  public void draw(
      ParticleBurstPool3D pool,
      Camera camera,
      Texture borrowedTexture,
      int viewportX,
      int viewportY,
      int viewportWidth,
      int viewportHeight) {
    live();
    Objects.requireNonNull(pool, "pool");
    Objects.requireNonNull(camera, "camera");
    Objects.requireNonNull(borrowedTexture, "borrowedTexture");
    if (viewportWidth < 1 || viewportHeight < 1)
      throw new IllegalArgumentException("Viewport must be positive");
    int count = pool.copyRenderData(packed);
    if (count == 0) return;
    if (count > capacity)
      throw new IllegalArgumentException("Renderer capacity is smaller than live particle count");
    float rx = camera.direction.y * camera.up.z - camera.direction.z * camera.up.y;
    float ry = camera.direction.z * camera.up.x - camera.direction.x * camera.up.z;
    float rz = camera.direction.x * camera.up.y - camera.direction.y * camera.up.x;
    float rlen = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
    if (!(rlen > 1e-8f)) throw new IllegalArgumentException("Camera up and direction are parallel");
    rx /= rlen;
    ry /= rlen;
    rz /= rlen;
    float ux = ry * camera.direction.z - rz * camera.direction.y;
    float uy = rz * camera.direction.x - rx * camera.direction.z;
    float uz = rx * camera.direction.y - ry * camera.direction.x;
    int vi = 0;
    for (int i = 0; i < count; i++) {
      int p = i * 8;
      float x = packed[p], y = packed[p + 1], z = packed[p + 2], half = packed[p + 3] * .5f;
      float r = packed[p + 4], g = packed[p + 5], b = packed[p + 6], a = packed[p + 7];
      vi =
          vertex(
              vi,
              x - rx * half - ux * half,
              y - ry * half - uy * half,
              z - rz * half - uz * half,
              r,
              g,
              b,
              a,
              0,
              1);
      vi =
          vertex(
              vi,
              x + rx * half - ux * half,
              y + ry * half - uy * half,
              z + rz * half - uz * half,
              r,
              g,
              b,
              a,
              1,
              1);
      vi =
          vertex(
              vi,
              x + rx * half + ux * half,
              y + ry * half + uy * half,
              z + rz * half + uz * half,
              r,
              g,
              b,
              a,
              1,
              0);
      vi =
          vertex(
              vi,
              x - rx * half + ux * half,
              y - ry * half + uy * half,
              z - rz * half + uz * half,
              r,
              g,
              b,
              a,
              0,
              0);
    }
    GLState state = new GLState();
    try {
      mesh.setVertices(vertices, 0, vi);
      Gdx.gl.glViewport(viewportX, viewportY, viewportWidth, viewportHeight);
      Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
      Gdx.gl.glDepthMask(false);
      Gdx.gl.glEnable(GL20.GL_BLEND);
      Gdx.gl.glBlendEquation(GL20.GL_FUNC_ADD);
      Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
      shader.bind();
      shader.setUniformMatrix("u_projView", camera.combined);
      shader.setUniformi("u_texture", 0);
      borrowedTexture.bind(0);
      mesh.render(shader, GL20.GL_TRIANGLES, 0, count * 6);
    } finally {
      state.restore();
    }
  }

  public int getMeshVertexBufferId() {
    live();
    GLState state = new GLState();
    try {
      mesh.bind(shader);
      int location = shader.getAttributeLocation(ShaderProgram.POSITION_ATTRIBUTE);
      IntBuffer value = BufferUtils.newIntBuffer(1);
      Gdx.gl.glGetVertexAttribiv(location, GL20.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING, value);
      return value.get(0);
    } finally {
      mesh.unbind(shader);
      state.restore();
    }
  }

  public int getMeshIndexBufferId() {
    live();
    GLState state = new GLState();
    try {
      mesh.bind(shader);
      return glInt(GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING);
    } finally {
      mesh.unbind(shader);
      state.restore();
    }
  }

  /** Returns the owned mesh VAO. This renderer requires GL30. */
  public int getMeshVertexArrayId() {
    live();
    GLState state = new GLState();
    try {
      mesh.bind(shader);
      return glInt(GL30.GL_VERTEX_ARRAY_BINDING);
    } finally {
      mesh.unbind(shader);
      state.restore();
    }
  }

  private static int glInt(int name) {
    IntBuffer value = BufferUtils.newIntBuffer(1);
    Gdx.gl.glGetIntegerv(name, value);
    return value.get(0);
  }

  public int getShaderProgramId() {
    live();
    return shader.getHandle();
  }

  public int getCapacity() {
    return capacity;
  }

  @Override
  public void close() {
    dispose();
  }

  public void dispose() {
    if (disposed) return;
    checkThread();
    disposed = true;
    Throwable failure = null;
    try {
      mesh.dispose();
    } catch (RuntimeException | Error e) {
      failure = e;
    }
    try {
      shader.dispose();
    } catch (RuntimeException | Error e) {
      if (failure == null) failure = e;
      else failure.addSuppressed(e);
    }
    if (failure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
    if (failure instanceof Error errorFailure) throw errorFailure;
  }

  private int vertex(
      int index, float x, float y, float z, float r, float g, float b, float a, float u, float v) {
    vertices[index] = x;
    vertices[index + 1] = y;
    vertices[index + 2] = z;
    vertices[index + 3] = r;
    vertices[index + 4] = g;
    vertices[index + 5] = b;
    vertices[index + 6] = a;
    vertices[index + 7] = u;
    vertices[index + 8] = v;
    return index + 9;
  }

  private void live() {
    checkThread();
    if (disposed) throw new IllegalStateException("Particle renderer disposed");
  }

  private void checkThread() {
    if (Thread.currentThread() != renderThread)
      throw new IllegalStateException("Particle GPU access must stay on its render thread");
  }

  private static void requireGlThread() {
    if (Gdx.gl == null) throw new IllegalStateException("An active OpenGL context is required");
  }

  static void requireGl30Support(boolean available) {
    if (!available)
      throw new UnsupportedOperationException(
          "ParticleBurstRenderer3D requires GL30 for exact caller VAO and element-buffer "
              + "preservation");
  }

  private static void validateRawProgram(String vertexSource, String fragmentSource) {
    GL20 gl = Gdx.gl20;
    int vs = 0, fs = 0, program = 0;
    Throwable primary = null;
    try {
      vs = compile(gl, GL20.GL_VERTEX_SHADER, vertexSource);
      fs = compile(gl, GL20.GL_FRAGMENT_SHADER, fragmentSource);
      program = gl.glCreateProgram();
      gl.glAttachShader(program, vs);
      gl.glAttachShader(program, fs);
      gl.glLinkProgram(program);
      IntBuffer status = BufferUtils.newIntBuffer(1);
      gl.glGetProgramiv(program, GL20.GL_LINK_STATUS, status);
      if (status.get(0) == 0)
        throw new IllegalStateException(
            "Particle shader link failed: " + gl.glGetProgramInfoLog(program));
    } catch (RuntimeException | Error failure) {
      primary = failure;
      throw failure;
    } finally {
      Throwable cleanup = null;
      if (program != 0)
        try {
          gl.glDeleteProgram(program);
        } catch (RuntimeException | Error failure) {
          cleanup = failure;
        }
      if (vs != 0)
        try {
          gl.glDeleteShader(vs);
        } catch (RuntimeException | Error failure) {
          if (cleanup == null) cleanup = failure;
          else cleanup.addSuppressed(failure);
        }
      if (fs != 0)
        try {
          gl.glDeleteShader(fs);
        } catch (RuntimeException | Error failure) {
          if (cleanup == null) cleanup = failure;
          else cleanup.addSuppressed(failure);
        }
      if (cleanup != null) {
        if (primary != null) primary.addSuppressed(cleanup);
        else if (cleanup instanceof RuntimeException runtimeFailure) throw runtimeFailure;
        else throw (Error) cleanup;
      }
    }
  }

  private static int compile(GL20 gl, int type, String source) {
    int id = gl.glCreateShader(type);
    try {
      gl.glShaderSource(id, source);
      gl.glCompileShader(id);
      IntBuffer status = BufferUtils.newIntBuffer(1);
      gl.glGetShaderiv(id, GL20.GL_COMPILE_STATUS, status);
      if (status.get(0) == 0)
        throw new IllegalStateException(
            "Particle shader compile failed: " + gl.glGetShaderInfoLog(id));
      return id;
    } catch (RuntimeException | Error failure) {
      try {
        gl.glDeleteShader(id);
      } catch (RuntimeException | Error cleanup) {
        failure.addSuppressed(cleanup);
      }
      throw failure;
    }
  }

  private static final class GLState {
    final int[] viewport = new int[4];
    final int program, activeTexture, texture0, arrayBuffer, depthFunc, vertexArray;
    final int blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha, blendEqRgb, blendEqAlpha;
    final boolean depth, blend, depthMask;

    GLState() {
      IntBuffer q = BufferUtils.newIntBuffer(4);
      program = get(GL20.GL_CURRENT_PROGRAM);
      vertexArray = get(GL30.GL_VERTEX_ARRAY_BINDING);
      activeTexture = get(GL20.GL_ACTIVE_TEXTURE);
      Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
      texture0 = get(GL20.GL_TEXTURE_BINDING_2D);
      Gdx.gl.glActiveTexture(activeTexture);
      arrayBuffer = get(GL20.GL_ARRAY_BUFFER_BINDING);
      depthFunc = get(GL20.GL_DEPTH_FUNC);
      blendSrcRgb = get(GL20.GL_BLEND_SRC_RGB);
      blendDstRgb = get(GL20.GL_BLEND_DST_RGB);
      blendSrcAlpha = get(GL20.GL_BLEND_SRC_ALPHA);
      blendDstAlpha = get(GL20.GL_BLEND_DST_ALPHA);
      blendEqRgb = get(GL20.GL_BLEND_EQUATION_RGB);
      blendEqAlpha = get(GL20.GL_BLEND_EQUATION_ALPHA);
      Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, q);
      for (int i = 0; i < 4; i++) viewport[i] = q.get(i);
      depth = Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST);
      blend = Gdx.gl.glIsEnabled(GL20.GL_BLEND);
      ByteBuffer b = BufferUtils.newByteBuffer(1);
      Gdx.gl.glGetBooleanv(GL20.GL_DEPTH_WRITEMASK, b);
      depthMask = b.get(0) != 0;
    }

    int get(int name) {
      IntBuffer q = BufferUtils.newIntBuffer(1);
      Gdx.gl.glGetIntegerv(name, q);
      return q.get(0);
    }

    void restore() {
      Gdx.gl.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
      if (depth) Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
      else Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
      if (blend) Gdx.gl.glEnable(GL20.GL_BLEND);
      else Gdx.gl.glDisable(GL20.GL_BLEND);
      Gdx.gl.glDepthFunc(depthFunc);
      Gdx.gl.glDepthMask(depthMask);
      Gdx.gl.glBlendEquationSeparate(blendEqRgb, blendEqAlpha);
      Gdx.gl.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
      Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
      Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, texture0);
      Gdx.gl.glActiveTexture(activeTexture);
      Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, arrayBuffer);
      Gdx.gl20.glUseProgram(program);
      Gdx.gl30.glBindVertexArray(vertexArray);
    }
  }
}
