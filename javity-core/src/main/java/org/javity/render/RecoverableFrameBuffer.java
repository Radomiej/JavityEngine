package org.javity.render;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer;
import com.badlogic.gdx.utils.Array;

/**
 * FrameBuffer whose context-loss rebuild stays in libGDX's managed registry exactly once. Only call
 * the rebuild hook through GLFrameBuffer.invalidateAllFrameBuffers after context loss.
 */
public final class RecoverableFrameBuffer extends FrameBuffer {
  // No initializer: GLFrameBuffer's constructor dispatches to build() before this subclass
  // constructor has completed. The first successful build sets it true for subsequent rebuilds.
  private boolean buildCompleted;

  public RecoverableFrameBuffer(Pixmap.Format format, int width, int height, boolean hasDepth) {
    super(format, width, height, hasDepth);
  }

  @Override
  protected void build() {
    boolean contextRebuild = buildCompleted;
    if (contextRebuild) clearLostContextBookkeeping();

    try {
      super.build();
      buildCompleted = true;
    } catch (RuntimeException | Error failure) {
      // GLFrameBuffer.build() deletes its newly allocated handles on an incomplete attachment,
      // but leaves those deleted integer names in these fields. Neutralize them so later dispose
      // or retry cannot delete a fresh resource that reused a name (ABA). Do not GL-delete here:
      // the base failure branch may already have deleted some or all of these resources.
      clearLostContextBookkeeping();
      throw failure;
    }

    if (contextRebuild) removeOnlyTheRegistrationJustAdded();
  }

  private void clearLostContextBookkeeping() {
    // Drop old texture wrappers without GL deletes: handles belong to a destroyed context and may
    // already name unrelated resources in the replacement context.
    textureAttachments.clear();
    colorBufferHandles.clear();
    framebufferHandle = 0;
    depthbufferHandle = 0;
    stencilbufferHandle = 0;
    depthStencilPackedBufferHandle = 0;
    hasDepthStencilPackedBuffer = false;
  }

  private void removeOnlyTheRegistrationJustAdded() {
    Array<GLFrameBuffer> managed = buffers.get(Gdx.app);
    if (managed == null || managed.size < 2 || managed.peek() != this) {
      throw new IllegalStateException(
          "libGDX FrameBuffer rebuild registration was not appended last");
    }
    managed.pop();
  }

  /** Package-private inspection hook for diagnostics and tests. */
  static Array<GLFrameBuffer> managedSnapshot(Application app) {
    Array<GLFrameBuffer> managed = buffers.get(app);
    return managed == null ? new Array<GLFrameBuffer>() : new Array<GLFrameBuffer>(managed);
  }

  static int managedIdentityCount(Application app, GLFrameBuffer<?> target) {
    Array<GLFrameBuffer> managed = buffers.get(app);
    if (managed == null) return 0;
    int matches = 0;
    for (GLFrameBuffer<?> entry : managed) if (entry == target) matches++;
    return matches;
  }
}
