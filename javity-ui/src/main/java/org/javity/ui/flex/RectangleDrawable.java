package org.javity.ui.flex;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;

final class RectangleDrawable extends BaseDrawable {
  private final UiTheme theme;
  private final UtilityStyles.Visual style;

  RectangleDrawable(UiTheme theme, UtilityStyles.Visual style) {
    this.theme = theme;
    this.style = style;
    setLeftWidth(style.borderWidth());
    setRightWidth(style.borderWidth());
    setTopHeight(style.borderWidth());
    setBottomHeight(style.borderWidth());
  }

  @Override
  public void draw(Batch batch, float x, float y, float width, float height) {
    theme.drawRectangle(batch, x, y, width, height, style);
  }
}
