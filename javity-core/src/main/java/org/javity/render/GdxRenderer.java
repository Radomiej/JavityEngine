package org.javity.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.Disposable;
import java.util.*;
import org.javity.core.*;
import org.javity.presentation.Presentation2D;

/** Render passes: models with depth, sprites without depth, then caller-owned UI. */
public final class GdxRenderer implements Disposable {
  private final SpriteBatch sprites = new SpriteBatch();
  private final ShapeRenderer shapes = new ShapeRenderer();
  private SmoothShapeRenderer smoothShapes;
  private final Matrix4 screenProjection = new Matrix4().setToOrtho2D(0, 0, 1, 1);
  static final Comparator<Render2D> RENDER_ORDER =
      Comparator.comparingInt(
              (Render2D r) ->
                  r instanceof ShapeRenderer2D shape && shape.space == ShapeRenderer2D.Space.SCREEN
                      ? 1
                      : 0)
          .thenComparingInt(r -> r.layer)
          .thenComparingInt(r -> r.order);
  private final Matrix4 drawingTransform = new Matrix4(), identityTransform = new Matrix4();
  private final ModelBatch models = new ModelBatch();
  private final AssetManager assets = new AssetManager();
  public final OrthographicCamera camera2D = new OrthographicCamera();
  public final Environment environment = new Environment();
  private int width = 1, height = 1;
  private boolean sceneCameraActive;

  public GdxRenderer() {
    environment.set(new ColorAttribute(ColorAttribute.AmbientLight, .45f, .45f, .5f, 1));
    environment.add(new DirectionalLight().set(.9f, .9f, 1, -1, -.8f, -.4f));
  }

  /** Load during initialization. Supports libGDX Model formats: G3DJ, G3DB and OBJ. */
  public Model loadModel(String path) {
    if (!assets.isLoaded(path, Model.class)) {
      assets.load(path, Model.class);
      assets.finishLoadingAsset(path);
    }
    return assets.get(path, Model.class);
  }

  public void resize(int width, int height) {
    this.width = Math.max(1, width);
    this.height = Math.max(1, height);
    screenProjection.setToOrtho2D(0, 0, this.width, this.height);
    camera2D.setToOrtho(false, this.width, this.height);
    camera2D.update();
  }

  private static boolean visible(Component c) {
    return c.isInitialized()
        && c.isEnabled()
        && c.getGameObject().isActive()
        && !c.getGameObject().isDestroyed();
  }

  public void render(Game game) {
    Camera3D view = null;
    for (GameObject o : game.getObjectsWith(Camera3D.class)) {
      for (Camera3D c : o.getComponents(Camera3D.class))
        if (visible(c)) {
          view = c;
          break;
        }
      if (view != null) break;
    }
    if (view != null) {
      view.camera.viewportWidth = width;
      view.camera.viewportHeight = height;
      view.camera.update();
      models.begin(view.camera);
      for (GameObject o : game.getObjectsWith(ModelRenderer.class, Transform3D.class)) {
        Transform3D transform = o.getComponent(Transform3D.class);
        if (!visible(transform)) continue;
        for (ModelRenderer r : o.getComponents(ModelRenderer.class))
          if (visible(r)) {
            r.prepareForRender();
            r.instance.transform.set(transform.worldMatrix());
            models.render(r.instance, environment);
          }
      }
      models.end();
    }
    Camera2D view2D = null;
    for (GameObject o : game.getObjectsWith(Camera2D.class)) {
      for (Camera2D c : o.getComponents(Camera2D.class))
        if (c.isInitialized() && visible(c)) {
          view2D = c;
          break;
        }
      if (view2D != null) break;
    }
    if (view2D != null) {
      view2D.resize(width, height);
      view2D.applyTo(camera2D);
      sceneCameraActive = true;
    } else if (sceneCameraActive) {
      camera2D.zoom = 1;
      camera2D.setToOrtho(false, width, height);
      camera2D.update();
      sceneCameraActive = false;
    }
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    List<Render2D> renderers = new ArrayList<>();
    for (GameObject o : game.getObjectsWith(Render2D.class))
      for (Render2D r : o.getComponents(Render2D.class)) if (visible(r)) renderers.add(r);
    renderers.sort(RENDER_ORDER);
    sprites.setProjectionMatrix(camera2D.combined);
    sprites.begin();
    try {
      for (Render2D drawable : renderers) {
        if (drawable instanceof ShapeRenderer2D shape) {
          sprites.end();
          try {
            drawShape(shape);
          } finally {
            sprites.begin();
          }
          continue;
        }
        if (drawable instanceof TextRenderer2D text) {
          drawText(text);
          continue;
        }
        if (!(drawable instanceof SpriteRenderer2D r)) continue;
        Transform2D t = r.getTransform();
        Presentation2D presentation = r.getGameObject().getComponent(Presentation2D.class);
        sprites.setColor(r.tint);
        sprites.draw(
            r.region,
            renderX(t, presentation) - r.width / 2,
            renderY(t, presentation) - r.height / 2,
            r.width / 2,
            r.height / 2,
            r.width,
            r.height,
            renderScaleX(t, presentation) * (r.flipX ? -1 : 1),
            renderScaleY(t, presentation) * (r.flipY ? -1 : 1),
            renderRotation(t, presentation) * MathUtils.radiansToDegrees);
      }
    } finally {
      if (sprites.isDrawing()) sprites.end();
    }
  }

