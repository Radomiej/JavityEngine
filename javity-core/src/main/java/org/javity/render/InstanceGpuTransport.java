package org.javity.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import java.nio.*;

/** Shared owned geometry, sparse instance uploads and bounded GL state boundary. */
final class InstanceGpuTransport {
  static final class Chunk implements Disposable {
    final int capacity, stride;

    Chunk(int capacity) {
      this(capacity, 16);
    }

    Chunk(int capacity, int stride) {
      this.capacity = capacity;
      this.stride = stride;
    }

    int buffer, count;
    float[] cpu = new float[0];
    boolean uploaded;

    long update(float[] data, int count) {
      if (buffer == 0) {
        buffer = Gdx.gl.glGenBuffer();
        if (buffer == 0) throw new IllegalStateException("Instance buffer allocation failed");
        Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, buffer);
        Gdx.gl.glBufferData(
            GL20.GL_ARRAY_BUFFER, capacity * stride * 4, null, GL20.GL_DYNAMIC_DRAW);
      }
      int start = 0, end = data.length;
      if (uploaded) {
        while (start < Math.min(cpu.length, data.length)
            && Float.floatToIntBits(cpu[start]) == Float.floatToIntBits(data[start])) start++;
        if (cpu.length == data.length)
          while (end > start
              && Float.floatToIntBits(cpu[end - 1]) == Float.floatToIntBits(data[end - 1])) end--;
      }
      long bytes = (end - start) * 4L;
      if (bytes > 0) {
        FloatBuffer upload = BufferUtils.newFloatBuffer(end - start);
        upload.put(data, start, end - start).flip();
        Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, buffer);
        Gdx.gl.glBufferSubData(GL20.GL_ARRAY_BUFFER, start * 4, (int) bytes, upload);
      }
      cpu = data;
      this.count = count;
      uploaded = true;
      return bytes;
    }

    @Override
    public void dispose() {
      if (buffer != 0) {
        Gdx.gl.glDeleteBuffer(buffer);
        buffer = 0;
      }
    }
  }

  static final class Geometry implements Disposable {
    final Mesh source;
    final InstancedMesh mesh;

    Geometry(Mesh source) {
      this.source = source;
      float[] vertices =
          source.getVertices(new float[source.getNumVertices() * source.getVertexSize() / 4]);
      short[] indices = new short[source.getNumIndices()];
      source.getIndices(indices);
      InstancedMesh allocated = new InstancedMesh(source);
      try {
        allocated.setVertices(vertices);
        allocated.setIndices(indices);
      } catch (RuntimeException | Error failure) {
        allocated.dispose();
        throw failure;
      }
      mesh = allocated;
    }

    @Override
    public void dispose() {
      mesh.dispose();
    }
  }

  /** Owns a private VAO/geometry; chunks share geometry and switch only their instance buffer. */
  static final class InstancedMesh extends Mesh {
    Chunk chunk;

    InstancedMesh(Mesh source) {
      super(
          VertexDataType.VertexBufferObjectWithVAO,
          true,
          source.getNumVertices(),
          source.getNumIndices(),
          source.getVertexAttributes());
    }

    @Override
    public void render(
        ShaderProgram shader, int primitive, int offset, int count, boolean autoBind) {
      if (offset < 0
          || count < 0
          || (long) offset + count > (getNumIndices() > 0 ? getNumIndices() : getNumVertices()))
        throw new IllegalArgumentException("Invalid instanced mesh range");
      int[] locations = new int[chunk.stride == 17 ? 5 : 4];
      Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, chunk.buffer);
      try {
        for (int i = 0; i < locations.length; i++) {
          int location =
              locations[i] = shader.getAttributeLocation(i == 4 ? "a_paletteRow" : "a_i" + i);
          Gdx.gl.glEnableVertexAttribArray(location);
          Gdx.gl.glVertexAttribPointer(
              location, i == 4 ? 1 : 4, GL20.GL_FLOAT, false, chunk.stride * 4, i * 16);
          Gdx.gl30.glVertexAttribDivisor(location, 1);
        }
        if (getNumIndices() > 0)
          Gdx.gl30.glDrawElementsInstanced(
              primitive, count, GL20.GL_UNSIGNED_SHORT, offset * 2, chunk.count);
        else Gdx.gl30.glDrawArraysInstanced(primitive, offset, count, chunk.count);
      } finally {
        for (int location : locations) {
          Gdx.gl30.glVertexAttribDivisor(location, 0);
          Gdx.gl.glDisableVertexAttribArray(location);
        }
      }
    }
  }

  /** Restore caller state, including VAO/element binding and the texture units used by binder. */
  static final class State {
    final int vao = integer(0x85B5),
        array = integer(GL20.GL_ARRAY_BUFFER_BINDING),
        element = integer(GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING);
    final int program = integer(GL20.GL_CURRENT_PROGRAM), active = integer(GL20.GL_ACTIVE_TEXTURE);
    final int depthFunc = integer(GL20.GL_DEPTH_FUNC), cullMode = integer(GL20.GL_CULL_FACE_MODE);
    final boolean blend = Gdx.gl.glIsEnabled(GL20.GL_BLEND),
        depth = Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST),
        cull = Gdx.gl.glIsEnabled(GL20.GL_CULL_FACE);
    final int srcRGB = integer(GL20.GL_BLEND_SRC_RGB),
        dstRGB = integer(GL20.GL_BLEND_DST_RGB),
        srcAlpha = integer(GL20.GL_BLEND_SRC_ALPHA),
        dstAlpha = integer(GL20.GL_BLEND_DST_ALPHA);
    final boolean mask = integer(GL20.GL_DEPTH_WRITEMASK) != 0;
    final FloatBuffer range = BufferUtils.newFloatBuffer(2);
    final int[] textures;

    State() {
      this(1);
    }

    State(int textureUnits) {
      Gdx.gl.glGetFloatv(GL20.GL_DEPTH_RANGE, range);
      textures = new int[textureUnits];
      for (int i = 0; i < textures.length; i++) {
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0 + i);
        textures[i] = integer(GL20.GL_TEXTURE_BINDING_2D);
      }
      Gdx.gl.glActiveTexture(active);
    }

    void restore() {
      Gdx.gl30.glBindVertexArray(vao);
      Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, array);
      Gdx.gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, element);
      Gdx.gl.glUseProgram(program);
      enabled(GL20.GL_BLEND, blend);
      enabled(GL20.GL_DEPTH_TEST, depth);
      enabled(GL20.GL_CULL_FACE, cull);
      Gdx.gl.glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
      Gdx.gl.glDepthFunc(depthFunc);
      Gdx.gl.glCullFace(cullMode);
      Gdx.gl.glDepthMask(mask);
      Gdx.gl.glDepthRangef(range.get(0), range.get(1));
      for (int i = 0; i < textures.length; i++) {
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0 + i);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, textures[i]);
      }
      Gdx.gl.glActiveTexture(active);
    }

    static void enabled(int state, boolean yes) {
      if (yes) Gdx.gl.glEnable(state);
      else Gdx.gl.glDisable(state);
    }

    static int integer(int name) {
      IntBuffer value = BufferUtils.newIntBuffer(1);
      Gdx.gl.glGetIntegerv(name, value);
      return value.get(0);
    }
  }
}
