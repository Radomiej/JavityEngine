package org.javity.render;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer;
import com.badlogic.gdx.utils.Array;

/** One owned Pixmap-format color target and optional depth16, with managed-context recovery. */
public final class RecoverableFrameBuffer extends RecoverableColorFrameBuffer {
  public RecoverableFrameBuffer(Pixmap.Format format, int width, int height, boolean hasDepth) {
    super(builder(format, width, height, hasDepth));
  }

  private static FrameBufferBuilder builder(
      Pixmap.Format format, int width, int height, boolean depth) {
    FrameBufferBuilder builder = new FrameBufferBuilder(width, height);
    builder.addBasicColorTextureAttachment(format);
    if (depth) builder.addBasicDepthRenderBuffer();
    return builder;
  }

  /** Package-private inspection hooks retained for the core lifecycle regression suite. */
  static Array<GLFrameBuffer> managedSnapshot(Application app) {
    return managedFramebuffersSnapshot(app);
  }

  static int managedIdentityCount(Application app, GLFrameBuffer<?> target) {
    return managedFramebufferIdentityCount(app, target);
  }
}
