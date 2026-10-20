package org.javity.gltf;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer;
import com.badlogic.gdx.utils.Array;
import org.javity.render.RecoverableColorFrameBuffer;

/**
 * Actual half-float target with single-entry managed context recovery and owned allocation cleanup.
 */
final class RecoverableHdrFrameBuffer extends RecoverableColorFrameBuffer {
  RecoverableHdrFrameBuffer(int width, int height) {
    super(builder(width, height));
  }

  private static FrameBufferBuilder builder(int width, int height) {
    if (Gdx.gl30 == null) throw new UnsupportedOperationException("HDR requires GL30");
    FrameBufferBuilder builder = new FrameBufferBuilder(width, height);
    builder.addFloatAttachment(GL30.GL_RGBA16F, GL20.GL_RGBA, GL30.GL_HALF_FLOAT, true);
    builder.addBasicDepthRenderBuffer();
    return builder;
  }

  static Array<GLFrameBuffer> managedSnapshot(Application app) {
    return managedFramebuffersSnapshot(app);
  }

  static int managedIdentityCount(Application app, GLFrameBuffer<?> target) {
    return managedFramebufferIdentityCount(app, target);
  }
}
