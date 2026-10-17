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

/** A small native switch track and knob. The caller owns controlled state. */
public final class Toggle extends Actor {
  private final UiTheme theme;
  private String classes;
  private boolean checked;
  private boolean disabled;
  private Consumer<Boolean> onChange;
  private int pressedPointer = -1;
  private boolean hovered;

  public Toggle(UiTheme theme, String classes) {
    this.theme = java.util.Objects.requireNonNull(theme);
    this.classes = classes == null ? "" : classes;
    setTouchable(Touchable.enabled);
    addListener(
        new InputListener() {
          @Override
          public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
            if (disabled) return false;
            pressedPointer = pointer;
            com.badlogic.gdx.scenes.scene2d.Stage stage = getStage();
            if (stage != null) stage.setKeyboardFocus(Toggle.this);
            if (disabled || getStage() != stage || getTouchable() != Touchable.enabled) {
              pressedPointer = -1;
              return false;
            }
            return true;
          }

          @Override
          public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
            boolean activate =
                pointer == pressedPointer
                    && !event.isTouchFocusCancel()
                    && x >= 0
                    && y >= 0
                    && x <= getWidth()
                    && y <= getHeight();
            pressedPointer = -1;
            if (activate) activate();
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

  public boolean isChecked() {
    return checked;
  }

  public void setChecked(boolean checked) {
    this.checked = checked;
  }

  public void setOnChange(Consumer<Boolean> onChange) {
    this.onChange = onChange;
  }

  public void setDisabled(boolean disabled) {
    this.disabled = disabled;
    setTouchable(disabled ? Touchable.disabled : Touchable.enabled);
  }

  public boolean isDisabled() {
    return disabled;
  }

  public void setStyleClasses(String classes) {
    this.classes = classes == null ? "" : classes;
  }

  public void activate() {
    if (!disabled && onChange != null) onChange.accept(!checked);
  }

  @Override
  public void draw(Batch batch, float parentAlpha) {
    float height = getHeight();
    float trackHeight = Math.min(height * .62f, 24f);
    float trackWidth = Math.min(getWidth(), trackHeight * 1.75f);
    float x = getX() + (getWidth() - trackWidth) / 2f;
    float y = getY() + (height - trackHeight) / 2f;
    String state =
        disabled
            ? "disabled"
            : pressedPointer >= 0
                ? "active"
                : getStage() != null && getStage().getKeyboardFocus() == this
                    ? "focus"
                    : hovered ? "hover" : "";
    UtilityStyles.Visual visual = theme.visualFor(classes, state);
    Color track =
        visual.background() == null
            ? checked ? new Color(.12f, .55f, .93f, 1f) : new Color(.42f, .47f, .55f, 1f)
            : visual.background().cpy();
    if (disabled && visual.background() == null) track.a = .48f;
    Color previous = batch.getColor().cpy();
    Color actor = getColor();
    track.a *= visual.text().a;
    batch.setColor(
        previous.r * actor.r,
        previous.g * actor.g,
        previous.b * actor.b,
        previous.a * actor.a * parentAlpha);
    try {
      float border = visual.borderWidth();
      boolean explicitSquare =
          java.util.Arrays.asList(classes.split("\\s+")).contains("rounded-none");
      float radius =
          visual.radius() > 0 || Float.isInfinite(visual.radius())
              ? visual.radius()
              : explicitSquare ? 0 : trackHeight / 2f;
      theme.drawRectangle(
          batch,
          x,
          y,
          trackWidth,
          trackHeight,
          new UtilityStyles.Visual(
              track,
              visual.text(),
              visual.textScale(),
              visual.fontSize(),
              visual.lineHeight(),
              radius,
              border,
              visual.borderColor()));
      float knob = Math.max(2f, trackHeight - 4f);
      float knobX = checked ? x + trackWidth - knob - 2f : x + 2f;
      Color knobColor = disabled ? new Color(.9f, .91f, .93f, .72f) : Color.WHITE.cpy();
      knobColor.a *= visual.text().a;
      float knobRadius =
          explicitSquare
              ? 0
              : visual.radius() > 0 || Float.isInfinite(visual.radius())
                  ? visual.radius()
                  : knob / 2f;
      theme.drawRectangle(
          batch,
          knobX,
          y + 2f,
          knob,
          knob,
          new UtilityStyles.Visual(knobColor, visual.text(), 1, 16, 24, knobRadius, 0, knobColor));
    } finally {
      batch.setColor(previous);
    }
  }
}
