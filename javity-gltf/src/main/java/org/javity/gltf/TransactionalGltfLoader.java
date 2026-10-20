package org.javity.gltf;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.utils.Disposable;
import net.mgsx.gltf.loaders.shared.GLTFLoaderBase;
import net.mgsx.gltf.loaders.shared.data.DataFileResolver;
import net.mgsx.gltf.loaders.shared.texture.ImageResolver;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

/**
 * Pinned 2.3.0 rollback adapter. Upstream base rollback omits meshes created before loadScene
 * returns. MeshLoader.getMeshes() tracks those allocations; dispose each once on failure.
 */
final class TransactionalGltfLoader extends GLTFLoaderBase {
  private boolean rolledBack;
  private Throwable cleanupFailure;

  TransactionalGltfLoader() {
    super(new ManagedTextureResolver());
  }

  @Override
  public SceneAsset load(DataFileResolver resolver, boolean withData) {
    try {
      imageResolver = new ImageResolver(resolver);
      imageResolver.load(resolver.getRoot().images);
      textureResolver.loadTextures(
          resolver.getRoot().textures, resolver.getRoot().samplers, imageResolver);
      return super.load(resolver, withData);
    } catch (RuntimeException | Error failure) {
      dispose();
      if (cleanupFailure != null && cleanupFailure != failure)
        failure.addSuppressed(cleanupFailure);
      throw failure;
    }
  }

  @Override
  public void dispose() {
    if (rolledBack) return;
    rolledBack = true;
    release(imageResolver);
    release(textureResolver);
    for (Mesh mesh : meshLoader.getMeshes()) release(mesh);
  }

  private void release(Disposable resource) {
    if (resource == null) return;
    try {
      resource.dispose();
    } catch (RuntimeException | Error next) {
      if (cleanupFailure == null) cleanupFailure = next;
      else if (cleanupFailure != next) cleanupFailure.addSuppressed(next);
    }
  }
}
