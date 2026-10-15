package org.javity.input;

import com.badlogic.gdx.math.Vector2;
import java.util.Objects;
import java.util.function.Consumer;
import org.javity.core.Component;
import org.javity.presentation.Presentation2D;

/** Centered rectangular hit area, independent of visual texture. */
public final class Pickable2D extends Component {
  public int layer, order;
  private final float width, height;
  private final Consumer<Vector2> onClick;

  public Pickable2D(float width, float height, Consumer<Vector2> onClick) {
    if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0)
      throw new IllegalArgumentException("Positive hit size required");
    this.width = width;
    this.height = height;
    this.onClick = Objects.requireNonNull(onClick);
  }

  public boolean contains(Vector2 world) {
    var t = getTransform();
    Presentation2D presentation = getGameObject().getComponent(Presentation2D.class);
    float scaleX = presentation == null ? t.scale.x : presentation.presentedScaleX();
    float scaleY = presentation == null ? t.scale.y : presentation.presentedScaleY();
    if (scaleX == 0 || scaleY == 0) return false;
    float centerX = presentation == null ? t.position.x + t.visualOffset.x : presentation.renderX();
    float centerY = presentation == null ? t.position.y + t.visualOffset.y : presentation.renderY();
    float rotation = presentation == null ? t.rotation : presentation.presentedRotation();
    float dx = world.x - centerX, dy = world.y - centerY;
    float cos = (float) Math.cos(rotation), sin = (float) Math.sin(rotation);
    float localX = (dx * cos + dy * sin) / scaleX;
    float localY = (-dx * sin + dy * cos) / scaleY;
    return Math.abs(localX) <= width / 2 && Math.abs(localY) <= height / 2;
  }

  void click(Vector2 world) {
    onClick.accept(world.cpy());
  }
}
