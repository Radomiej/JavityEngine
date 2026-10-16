package org.javity.demo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.BufferUtils;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.javity.particles.ParticleBurstRenderer3D;

/** Proves GL20 rejection occurs before particle renderer allocation or GL state mutation. */
final class ParticleGl20UnsupportedProbe extends ApplicationAdapter {
  private Throwable failure;

  @Override
  public void create() {
    try {
      if (Gdx.gl30 != null) throw new AssertionError("fixture did not create a GL20-only context");
      int[] state = {
        query(GL20.GL_VIEWPORT, 0),
        query(GL20.GL_VIEWPORT, 1),
        query(GL20.GL_VIEWPORT, 2),
        query(GL20.GL_VIEWPORT, 3),
        query(GL20.GL_ACTIVE_TEXTURE, 0),
        query(GL20.GL_ARRAY_BUFFER_BINDING, 0),
        query(GL20.GL_CURRENT_PROGRAM, 0),
        query(GL20.GL_DEPTH_FUNC, 0),
        query(GL20.GL_BLEND_SRC_RGB, 0),
        query(GL20.GL_BLEND_DST_RGB, 0)
      };
      boolean depthEnabled = Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST);
      boolean blendEnabled = Gdx.gl.glIsEnabled(GL20.GL_BLEND);
      boolean depthMask = queryBoolean(GL20.GL_DEPTH_WRITEMASK);
      try {
        new ParticleBurstRenderer3D(1);
        throw new AssertionError("GL20 particle renderer construction unexpectedly succeeded");
      } catch (UnsupportedOperationException expected) {
        if (!expected.getMessage().contains("requires GL30")) throw expected;
      }
      for (int i = 0; i < state.length; i++) {
        int[] names = {
          GL20.GL_VIEWPORT,
          GL20.GL_VIEWPORT,
          GL20.GL_VIEWPORT,
          GL20.GL_VIEWPORT,
          GL20.GL_ACTIVE_TEXTURE,
          GL20.GL_ARRAY_BUFFER_BINDING,
          GL20.GL_CURRENT_PROGRAM,
          GL20.GL_DEPTH_FUNC,
          GL20.GL_BLEND_SRC_RGB,
          GL20.GL_BLEND_DST_RGB
        };
        int component = i < 4 ? i : 0;
        if (query(names[i], component) != state[i])
          throw new AssertionError("GL20 rejection changed queried GL state");
      }
      if (depthEnabled != Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST)
          || blendEnabled != Gdx.gl.glIsEnabled(GL20.GL_BLEND)
          || depthMask != queryBoolean(GL20.GL_DEPTH_WRITEMASK))
        throw new AssertionError("GL20 rejection changed enable/depth-mask state");
      String profile = Gdx.graphics.getGLVersion().getVersionString();
      Files.writeString(
          Path.of("docs/artifacts/particles3d/gl20-unsupported.json"),
          "{\n"
              + "  \"status\": \"PASS\",\n"
              + "  \"configuredApi\": \"GL20\",\n"
              + "  \"gl30InterfaceAvailable\": false,\n"
              + "  \"profile\": \""
              + profile.replace("\\", "\\\\").replace("\"", "'")
              + "\",\n"
              + "  \"rendererRejectedBeforeAllocation\": true,\n"
              + "  \"queriedGlStateUnchanged\": true,\n"
              + "  \"depthBlendAndMaskUnchanged\": true\n"
              + "}\n");
      System.out.println("particleGl20Unsupported: PASS (rejected before GL state mutation)");
    } catch (Throwable error) {
      failure = error;
      error.printStackTrace();
    } finally {
      Gdx.app.exit();
    }
  }

  @Override
  public void render() {
    if (failure != null) throw new IllegalStateException("GL20 rejection proof failed", failure);
  }

  private int query(int name, int component) {
    IntBuffer value = BufferUtils.newIntBuffer(name == GL20.GL_VIEWPORT ? 4 : 1);
    Gdx.gl.glGetIntegerv(name, value);
    return value.get(component);
  }

  private boolean queryBoolean(int name) {
    java.nio.ByteBuffer value = BufferUtils.newByteBuffer(1);
    Gdx.gl.glGetBooleanv(name, value);
    return value.get(0) != 0;
  }
}
