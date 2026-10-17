package org.javity.maplibre;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.GLOnlyTextureData;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.javity.maplibre.internal.NativeBridge;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeEGL;
import org.lwjgl.system.MemoryStack;
import org.maplibre.nativeffi.Maplibre;
import org.maplibre.nativeffi.camera.CameraOptions;
import org.maplibre.nativeffi.geo.LatLng;
import org.maplibre.nativeffi.geo.ScreenPoint;
import org.maplibre.nativeffi.map.MapHandle;
import org.maplibre.nativeffi.map.MapOptions;
import org.maplibre.nativeffi.query.FeatureStateSelector;
import org.maplibre.nativeffi.query.RenderedFeatureQueryOptions;
import org.maplibre.nativeffi.query.RenderedQueryGeometry;
import org.maplibre.nativeffi.render.OpenGLOwnedTextureDescriptor;
import org.maplibre.nativeffi.render.RenderSessionHandle;
import org.maplibre.nativeffi.render.RenderTargetExtent;
import org.maplibre.nativeffi.runtime.RuntimeHandle;
import org.maplibre.nativeffi.runtime.RuntimeOptions;

/**
 * A real MapLibre Native renderer backed by a shared GPU texture. All methods run on the creating
 * GL thread. Owns Native handles, borrows the host window and Batch. Network reachability is
 * process-global in upstream Native and is set OFFLINE.
 */
public final class MapLibreMap implements AutoCloseable {
  public record Point(double x, double y) {}

  public record Coordinate(double longitude, double latitude) {}

  public record Feature(String source, String sourceLayer, String geoJson, String stateJson) {}

  private final Thread owner = Thread.currentThread();
  private final OfflineMapPackage mapPackage;
  private RuntimeHandle runtime;
  private MapHandle map;
  private RenderSessionHandle session;
  private BorrowedTexture texture;
  private boolean closed;
  private boolean hasFrame;
  private int width, height;
  private double scaleFactor;
  private long renderedFrames;
  private long consumerFence;
  private String style;
  private final long hostWindow;

  private record StateKey(String source, String layer, String id) {}

  private final java.util.Set<StateKey> featureStates = new java.util.LinkedHashSet<>();

  private record PropertyKey(String layer, String property) {}

  private final java.util.Map<PropertyKey, String> layerProperties =
      new java.util.LinkedHashMap<>();
  private final java.util.Map<String, String> layerFilters = new java.util.LinkedHashMap<>();

