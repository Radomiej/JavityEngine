package org.javity.android;

import android.opengl.EGL14;
import android.opengl.EGLContext;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.StretchViewport;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.javity.render.DashPattern2D;
import org.javity.render.RecoverableFrameBuffer;
import org.javity.render.ShapeMaterial2D;
import org.javity.render.ShapeRenderer2D;
import org.javity.render.SmoothShapeRenderer;
import org.javity.ui.flex.Rectangle;
import org.javity.ui.flex.UiTheme;

/**
 * GL-thread diagnostic fixture for managed-resource recovery after EGL context loss. It remains
 * retained when a test pauses/resumes the GLSurfaceView directly, which exercises context recovery
 * without dispatching the ApplicationListener lifecycle. An ordinary Android Activity listener
 * pause closes it while the old context is current; Activity resume creates a fresh probe. This is
 * an app diagnostic, not an engine API.
 */
public final class AndroidManagedContextProbe implements Disposable {
  private static final int WIDTH = 128;
  private static final int HEIGHT = 80;
  private static final AtomicLong NEXT_OWNER_ID = new AtomicLong();

  public record Bounds(int minX, int minY, int maxX, int maxY, int pixels) {}

  /**
   * Bounds are enclosing FBO-local bottom-left ranges; pixels is the observed count or expected
   * minimum.
   */
  public record Snapshot(
      long eglContextNativeHandle,
      long helperIdentity,
      long shapeRendererIdentity,
      long themeIdentity,
      long controlTextureIdentity,
      long managedUploadCount,
      Bounds expectedMaterialBounds,
      Bounds observedMaterialBounds,
      Bounds expectedDashedBounds,
      Bounds observedDashedBounds,
      Bounds expectedRoundedBounds,
      Bounds observedRoundedBounds,
      int nonEmptyGlyphPixels,
      AndroidInstancingContextProbe.Snapshot instancing) {}

  private final Pixmap retainedControlPixmap;
  private final CountingManagedPixmapData controlData;
  private final Texture controlTexture;
  private final FrameBuffer framebuffer;
  private final SmoothShapeRenderer shapeRenderer;
  private final UiTheme theme;
  private final Stage stage;
  private final ShapeRenderer2D materialShape;
  private final ShapeRenderer2D dashedShape;
  private final Matrix4 projection = new Matrix4().setToOrtho2D(0, 0, WIDTH, HEIGHT);
  private final Matrix4 transform = new Matrix4();
  private final ByteBuffer pixels = BufferUtils.newByteBuffer(WIDTH * HEIGHT * 4);
  private final IntBuffer savedViewport = BufferUtils.newIntBuffer(4);
  private final IntBuffer savedFramebuffer = BufferUtils.newIntBuffer(1);
  private final IntBuffer savedPackAlignment = BufferUtils.newIntBuffer(1);
  private final IntBuffer savedActiveTexture = BufferUtils.newIntBuffer(1);
  private final FloatBufferCompat savedClearColor = new FloatBufferCompat();
  private final Thread ownerThread = Thread.currentThread();
  private final long helperIdentity = NEXT_OWNER_ID.incrementAndGet();
  private final long shapeRendererIdentity;
  private final long themeIdentity;
  private final long controlTextureIdentity;
  private boolean closed;
  private final AndroidInstancingContextProbe instancing;

