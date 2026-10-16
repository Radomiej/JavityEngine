package org.javity.demo;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import org.javity.assets.AssetLease;

/** Native integration assertions; the delay gate wraps the ordinary TextureLoader only in smoke. */
final class AssetStreamingGpuSmokeProbe {
  private final GL20 original = Gdx.gl20;
  private final GL20 originalGl = Gdx.gl;
  private final Map<Integer, Integer> buffers = new HashMap<>(), textures = new HashMap<>();
  private final Map<Integer, Integer> bufferCreates = new HashMap<>(), textureCreates = new HashMap<>();
  private final Thread owner = Thread.currentThread();
  private int phase, loadingFrames, readyFrame, texture, vertex, index, checks;
  private float sentinelBefore, sentinelPixelBefore, sentinelPixelAfter;
  private Model model;
  private final com.badlogic.gdx.math.Quaternion pausedWorldRotation = new com.badlogic.gdx.math.Quaternion();
  private AssetLease<Model> first, second, failed;
  private int modelPixels, retainedPixels, emptyPixels;

  AssetStreamingGpuSmokeProbe() {
    GL20 proxy = (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(), new Class<?>[] {GL20.class},
        (ignored, method, args) -> {
          try {
            Object result = method.invoke(original, args);
            String name = method.getName();
            if (name.equals("glGenBuffer")) { bufferCreates.merge((Integer) result, 1, Integer::sum); buffers.putIfAbsent((Integer) result, 0); }
            if (name.equals("glGenTexture")) { textureCreates.merge((Integer) result, 1, Integer::sum); textures.putIfAbsent((Integer) result, 0); }
            if (name.equals("glDeleteBuffer")) buffers.merge((Integer) args[0], 1, Integer::sum);
            if (name.equals("glDeleteTexture")) textures.merge((Integer) args[0], 1, Integer::sum);
            if (name.equals("glDeleteBuffers") || name.equals("glDeleteTextures")) {
              Map<Integer, Integer> target = name.equals("glDeleteBuffers") ? buffers : textures;
              IntBuffer ids = ((IntBuffer) args[1]).duplicate();
              for (int i = 0; i < (Integer) args[0]; i++) target.merge(ids.get(), 1, Integer::sum);
            }
            if (name.startsWith("glGen") || name.startsWith("glDelete") || name.startsWith("glTexImage"))
              require(Thread.currentThread() == owner, "Native allocation/disposal ran off owner thread");
            return result;
          } catch (InvocationTargetException failure) { throw failure.getCause(); }
        });
    Gdx.gl = Gdx.gl20 = proxy;
  }

