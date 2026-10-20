package org.javity.render;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer;
import com.badlogic.gdx.graphics.glutils.GLOnlyTextureData;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import java.nio.IntBuffer;

/**
 * FrameBuffer whose context-loss rebuild stays in libGDX's managed registry exactly once. Only call
 * the rebuild hook through GLFrameBuffer.invalidateAllFrameBuffers after context loss. Pinned to
 * libGDX 1.14.2 protected framebuffer/texture fields and GPU-only data semantics; audit this
 * adapter when changing the libGDX version.
 */
public abstract class RecoverableColorFrameBuffer extends FrameBuffer {
  // No initializer: GLFrameBuffer's constructor dispatches to build() before this subclass
  // constructor has completed. The first successful build sets it true for subsequent rebuilds.
  private boolean buildCompleted;
  private boolean disposed;

  /** One unmanaged GPU-only color texture, with optional depth16; builder must stay immutable. */
  protected RecoverableColorFrameBuffer(FrameBufferBuilder builder) {
    super(builder);
  }

  @Override
  protected final void build() {
    if (disposed) throw new IllegalStateException("Cannot rebuild a disposed framebuffer");
    boolean contextRebuild = buildCompleted;
    if (contextRebuild) clearLostContextBookkeeping();
    int newFramebuffer = 0, newDepth = 0, newTexture = 0;
    Texture newAttachment = null;
    try {
      if (bufferBuilder.textureAttachmentSpecs.size != 1
          || bufferBuilder.textureAttachmentSpecs.first().isDepth
          || bufferBuilder.textureAttachmentSpecs.first().isStencil
          || bufferBuilder.colorRenderBufferSpecs.size != 0
          || bufferBuilder.samples != 0
          || bufferBuilder.hasStencilRenderBuffer
          || bufferBuilder.hasPackedStencilDepthRenderBuffer
          || (bufferBuilder.hasDepthRenderBuffer
              && bufferBuilder.depthRenderBufferSpec.internalFormat != GL20.GL_DEPTH_COMPONENT16))
        throw new IllegalArgumentException(
            "Recoverable color framebuffer requires one color texture and optional depth16");
      FrameBufferTextureAttachmentSpec color = bufferBuilder.textureAttachmentSpecs.first();
      if (!defaultFramebufferHandleInitialized) {
        if (Gdx.app.getType() == Application.ApplicationType.iOS) {
          IntBuffer value = BufferUtils.newIntBuffer(1);
          Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING, value);
          defaultFramebufferHandle = value.get(0);
        } else defaultFramebufferHandle = 0;
        defaultFramebufferHandleInitialized = true;
      }
      newFramebuffer = Gdx.gl.glGenFramebuffer();
      if (newFramebuffer == 0) throw new IllegalStateException("Framebuffer allocation failed");
      Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, newFramebuffer);
      if (bufferBuilder.hasDepthRenderBuffer) {
        newDepth = Gdx.gl.glGenRenderbuffer();
        if (newDepth == 0) throw new IllegalStateException("Framebuffer depth allocation failed");
        Gdx.gl.glBindRenderbuffer(GL20.GL_RENDERBUFFER, newDepth);
        Gdx.gl.glRenderbufferStorage(
            GL20.GL_RENDERBUFFER,
            GL20.GL_DEPTH_COMPONENT16,
            bufferBuilder.width,
            bufferBuilder.height);
        Gdx.gl.glFramebufferRenderbuffer(
            GL20.GL_FRAMEBUFFER, GL20.GL_DEPTH_ATTACHMENT, GL20.GL_RENDERBUFFER, newDepth);
      }
      newTexture = Gdx.gl.glGenTexture();
      if (newTexture == 0) throw new IllegalStateException("Framebuffer color allocation failed");
      // The handle exists before Texture's upload constructor can throw. The failure path owns it
      // even if no Java wrapper has completed construction.
      newAttachment =
          new OwnedColorTexture(
              newTexture,
              bufferBuilder.width,
              bufferBuilder.height,
              color.internalFormat,
              color.format,
              color.type);
      newAttachment.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
      newAttachment.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
      Gdx.gl.glFramebufferTexture2D(
          GL20.GL_FRAMEBUFFER, GL20.GL_COLOR_ATTACHMENT0, GL20.GL_TEXTURE_2D, newTexture, 0);
      int status = Gdx.gl.glCheckFramebufferStatus(GL20.GL_FRAMEBUFFER);
      if (status != GL20.GL_FRAMEBUFFER_COMPLETE)
        throw new IllegalStateException(
            status == GL20.GL_FRAMEBUFFER_UNSUPPORTED
                ? "Framebuffer unsupported attachment combination"
                : "Framebuffer incomplete: " + status);
      Gdx.gl.glBindRenderbuffer(GL20.GL_RENDERBUFFER, 0);
      Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);
      Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, defaultFramebufferHandle);
      framebufferHandle = newFramebuffer;
      depthbufferHandle = newDepth;
      textureAttachments.add(newAttachment);
      if (!contextRebuild) {
        Array<GLFrameBuffer> managed = buffers.get(Gdx.app);
        if (managed == null) {
          managed = new Array<>();
          buffers.put(Gdx.app, managed);
        }
        managed.add(this);
      }
      buildCompleted = true;
    } catch (RuntimeException | Error failure) {
      // This allocator never delegates deletion to GLFrameBuffer.build. Retire Java bookkeeping
      // before cleanup; each new allocation token is deleted at most once, including after throws.
      clearLostContextBookkeeping();
      if (!contextRebuild) {
        Array<GLFrameBuffer> managed = buffers.get(Gdx.app);
        if (managed != null) managed.removeValue(this, true);
      }
      if (newAttachment != null) {
        try {
          newAttachment.dispose();
        } catch (RuntimeException | Error problem) {
          suppress(failure, problem);
        }
      } else if (newTexture != 0) {
        try {
          Gdx.gl.glDeleteTexture(newTexture);
        } catch (RuntimeException | Error problem) {
          suppress(failure, problem);
        }
      }
      if (newDepth != 0)
        try {
          Gdx.gl.glDeleteRenderbuffer(newDepth);
        } catch (RuntimeException | Error problem) {
          suppress(failure, problem);
        }
      if (newFramebuffer != 0)
        try {
          Gdx.gl.glDeleteFramebuffer(newFramebuffer);
        } catch (RuntimeException | Error problem) {
          suppress(failure, problem);
        }
      throw failure;
    }
  }

  private static void suppress(Throwable primary, Throwable cleanup) {
    if (primary == cleanup) return;
    for (Throwable already : primary.getSuppressed()) if (already == cleanup) return;
    primary.addSuppressed(cleanup);
  }

  @Override
  public final void dispose() {
    if (disposed) return;
    disposed = true;
    Texture retiredTexture = textureAttachments.size == 0 ? null : textureAttachments.first();
    int retiredDepth = depthbufferHandle, retiredFramebuffer = framebufferHandle;
    // Retire all Java ownership before any GL decorator can throw or reenter. Deleted numeric names
    // may immediately be reused for unrelated resources; a second dispose must never touch them.
    clearLostContextBookkeeping();
    Array<GLFrameBuffer> managed = buffers.get(Gdx.app);
    if (managed != null) managed.removeValue(this, true);
    Throwable first = null;
    if (retiredTexture != null) {
      try {
        retiredTexture.dispose();
      } catch (RuntimeException | Error failure) {
        first = failure;
      }
    }
    if (retiredDepth != 0) {
      try {
        Gdx.gl.glDeleteRenderbuffer(retiredDepth);
      } catch (RuntimeException | Error failure) {
        if (first == null) first = failure;
        else suppress(first, failure);
      }
    }
    if (retiredFramebuffer != 0) {
      try {
        Gdx.gl.glDeleteFramebuffer(retiredFramebuffer);
      } catch (RuntimeException | Error failure) {
        if (first == null) first = failure;
        else suppress(first, failure);
      }
    }
    if (first instanceof RuntimeException runtime) throw runtime;
    if (first instanceof Error error) throw error;
  }

  private static final class OwnedColorTexture extends Texture {
    OwnedColorTexture(int handle, int width, int height, int internalFormat, int format, int type) {
      super(
          GL20.GL_TEXTURE_2D,
          handle,
          new GLOnlyTextureData(width, height, 0, internalFormat, format, type));
    }

    @Override
    public void dispose() {
      // GLTexture.delete in pinned libGDX retires the field only after glDeleteTexture returns.
      // A throwing observer may already have deleted/reused the name; retire it before calling GL.
      // GLOnlyTextureData is unmanaged, so this object has no Texture registry entry to remove.
      int retired = glHandle;
      glHandle = 0;
      if (retired != 0) Gdx.gl.glDeleteTexture(retired);
    }
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

  /** Package-private inspection hook for diagnostics and tests. */
  protected static Array<GLFrameBuffer> managedFramebuffersSnapshot(Application app) {
    Array<GLFrameBuffer> managed = buffers.get(app);
    return managed == null ? new Array<GLFrameBuffer>() : new Array<GLFrameBuffer>(managed);
  }

  protected static int managedFramebufferIdentityCount(Application app, GLFrameBuffer<?> target) {
    Array<GLFrameBuffer> managed = buffers.get(app);
    if (managed == null) return 0;
    int matches = 0;
    for (GLFrameBuffer<?> entry : managed) if (entry == target) matches++;
    return matches;
  }
}
