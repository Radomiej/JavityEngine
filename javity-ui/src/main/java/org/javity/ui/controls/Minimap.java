package org.javity.ui.controls;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import java.util.Objects;
import org.javity.ui.flex.*;

/** Reusable, non-interactive Scene2D minimap. Borrows theme, model and background texture. */
public final class Minimap extends Widget {
  private final UiTheme theme;
  private final MinimapModel model;
  private final UtilityStyles.Visual background, markerStyle;
  private final Color markerColor = new Color();
  private TextureRegion map;

  public Minimap(UiTheme theme, MinimapModel model) {
    this.theme = Objects.requireNonNull(theme);
    this.model = Objects.requireNonNull(model);
    background = theme.visualFor("bg-[#243d2d]");
    markerStyle = theme.visualFor("bg-white");
    setTouchable(Touchable.disabled);
  }

  /** Region geometry is copied; its texture must outlive the widget. Null clears the background. */
  public void setMap(TextureRegion region) {
    map = region == null ? null : new TextureRegion(region);
  }

  public MinimapModel model() {
    return model;
  }

  @Override
  public float getPrefWidth() {
    return 200;
  }

  @Override
  public float getPrefHeight() {
    return 130;
  }

  @Override
  public void draw(Batch batch, float parentAlpha) {
    if (getWidth() <= 0 || getHeight() <= 0) return;
    float packed = batch.getPackedColor();
    try {
      batch.setColor(getColor().r, getColor().g, getColor().b, getColor().a * parentAlpha);
      theme.drawRectangle(batch, getX(), getY(), getWidth(), getHeight(), background);
      float scale = Math.min(getWidth() / model.width(), getHeight() / model.height());
      float width = model.width() * scale, height = model.height() * scale;
      float x = getX() + (getWidth() - width) / 2, y = getY() + (getHeight() - height) / 2;
      if (map != null) batch.draw(map, x, y, width, height);
      for (var marker : model.markers()) {
        float size = Math.min(marker.size(), Math.min(width, height));
        float px =
            x
                + Math.max(
                    size / 2, Math.min(width - size / 2, model.normalizedX(marker.x()) * width));
        float py =
            y
                + Math.max(
                    size / 2, Math.min(height - size / 2, model.normalizedY(marker.y()) * height));
        Color.rgba8888ToColor(markerColor, marker.rgba());
        theme.drawRectangle(
            batch,
            px - size / 2,
            py - size / 2,
            size,
            size,
            markerStyle.withBackground(markerColor));
      }
    } finally {
      batch.setPackedColor(packed);
    }
  }
}
