package org.javity.render.foliage;

import static org.junit.Assert.*;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class FoliagePatch3DTest {
  static final class BorrowedTexture extends Texture {
    boolean disposed;

    BorrowedTexture() {
      super();
    }

    @Override
    public int getWidth() {
      return 1024;
    }

    @Override
    public int getHeight() {
      return 1024;
    }

    @Override
    public void dispose() {
      disposed = true;
    }
  }

  static FoliageAtlas atlas() {
    return FoliageAtlas.bundled(new BorrowedTexture());
  }

  @Test
  public void constructorsAndSettingsNeedNoGlAndPresetsAreUseful() {
    assertNull(Gdx.gl);
    FoliagePatch3D grass = FoliagePatch3D.grass(atlas());
    assertEquals(256, grass.count());
    assertEquals(8, grass.width(), 0);
    assertEquals(8, grass.depth(), 0);
    assertEquals(.8f, grass.minScale(), 0);
    assertEquals(1.2f, grass.maxScale(), 0);
    assertEquals(.5f, grass.cutoff(), 0);
    assertEquals(1, grass.seed());
    FoliagePatch3D bushes = FoliagePatch3D.bushes(atlas());
    assertEquals(12, bushes.count());
    assertEquals(.85f, bushes.minScale(), 0);
    assertEquals(1.15f, bushes.maxScale(), 0);
    assertSame(grass, grass.area(2, 3).count(4).seed(9).scaleRange(.5f, 2).cutoff(.7f));
    assertEquals(5, grass.revision());
    grass.area(2, 3).count(4).seed(9).scaleRange(.5f, 2).cutoff(.7f);
    assertEquals(5, grass.revision());
  }

  @Test
  public void invalidSettingsLeaveThePreviousRevisionAndValuesUntouched() {
    FoliagePatch3D patch = FoliagePatch3D.grass(atlas());
    assertThrows(IllegalArgumentException.class, () -> patch.area(2, Float.NaN));
    assertThrows(IllegalArgumentException.class, () -> patch.area(0, 2));
    assertThrows(IllegalArgumentException.class, () -> patch.count(-1));
    assertThrows(IllegalArgumentException.class, () -> patch.count(4097));
    assertThrows(IllegalArgumentException.class, () -> patch.scaleRange(1, .5f));
    assertThrows(IllegalArgumentException.class, () -> patch.scaleRange(0, 1));
    assertThrows(
        IllegalArgumentException.class, () -> patch.scaleRange(1, Float.POSITIVE_INFINITY));
    assertThrows(IllegalArgumentException.class, () -> patch.cutoff(-.1f));
    assertThrows(IllegalArgumentException.class, () -> patch.cutoff(Float.NaN));
    assertEquals(0, patch.revision());
    assertEquals(8, patch.width(), 0);
    assertEquals(256, patch.count());
    patch.count(0).cutoff(0).cutoff(1);
    assertEquals(0, patch.count());
  }

  @Test
  public void snapshotsAndMaterialTemplatesCopyMutableStateButBorrowTextures() {
    BorrowedTexture texture = new BorrowedTexture();
    FoliageAtlas atlas = FoliageAtlas.bundled(texture);
    Material material = new Material(ColorAttribute.createDiffuse(Color.GREEN));
    FoliagePatch3D patch = FoliagePatch3D.grass(atlas).material(material);
    FoliagePatch3D.Snapshot snapshot = patch.snapshot();
    material.get(ColorAttribute.class, ColorAttribute.Diffuse).color.set(Color.RED);
    patch
        .materialTemplate()
        .get(ColorAttribute.class, ColorAttribute.Diffuse)
        .color
        .set(Color.BLUE);
    assertEquals(
        Color.GREEN,
        snapshot.materialTemplate().get(ColorAttribute.class, ColorAttribute.Diffuse).color);
    snapshot.materialTemplate().clear();
    assertEquals(1, snapshot.materialTemplate().size());
    patch.area(1, 2).material(null);
    assertEquals(8, snapshot.width(), 0);
    assertNull(patch.materialTemplate());
    assertSame(texture, snapshot.atlas().texture());
    patch.onDestroy();
    assertFalse(texture.disposed);
  }

  @Test
  public void materialProviderRequirementIsCopiedAndClearedExplicitly() {
    FoliagePatch3D patch = FoliagePatch3D.grass(atlas());
    Material template = new Material(ColorAttribute.createDiffuse(Color.GREEN));
    patch.material(template, org.javity.render.RecoverableShaderProvider.class);
    FoliagePatch3D.Snapshot snapshot = patch.snapshot();
    assertEquals(org.javity.render.RecoverableShaderProvider.class, snapshot.requiredProvider());
    patch.material(template);
    assertNull(patch.requiredProvider());
    assertEquals(org.javity.render.RecoverableShaderProvider.class, snapshot.requiredProvider());
    assertThrows(
        IllegalArgumentException.class,
        () -> patch.material(null, org.javity.render.RecoverableShaderProvider.class));
    assertEquals(2, patch.revision());
    assertThrows(IllegalArgumentException.class, () -> patch.area(Float.MAX_VALUE, 1));
    assertThrows(IllegalArgumentException.class, () -> patch.scaleRange(1, Float.MAX_VALUE));
    assertEquals(2, patch.revision());
  }

  @Test
  public void atlasCopiesRegionsAndRejectsInvalidUvWithoutTouchingBorrowedTexture() {
    BorrowedTexture texture = new BorrowedTexture();
    List<FoliageAtlas.Region> source =
        new ArrayList<>(List.of(new FoliageAtlas.Region(.1f, .2f, .3f, .4f)));
    FoliageAtlas atlas = new FoliageAtlas(texture, source, source);
    source.clear();
    assertEquals(1, atlas.grassRegions().size());
    assertEquals(.1f, atlas.grassRegions().get(0).u(), 0);
    assertThrows(UnsupportedOperationException.class, () -> atlas.leafRegions().clear());
    assertThrows(IllegalArgumentException.class, () -> new FoliageAtlas.Region(0, 0, Float.NaN, 1));
    assertThrows(IllegalArgumentException.class, () -> new FoliageAtlas.Region(.5f, 0, .2f, 1));
    assertThrows(IllegalArgumentException.class, () -> new FoliageAtlas.Region(0, 0, 2, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new FoliageAtlas(texture, List.of(), atlas.leafRegions()));
    assertFalse(texture.disposed);
    FoliageAtlas bundled = FoliageAtlas.bundled(texture);
    assertEquals(4, bundled.grassRegions().size());
    assertEquals(4, bundled.leafRegions().size());
    assertEquals(2f / 1024, bundled.grassRegions().get(0).u(), 0);
    assertEquals(.5f + 2f / 1024, bundled.leafRegions().get(0).u(), 0);
    assertEquals(.75f + 2f / 1024, bundled.grassRegions().get(3).v(), 0);
  }
}
