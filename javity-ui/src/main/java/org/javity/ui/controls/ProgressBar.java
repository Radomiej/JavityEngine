package org.javity.ui.controls;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import org.javity.effects.Ease;
import org.javity.ui.flex.UiTheme;
import org.javity.ui.flex.UtilityStyles;

/** A layout-owned Scene2D progress bar. Its model advances with the Stage's supplied delta. */
public final class ProgressBar extends Widget implements AutoCloseable {
  private static final Color DEFAULT_TRACK = new Color(.15f, .18f, .22f, 1);
  private static final Color DEFAULT_FILL = UtilityStyles.color("cyan-400");
  private static final Color GAIN_FILL = UtilityStyles.color("emerald-500");
  private static final Color DAMAGE_FILL = UtilityStyles.color("amber-400");

  private final UiTheme theme;
  private final ProgressModel model = new ProgressModel();
  private String classes = "";
  private UtilityStyles.Visual visual;
  private Color track = new Color(DEFAULT_TRACK);
  private Color fill = new Color(DEFAULT_FILL);
  private boolean closed;

  public ProgressBar(UiTheme theme) {
    this(theme, "");
  }

  public ProgressBar(UiTheme theme, String classes) {
    this.theme = java.util.Objects.requireNonNull(theme, "theme");
    setTouchable(Touchable.disabled);
    setStyleClasses(classes);
  }

  public ProgressBar setRange(float min, float max) {
    model.setRange(min, max);
    return this;
  }

  public ProgressBar setValue(float value) {
    model.setValue(value);
    return this;
  }

  public float getValue() {
    return model.getValue();
  }

  public float getDisplayedValue() {
    return model.getDisplayedValue();
  }

  public float getDelayedIncrease() {
    return model.getDelayedIncrease();
  }

  public float getDelayedDecrease() {
    return model.getDelayedDecrease();
  }

  public boolean isAnimating() {
    return model.isAnimating();
  }

  public ProgressBar setDuration(float seconds) {
    model.setDuration(seconds);
    return this;
  }

  public ProgressBar setDelay(float seconds) {
    model.setDelay(seconds);
    return this;
  }

  public ProgressBar setEase(Ease ease) {
    model.setEase(ease);
    return this;
  }

  public ProgressBar setOnComplete(Runnable callback) {
    model.setOnComplete(callback);
    return this;
  }

  public void finishAnimation() {
    model.finishAnimation();
  }

  public void setStyleClasses(String classes) {
    String next = classes == null ? "" : classes;
    if (this.classes.equals(next) && visual != null) return;
    visual = theme.visualFor(next);
    track = new Color(visual.background() == null ? DEFAULT_TRACK : visual.background());
    fill = new Color(hasExplicitTextColor(next) ? visual.text() : DEFAULT_FILL);
    this.classes = next;
    invalidateHierarchy();
  }

  @Override
  public float getPrefWidth() {
    return 120;
  }

  @Override
  public float getPrefHeight() {
    return 16;
  }

  @Override
  public void act(float delta) {
    super.act(delta);
    if (!closed) model.act(delta);
  }

  @Override
  public void draw(Batch batch, float parentAlpha) {
    if (closed) return;
    float fraction = ratio(model.getDisplayedValue());
    float delayed =
        ratio(model.isIncreasing() ? model.getDelayedIncrease() : model.getDelayedDecrease());
    float saved = batch.getPackedColor();
    try {
      batch.setColor(getColor().r, getColor().g, getColor().b, getColor().a * parentAlpha);
      theme.drawRectangle(
          batch, getX(), getY(), getWidth(), getHeight(), visual.withBackground(track));
      float border = visual.borderFor(getWidth(), getHeight());
      float innerX = getX() + border;
      float innerY = getY() + border;
      float innerWidth = Math.max(0, getWidth() - 2 * border);
      float innerHeight = Math.max(0, getHeight() - 2 * border);
      UtilityStyles.Visual innerStyle = withoutBorder(visual);
      if (delayed > fraction) {
        Color trail = model.isIncreasing() ? GAIN_FILL : DAMAGE_FILL;
        theme.drawRectangle(
            batch,
            innerX,
            innerY,
            innerWidth * delayed,
            innerHeight,
            innerStyle.withBackground(trail));
      }
      if (fraction > 0) {
        theme.drawRectangle(
            batch,
            innerX,
            innerY,
            innerWidth * fraction,
            innerHeight,
            innerStyle.withBackground(fill));
      }
    } finally {
      batch.setPackedColor(saved);
    }
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    model.close();
    remove();
  }

  @Override
  public boolean remove() {
    if (!closed) {
      closed = true;
      model.close();
    }
    return super.remove();
  }

  private float ratio(float value) {
    double range = (double) model.getMax() - model.getMin();
    if (!(range > 0) || !Double.isFinite(range)) return 0;
    return (float) Math.max(0, Math.min(1, ((double) value - model.getMin()) / range));
  }

  private static boolean hasExplicitTextColor(String classes) {
    for (String token : classes.split("\\s+")) {
      if (token.startsWith("text-")
          && !token.matches("text-(sm|base|lg|xl|2xl)")
          && (token.startsWith("text-[#")
              || token.matches(
                  "text-(white|black|slate-[0-9]+|cyan-[0-9]+|blue-[0-9]+|red-[0-9]+|amber-[0-9]+|emerald-[0-9]+)")))
        return true;
    }
    return false;
  }

  private static UtilityStyles.Visual withoutBorder(UtilityStyles.Visual style) {
    return new UtilityStyles.Visual(
        style.background(),
        style.text(),
        style.textScale(),
        style.fontSize(),
        style.lineHeight(),
        style.radius(),
        0,
        style.borderColor());
  }
}
