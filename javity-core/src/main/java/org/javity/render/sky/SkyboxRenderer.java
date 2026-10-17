package org.javity.render.sky;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import java.nio.*;
import java.util.Objects;

/**
 * Owned background shader/mesh, borrowed settings/cubemap. Call only on the GL owner thread,
 * outside all batches. Uses the caller viewport and framebuffer. On GL20 the position attribute
 * slot must be disabled with its default descriptor and no buffer or client pointer. Backends
 * lacking pointer queries require the caller to guarantee no client pointer at that slot.
 */
public final class SkyboxRenderer implements Disposable {
  private static final String VERTEX =
      "attribute vec2 a_position; varying vec3 v_direction; uniform mat4 u_inverse;\n"
          + "void main(){vec4 farPoint=u_inverse*vec4(a_position,1.0,1.0);"
          + "vec4 nearPoint=u_inverse*vec4(a_position,-1.0,1.0);"
          + "v_direction=farPoint.xyz/farPoint.w-nearPoint.xyz/nearPoint.w;"
          + "gl_Position=vec4(a_position,1.0,1.0);}";
  private static final String FRAGMENT =
      "#ifdef GL_ES\n"
          + "precision highp float;\n"
          + "#endif\n"
          + "varying vec3 v_direction; uniform samplerCube u_cube; uniform int u_mode; uniform vec3"
          + " u_zenith,u_horizon,u_nadir;\n"
          + "void main(){vec3 d=normalize(v_direction);vec3 c;"
          + " if(u_mode==1)c=textureCube(u_cube,d).rgb;else"
          + " c=mix(u_horizon,d.y>=0.0?u_zenith:u_nadir,abs(d.y));gl_FragColor=vec4(c,1.0);}";
  private final Thread owner = Thread.currentThread();
  private final ShaderProgram shader;
  private Mesh mesh;
  private final Matrix4 inverse = new Matrix4(), rotationView = new Matrix4();
  private final Color color = new Color();
  private final int[] attributes = new int[1];
  private final State state = new State();
  private int inverseLocation,
      modeLocation,
      cubeLocation,
      zenithLocation,
      horizonLocation,
      nadirLocation;
  private boolean disposed;

