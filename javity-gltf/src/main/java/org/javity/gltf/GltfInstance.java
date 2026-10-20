package org.javity.gltf;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import org.javity.render.ModelRenderer;

/**
 * Independent nodes/material copies/pose borrowing its asset's shared GPU data. Close after
 * detaching the consuming ModelRenderer; instances pin assets across lease release.
 */
public final class GltfInstance implements AutoCloseable {
  private final GltfAsset asset;
  private final ModelInstance instance;
  private boolean closed;

  GltfInstance(GltfAsset asset, ModelInstance instance) {
    this.asset = asset;
    this.instance = instance;
  }

  public ModelInstance modelInstance() {
    asset.checkThread();
    if (closed) throw new IllegalStateException("glTF instance is closed");
    return instance;
  }

  /**
   * The returned component borrows this handle; close this handle after the component is removed.
   */
  public ModelRenderer createRenderer() {
    return new ModelRenderer(modelInstance(), PbrShaderProvider.class);
  }

  @Override
  public void close() {
    asset.checkThread();
    if (closed) return;
    closed = true;
    asset.releaseInstance();
  }
}
