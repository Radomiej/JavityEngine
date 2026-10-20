package org.javity.render.foliage;

import com.badlogic.gdx.graphics.Texture;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable normalized atlas layout. The caller retains ownership of the borrowed texture. */
public final class FoliageAtlas {
  /** Unflipped normalized UV rectangle; v is the image's top edge. */
  public record Region(float u, float v, float u2, float v2) {
    public Region {
      if (!Float.isFinite(u)
          || !Float.isFinite(v)
          || !Float.isFinite(u2)
          || !Float.isFinite(v2)
          || u < 0
          || v < 0
          || u2 > 1
          || v2 > 1
          || u >= u2
          || v >= v2) {
        throw new IllegalArgumentException(
            "Foliage UV region must be finite, ordered and within [0,1]");
      }
    }
  }

  private final Texture texture;
  private final List<Region> grassRegions;
  private final List<Region> leafRegions;

  public FoliageAtlas(Texture texture, List<Region> grassRegions, List<Region> leafRegions) {
    this.texture = Objects.requireNonNull(texture, "texture");
    this.grassRegions = copyRegions(grassRegions);
    this.leafRegions = copyRegions(leafRegions);
  }

  private static List<Region> copyRegions(List<Region> regions) {
    Objects.requireNonNull(regions, "regions");
    if (regions.isEmpty())
      throw new IllegalArgumentException("Foliage atlas regions must not be empty");
    return List.copyOf(regions);
  }

  /**
   * Layout of the shipped atlas: grass left, leaves right, four top-to-bottom variants each. Two
   * pixels of inset keep filtering within each padded cell. Does not allocate GL resources.
   */
  public static FoliageAtlas bundled(Texture texture) {
    Objects.requireNonNull(texture, "texture");
    int width = texture.getWidth(), height = texture.getHeight();
    if (width <= 8 || height <= 16) {
      throw new IllegalArgumentException("Bundled foliage atlas must exceed 8 by 16 pixels");
    }
    float insetU = 2f / width, insetV = 2f / height;
    List<Region> grass = new ArrayList<>(4), leaves = new ArrayList<>(4);
    for (int row = 0; row < 4; row++) {
      float v = row * .25f + insetV, v2 = (row + 1) * .25f - insetV;
      grass.add(new Region(insetU, v, .5f - insetU, v2));
      leaves.add(new Region(.5f + insetU, v, 1 - insetU, v2));
    }
    return new FoliageAtlas(texture, grass, leaves);
  }

  public Texture texture() {
    return texture;
  }

  public List<Region> grassRegions() {
    return grassRegions;
  }

  public List<Region> leafRegions() {
    return leafRegions;
  }
}
