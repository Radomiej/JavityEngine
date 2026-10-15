package org.javity.ui.controls;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import java.util.function.Consumer;
import org.javity.ui.flex.UiTheme;
import org.javity.ui.flex.UtilityStyles;

/** A compact horizontal native range control. The caller owns controlled state. */
public final class Slider extends Actor {
  private final UiTheme theme;
  private String classes;
  private float min;
  private float max = 1;
  private float step = .1f;
  private float value;
  private boolean disabled;
  private Consumer<Float> onChange;
  private int pressedPointer = -1;
  private boolean hovered;

  public Slider(UiTheme theme, String classes) {
    this.theme = java.util.Objects.requireNonNull(theme);
    this.classes = classes == null ? "" : classes;
    setTouchable(Touchable.enabled);
    addListener(
        new InputListener() {
          private void update(float x) {
            if (getWidth() <= 0) return;
            double ratio = Math.max(0d, Math.min(1d, (double) x / getWidth()));
            setValueFromUser((double) min + ratio * ((double) max - min));
          }

          @Override
          public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
            if (disabled) return false;
            pressedPointer = pointer;
            com.badlogic.gdx.scenes.scene2d.Stage stage = getStage();
            if (stage != null) stage.setKeyboardFocus(Slider.this);
            if (disabled || getStage() != stage || getTouchable() != Touchable.enabled) {
              pressedPointer = -1;
              return false;
            }
            update(x);
            return true;
          }

          @Override
          public void touchDragged(InputEvent event, float x, float y, int pointer) {
            if (!disabled && pointer == pressedPointer && !event.isTouchFocusCancel()) update(x);
          }

          @Override
          public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
            if (pointer == pressedPointer) pressedPointer = -1;
          }

          @Override
          public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
            hovered = true;
          }

          @Override
          public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
            hovered = false;
          }
        });
  }

  public float getMin() {
    return min;
  }

  public float getMax() {
    return max;
  }

  public float getStep() {
    return step;
  }

  public float getValue() {
    return value;
  }

  public boolean isDisabled() {
    return disabled;
  }

  public void setRange(float min, float max) {
    if (!Float.isFinite(min) || !Float.isFinite(max) || max <= min)
      throw new IllegalArgumentException("Slider requires finite min < max");
    this.min = min;
    this.max = max;
    value = clamp(value);
  }

  public void setStep(float step) {
    if (!Float.isFinite(step) || step <= 0)
      throw new IllegalArgumentException("Slider step must be finite and positive");
    this.step = step;
    value = quantize(value);
  }

  public void setValue(float value) {
    if (!Float.isFinite(value)) throw new IllegalArgumentException("Slider value must be finite");
    this.value = quantize(value);
  }

  public void setOnChange(Consumer<Float> onChange) {
    this.onChange = onChange;
  }

  public void setDisabled(boolean disabled) {
    this.disabled = disabled;
    setTouchable(disabled ? Touchable.disabled : Touchable.enabled);
  }

  public void setStyleClasses(String classes) {
    this.classes = classes == null ? "" : classes;
  }

  public void stepBy(float direction) {
    if (!disabled) setValueFromUser((double) value + (double) direction * step);
  }

  private float clamp(float value) {
    return Math.max(min, Math.min(max, value));
  }

  private float quantize(float value) {
    double ticks = Math.rint(((double) clamp(value) - min) / step);
    return clamp((float) Math.max(min, Math.min(max, min + ticks * step)));
  }

  private void setValueFromUser(double value) {
    float next = quantize((float) value);
    if (Float.compare(next, this.value) == 0) return;
    Consumer<Float> handler = onChange;
    if (handler != null) handler.accept(next);
  }

  @Override
  public void draw(Batch batch, float parentAlpha) {
    float h = getHeight();
    float railHeight = Math.min(6f, Math.max(3f, h * .22f));
    float knob = Math.min(18f, Math.max(10f, h * .72f));
    float usable = Math.max(0, getWidth() - knob);
    double ratio = ((double) value - min) / ((double) max - min);
    float centerY = getY() + h / 2f;
    String state =
        disabled
            ? "disabled"
            : pressedPointer >= 0
                ? "active"
                : getStage() != null && getStage().getKeyboardFocus() == this
                    ? "focus"
                    : hovered ? "hover" : "";
    UtilityStyles.Visual visual = theme.visualFor(classes, state);
    Color rail = disabled ? new Color(.68f, .71f, .75f, .7f) : new Color(.34f, .4f, .49f, 1f);
    Color fill =
        visual.background() == null ? new Color(.12f, .55f, .93f, 1f) : visual.background().cpy();
    rail.a *= visual.text().a;
    fill.a *= visual.text().a;
    Color previous = batch.getColor().cpy();
    Color actor = getColor();
    batch.setColor(
        previous.r * actor.r,
        previous.g * actor.g,
        previous.b * actor.b,
        previous.a * actor.a * parentAlpha);
    try {
      theme.drawRectangle(
          batch,
          getX() + knob / 2f,
          centerY - railHeight / 2f,
          Math.max(0, getWidth() - knob),
          railHeight,
          new UtilityStyles.Visual(
              rail,
              visual.text(),
              1,
              16,
              24,
              Float.isFinite(visual.radius()) ? visual.radius() : railHeight / 2f,
              visual.borderWidth(),
              visual.borderColor()));
      theme.drawRectangle(
          batch,
          getX() + knob / 2f,
          centerY - railHeight / 2f,
          (float) (usable * ratio),
          railHeight,
          new UtilityStyles.Visual(
              fill,
              visual.text(),
              1,
              16,
              24,
              Float.isFinite(visual.radius()) ? visual.radius() : railHeight / 2f,
              visual.borderWidth(),
              visual.borderColor()));
      Color knobColor = disabled ? new Color(.76f, .79f, .82f, 1f) : Color.WHITE.cpy();
      knobColor.a *= visual.text().a;
      boolean explicitSquare =
          java.util.Arrays.asList(classes.split("\\s+")).contains("rounded-none");
      float knobRadius =
          explicitSquare
              ? 0
              : visual.radius() > 0 || Float.isInfinite(visual.radius())
                  ? visual.radius()
                  : knob / 2f;
      theme.drawRectangle(
          batch,
          getX() + (float) (usable * ratio),
          centerY - knob / 2f,
          knob,
          knob,
          new UtilityStyles.Visual(
              knobColor,
              visual.text(),
              1,
              16,
              24,
              knobRadius,
              visual.borderWidth(),
              visual.borderColor()));
    } finally {
      batch.setColor(previous);
    }
  }
}