  void afterRender(AssetStreamingDemo demo) {
    require(demo.frames < 600, "Streaming smoke timed out; phase " + phase);
    require(demo.streams.stats().inflight() <= 1, "More than one physically admitted root");
    if (phase == 0) {
      first = demo.first;
      second = demo.second;
      if (demo.gate.started.getCount() == 0 && first.state() == AssetLease.State.LOADING) {
        if (loadingFrames++ == 0) {
          sentinelBefore = demo.sentinelTime;
          sentinelPixelBefore = sentinelCentroid();
          capture("task3-loading-start.png");
          pausedWorldRotation.set((com.badlogic.gdx.math.Quaternion) field(field(demo, "modelTransform"), "rotation"));
          demo.game.pause();
        }
        if (loadingFrames == 12) {
          require(demo.sentinelTime > sentinelBefore + .05f, "Sentinel stopped during actual loading");
          sentinelPixelAfter = sentinelCentroid();
          require(Math.abs(sentinelPixelAfter - sentinelPixelBefore) > 8, "Sentinel pixels did not move while loading/paused");
          require(demo.game.isPaused(), "Paused loading control was lost");
          require(pausedWorldRotation.equals((com.badlogic.gdx.math.Quaternion) field(field(demo, "modelTransform"), "rotation")),
              "WORLD model rotated while game was paused");
          capture("task3-loading.png");
          demo.game.resume();
          demo.gate.release.countDown();
          phase = 1;
        }
      }
    } else if (phase == 1 && first.state() == AssetLease.State.READY) {
      require(second.state() == AssetLease.State.READY && first.get() == second.get(), "Leases do not share one model");
      require(demo.gate.loads == 1, "Default texture dependency loaded more than once");
      require(demo.modelLoads == 1, "Default model loaded more than once");
      model = second.get();
      require(demo.display != null && demo.display.instance.model == model, "READY model was not attached");
      texture = ((TextureAttribute) model.materials.first().get(TextureAttribute.Diffuse)).textureDescription.texture.getTextureObjectHandle();
      vertex = handle(field(model.meshes.first(), "vertices"));
      index = handle(field(model.meshes.first(), "indices"));
      require(original.glIsTexture(texture) && original.glIsBuffer(vertex) && original.glIsBuffer(index), "Model has no real native resources");
      modelPixels = capture("task3-ready-two-leases.png");
      require(modelPixels > 500, "Textured model did not produce expected cyan/gold pixels");
      demo.releaseFirst();
      readyFrame = demo.frames;
      phase = 2;
    } else if (phase == 2 && demo.frames > readyFrame + 2) {
      require(first.state() == AssetLease.State.CLOSED && second.state() == AssetLease.State.READY, "First close invalidated second lease");
      require(second.get() == model && original.glIsTexture(texture), "First close released shared model");
      retainedPixels = capture("task3-one-lease.png");
      require(retainedPixels > 500, "Remaining lease no longer renders textured model");
      demo.releaseAll();
      require(demo.display == null && demo.modelObject.getComponent(org.javity.render.ModelRenderer.class) == null, "Component must detach before final lease close");
      require(second.state() == AssetLease.State.CLOSED && original.glIsTexture(texture), "Final close must retain unpinned cache");
      phase = 3;
    } else if (phase == 3) {
      require(demo.streams.stats().ready() == 1 && demo.streams.stats().pinned() == 0, "Unused root was not cached");
      require(original.glIsTexture(texture) && original.glIsBuffer(vertex), "Cache unloaded without pressure");
      emptyPixels = capture("task3-released-cache.png");
      require(emptyPixels == 0, "Detached model still renders");
      demo.requestModel();
      require(demo.first.state() == AssetLease.State.READY && demo.first.get() == model
          && demo.second.get() == model && demo.modelLoads == 1, "Cache reacquisition reloaded the model");
      demo.releaseAll();
      demo.prefetch();
      require(demo.prefetch.state() == AssetLease.State.QUEUED && demo.streams.stats().overBudget(), "Replacement did not create real entry pressure");
      require(original.glIsTexture(texture), "Request evicted before finishFrame");
      phase = 4;
    } else if (phase == 40) {
      if (demo.prefetch.state() == AssetLease.State.READY) {
        demo.releasePrefetch();
        require(demo.streams.stats().pinned() == 0, "Prefetch lease remained pinned");
        phase = 45;
      }
    } else if (phase == 45) {
      require(demo.streams.stats().ready() == 1 && demo.streams.stats().pinned() == 0, "Released prefetch did not remain cached");
      capture("task3-prefetch-cache.png");
      demo.requestMissing();
      phase = 5;
    } else if (phase == 5 && demo.missing.state() == AssetLease.State.FAILED) {
      failed = demo.missing;
      require(failed.failure() != null, "Missing file has no diagnostic");
      capture("task3-explicit-failure.png");
      demo.requestMissing();
      require(demo.missing != failed && failed.state() == AssetLease.State.FAILED, "Explicit retry changed old failed lease");
      phase = 6;
    } else if (phase == 6 && demo.missing.state() == AssetLease.State.FAILED) {
      require(demo.batchReady() == 0 && demo.batchFailed() == 1, "Batch counts do not describe the current retry");
      demo.dispose();
      closeDuringLoad();
      require(demo.streams.stats().ready() == 0, "Disposed service has resident roots");
      for (var entry : buffers.entrySet()) {
        require(!original.glIsBuffer(entry.getKey()), "Native buffer leak " + entry.getKey());
        require(entry.getValue().equals(bufferCreates.get(entry.getKey())), "Buffer creation/disposal count " + entry);
      }
      for (var entry : textures.entrySet()) {
        require(!original.glIsTexture(entry.getKey()), "Native texture leak " + entry.getKey());
        require(entry.getValue().equals(textureCreates.get(entry.getKey())), "Texture creation/disposal count " + entry);
      }
      require(original.glGetError() == GL20.GL_NO_ERROR, "GL error after cleanup");
      Gdx.files.local("docs/artifacts/asset-streaming/task3-proof.json").writeString(
          "{\n  \"desktop\": \"PASS\", \"android\": \"UNVERIFIED\",\n"
          + "  \"checks\": " + checks + ", \"loadingFrames\": " + loadingFrames + ",\n"
          + "  \"defaultModelLoads\": " + demo.modelLoads + ", \"textureDependencyLoads\": " + demo.gate.loads + ",\n"
          + "  \"sentinelPixelDisplacement\": " + Math.abs(sentinelPixelAfter - sentinelPixelBefore) + ",\n"
          + "  \"modelPixels\": " + modelPixels + ", \"oneLeasePixels\": " + retainedPixels + ", \"detachedPixels\": " + emptyPixels + ",\n"
          + "  \"buffersCreatedDeleted\": " + bufferCreates.values().stream().mapToInt(Integer::intValue).sum() + ", \"texturesCreatedDeleted\": " + textureCreates.values().stream().mapToInt(Integer::intValue).sum() + ",\n"
          + "  \"cachePressureDisposal\": true, \"explicitRetry\": true, \"ownerThreadNativeOperations\": true, \"closeDuringLoad\": true, \"pausedLoading\": true\n}\n", false, "UTF-8");
      Gdx.gl = originalGl; Gdx.gl20 = original;
      Gdx.app.log("AssetStreaming", "PASS: default loaders, live sentinel, textured model, leases/cache/pressure/retry and native cleanup");
      Gdx.app.exit();
      phase = 7;
    }
  }

