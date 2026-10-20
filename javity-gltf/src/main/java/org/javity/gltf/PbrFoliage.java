package org.javity.gltf;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import java.util.Objects;
import net.mgsx.gltf.scene3d.attributes.PBRColorAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute;
import org.javity.render.foliage.FoliageAtlas;

/** Explicit PBR cutout material for foliage; borrows the atlas and owns no GL resources. */
public final class PbrFoliage {
  private PbrFoliage() {}

  /** Use with {@code patch.material(material(atlas, .5f), PbrShaderProvider.class)}. */
  public static Material material(FoliageAtlas atlas, float cutoff) {
    Objects.requireNonNull(atlas, "atlas");
    if (!Float.isFinite(cutoff) || cutoff < 0 || cutoff > 1)
      throw new IllegalArgumentException("cutoff must be finite and within [0, 1]");
    return new Material(
        PBRTextureAttribute.createBaseColorTexture(atlas.texture()),
        new PBRColorAttribute(PBRColorAttribute.BaseColorFactor, Color.WHITE),
        PBRFloatAttribute.createMetallic(0),
        PBRFloatAttribute.createRoughness(.93f),
        FloatAttribute.createAlphaTest(cutoff),
        new BlendingAttribute(false, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 1),
        new DepthTestAttribute(GL20.GL_LEQUAL, true),
        IntAttribute.createCullFace(GL20.GL_NONE));
  }
}
