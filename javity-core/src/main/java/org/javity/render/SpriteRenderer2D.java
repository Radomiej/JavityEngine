package org.javity.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.Objects;

/** Texture ownership stays with the asset manager/caller. */
public final class SpriteRenderer2D extends Render2D {
  public TextureRegion region;
  public final Color tint = Color.WHITE.cpy();
  public float width, height;
  public boolean flipX, flipY;

  public SpriteRenderer2D(TextureRegion region) {
    this.region = Objects.requireNonNull(region);
    width = region.getRegionWidth();
    height = region.getRegionHeight();
  }
}