  void afterFinishFrame() {
    if (phase != 4) return;
    require(!original.glIsTexture(texture) && !original.glIsBuffer(vertex) && !original.glIsBuffer(index),
        "Pressure failed to dispose native model/dependency before replacement upload");
    require(buffers.get(vertex) == 1 && buffers.get(index) == 1 && textures.get(texture) == 1,
        "Model resources not disposed exactly once at safe boundary");
    phase = 40;
  }

  private float sentinelCentroid() {
    Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    try {
      int count = 0; long sum = 0;
      for (int y = 0; y < pixels.getHeight() / 4; y++)
        for (int x = pixels.getWidth() / 2; x < pixels.getWidth(); x++) {
          int rgba = pixels.getPixel(x, y), r = rgba >>> 24 & 255, g = rgba >>> 16 & 255, b = rgba >>> 8 & 255;
          if (r > 180 && g > 100 && b < 90) { count++; sum += x; }
        }
      require(count > 100, "Animated sentinel produced no pixels");
      return (float) sum / count;
    } finally { pixels.dispose(); }
  }

  private void closeDuringLoad() {
    var manager = new com.badlogic.gdx.assets.AssetManager();
    var loader = new GatedTextureLoader(manager.getFileHandleResolver());
    manager.setLoader(com.badlogic.gdx.graphics.Texture.class, loader);
    var game = new org.javity.core.Game();
    var streams = game.addSystem(new org.javity.assets.AssetStreams(manager,
        new org.javity.assets.StreamBudget(4, 1, 96 * 1024, 1, 1)));
    var lease = streams.request(new org.javity.assets.AssetKey<>(AssetStreamingDemo.ROOT + "signal-crate.g3dj", Model.class), 1, 64 * 1024);
    game.start();
    try {
      long deadline = System.nanoTime() + 5_000_000_000L;
      while (loader.started.getCount() != 0 && System.nanoTime() < deadline) {
        game.step(1f / 60); streams.finishFrame(); Thread.yield();
      }
      require(loader.started.getCount() == 0 && lease.state() == AssetLease.State.LOADING, "Scene-close fixture was not actually loading");
      // Release the CPU worker after close begins; close must drain and upload/dispose on owner.
      Thread releaser = Thread.ofPlatform().start(() -> {
        java.util.concurrent.locks.LockSupport.parkNanos(40_000_000L);
        loader.release.countDown();
      });
      game.close();
      try { releaser.join(); } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
      require(lease.state() == AssetLease.State.CLOSED && streams.stats().ready() == 0,
          "Scene close published or retained active load");
      game.close();
    } finally { loader.release.countDown(); game.close(); }
  }

