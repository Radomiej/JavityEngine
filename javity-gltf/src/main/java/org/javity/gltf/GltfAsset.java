package org.javity.gltf;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import java.util.Objects;
import net.mgsx.gltf.loaders.glb.BinaryDataFileResolver;
import net.mgsx.gltf.loaders.gltf.SeparatedDataFileResolver;
import net.mgsx.gltf.loaders.shared.data.DataFileResolver;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

/**
 * Render-thread owner of all imported scenes, meshes, textures and retained pixmaps. Close prevents
 * new instances and defers disposal until every existing instance closes.
 */
public final class GltfAsset implements AutoCloseable {
  private final Thread owner = Thread.currentThread();
  private final SceneAsset asset;
  private int instances;
  private boolean closing, disposed;

  GltfAsset(SceneAsset asset) {
    this.asset = Objects.requireNonNull(asset);
    if (asset.scene == null || asset.scene.model == null)
      throw new IllegalArgumentException("Missing default scene");
  }

  /** Synchronous upload; call with a current OpenGL context on its owner thread. */
  public static GltfAsset load(FileHandle file, int maxBones) {
    Objects.requireNonNull(file, "file");
    GltfDocument.validate(file, maxBones);
    DataFileResolver resolver =
        file.extension().equalsIgnoreCase("glb")
            ? new BinaryDataFileResolver()
            : new SeparatedDataFileResolver();
    resolver.load(file);
    SceneAsset imported = new TransactionalGltfLoader().load(resolver, false);
    try {
      return new GltfAsset(imported);
    } catch (RuntimeException | Error failure) {
      try {
        imported.dispose();
      } catch (RuntimeException | Error cleanup) {
        if (failure != cleanup) failure.addSuppressed(cleanup);
      }
      throw failure;
    }
  }

  public int maxBones() {
    checkThread();
    return asset.maxBones;
  }

  public boolean isDisposed() {
    checkThread();
    return disposed;
  }

  public boolean isClosing() {
    checkThread();
    return closing;
  }

  public GltfInstance instantiate() {
    checkThread();
    if (closing) throw new IllegalStateException("glTF asset is closing");
    // Preflight excludes morph/nonlinear animation, so the ordinary engine pose owners remain
    // valid.
    ModelInstance instance = new ModelInstance(asset.scene.model);
    instances++;
    return new GltfInstance(this, instance);
  }

  void checkThread() {
    if (Thread.currentThread() != owner)
      throw new IllegalStateException("glTF resources require their owner thread");
  }

  void releaseInstance() {
    checkThread();
    if (--instances < 0) throw new IllegalStateException("Unbalanced glTF instance release");
    disposeIfUnused();
  }

  @Override
  public void close() {
    checkThread();
    closing = true;
    disposeIfUnused();
  }

  private void disposeIfUnused() {
    if (!closing || disposed || instances != 0) return;
    disposed = true;
    Throwable failure = null;
    if (asset.scenes != null) for (var scene : asset.scenes) failure = release(failure, scene);
    if (asset.textures != null)
      for (var texture : asset.textures) failure = release(failure, texture);
    if (asset.pixmaps != null) for (var pixmap : asset.pixmaps) failure = release(failure, pixmap);
    if (asset.meshes != null) for (var mesh : asset.meshes) failure = release(failure, mesh);
    if (failure instanceof RuntimeException next) throw next;
    if (failure instanceof Error next) throw next;
  }

  private static Throwable release(Throwable failure, com.badlogic.gdx.utils.Disposable resource) {
    try {
      resource.dispose();
    } catch (RuntimeException | Error next) {
      if (failure == null) return next;
      if (failure != next) failure.addSuppressed(next);
    }
    return failure;
  }
}
