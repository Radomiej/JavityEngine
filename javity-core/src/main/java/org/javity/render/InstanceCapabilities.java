package org.javity.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.GLVersion;
import com.badlogic.gdx.utils.BufferUtils;
import java.nio.IntBuffer;

/**
 * Actual current-context capability snapshot, never cached across context recreation.
 * maxTextureUnits is the fragment-stage limit; this static path does not sample vertex textures. A
 * later skinning palette path must separately query GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS.
 */
public record InstanceCapabilities(
    boolean supported,
    String reason,
    int maxVertexAttributes,
    int maxTextureSize,
    int maxTextureUnits) {
  public static InstanceCapabilities detect() {
    if (Gdx.graphics == null || Gdx.gl30 == null || Gdx.gl == null)
      return new InstanceCapabilities(false, "GL30_UNAVAILABLE", 0, 0, 0);
    GLVersion version = Gdx.graphics.getGLVersion();
    boolean desktop = version.getType() == GLVersion.Type.OpenGL;
    boolean es = version.getType() == GLVersion.Type.GLES;
    boolean baseline =
        desktop
            ? version.isVersionEqualToOrHigher(3, 3)
            : es && version.isVersionEqualToOrHigher(3, 0);
    if (!baseline) return new InstanceCapabilities(false, "GL_VERSION_UNSUPPORTED", 0, 0, 0);
    int attributes = limit(GL20.GL_MAX_VERTEX_ATTRIBS),
        texture = limit(GL20.GL_MAX_TEXTURE_SIZE),
        units = limit(GL20.GL_MAX_TEXTURE_IMAGE_UNITS);
    boolean valid = attributes >= 7 && texture > 0 && units >= 1;
    return new InstanceCapabilities(
        valid, valid ? null : "GL_LIMITS_INSUFFICIENT", attributes, texture, units);
  }

  private static int limit(int name) {
    IntBuffer result = BufferUtils.newIntBuffer(1);
    Gdx.gl.glGetIntegerv(name, result);
    return result.get(0);
  }
}