  public MapLibreMap(OfflineMapPackage mapPackage, int width, int height, double scaleFactor) {
    this.mapPackage = Objects.requireNonNull(mapPackage, "mapPackage");
    validateSize(width, height, scaleFactor);
    if (!(Gdx.graphics instanceof Lwjgl3Graphics graphics))
      throw new IllegalStateException("MapLibre requires a live LWJGL3 EGL window");
    long window = graphics.getWindow().getWindowHandle();
    if (GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_CONTEXT_CREATION_API)
        != GLFW.GLFW_EGL_CONTEXT_API)
      throw new IllegalStateException(
          "Configure MapLibreDesktop before creating the window; GLX cannot share EGL textures");
    long display = GLFWNativeEGL.glfwGetEGLDisplay();
    long context = GLFWNativeEGL.glfwGetEGLContext(window);
    if (display == 0 || context == 0)
      throw new IllegalStateException("No current EGL host context");
    hostWindow = window;
    try {
      Maplibre.INSTANCE.loadNativeLibrary();
      NativeBridge.offline();
      RuntimeOptions runtimeOptions = new RuntimeOptions();
      runtimeOptions.setCachePath(":memory:");
      runtime = RuntimeHandle.Companion.create(runtimeOptions);
      createNativeMap(width, height, scaleFactor);
      this.width = width;
      this.height = height;
      this.scaleFactor = scaleFactor;
      setStyle(mapPackage.defaultStyle());
      jumpTo(mapPackage.initialCamera());
    } catch (RuntimeException | Error failure) {
      try {
        close();
      } catch (RuntimeException | Error cleanup) {
        failure.addSuppressed(cleanup);
      }
      throw failure;
    }
  }

  private void createNativeMap(int width, int height, double scaleFactor) {
    long window = hostWindow;
    long display = GLFWNativeEGL.glfwGetEGLDisplay();
    long context = GLFWNativeEGL.glfwGetEGLContext(window);
    MapOptions options = new MapOptions();
    options.setWidth(width);
    options.setHeight(height);
    options.setScaleFactor(scaleFactor);
    map = MapHandle.Companion.create(runtime, options);
    var bounds = new org.maplibre.nativeffi.camera.BoundOptions();
    bounds.setMinZoom(0.0);
    bounds.setMaxZoom(22.0);
    bounds.setMinPitch(0.0);
    bounds.setMaxPitch(60.0);
    map.setBounds(bounds);
    try (MemoryStack stack = MemoryStack.stackPush()) {
      var config = stack.mallocPointer(1);
      if (!GLFWNativeEGL.glfwGetEGLConfig(window, config))
        throw new IllegalStateException("No host EGL config");
      long proc = org.lwjgl.egl.EGL.getFunctionProvider().getFunctionAddress("eglGetProcAddress");
      session =
          map.attachOpenGLOwnedTexture(
              new OpenGLOwnedTextureDescriptor(
                  new RenderTargetExtent(width, height, scaleFactor),
                  NativeBridge.egl(display, config.get(0), context, proc)));
    }
  }

  /** Pump/render outside any begun Batch. No CPU image copy occurs. */
  public boolean update() {
    check();
    runtime.pump(0, 4);
    var events = runtime.drainEvents(256);
    NativeBridge.checkErrors(events);
    awaitConsumer();
    boolean rendered = NativeBridge.rendered(session.renderUpdate());
    if (rendered) {
      hasFrame = true;
      renderedFrames++;
    }
    return rendered;
  }

  /** Draw into a begun caller Batch; flush while the borrowed Native frame is live. */
  public boolean draw(Batch batch, float x, float y, float width, float height) {
    check();
    Objects.requireNonNull(batch, "batch");
    if (!batch.isDrawing()) throw new IllegalStateException("Begin Batch before drawing the map");
    if (!Float.isFinite(x)
        || !Float.isFinite(y)
        || !Float.isFinite(width)
        || !Float.isFinite(height)
        || width < 0
        || height < 0) throw new IllegalArgumentException("Invalid map destination");
    if (!hasFrame) return false;
    try (var frame = session.acquireOpenGLOwnedTextureFrame()) {
      var image = frame.frame();
      if (image.target() != GL20.GL_TEXTURE_2D)
        throw new IllegalStateException("Unsupported native texture target");
      if (texture == null)
        texture = new BorrowedTexture(image.texture(), image.width(), image.height());
      else texture.assign(image.texture(), image.width(), image.height());
      int src = batch.getBlendSrcFunc(), dst = batch.getBlendDstFunc();
      int srcAlpha = batch.getBlendSrcFuncAlpha(), dstAlpha = batch.getBlendDstFuncAlpha();
      boolean blending = batch.isBlendingEnabled();
      float packedColor = batch.getPackedColor();
      Color tint = batch.getColor();
      float r = tint.r, g = tint.g, b = tint.b, a = tint.a;
      try {
        // Native outputs premultiplied RGBA. Premultiply the caller's tint too,
        // and preserve its blend/color settings for later UI drawing.
        batch.enableBlending();
        batch.setBlendFunctionSeparate(
            GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.setColor(r * a, g * a, b * a, a);
        // FBO texture origin is bottom-left, unlike a normal Pixmap texture.
        batch.draw(texture, x, y, width, height, 0, 0, 1, 1);
        batch.flush();
        signalConsumer();
      } finally {
        batch.setPackedColor(packedColor);
        batch.setBlendFunctionSeparate(src, dst, srcAlpha, dstAlpha);
        if (!blending) batch.disableBlending();
      }
    }
    return true;
  }

  public void resize(int width, int height, double scaleFactor) {
    check();
    validateSize(width, height, scaleFactor);
    awaitConsumer();
    if (Double.compare(scaleFactor, this.scaleFactor) != 0) {
      // Native MapOptions pixel ratio is immutable. A new map keeps styled
      // imagery (glyphs/sprites) consistent with the new physical render extent.
      byte[] currentStyle = map.loadedStyleJson();
      var currentCamera = map.getCamera();
      var states = new java.util.LinkedHashMap<StateKey, byte[]>();
      for (StateKey key : featureStates) states.put(key, map.getFeatureState(selector(key)));
      try {
        session.close();
        session = null;
        map.close();
        map = null;
        createNativeMap(width, height, scaleFactor);
        map.setStyleJson(currentStyle);
        map.jumpTo(currentCamera);
        // Native loadedStyleJson is the originally loaded JSON, not a snapshot
        // of imperative paint/layout/filter edits. Replay our explicit overrides.
        for (var property : layerProperties.entrySet())
          map.setLayerProperty(
              property.getKey().layer(), property.getKey().property(), utf8(property.getValue()));
        for (var filter : layerFilters.entrySet())
          map.setLayerFilter(filter.getKey(), utf8(filter.getValue()));
        for (var state : states.entrySet())
          map.setFeatureState(selector(state.getKey()), state.getValue());
        texture = null;
      } catch (RuntimeException | Error failure) {
        try {
          close();
        } catch (RuntimeException | Error cleanup) {
          failure.addSuppressed(cleanup);
        }
        throw failure;
      }
    } else session.resize(width, height, scaleFactor);
    this.width = width;
    this.height = height;
    this.scaleFactor = scaleFactor;
    hasFrame = false;
  }

  public int width() {
    check();
    return width;
  }

  public int height() {
    check();
    return height;
  }

  public double scaleFactor() {
    check();
    return scaleFactor;
  }

  public long renderedFrames() {
    check();
    return renderedFrames;
  }

  public boolean isFullyLoaded() {
    check();
    return map.isFullyLoaded();
  }

  public String style() {
    check();
    return style;
  }

  public String attribution() {
    return mapPackage.attribution();
  }

  public void setStyle(String name) {
    check();
    String json = mapPackage.resolvedStyle(name);
    map.setStyleJson(json.getBytes(StandardCharsets.UTF_8));
    style = name;
    featureStates.clear();
    layerProperties.clear();
    layerFilters.clear();
  }

  public void jumpTo(OfflineMapPackage.Camera camera) {
    check();
    Objects.requireNonNull(camera, "camera");
    var c = new CameraOptions();
    c.setCenter(new LatLng(camera.latitude(), camera.longitude()));
    c.setZoom(camera.zoom());
    c.setBearing(camera.bearing());
    c.setPitch(camera.pitch());
    map.jumpTo(c);
  }

  public OfflineMapPackage.Camera camera() {
    check();
    var c = map.getCamera();
    var center = c.getCenter();
    return new OfflineMapPackage.Camera(
        center.getLongitude(), center.getLatitude(), c.getZoom(), c.getBearing(), c.getPitch());
  }

  /** MapLibre screen coordinates: logical pixels, top-left origin. */
  public void panBy(double x, double y) {
    check();
    finite(x, y);
    map.moveBy(x, y);
  }

  public void zoomBy(double factor, double anchorX, double anchorY) {
    check();
    finite(factor, anchorX, anchorY);
    if (factor <= 0) throw new IllegalArgumentException("Zoom factor must be positive");
    map.scaleBy(factor, new ScreenPoint(anchorX, anchorY));
  }

  public Point pixelFor(Coordinate point) {
    check();
    Objects.requireNonNull(point);
    finite(point.longitude(), point.latitude());
    var p = map.pixelForLatLng(new LatLng(point.latitude(), point.longitude()));
    return new Point(p.getX(), p.getY());
  }

  public Coordinate coordinateFor(double x, double y) {
    check();
    finite(x, y);
    var p = map.latLngForPixel(new ScreenPoint(x, y));
    return new Coordinate(p.getLongitude(), p.getLatitude());
  }

  /** Set any native-supported style property using a JSON value/expression. */
  public void setLayerProperty(String layer, String property, String valueJson) {
    check();
    map.setLayerProperty(
        Objects.requireNonNull(layer), Objects.requireNonNull(property), utf8(valueJson));
    layerProperties.put(new PropertyKey(layer, property), valueJson);
  }

  public void setLayerFilter(String layer, String filterJson) {
    check();
    map.setLayerFilter(Objects.requireNonNull(layer), utf8(filterJson));
    layerFilters.put(layer, filterJson);
  }

  public void setFeatureState(String source, String sourceLayer, String id, String stateJson) {
    check();
    var key = new StateKey(Objects.requireNonNull(source), sourceLayer, Objects.requireNonNull(id));
    map.setFeatureState(selector(key), utf8(stateJson));
    featureStates.add(key);
  }

  private static FeatureStateSelector selector(StateKey key) {
    var selector = new FeatureStateSelector(key.source());
    selector.setSourceLayerId(key.layer());
    selector.setFeatureId(key.id());
    return selector;
  }

  public List<Feature> query(double x, double y, List<String> layerIds) {
    check();
    finite(x, y);
    var options = new RenderedFeatureQueryOptions();
    options.setLayerIds(layerIds);
    return session
        .queryRenderedFeatures(new RenderedQueryGeometry.Point(new ScreenPoint(x, y)), options)
        .stream()
        .map(
            f ->
                new Feature(
                    f.getSourceId(),
                    f.getSourceLayerId(),
                    new String(f.getFeature(), StandardCharsets.UTF_8),
                    f.getState() == null ? null : new String(f.getState(), StandardCharsets.UTF_8)))
        .toList();
  }

  private static byte[] utf8(String s) {
    return Objects.requireNonNull(s).getBytes(StandardCharsets.UTF_8);
  }

  private static void finite(double... values) {
    for (double value : values)
      if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite map coordinate");
  }

  private static void validateSize(int w, int h, double scale) {
    if (w <= 0
        || h <= 0
        || !Double.isFinite(scale)
        || scale <= 0
        || Math.ceil(w * scale) > 16384
        || Math.ceil(h * scale) > 16384)
      throw new IllegalArgumentException("Invalid map extent (physical size limit 16384)");
  }

  private void check() {
    if (Thread.currentThread() != owner)
      throw new IllegalStateException("MapLibre must be used on its creating GL thread");
    if (closed) throw new IllegalStateException("MapLibre map is closed");
    if (GLFW.glfwGetCurrentContext() != hostWindow)
      throw new IllegalStateException("The map's EGL host window must be current");
  }

  // Native acquire synchronizes its producer. Releasing the frame does not wait
  // for the host GPU's sampling, so fence our draw before the next native write.
  private void signalConsumer() {
    if (org.lwjgl.opengles.GLES.getCapabilities().GLES30) {
      long next =
          org.lwjgl.opengles.GLES30.glFenceSync(
              org.lwjgl.opengles.GLES30.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
      if (next == 0) {
        Gdx.gl.glFinish();
        return;
      }
      Gdx.gl.glFlush();
      if (consumerFence != 0) org.lwjgl.opengles.GLES30.glDeleteSync(consumerFence);
      consumerFence = next;
    } else Gdx.gl.glFinish();
  }

  private void awaitConsumer() {
    if (consumerFence == 0) return;
    int result = org.lwjgl.opengles.GLES30.glClientWaitSync(consumerFence, 0, 1_000_000);
    if (result != org.lwjgl.opengles.GLES30.GL_ALREADY_SIGNALED
        && result != org.lwjgl.opengles.GLES30.GL_CONDITION_SATISFIED) Gdx.gl.glFinish();
    org.lwjgl.opengles.GLES30.glDeleteSync(consumerFence);
    consumerFence = 0;
  }

  @Override
  public void close() {
    if (Thread.currentThread() != owner)
      throw new IllegalStateException("Close MapLibre on its creating GL thread");
    if (closed && session == null && map == null && runtime == null) return;
    if (GLFW.glfwGetCurrentContext() != hostWindow)
      throw new IllegalStateException("Close the map before closing its EGL host window");
    closed = true;
    awaitConsumer();
    Throwable failure = null;
    try {
      if (session != null) {
        session.close();
        session = null;
      }
    } catch (Throwable e) {
      failure = e;
    }
    try {
      if (map != null) {
        map.close();
        map = null;
      }
    } catch (Throwable e) {
      if (failure == null) failure = e;
      else failure.addSuppressed(e);
    }
    try {
      if (runtime != null) {
        runtime.close();
        runtime = null;
      }
    } catch (Throwable e) {
      if (failure == null) failure = e;
      else failure.addSuppressed(e);
    }
    if (failure == null) texture = null;
    if (failure instanceof Error e) throw e;
    if (failure != null)
      throw new IllegalStateException("MapLibre cleanup failed; close may be retried", failure);
  }

  /** Never allocates/uploads/deletes the Native-owned texture. */
  private static final class BorrowedTexture extends Texture {
    private int width, height;

    BorrowedTexture(int id, int w, int h) {
      super(
          GL20.GL_TEXTURE_2D,
          id,
          new GLOnlyTextureData(w, h, 0, GL20.GL_RGBA, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE) {
            @Override
            public void consumeCustomData(int target) {}
          });
      assign(id, w, h);
    }

    void assign(int id, int w, int h) {
      glHandle = id;
      width = w;
      height = h;
    }

    // Texture's constructor normally uploads pixels and changes the sampler.
    // This wrapper only identifies an already allocated borrowed GPU object.
    @Override
    public void load(com.badlogic.gdx.graphics.TextureData ignored) {}

    @Override
    public boolean isManaged() {
      return false;
    }

    @Override
    public int getDepth() {
      return 0;
    }

    @Override
    public int getWidth() {
      return width;
    }

    @Override
    public int getHeight() {
      return height;
    }

    @Override
    public void dispose() {}
  }
}