  private int capture(String name) {
    Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    try {
      var target = Gdx.files.local("docs/artifacts/asset-streaming/" + name);
      target.parent().mkdirs();
      PixmapIO.writePNG(target, pixels, -1, true);
      int count = 0;
      // The model occupies the right half; omit the panel and bottom sentinel.
      for (int y = pixels.getHeight() / 4; y < pixels.getHeight() * 3 / 4; y++)
        for (int x = pixels.getWidth() / 2; x < pixels.getWidth(); x++) {
          int rgba = pixels.getPixel(x, y), r = rgba >>> 24 & 255, g = rgba >>> 16 & 255, b = rgba >>> 8 & 255;
          if ((r > 160 && g > 100 && b < 90) || (r < 90 && g > 120 && b > 130)) count++;
        }
      return count;
    } finally { pixels.dispose(); }
  }

  static final class GatedTextureLoader extends com.badlogic.gdx.assets.loaders.TextureLoader {
    final java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
    final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
    private final Thread owner = Thread.currentThread();
    volatile int loads;
    GatedTextureLoader(com.badlogic.gdx.assets.loaders.FileHandleResolver resolver) { super(resolver); }
    @Override public void loadAsync(com.badlogic.gdx.assets.AssetManager manager, String path,
        com.badlogic.gdx.files.FileHandle file, TextureParameter parameter) {
      if (path.endsWith("/signal.png")) {
        loads++;
        if (Thread.currentThread() == owner) throw new IllegalStateException("Texture preparation ran on owner");
        started.countDown();
        try {
          if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Smoke gate timed out");
        } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
      }
      super.loadAsync(manager, path, file, parameter);
    }
    @Override public com.badlogic.gdx.graphics.Texture loadSync(com.badlogic.gdx.assets.AssetManager manager,
        String path, com.badlogic.gdx.files.FileHandle file, TextureParameter parameter) {
      if (Thread.currentThread() != owner) throw new IllegalStateException("Texture upload ran off owner");
      return super.loadSync(manager, path, file, parameter);
    }
  }

  static final class CountingModelLoader extends com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader {
    private final Runnable counted;
    CountingModelLoader(com.badlogic.gdx.assets.loaders.FileHandleResolver resolver, Runnable counted) {
      super(new com.badlogic.gdx.utils.JsonReader(), resolver); this.counted = counted;
    }
    @Override public com.badlogic.gdx.graphics.g3d.model.data.ModelData loadModelData(
        com.badlogic.gdx.files.FileHandle file, com.badlogic.gdx.assets.loaders.ModelLoader.ModelParameters parameter) {
      if (file.name().equals("signal-crate.g3dj")) counted.run();
      return super.loadModelData(file, parameter);
    }
  }

  private static Object field(Object object, String name) {
    try { var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
    catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
  }
  private static int handle(Object object) { return (Integer) field(object, "bufferHandle"); }
  private void require(boolean condition, String message) { checks++; if (!condition) throw new IllegalStateException(message); }
}
