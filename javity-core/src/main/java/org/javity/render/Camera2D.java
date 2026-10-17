package org.javity.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.*;
import org.javity.core.Component;
import org.javity.presentation.Presentation2D;

/**
 * Scene-local orthographic camera. Position/rotation come from Transform2D. Screen conversion uses
 * top-left origin, matching libGDX pointer input.
 */
public final class Camera2D extends Component {
  private int width = 1, height = 1;
  private float zoom = 1, pixelsPerUnit = 1;

  public void resize(int width, int height) {
    if (width <= 0 || height <= 0) throw new IllegalArgumentException("Positive viewport required");
    this.width = width;
    this.height = height;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public float getZoom() {
    return zoom;
  }

  public void setZoom(float zoom) {
    positive(zoom);
    this.zoom = zoom;
  }

  public float getPixelsPerUnit() {
    return pixelsPerUnit;
  }

  public void setPixelsPerUnit(float value) {
    positive(value);
    pixelsPerUnit = value;
  }

  private static void positive(float value) {
    if (!Float.isFinite(value) || value <= 0)
      throw new IllegalArgumentException("Finite positive value required");
  }

  public Vector2 screenToWorld(float x, float y) {
    float rotation = presentedRotation();
    Vector2 offset =
        new Vector2(
            (x - width / 2f) * zoom / pixelsPerUnit, (height / 2f - y) * zoom / pixelsPerUnit);
    return offset.rotateRad(rotation).add(presentedX(), presentedY());
  }

  public Vector2 worldToScreen(float x, float y) {
    float rotation = presentedRotation();
    Vector2 offset =
        new Vector2(x, y)
            .sub(presentedX(), presentedY())
            .rotateRad(-rotation)
            .scl(pixelsPerUnit / zoom);
    return offset.set(width / 2f + offset.x, height / 2f - offset.y);
  }

  public Vector2 directionFromScreen(float originX, float originY, float screenX, float screenY) {
    return screenToWorld(screenX, screenY).sub(originX, originY).nor();
  }

  /** Renderer applies these settings to its existing native camera. */
  public void applyTo(OrthographicCamera camera) {
    camera.viewportWidth = width / pixelsPerUnit;
    camera.viewportHeight = height / pixelsPerUnit;
    camera.zoom = zoom;
    camera.position.set(presentedX(), presentedY(), 0);
    camera.direction.set(0, 0, -1);
    camera.up.set(0, 1, 0).rotateRad(Vector3.Z, presentedRotation());
    camera.update();
  }

  private float presentedX() {
    Presentation2D presentation = getGameObject().getComponent(Presentation2D.class);
    return presentation == null ? getTransform().position.x : presentation.presentedX();
  }

  private float presentedY() {
    Presentation2D presentation = getGameObject().getComponent(Presentation2D.class);
    return presentation == null ? getTransform().position.y : presentation.presentedY();
  }

  private float presentedRotation() {
    Presentation2D presentation = getGameObject().getComponent(Presentation2D.class);
    return presentation == null ? getTransform().rotation : presentation.presentedRotation();
  }
}