  private Matrix4 transform(Render2D r) {
    Transform2D t = r.getTransform();
    Presentation2D presentation = r.getGameObject().getComponent(Presentation2D.class);
    return drawingTransform
        .idt()
        .translate(renderX(t, presentation), renderY(t, presentation), 0)
        .rotate(Vector3.Z, renderRotation(t, presentation) * MathUtils.radiansToDegrees)
        .scale(renderScaleX(t, presentation), renderScaleY(t, presentation), 1);
  }

  private static float renderX(Transform2D transform, Presentation2D presentation) {
    return presentation == null
        ? transform.position.x + transform.visualOffset.x
        : presentation.renderX();
  }

  private static float renderY(Transform2D transform, Presentation2D presentation) {
    return presentation == null
        ? transform.position.y + transform.visualOffset.y
        : presentation.renderY();
  }

  private static float renderRotation(Transform2D transform, Presentation2D presentation) {
    return presentation == null ? transform.rotation : presentation.presentedRotation();
  }

  private static float renderScaleX(Transform2D transform, Presentation2D presentation) {
    return presentation == null ? transform.scale.x : presentation.presentedScaleX();
  }

  private static float renderScaleY(Transform2D transform, Presentation2D presentation) {
    return presentation == null ? transform.scale.y : presentation.presentedScaleY();
  }

  private void drawText(TextRenderer2D text) {
    Color original = text.font.getColor().cpy();
    sprites.setTransformMatrix(transform(text));
    try {
      text.font.setColor(text.color);
      text.font.draw(sprites, text.text, 0, 0);
    } finally {
      text.font.setColor(original);
      sprites.setTransformMatrix(identityTransform);
    }
  }

  private void drawShape(ShapeRenderer2D shape) {
    if (shape.space == null) throw new IllegalArgumentException("Shape coordinate space required");
    Matrix4 projection =
        shape.space == ShapeRenderer2D.Space.SCREEN ? screenProjection : camera2D.combined;
    if (shape.kind != ShapeRenderer2D.Kind.POLYGON) {
      if (smoothShapes == null) smoothShapes = new SmoothShapeRenderer();
      smoothShapes.draw(
          shape,
          projection,
          transform(shape),
          Gdx.graphics.getBackBufferWidth(),
          Gdx.graphics.getBackBufferHeight());
      return;
    }
    if (shape.material != null)
      throw new UnsupportedOperationException(
          "Shape materials are not supported for legacy polygons");
    if (shape.antiAlias)
      throw new UnsupportedOperationException("Polygon AA is not implemented; use antiAlias=false");
    if (!Float.isFinite(shape.strokeWidth) || shape.strokeWidth < 0)
      throw new IllegalArgumentException("Invalid stroke width");
    shapes.setProjectionMatrix(projection);
    shapes.setTransformMatrix(transform(shape));
    Gdx.gl.glEnable(GL20.GL_BLEND);
    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    shapes.begin(ShapeRenderer.ShapeType.Filled);
    shapes.setColor(shape.color);
    try {
      float[] vertices = shape.vertices;
      var indices = shape.triangles;
      for (int i = 0; i < indices.size; i += 3) {
        int a = indices.get(i) * 2, b = indices.get(i + 1) * 2, c = indices.get(i + 2) * 2;
        shapes.triangle(
            vertices[a],
            vertices[a + 1],
            vertices[b],
            vertices[b + 1],
            vertices[c],
            vertices[c + 1]);
      }
    } finally {
      shapes.end();
    }
  }

  @Override
  public void dispose() {
    models.dispose();
    sprites.dispose();
    shapes.dispose();
    if (smoothShapes != null) smoothShapes.dispose();
    assets.dispose();
  }
}