  public AndroidManagedContextProbe() {
    Pixmap pixmap = null;
    Texture texture = null;
    FrameBuffer fbo = null;
    SmoothShapeRenderer renderer = null;
    UiTheme uiTheme = null;
    Stage uiStage = null;
    CountingManagedPixmapData data = null;
    AndroidInstancingContextProbe instanceProbe = null;
    try {
      if (Gdx.app == null || Gdx.gl20 == null)
        throw new IllegalStateException("Probe must be constructed on a current libGDX GL thread");
      pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
      pixmap.setColor(Color.WHITE);
      pixmap.fill();
      data = new CountingManagedPixmapData(pixmap);
      texture = new Texture(data);
      texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
      fbo = new RecoverableFrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, true);
      renderer = new SmoothShapeRenderer();
      uiTheme = new UiTheme();
      uiStage = new Stage(new StretchViewport(WIDTH, HEIGHT));

      Rectangle rounded = uiTheme.rectangle("bg-[#00ff00] rounded-lg");
      rounded.setBounds(8, 8, 42, 24);
      uiStage.addActor(rounded);
      Label label = uiTheme.label("GL", "text-[#ffffff] text-sm");
      label.setPosition(64, 10);
      label.setSize(38, 24);
      uiStage.addActor(label);

      ShapeRenderer2D material = ShapeRenderer2D.rectangle(40, 22);
      material.cornerRadius = 7;
      material.color.set(Color.WHITE);
      material.material =
          ShapeMaterial2D.gradient(new Color(1, .05f, .02f, 1), new Color(.02f, .1f, 1, 1), 0);
      ShapeRenderer2D dashed =
          ShapeRenderer2D.polyline(List.of(new Vector2(0, 0), new Vector2(62, 0)), 4);
      dashed.setDashes(new DashPattern2D(8, 5), 0);
      dashed.lineCap = ShapeRenderer2D.LineCap.BUTT;
      dashed.color.set(Color.YELLOW);
      instanceProbe = new AndroidInstancingContextProbe();
      instancing = instanceProbe;
      pixmap = null; // retained by the managed control texture and disposed after it.
      retainedControlPixmap = data.pixmap;
      controlData = data;
      controlTexture = texture;
      framebuffer = fbo;
      shapeRenderer = renderer;
      theme = uiTheme;
      shapeRendererIdentity = NEXT_OWNER_ID.incrementAndGet();
      themeIdentity = NEXT_OWNER_ID.incrementAndGet();
      controlTextureIdentity = NEXT_OWNER_ID.incrementAndGet();
      stage = uiStage;
      materialShape = material;
      dashedShape = dashed;
    } catch (RuntimeException | Error failure) {
      cleanupFailure(instanceProbe, failure);
      cleanupFailure(uiStage, failure);
      cleanupFailure(uiTheme, failure);
      cleanupFailure(renderer, failure);
      cleanupFailure(fbo, failure);
      cleanupFailure(texture, failure);
      if (data != null) cleanupFailure(data.pixmap, failure);
      else cleanupFailure(pixmap, failure);
      throw failure;
    }
  }

  /**
   * Render after resume/context recreation on the GL thread, then inspect the actual FBO pixels.
   */
  public Snapshot renderAndSnapshot() {
    requireOpen();
    long contextHandle = currentContextHandle();
    GL20 gl = Gdx.gl20;
    gl.glGetIntegerv(GL20.GL_VIEWPORT, savedViewport);
    gl.glGetIntegerv(0x8CA6, savedFramebuffer); // GL_FRAMEBUFFER_BINDING
    gl.glGetIntegerv(GL20.GL_PACK_ALIGNMENT, savedPackAlignment);
    gl.glGetIntegerv(GL20.GL_ACTIVE_TEXTURE, savedActiveTexture);
    savedClearColor.read(gl);
    int oldScissor = gl.glIsEnabled(GL20.GL_SCISSOR_TEST) ? 1 : 0;
    gl.glDisable(GL20.GL_SCISSOR_TEST);
    boolean depthTest = gl.glIsEnabled(GL20.GL_DEPTH_TEST);
    IntBuffer depthState = BufferUtils.newIntBuffer(1);
    gl.glGetIntegerv(GL20.GL_DEPTH_WRITEMASK, depthState);
    boolean depthMask = depthState.get(0) != 0;
    gl.glDisable(GL20.GL_DEPTH_TEST);
    gl.glDepthMask(true);
    boolean framebufferBegun = false;
    AndroidInstancingContextProbe.Snapshot instanceProof;
    try {
      framebuffer.begin();
      framebufferBegun = true;
      gl.glViewport(0, 0, WIDTH, HEIGHT);
      gl.glClearColor(0, 0, 0, 0);
      gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
      // Touch the explicit control so libGDX binds it after managed reload as well.
      controlTexture.bind(0);
      gl.glActiveTexture(GL20.GL_TEXTURE0);
      transform.idt().setToTranslation(28, 59, 0);
      shapeRenderer.draw(materialShape, projection, transform, WIDTH, HEIGHT);
      transform.idt().setToTranslation(58, 57, 0);
      shapeRenderer.draw(dashedShape, projection, transform, WIDTH, HEIGHT);
      stage.draw();
      gl.glPixelStorei(GL20.GL_PACK_ALIGNMENT, 1);
      pixels.clear();
      gl.glReadPixels(0, 0, WIDTH, HEIGHT, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixels);
      pixels.position(0);
      pixels.limit(WIDTH * HEIGHT * 4);
      instanceProof = instancing.snapshot();
    } finally {
      try {
        if (framebufferBegun) framebuffer.end();
      } finally {
        gl.glBindFramebuffer(0x8D40, savedFramebuffer.get(0)); // GL_FRAMEBUFFER
        gl.glViewport(
            savedViewport.get(0), savedViewport.get(1), savedViewport.get(2), savedViewport.get(3));
        savedClearColor.restore(gl);
        gl.glPixelStorei(GL20.GL_PACK_ALIGNMENT, savedPackAlignment.get(0));
        gl.glActiveTexture(savedActiveTexture.get(0));
        gl.glDepthMask(depthMask);
        if (depthTest) gl.glEnable(GL20.GL_DEPTH_TEST);
        else gl.glDisable(GL20.GL_DEPTH_TEST);
        if (oldScissor != 0) gl.glEnable(GL20.GL_SCISSOR_TEST);
        else gl.glDisable(GL20.GL_SCISSOR_TEST);
      }
    }
    Bounds material = bounds(8, 48, 40, 22, (r, g, b, a) -> a > 32 && (r > 32 || b > 32));
    Bounds dashed = bounds(56, 54, 66, 7, (r, g, b, a) -> a > 96 && r > 180 && g > 180 && b < 80);
    Bounds rounded = bounds(8, 8, 42, 24, (r, g, b, a) -> a > 96 && g > 150 && r < 80 && b < 80);
    int glyphs = count(64, 8, 40, 28, (r, g, b, a) -> a > 100 && r > 190 && g > 190 && b > 190);
    return new Snapshot(
        contextHandle,
        helperIdentity,
        shapeRendererIdentity,
        themeIdentity,
        controlTextureIdentity,
        controlData.consumePixmapCount,
        new Bounds(8, 48, 47, 69, 600),
        material,
        new Bounds(58, 54, 119, 60, 60),
        dashed,
        new Bounds(8, 8, 49, 31, 500),
        rounded,
        glyphs,
        instanceProof);
  }

  public void contextRestored() {
    requireOpen();
    instancing.contextRestored();
  }

  public AndroidInstancingContextProbe.Cleanup closeInstancingAndVerify() {
    requireOpen();
    return instancing.closeAndVerify();
  }

  /** Managed TextureData upload count, readable only from the owner GL thread. */
  public long managedUploadCount() {
    requireOwnerThread();
    requireOpen();
    return controlData.consumePixmapCount;
  }

  public long currentContextHandle() {
    requireOwnerThread();
    EGLContext context = EGL14.eglGetCurrentContext();
    if (context == null || context == EGL14.EGL_NO_CONTEXT)
      throw new IllegalStateException("No current EGL context on the GL thread");
    return context.getNativeHandle();
  }

  private Bounds bounds(int x, int y, int width, int height, PixelTest test) {
    int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1, count = 0;
    for (int py = y; py < y + height; py++)
      for (int px = x; px < x + width; px++) {
        int offset = (py * WIDTH + px) * 4;
        int r = pixels.get(offset) & 255, g = pixels.get(offset + 1) & 255;
        int b = pixels.get(offset + 2) & 255, a = pixels.get(offset + 3) & 255;
        if (test.matches(r, g, b, a)) {
          minX = Math.min(minX, px);
          minY = Math.min(minY, py);
          maxX = Math.max(maxX, px);
          maxY = Math.max(maxY, py);
          count++;
        }
      }
    return count == 0 ? new Bounds(-1, -1, -1, -1, 0) : new Bounds(minX, minY, maxX, maxY, count);
  }

  private int count(int x, int y, int width, int height, PixelTest test) {
    return bounds(x, y, width, height, test).pixels();
  }

  private interface PixelTest {
    boolean matches(int r, int g, int b, int a);
  }

  private void requireOpen() {
    requireOwnerThread();
    if (closed) throw new IllegalStateException("Probe is closed");
    if (Gdx.gl20 == null || currentContextHandle() == 0)
      throw new IllegalStateException("Probe operation requires a current GL context");
  }

  @Override
  public void dispose() {
    requireOwnerThread();
    if (closed) return;
    closed = true;
    Throwable failure = null;
    failure = dispose(instancing, failure);
    failure = dispose(stage, failure);
    failure = dispose(theme, failure);
    failure = dispose(shapeRenderer, failure);
    failure = dispose(framebuffer, failure);
    failure = dispose(controlTexture, failure);
    failure = dispose(retainedControlPixmap, failure);
    if (failure instanceof RuntimeException e) throw e;
    if (failure instanceof Error e) throw e;
  }

  private void requireOwnerThread() {
    if (Thread.currentThread() != ownerThread)
      throw new IllegalStateException("Probe operations must run on the constructing GL thread");
  }

  private static Throwable dispose(Disposable resource, Throwable failure) {
    if (resource == null) return failure;
    try {
      resource.dispose();
    } catch (RuntimeException | Error e) {
      if (failure == null) failure = e;
      else failure.addSuppressed(e);
    }
    return failure;
  }

  private static void cleanupFailure(Disposable resource, Throwable failure) {
    Throwable cleanup = dispose(resource, null);
    if (cleanup != null) failure.addSuppressed(cleanup);
  }

  private static final class CountingManagedPixmapData implements TextureData {
    final Pixmap pixmap;
    private final PixmapTextureData delegate;
    private boolean prepared;
    long consumePixmapCount;

    CountingManagedPixmapData(Pixmap pixmap) {
      this.pixmap = pixmap;
      delegate = new PixmapTextureData(pixmap, pixmap.getFormat(), false, false, true);
    }

    @Override
    public TextureDataType getType() {
      return TextureDataType.Pixmap;
    }

    @Override
    public boolean isPrepared() {
      return prepared;
    }

    @Override
    public void prepare() {
      prepared = true;
    }

    @Override
    public Pixmap consumePixmap() {
      if (!prepared) throw new IllegalStateException("prepare first");
      prepared = false;
      consumePixmapCount++;
      return pixmap;
    }

    @Override
    public boolean disposePixmap() {
      return false;
    }

    @Override
    public int getWidth() {
      return pixmap.getWidth();
    }

    @Override
    public int getHeight() {
      return pixmap.getHeight();
    }

    @Override
    public Pixmap.Format getFormat() {
      return pixmap.getFormat();
    }

    @Override
    public boolean useMipMaps() {
      return false;
    }

    @Override
    public boolean isManaged() {
      return true;
    }

    @Override
    public void consumeCustomData(int target) {
      delegate.consumeCustomData(target);
    }
  }

  private static final class FloatBufferCompat {
    private final java.nio.FloatBuffer values = BufferUtils.newFloatBuffer(4);

    void read(GL20 gl) {
      values.clear();
      gl.glGetFloatv(GL20.GL_COLOR_CLEAR_VALUE, values);
      values.position(0);
    }

    void restore(GL20 gl) {
      gl.glClearColor(values.get(0), values.get(1), values.get(2), values.get(3));
    }
  }
}
