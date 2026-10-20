package org.javity.gltf;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.utils.BufferUtils;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** GL30 state touched by FBO allocation, ModelBatch/depth shaders, and explicit pass setup. */
final class ShadowGlState {
  private static final int SAMPLER_BINDING = 0x8919;
  private final IntBuffer ints = BufferUtils.newIntBuffer(4);
  private final FloatBuffer floats = BufferUtils.newFloatBuffer(4);
  private int drawFramebuffer = integer(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
  private int readFramebuffer = integer(GL30.GL_READ_FRAMEBUFFER_BINDING);
  private int renderbuffer = integer(GL20.GL_RENDERBUFFER_BINDING);
  private final int vao = integer(GL30.GL_VERTEX_ARRAY_BINDING);
  private final int arrayBuffer = integer(GL20.GL_ARRAY_BUFFER_BINDING);
  private final int elementBuffer = integer(GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING);
  private int program = integer(GL20.GL_CURRENT_PROGRAM);
  private final int activeTexture = integer(GL20.GL_ACTIVE_TEXTURE);
  private final int[] viewport = vector(GL20.GL_VIEWPORT), scissor = vector(GL20.GL_SCISSOR_BOX);
  private final int[] colorMask = vector(GL20.GL_COLOR_WRITEMASK);
  private final boolean depthTest = enabled(GL20.GL_DEPTH_TEST), blend = enabled(GL20.GL_BLEND);
  private final boolean cull = enabled(GL20.GL_CULL_FACE),
      scissorTest = enabled(GL20.GL_SCISSOR_TEST);
  private final boolean stencil = enabled(GL20.GL_STENCIL_TEST);
  private final boolean rasterDiscard = enabled(GL30.GL_RASTERIZER_DISCARD);
  private final boolean polygonOffset = enabled(GL20.GL_POLYGON_OFFSET_FILL);
  private final boolean depthMask = integer(GL20.GL_DEPTH_WRITEMASK) != 0;
  private final int depthFunc = integer(GL20.GL_DEPTH_FUNC),
      cullMode = integer(GL20.GL_CULL_FACE_MODE);
  private final int frontFace = integer(GL20.GL_FRONT_FACE);
  private final int srcRgb = integer(GL20.GL_BLEND_SRC_RGB),
      dstRgb = integer(GL20.GL_BLEND_DST_RGB);
  private final int srcAlpha = integer(GL20.GL_BLEND_SRC_ALPHA),
      dstAlpha = integer(GL20.GL_BLEND_DST_ALPHA);
  private final int equationRgb = integer(GL20.GL_BLEND_EQUATION_RGB),
      equationAlpha = integer(GL20.GL_BLEND_EQUATION_ALPHA);
  private final float[] clearColor = floatVector(GL20.GL_COLOR_CLEAR_VALUE);
  private final float[] depthRange = floatVector(GL20.GL_DEPTH_RANGE);
  private final float clearDepth = floatVector(GL20.GL_DEPTH_CLEAR_VALUE)[0];
  private final int[] textures2D, texturesCube, samplers;

  ShadowGlState() {
    int units = integer(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
    textures2D = new int[units];
    texturesCube = new int[units];
    samplers = new int[units];
    try {
      for (int i = 0; i < units; i++) {
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0 + i);
        textures2D[i] = integer(GL20.GL_TEXTURE_BINDING_2D);
        texturesCube[i] = integer(GL20.GL_TEXTURE_BINDING_CUBE_MAP);
        samplers[i] = integer(SAMPLER_BINDING);
      }
    } finally {
      Gdx.gl.glActiveTexture(activeTexture);
    }
  }

  /** Eliminate caller tests/bindings that would prevent or alter the packed-depth write. */
  void prepareDepth() {
    Gdx.gl.glDisable(GL20.GL_STENCIL_TEST);
    Gdx.gl.glDisable(GL30.GL_RASTERIZER_DISCARD);
    Gdx.gl.glDisable(GL20.GL_POLYGON_OFFSET_FILL);
    Gdx.gl.glFrontFace(GL20.GL_CCW);
    for (int i = 0; i < samplers.length; i++) Gdx.gl30.glBindSampler(i, 0);
  }

  /** Retired pass-owned names cannot be rebound after resize; keep their replacements bound. */
  void replaceOwnedTarget(
      int oldFramebuffer,
      int oldTexture,
      int oldDepth,
      int newFramebuffer,
      int newTexture,
      int newDepth) {
    if (drawFramebuffer == oldFramebuffer) drawFramebuffer = newFramebuffer;
    if (readFramebuffer == oldFramebuffer) readFramebuffer = newFramebuffer;
    if (renderbuffer == oldDepth) renderbuffer = newDepth;
    for (int i = 0; i < textures2D.length; i++)
      if (textures2D[i] == oldTexture) textures2D[i] = newTexture;
  }

  void replaceOwnedProgram(int previous, int replacement) {
    if (program == previous) program = replacement;
  }

  void restore() {
    Gdx.gl.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
    Gdx.gl.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
    Gdx.gl.glBindRenderbuffer(GL20.GL_RENDERBUFFER, renderbuffer);
    Gdx.gl.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
    Gdx.gl.glScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
    Gdx.gl30.glBindVertexArray(vao);
    Gdx.gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, elementBuffer);
    Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, arrayBuffer);
    Gdx.gl.glUseProgram(program);
    for (int i = 0; i < textures2D.length; i++) {
      Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0 + i);
      Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, textures2D[i]);
      Gdx.gl.glBindTexture(GL20.GL_TEXTURE_CUBE_MAP, texturesCube[i]);
      Gdx.gl30.glBindSampler(i, samplers[i]);
    }
    Gdx.gl.glActiveTexture(activeTexture);
    toggle(GL20.GL_DEPTH_TEST, depthTest);
    toggle(GL20.GL_BLEND, blend);
    toggle(GL20.GL_CULL_FACE, cull);
    toggle(GL20.GL_SCISSOR_TEST, scissorTest);
    toggle(GL20.GL_STENCIL_TEST, stencil);
    toggle(GL30.GL_RASTERIZER_DISCARD, rasterDiscard);
    toggle(GL20.GL_POLYGON_OFFSET_FILL, polygonOffset);
    Gdx.gl.glDepthMask(depthMask);
    Gdx.gl.glDepthFunc(depthFunc);
    Gdx.gl.glDepthRangef(depthRange[0], depthRange[1]);
    Gdx.gl.glColorMask(colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0);
    Gdx.gl.glCullFace(cullMode);
    Gdx.gl.glFrontFace(frontFace);
    Gdx.gl.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    Gdx.gl.glBlendEquationSeparate(equationRgb, equationAlpha);
    Gdx.gl.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
    Gdx.gl.glClearDepthf(clearDepth);
  }

  private static boolean enabled(int flag) {
    return Gdx.gl.glIsEnabled(flag);
  }

  private static void toggle(int flag, boolean value) {
    if (value) Gdx.gl.glEnable(flag);
    else Gdx.gl.glDisable(flag);
  }

  private int integer(int name) {
    ints.clear();
    Gdx.gl.glGetIntegerv(name, ints);
    return ints.get(0);
  }

  private int[] vector(int name) {
    ints.clear();
    Gdx.gl.glGetIntegerv(name, ints);
    return new int[] {ints.get(0), ints.get(1), ints.get(2), ints.get(3)};
  }

  private float[] floatVector(int name) {
    floats.clear();
    Gdx.gl.glGetFloatv(name, floats);
    return new float[] {floats.get(0), floats.get(1), floats.get(2), floats.get(3)};
  }
}