  public SkyboxRenderer() {
    if (Gdx.gl20 == null) throw new IllegalStateException("Sky requires an active GL context");
    ShaderProgram created = null;
    Mesh createdMesh = null;
    int previousProgram = integer(GL20.GL_CURRENT_PROGRAM);
    int previousArray = integer(GL20.GL_ARRAY_BUFFER_BINDING);
    int previousElements = integer(GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING);
    int previousVao = Gdx.gl30 == null ? 0 : integer(GL30.GL_VERTEX_ARRAY_BINDING);
    try {
      String[] sources = sources();
      validate(sources[0], sources[1]);
      created = new ShaderProgram(sources[0], sources[1]);
      if (!created.isCompiled())
        throw new IllegalArgumentException("Sky shader: " + created.getLog());
      createdMesh = createMesh();
      shader = created;
      mesh = createdMesh;
      if (Gdx.gl30 == null) reservePosition();
      refreshLocations();
    } catch (RuntimeException | Error failure) {
      if (createdMesh != null)
        try {
          createdMesh.dispose();
        } catch (RuntimeException | Error e) {
          failure.addSuppressed(e);
        }
      if (created != null)
        try {
          created.dispose();
        } catch (RuntimeException | Error e) {
          failure.addSuppressed(e);
        }
      throw failure;
    } finally {
      if (Gdx.gl30 != null) Gdx.gl30.glBindVertexArray(previousVao);
      Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, previousArray);
      Gdx.gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, previousElements);
      Gdx.gl.glUseProgram(previousProgram);
    }
  }

  /**
   * Rebuilds owned mesh descriptors and refreshes raw locations after managed resource recovery.
   */
  public void contextRestored() {
    checkOpen();
    refreshLocations();
    int array = integer(GL20.GL_ARRAY_BUFFER_BINDING);
    int elements = integer(GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING);
    int vao = Gdx.gl30 == null ? 0 : integer(GL30.GL_VERTEX_ARRAY_BINDING);
    try {
      // Pinned VertexBufferObjectWithVAO.invalidate retains cachedLocations although its new VAO
      // has no descriptors. Replace only our tiny owned mesh, even if every location is unchanged.
      Mesh replacement = createMesh();
      Mesh previous = mesh;
      mesh = replacement;
      previous.dispose();
    } finally {
      if (Gdx.gl30 != null) Gdx.gl30.glBindVertexArray(vao);
      Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, array);
      Gdx.gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, elements);
    }
  }

  private static Mesh createMesh() {
    Mesh created =
        new Mesh(true, 3, 0, new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"));
    try {
      created.setVertices(new float[] {-1, -1, 3, -1, -1, 3});
      return created;
    } catch (RuntimeException | Error failure) {
      try {
        created.dispose();
      } catch (RuntimeException | Error cleanup) {
        failure.addSuppressed(cleanup);
      }
      throw failure;
    }
  }

  /** Never consults libGDX's stale alias caches. */
  private void refreshLocations() {
    int program = shader.getHandle();
    attributes[0] = Gdx.gl.glGetAttribLocation(program, "a_position");
    if (Gdx.gl30 == null && !state.pristine(attributes[0])) {
      reservePosition();
      attributes[0] = Gdx.gl.glGetAttribLocation(program, "a_position");
    }
    inverseLocation = uniform(program, "u_inverse");
    modeLocation = uniform(program, "u_mode");
    cubeLocation = uniform(program, "u_cube");
    zenithLocation = uniform(program, "u_zenith");
    horizonLocation = uniform(program, "u_horizon");
    nadirLocation = uniform(program, "u_nadir");
    if (attributes[0] < 0)
      throw new IllegalStateException("Sky position attribute missing after relink");
  }

  private void reservePosition() {
    for (int location = integer(GL20.GL_MAX_VERTEX_ATTRIBS) - 1; location >= 0; location--) {
      if (!state.pristine(location)) continue;
      Gdx.gl.glBindAttribLocation(shader.getHandle(), location, "a_position");
      Gdx.gl.glLinkProgram(shader.getHandle());
      IntBuffer status = BufferUtils.newIntBuffer(1);
      Gdx.gl.glGetProgramiv(shader.getHandle(), GL20.GL_LINK_STATUS, status);
      if (status.get(0) == 0)
        throw new IllegalStateException(
            "Sky attribute reservation link failed: " + shader.getLog());
      return;
    }
    throw new IllegalStateException("GL20 sky requires a pristine position attribute slot");
  }

  public void render(Camera camera, SkySettings settings) {
    checkOpen();
    Objects.requireNonNull(camera, "camera");
    Objects.requireNonNull(settings, "settings");
    // Remove translation before inversion: distant skies depend only on viewing direction.
    rotationView.set(camera.view);
    rotationView.val[Matrix4.M03] =
        rotationView.val[Matrix4.M13] = rotationView.val[Matrix4.M23] = 0;
    inverse.set(camera.projection).mul(rotationView).inv();
    state.capture(attributes[0]);
    try {
      Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
      Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
      Gdx.gl.glDepthMask(false);
      Gdx.gl.glDepthRangef(0, 1);
      Gdx.gl.glDisable(GL20.GL_BLEND);
      Gdx.gl.glDisable(GL20.GL_CULL_FACE);
      Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
      Gdx.gl.glDisable(GL20.GL_STENCIL_TEST);
      Gdx.gl.glColorMask(true, true, true, true);
      shader.bind();
      shader.setUniformMatrix(inverseLocation, inverse);
      shader.setUniformi(modeLocation, settings.mode() == SkySettings.Mode.CUBEMAP ? 1 : 0);
      shader.setUniformi(cubeLocation, 0);
      if (settings.mode() == SkySettings.Mode.CUBEMAP) settings.cubemap().bind(0);
      else {
        settings.zenith(color);
        shader.setUniformf(zenithLocation, color.r, color.g, color.b);
        settings.horizon(color);
        shader.setUniformf(horizonLocation, color.r, color.g, color.b);
        settings.nadir(color);
        shader.setUniformf(nadirLocation, color.r, color.g, color.b);
      }
      mesh.bind(shader, attributes, null);
      mesh.render(shader, GL20.GL_TRIANGLES, 0, 3, false);
    } finally {
      // Includes shader/texture/mesh failures. Restore actual caller state, not RenderContext
      // defaults.
      state.restore();
    }
  }

  private void checkOpen() {
    if (Thread.currentThread() != owner)
      throw new IllegalStateException("Sky requires its GL owner thread");
    if (disposed) throw new IllegalStateException("Sky renderer is disposed");
  }

  @Override
  public void dispose() {
    if (Thread.currentThread() != owner)
      throw new IllegalStateException("Sky requires its GL owner thread");
    if (disposed) return;
    disposed = true;
    // Resource deletion must not leave a previously unrelated program unbound.
    int program = integer(GL20.GL_CURRENT_PROGRAM), array = integer(GL20.GL_ARRAY_BUFFER_BINDING);
    int elements = integer(GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING),
        vao = Gdx.gl30 == null ? 0 : integer(GL30.GL_VERTEX_ARRAY_BINDING);
    try {
      try {
        mesh.dispose();
      } finally {
        shader.dispose();
      }
    } finally {
      if (Gdx.gl30 != null) Gdx.gl30.glBindVertexArray(vao);
      Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, array);
      Gdx.gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, elements);
      Gdx.gl.glUseProgram(program == shader.getHandle() ? 0 : program);
    }
  }

  private static int uniform(int program, String name) {
    int location = Gdx.gl.glGetUniformLocation(program, name);
    if (location < 0) throw new IllegalStateException("Sky uniform missing after relink: " + name);
    return location;
  }

  private static String[] sources() {
    boolean es =
        Gdx.graphics.getGLVersion().getType()
            == com.badlogic.gdx.graphics.glutils.GLVersion.Type.GLES;
    String vertex = VERTEX, fragment = FRAGMENT;
    if (es) {
      vertex =
          "#version 300 es\n" + vertex.replace("attribute ", "in ").replace("varying ", "out ");
      fragment =
          "#version 300 es\n"
              + fragment
                  .replace("varying ", "in ")
                  .replace("textureCube(", "texture(")
                  .replace("void main()", "out vec4 skyColor;\nvoid main()")
                  .replace("gl_FragColor", "skyColor");
    } else {
      vertex = "#version 120\n" + vertex;
      fragment = "#version 120\n" + fragment;
    }
    return new String[] {vertex, fragment};
  }

  /** Preflight owns every raw handle, including failed compile/link paths in pinned libGDX. */
  private static void validate(String vertexSource, String fragmentSource) {
    int vertex = 0, fragment = 0, program = 0;
    try {
      vertex =
          compile(GL20.GL_VERTEX_SHADER, prefix(ShaderProgram.prependVertexCode, vertexSource));
      fragment =
          compile(
              GL20.GL_FRAGMENT_SHADER, prefix(ShaderProgram.prependFragmentCode, fragmentSource));
      program = Gdx.gl.glCreateProgram();
      if (program == 0) throw new IllegalStateException("Cannot allocate sky program");
      Gdx.gl.glAttachShader(program, vertex);
      Gdx.gl.glAttachShader(program, fragment);
      Gdx.gl.glLinkProgram(program);
      IntBuffer status = BufferUtils.newIntBuffer(1);
      Gdx.gl.glGetProgramiv(program, GL20.GL_LINK_STATUS, status);
      if (status.get(0) == 0)
        throw new IllegalArgumentException(
            "Sky link failed: " + Gdx.gl.glGetProgramInfoLog(program));
    } finally {
      try {
        if (program != 0) Gdx.gl.glDeleteProgram(program);
      } finally {
        try {
          if (fragment != 0) Gdx.gl.glDeleteShader(fragment);
        } finally {
          if (vertex != 0) Gdx.gl.glDeleteShader(vertex);
        }
      }
    }
  }

  private static int compile(int type, String source) {
    int shader = Gdx.gl.glCreateShader(type);
    if (shader == 0) throw new IllegalStateException("Cannot allocate sky shader");
    try {
      Gdx.gl.glShaderSource(shader, source);
      Gdx.gl.glCompileShader(shader);
      IntBuffer status = BufferUtils.newIntBuffer(1);
      Gdx.gl.glGetShaderiv(shader, GL20.GL_COMPILE_STATUS, status);
      if (status.get(0) == 0)
        throw new IllegalArgumentException(
            "Sky compile failed: " + Gdx.gl.glGetShaderInfoLog(shader));
      return shader;
    } catch (RuntimeException | Error failure) {
      Gdx.gl.glDeleteShader(shader);
      throw failure;
    }
  }

  private static String prefix(String prefix, String source) {
    return prefix == null ? source : prefix + source;
  }

  private static int integer(int name) {
    IntBuffer b = BufferUtils.newIntBuffer(1);
    Gdx.gl.glGetIntegerv(name, b);
    return b.get(0);
  }

  private static final class State {
    int position, program, array, elements, vao, active, cube;
    int depthFunc;
    boolean depthMask;
    boolean depth, blend, cull, scissor, stencil;
    final int[] colorMask = new int[4];
    final float[] depthRange = new float[2];

    private final IntBuffer query = BufferUtils.newIntBuffer(4);
    private final FloatBuffer range = BufferUtils.newFloatBuffer(2);
    private final ByteBuffer pointer = BufferUtils.newByteBuffer(8).order(ByteOrder.nativeOrder());
    private static final int[] ATTRIBUTE_NAMES = {
      GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED, GL20.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING,
      GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE, GL20.GL_VERTEX_ATTRIB_ARRAY_TYPE,
      GL20.GL_VERTEX_ATTRIB_ARRAY_NORMALIZED, GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE
    };
    private static final int[] ATTRIBUTE_DEFAULTS = {0, 0, 4, GL20.GL_FLOAT, 0, 0};

    private int integer(int name) {
      query.clear();
      Gdx.gl.glGetIntegerv(name, query);
      return query.get(0);
    }

    void capture(int position) {
      this.position = position;
      // Reject unsupported GL20 descriptors before changing any state.
      if (Gdx.gl30 == null && !pristine(position))
        throw new IllegalStateException("GL20 sky requires a pristine position attribute slot");
      program = integer(GL20.GL_CURRENT_PROGRAM);
      array = integer(GL20.GL_ARRAY_BUFFER_BINDING);
      elements = integer(GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING);
      vao = Gdx.gl30 == null ? 0 : integer(GL30.GL_VERTEX_ARRAY_BINDING);
      active = integer(GL20.GL_ACTIVE_TEXTURE);
      Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
      try {
        cube = integer(GL20.GL_TEXTURE_BINDING_CUBE_MAP);
      } finally {
        Gdx.gl.glActiveTexture(active);
      }
      depthFunc = integer(GL20.GL_DEPTH_FUNC);
      depthMask = integer(GL20.GL_DEPTH_WRITEMASK) != 0;
      depth = Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST);
      blend = Gdx.gl.glIsEnabled(GL20.GL_BLEND);
      cull = Gdx.gl.glIsEnabled(GL20.GL_CULL_FACE);
      scissor = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
      stencil = Gdx.gl.glIsEnabled(GL20.GL_STENCIL_TEST);
      query.clear();
      Gdx.gl.glGetIntegerv(GL20.GL_COLOR_WRITEMASK, query);
      for (int i = 0; i < 4; i++) colorMask[i] = query.get(i);
      range.clear();
      Gdx.gl.glGetFloatv(GL20.GL_DEPTH_RANGE, range);
      depthRange[0] = range.get(0);
      depthRange[1] = range.get(1);
    }

    boolean pristine(int position) {
      if (position < 0) return false;
      for (int i = 0; i < ATTRIBUTE_NAMES.length; i++) {
        query.clear();
        Gdx.gl.glGetVertexAttribiv(position, ATTRIBUTE_NAMES[i], query);
        if (query.get(0) != ATTRIBUTE_DEFAULTS[i]) return false;
      }
      pointer.clear();
      pointer.putLong(0, 0);
      try {
        Gdx.gl.glGetVertexAttribPointerv(position, GL20.GL_VERTEX_ATTRIB_ARRAY_POINTER, pointer);
        return pointer.getLong(0) == 0;
      } catch (UnsupportedOperationException unsupported) {
        return true; /* Caller guarantees no client pointer. */
      }
    }

    void restore() {
      if (Gdx.gl30 != null) Gdx.gl30.glBindVertexArray(vao);
      else {
        Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, 0);
        Gdx.gl.glVertexAttribPointer(position, 4, GL20.GL_FLOAT, false, 0, 0);
        Gdx.gl.glDisableVertexAttribArray(position);
      }
      Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, array);
      Gdx.gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, elements);
      Gdx.gl.glUseProgram(program);
      Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
      Gdx.gl.glBindTexture(GL20.GL_TEXTURE_CUBE_MAP, cube);
      Gdx.gl.glActiveTexture(active);
      enabled(GL20.GL_DEPTH_TEST, depth);
      enabled(GL20.GL_BLEND, blend);
      enabled(GL20.GL_CULL_FACE, cull);
      enabled(GL20.GL_SCISSOR_TEST, scissor);
      enabled(GL20.GL_STENCIL_TEST, stencil);
      Gdx.gl.glDepthMask(depthMask);
      Gdx.gl.glDepthFunc(depthFunc);
      Gdx.gl.glDepthRangef(depthRange[0], depthRange[1]);
      Gdx.gl.glColorMask(
          colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0);
    }

    private static void enabled(int capability, boolean value) {
      if (value) Gdx.gl.glEnable(capability);
      else Gdx.gl.glDisable(capability);
    }
  }
}
