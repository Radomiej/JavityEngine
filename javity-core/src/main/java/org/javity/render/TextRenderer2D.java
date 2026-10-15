package org.javity.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import java.util.Objects;

/** Text anchored at left baseline in local units. Font is caller/asset-owned. */
public final class TextRenderer2D extends Render2D {
  public final BitmapFont font;
  public String text;
  public final Color color = Color.WHITE.cpy();

  public TextRenderer2D(BitmapFont font, String text) {
    this.font = Objects.requireNonNull(font);
    this.text = Objects.requireNonNull(text);
  }
}
