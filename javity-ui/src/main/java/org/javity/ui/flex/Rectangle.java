package org.javity.ui.flex;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import java.util.Objects;

/**
 * UI rectangle. Dimensions are set by its parent Flex or setSize; theme owns rendering resources.
 */
public final class Rectangle extends Widget {
  private final UiTheme theme;
  private final UtilityStyles.Visual style;

  public Rectangle(UiTheme theme, String classes) {
    this.theme = Objects.requireNonNull(theme);
    style = theme.visual(classes);
  }

  @Override
  public float getPrefWidth() {
    return 32;
  }

  @Override
  public float getPrefHeight() {
    return 32;
  }

  @Override
  public void draw(Batch batch, float parentAlpha) {
    validate();
    float saved = batch.getPackedColor();
    batch.setColor(getColor().r, getColor().g, getColor().b, getColor().a * parentAlpha);
    try {
      theme.drawRectangle(batch, getX(), getY(), getWidth(), getHeight(), style);
    } finally {
      batch.setPackedColor(saved);
    }
  }
}
