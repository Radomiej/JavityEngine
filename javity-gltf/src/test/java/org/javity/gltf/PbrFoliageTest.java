package org.javity.gltf;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import java.util.List;
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute;
import org.javity.render.foliage.FoliageAtlas;
import org.javity.render.foliage.FoliagePatch3D;
import org.junit.Test;

public class PbrFoliageTest {
  // No GL allocations: dimensions/handle are never queried by the explicit descriptor.
  private static final class BorrowedTexture extends Texture {
    BorrowedTexture() {
      super();
    }

    @Override
    public void dispose() {
      throw new AssertionError("borrowed atlas disposed");
    }
  }

  @Test
  public void explicitPbrTextureAndCutoutStateSurviveDefensiveTemplateCopies() {
    Texture texture = new BorrowedTexture();
    var regions = List.of(new FoliageAtlas.Region(0, 0, 1, 1));
    FoliageAtlas atlas = new FoliageAtlas(texture, regions, regions);
    Material material = PbrFoliage.material(atlas, .47f);
    PbrShaderProvider.validateMaterial(material);
    FoliagePatch3D patch = FoliagePatch3D.grass(atlas).material(material, PbrShaderProvider.class);
    material.get(FloatAttribute.class, FloatAttribute.AlphaTest).value = .2f;
    Material copy = patch.materialTemplate();
    assertEquals(.47f, copy.get(FloatAttribute.class, FloatAttribute.AlphaTest).value, 0);
    assertSame(
        texture,
        copy.get(PBRTextureAttribute.class, PBRTextureAttribute.BaseColorTexture)
            .textureDescription
            .texture);
    assertTrue(copy.get(TextureAttribute.Diffuse) instanceof PBRTextureAttribute);
    assertFalse(copy.get(BlendingAttribute.class, BlendingAttribute.Type).blended);
    assertTrue(copy.get(DepthTestAttribute.class, DepthTestAttribute.Type).depthMask);
    assertEquals(0, copy.get(IntAttribute.class, IntAttribute.CullFace).value);
    assertThrows(IllegalArgumentException.class, () -> PbrFoliage.material(atlas, Float.NaN));
    assertThrows(IllegalArgumentException.class, () -> PbrFoliage.material(atlas, 1.01f));
  }
}
