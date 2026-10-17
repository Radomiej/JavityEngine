package org.javity.ui.flex;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import java.util.Objects;

/** A keyed UI image that borrows a region registered with its theme. */
public final class UiImage extends Widget {
  private final UiTheme theme;
  private String source;
  private TextureRegion region;
  private ImageFit fit;
  private String classes;
  private UtilityStyles.Visual visual;
  private UtilityStyles.Insets padding;

  public UiImage(UiTheme theme, String source, TextureRegion region, ImageFit fit, String classes) {
    this.theme = Objects.requireNonNull(theme, "theme");
    updateImage(source, region, fit, classes);
  }

  /** Applies a source already resolved by UiDocument's validation pass. */
  public void updateImage(String source, TextureRegion region, ImageFit fit, String classes) {
    if (source == null || source.isBlank())
      throw new IllegalArgumentException("Image source is required");
    Objects.requireNonNull(region, "region");
    Objects.requireNonNull(fit, "fit");
    String replacementClasses = classes == null ? "" : classes;
    if (source.equals(this.source)
        && region == this.region
        && fit == this.fit
        && replacementClasses.equals(this.classes)) return;
    this.source = source;
    this.region = region;
    this.fit = fit;
    this.classes = replacementClasses;
    this.visual = theme.visualFor(this.classes);
    this.padding = UtilityStyles.padding(this.classes);
    invalidateHierarchy();
  }

  public String source() {
    return source;
  }

  public TextureRegion borrowedRegion() {
    return region;
  }

  public ImageFit fit() {
    return fit;
  }

  @Override
  public float getPrefWidth() {
    return region.getRegionWidth() + padding.horizontal() + visual.borderWidth() * 2;
  }

  @Override
  public float getPrefHeight() {
    return region.getRegionHeight() + padding.vertical() + visual.borderWidth() * 2;
  }

  @Override
  public void draw(Batch batch, float parentAlpha) {
    validate();
    float actorWidth = getWidth(), actorHeight = getHeight();
    if (actorWidth <= 0 || actorHeight <= 0) return;
    float border = visual.borderFor(actorWidth, actorHeight);
    float left = border + padding.left();
    float right = border + padding.right();
    float bottom = border + padding.bottom();
    float top = border + padding.top();
    float contentWidth = actorWidth - left - right;
    float contentHeight = actorHeight - bottom - top;
    float clipWidth = actorWidth - border * 2;
    float clipHeight = actorHeight - border * 2;
    if (contentWidth <= 0 || contentHeight <= 0) {
      if (visual.background() == null && border <= 0) return;
      float saved = batch.getPackedColor();
      batch.setColor(getColor().r, getColor().g, getColor().b, getColor().a * parentAlpha);
      try {
        theme.drawRectangle(batch, getX(), getY(), actorWidth, actorHeight, visual);
      } finally {
        batch.setPackedColor(saved);
      }
      return;
    }

    float sourceWidth = region.getRegionWidth();
    float sourceHeight = region.getRegionHeight();
    float drawX = left, drawY = bottom;
    float drawWidth = contentWidth, drawHeight = contentHeight;
    float u = region.getU(), v = region.getV(), u2 = region.getU2(), v2 = region.getV2();
    float sourceU = u2 - u, sourceV = v2 - v;
    if (contentWidth > 0 && contentHeight > 0 && fit == ImageFit.CONTAIN) {
      float scale = Math.min(contentWidth / sourceWidth, contentHeight / sourceHeight);
      drawWidth = sourceWidth * scale;
      drawHeight = sourceHeight * scale;
      drawX += (contentWidth - drawWidth) * .5f;
      drawY += (contentHeight - drawHeight) * .5f;
    } else if (contentWidth > 0 && contentHeight > 0 && fit == ImageFit.COVER) {
      float sourceAspect = sourceWidth / sourceHeight;
      float boxAspect = contentWidth / contentHeight;
      if (sourceAspect > boxAspect) {
        float crop = boxAspect / sourceAspect;
        float inset = (1 - crop) * .5f;
        u += sourceU * inset;
        u2 -= sourceU * inset;
      } else if (sourceAspect < boxAspect) {
        float crop = sourceAspect / boxAspect;
        float inset = (1 - crop) * .5f;
        v += sourceV * inset;
        v2 -= sourceV * inset;
      }
    }
    // LibGDX uploads Pixmap row zero at the GL texture's lower edge; CSS image
    // coordinates are top-down. Reverse the sampling endpoints without changing
    // the borrowed TextureRegion or its caller-authored flip state.
    float swap = v;
    v = v2;
    v2 = swap;
    float saved = batch.getPackedColor();
    batch.setColor(getColor().r, getColor().g, getColor().b, getColor().a * parentAlpha);
    try {
      if (contentWidth > 0 && contentHeight > 0) {
        float radius = Math.max(0, visual.radiusFor(actorWidth, actorHeight) - border);
        theme.drawImage(
            batch,
            region,
            getX(),
            getY(),
            actorWidth,
            actorHeight,
            visual,
            border / actorWidth,
            border / actorHeight,
            clipWidth / actorWidth,
            clipHeight / actorHeight,
            radius,
            drawX / actorWidth,
            drawY / actorHeight,
            drawWidth / actorWidth,
            drawHeight / actorHeight,
            u,
            v,
            u2,
            v2);
      }
    } finally {
      batch.setPackedColor(saved);
    }
  }
}
