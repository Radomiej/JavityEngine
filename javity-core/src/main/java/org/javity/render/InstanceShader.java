package org.javity.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.glutils.GLVersion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.BufferUtils;
import java.nio.IntBuffer;

/**
 * Independent program owner. Uses libGDX 1.14.2's bundled Apache-2.0 DefaultShader lighting and
 * material formulas with four instance columns instead of the world/normal uniforms. Positive
 * uniform scales permit normalizing mat3(world) * normal. No global GLSL prefixes are changed.
 */
final class InstanceShader extends DefaultShader {
  private InstanceShader(Renderable representative, Config config, ShaderProgram program) {
    super(representative, config, program);
  }

  static InstanceShader create(Renderable representative) {
    Config config = new Config();
    String prefix = createPrefix(representative, config);
    ShaderProgram program = prepareProgram(prefix, false);
    InstanceShader shader = null;
    try {
      if (!program.isCompiled()) throw new IllegalStateException(program.getLog());
      shader = new InstanceShader(representative, config, program);
      shader.init();
      for (int i = 0; i < 4; i++)
        if (program.getAttributeLocation("a_i" + i) < 0)
          throw new IllegalStateException("Instance matrix attribute missing");
      return shader;
    } catch (RuntimeException | Error failure) {
      program.dispose();
      throw failure;
    }
  }

  static ShaderProgram prepareProgram(String prefix, boolean skinned) {
    boolean es = Gdx.graphics.getGLVersion().getType() == GLVersion.Type.GLES;
    String header = es ? "#version 300 es\n" : "#version 330 core\n";
    String vertex =
        getDefaultVertexShader()
            .replace(
                "uniform mat4 u_worldTrans;",
                "in vec4 a_i0;\n"
                    + "in vec4 a_i1;\n"
                    + "in vec4 a_i2;\n"
                    + "in vec4 a_i3;\n"
                    + "#define u_worldTrans mat4(a_i0,a_i1,a_i2,a_i3)\n")
            .replace("uniform mat3 u_normalMatrix;", "#define u_normalMatrix mat3(u_worldTrans)\n")
            .replaceAll("\\battribute\\b", "in")
            .replaceAll("\\bvarying\\b", "out");
    String fragment =
        getDefaultFragmentShader()
            .replaceAll("\\bvarying\\b", "in")
            .replaceAll("\\btexture2D\\b", "texture")
            .replaceAll("\\btextureCube\\b", "texture")
            .replaceAll("\\bgl_FragColor\\b", "instanceFragColor");
    String fragmentOutput =
        es
            ? "precision mediump float;\nout vec4 instanceFragColor;\n"
            : "out vec4 instanceFragColor;\n";
    if (skinned) {
      vertex =
          vertex.replace(
              "uniform mat4 u_bones[numBones];",
              "#ifdef GL_ES\nprecision highp float;\nuniform highp sampler2D u_palette;\n"
                  + "#else\nuniform sampler2D u_palette;\n#endif\n"
                  + "in float a_paletteRow;\n"
                  + "mat4 paletteBone(int bone) { int x = bone * 4; int y = int(a_paletteRow); "
                  + "return mat4(texelFetch(u_palette,ivec2(x,y),0),"
                  + "texelFetch(u_palette,ivec2(x+1,y),0),texelFetch(u_palette,ivec2(x+2,y),0),"
                  + "texelFetch(u_palette,ivec2(x+3,y),0)); }\n");
      vertex =
          vertex.replaceAll(
              "u_bones\\[int\\((a_boneWeight[0-7]\\.x)\\)\\]", "paletteBone(int($1))");
    }
    vertex =
        header + (es ? "precision highp float;\nprecision highp int;\n" : "") + prefix + vertex;
    fragment = header + fragmentOutput + prefix + fragment;
    preflight(
        (ShaderProgram.prependVertexCode == null ? "" : ShaderProgram.prependVertexCode) + vertex,
        (ShaderProgram.prependFragmentCode == null ? "" : ShaderProgram.prependFragmentCode)
            + fragment);
    ShaderProgram program = new ShaderProgram(vertex, fragment);
    return program;
  }

  /** Raw compile/link first: ShaderProgram otherwise loses failed stage handles in this GDX. */
  private static void preflight(String vertex, String fragment) {
    GL20 gl = Gdx.gl20;
    int v = 0, f = 0, p = 0;
    try {
      v = compile(GL20.GL_VERTEX_SHADER, vertex);
      f = compile(GL20.GL_FRAGMENT_SHADER, fragment);
      p = gl.glCreateProgram();
      if (p == 0) throw new IllegalStateException("Instance program allocation failed");
      gl.glAttachShader(p, v);
      gl.glAttachShader(p, f);
      gl.glLinkProgram(p);
      IntBuffer status = BufferUtils.newIntBuffer(1);
      gl.glGetProgramiv(p, GL20.GL_LINK_STATUS, status);
      if (status.get(0) == 0) throw new IllegalStateException(gl.glGetProgramInfoLog(p));
    } finally {
      if (p != 0) gl.glDeleteProgram(p);
      if (f != 0) gl.glDeleteShader(f);
      if (v != 0) gl.glDeleteShader(v);
    }
  }

  private static int compile(int type, String source) {
    GL20 gl = Gdx.gl20;
    int shader = gl.glCreateShader(type);
    if (shader == 0) throw new IllegalStateException("Instance shader allocation failed");
    try {
      gl.glShaderSource(shader, source);
      gl.glCompileShader(shader);
      IntBuffer status = BufferUtils.newIntBuffer(1);
      gl.glGetShaderiv(shader, GL20.GL_COMPILE_STATUS, status);
      if (status.get(0) == 0) throw new IllegalStateException(gl.glGetShaderInfoLog(shader));
      return shader;
    } catch (RuntimeException | Error failure) {
      gl.glDeleteShader(shader);
      throw failure;
    }
  }
}
