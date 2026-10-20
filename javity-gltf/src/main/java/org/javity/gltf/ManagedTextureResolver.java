package org.javity.gltf;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.utils.Array;
import net.mgsx.gltf.data.texture.GLTFSampler;
import net.mgsx.gltf.data.texture.GLTFTexture;
import net.mgsx.gltf.loaders.shared.GLTFTypes;
import net.mgsx.gltf.loaders.shared.texture.ImageResolver;
import net.mgsx.gltf.loaders.shared.texture.TextureResolver;

/**
 * Uploads shared glTF images as managed textures. The SceneAsset retains source Pixmaps until final
 * close, allowing libGDX to reload the texture after context loss without rereading files.
 */
final class ManagedTextureResolver extends TextureResolver {
  @Override
  public void loadTextures(
      Array<GLTFTexture> definitions, Array<GLTFSampler> samplers, ImageResolver images) {
    glTextures = definitions;
    glSamplers = samplers;
    if (definitions == null) return;
    for (GLTFTexture definition : definitions) {
      boolean mipmaps =
          definition.sampler != null && GLTFTypes.isMipMapFilter(samplers.get(definition.sampler));
      var target = mipmaps ? texturesMipmap : texturesSimple;
      if (!target.containsKey(definition.source)) {
        var pixmap = images.get(definition.source);
        var data = new PixmapTextureData(pixmap, pixmap.getFormat(), mipmaps, false, true);
        target.put(definition.source, new Texture(data));
      }
    }
  }
}
