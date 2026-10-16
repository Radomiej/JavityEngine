package org.javity.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.Disposable;
import java.util.*;
import org.javity.avatar.AvatarBinding;
import org.javity.avatar.AvatarView;
import org.javity.core.*;
import org.javity.presentation.Presentation2D;
import org.javity.presentation.Presentation3D;

/** Render passes: models with depth, sprites without depth, then caller-owned UI. */
public final class GdxRenderer implements Disposable {
  private final SpriteBatch sprites;
  private final ShapeRenderer shapes;
  private SmoothShapeRenderer smoothShapes;
  private final Matrix4 screenProjection;
  static final Comparator<Render2D> RENDER_ORDER =
      Comparator.comparingInt(
              (Render2D r) ->
                  r instanceof ShapeRenderer2D shape && shape.space == ShapeRenderer2D.Space.SCREEN
                      ? 1
                      : 0)
          .thenComparingInt(r -> r.layer)
          .thenComparingInt(r -> r.order);
  private final Matrix4 drawingTransform, identityTransform;
  private final Matrix4 modelBase = new Matrix4();
  private final Matrix4 presentationLocal = new Matrix4();
  private final Matrix4 presentationCandidate = new Matrix4();
  private final Matrix4 renderableCandidate = new Matrix4();
  private final Presentation3D.Pose presentationPose = new Presentation3D.Pose();
  private final ModelBatch models;
  private StaticInstanceBatch staticInstances;
  private final com.badlogic.gdx.utils.LongArray worldCandidateOwners =
      new com.badlogic.gdx.utils.LongArray();
  private final IdentityHashMap<ModelRenderer, IdentityHashMap<NodePart, Long>> instanceOwnerIds =
      new IdentityHashMap<>();
  private final Set<ModelRenderer> seenInstanceOwners =
      Collections.newSetFromMap(new IdentityHashMap<>());
  private long nextInstanceOwnerId;
  private final com.badlogic.gdx.utils.Array<Renderable> worldOrdinary =
      new com.badlogic.gdx.utils.Array<>();
  private final com.badlogic.gdx.utils.Array<Renderable> worldCandidates =
      new com.badlogic.gdx.utils.Array<>();
  private final com.badlogic.gdx.utils.FlushablePool<Renderable> worldPool =
      new com.badlogic.gdx.utils.FlushablePool<>() {
        protected Renderable newObject() {
          return new Renderable();
        }

        @Override
        public Renderable obtain() {
          Renderable r = super.obtain();
          r.environment = null;
          r.shader = null;
          r.bones = null;
          r.userData = null;
          return r;
        }
      };
  private final RecoverableDefaultShaderProvider modelShaders;
  private final AssetManager assets;
  public final OrthographicCamera camera2D;
  public final Environment environment;
  private int width = 1, height = 1;
  private boolean sceneCameraActive;
  private final int maxBones, maxBoneWeights;

  public GdxRenderer() {
    this(12, 4);
  }

  /**
   * Creates a renderer whose DefaultShader explicitly supports the given skinning capacity. The
   * no-argument renderer keeps libGDX's 12-bone, 4-weight default; rigs beyond that require an
   * explicitly larger capacity and hardware/shader validation.
   */
  public GdxRenderer(int maxBones, int maxBoneWeights) {
    if (maxBones < 1 || maxBoneWeights < 0 || maxBoneWeights > 8)
      throw new IllegalArgumentException(
          "Shader capacity requires 1..N bones and 0..8 bone-weight attributes");
    this.maxBones = maxBones;
    this.maxBoneWeights = maxBoneWeights;
    DefaultShader.Config shaderConfig = new DefaultShader.Config();
    shaderConfig.numBones = maxBones;
    shaderConfig.numBoneWeights = maxBoneWeights;
    RecoverableDefaultShaderProvider createdModelShaders =
        new RecoverableDefaultShaderProvider(shaderConfig);
    SpriteBatch createdSprites = null;
    ShapeRenderer createdShapes = null;
    ModelBatch createdModels = null;
    AssetManager createdAssets = null;
    try {
      createdSprites = new SpriteBatch();
      createdShapes = new ShapeRenderer();
      createdModels = new ModelBatch(createdModelShaders);
      createdAssets = new AssetManager();
    } catch (RuntimeException | Error failure) {
      disposeAfterConstructionFailure(
          failure, createdAssets, createdModels, createdShapes, createdSprites);
      throw failure;
    }
    sprites = createdSprites;
    shapes = createdShapes;
    models = createdModels;
    modelShaders = createdModelShaders;
    assets = createdAssets;
    screenProjection = new Matrix4().setToOrtho2D(0, 0, 1, 1);
    drawingTransform = new Matrix4();
    identityTransform = new Matrix4();
    camera2D = new OrthographicCamera();
    environment = new Environment();
    environment.set(new ColorAttribute(ColorAttribute.AmbientLight, .45f, .45f, .5f, 1));
    environment.add(new DirectionalLight().set(.9f, .9f, 1, -1, -.8f, -.4f));
  }

  static void disposeAfterConstructionFailure(Throwable original, Disposable... allocated) {
    Objects.requireNonNull(original, "original");
    for (Disposable resource : allocated) {
      if (resource == null) continue;
      try {
        resource.dispose();
      } catch (RuntimeException | Error cleanupFailure) {
        if (cleanupFailure != original) original.addSuppressed(cleanupFailure);
      }
    }
  }

  /**
   * Repairs this renderer's retained DefaultShader bindings after managed programs have relinked
   * and their alias maps have been refreshed. Call on the GL owner thread before drawing, outside
   * ModelBatch.begin/end. The single owned provider serves both world and viewmodel passes;
   * caller-supplied shaders remain their caller's responsibility.
   */
  public void restoreContextShaderBindings() {
    modelShaders.contextRestored();
    if (staticInstances != null) staticInstances.contextRestored();
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
    if (view != null) renderWorld(game, view, null);
    render2D(game);
  }

  /**
   * Submits the world model pass to an explicit camera with an optional per-view avatar override.
   * For that view, renderable bases compose as objectWorld × localPresentationTRS × viewBase.
   */
  public void renderWorld(Game game, Camera3D camera, AvatarView localOverride) {
    Objects.requireNonNull(game, "game");
    Objects.requireNonNull(camera, "camera");
    if (camera.getGame() != game)
      throw new IllegalArgumentException("Camera3D must belong to the rendered Game");
    if (!visible(camera)) return;
    if (localOverride != null) {
      ModelRenderer target = localOverride.renderer();
      if (target.getGame() != null && target.getGame() != game)
        throw new IllegalArgumentException("AvatarView renderer must belong to the rendered Game");
      if (target.getGame() == game
          && target.getGameObject().containsComponent(target)
          && target.isInitialized()
          && target.isEnabled()
          && target.getGameObject().isActive()
          && !target.getGameObject().hasComponent(Transform3D.class))
        throw new IllegalArgumentException("AvatarView renderer requires Transform3D");
    }
    camera.camera.viewportWidth = width;
    camera.camera.viewportHeight = height;
    camera.camera.update();
    worldOrdinary.clear();
    worldCandidates.clear();
    worldCandidateOwners.clear();
    seenInstanceOwners.clear();
    try {
      for (GameObject o : game.getObjectsWith(ModelRenderer.class, Transform3D.class)) {
        Transform3D transform = o.getComponent(Transform3D.class);
        if (!visible(transform)) continue;
        for (ModelRenderer r : o.getComponents(ModelRenderer.class)) {
          if (!visible(r)) continue;
          r.prepareForRender();
          if (localOverride != null && localOverride.renderer() == r) {
            if (!o.containsComponent(r)) continue;
            localOverride.validateShaderCapacity(maxBones, maxBoneWeights);
            Matrix4 objectBase = presentedBase(transform, o);
            int start = worldOrdinary.size;
            localOverride.getRenderablesWithBase(worldOrdinary, worldPool, objectBase);
            for (int i = start; i < worldOrdinary.size; i++)
              requireFinite(
                  worldOrdinary.get(i).worldTransform,
                  "presented avatar renderable matrix is not representable");
          } else {
            validateShaderCapacity(r);
            Matrix4 base = presentedBase(transform, o);
            validateModelInstanceComposition(r.instance, base, renderableCandidate);
            r.instance.transform.set(base);
            InstancedModel marker = o.getComponent(InstancedModel.class);
            if (marker != null && visible(marker)) {
              seenInstanceOwners.add(r);
              IdentityHashMap<NodePart, Long> parts =
                  instanceOwnerIds.computeIfAbsent(r, ignored -> new IdentityHashMap<>());
              refreshInstancePartIds(
                  r.instance,
                  parts,
                  () -> nextInstanceOwnerId = Math.incrementExact(nextInstanceOwnerId));
              for (Node node : r.instance.nodes) collectInstances(r.instance, node, parts);
            } else r.instance.getRenderables(worldOrdinary, worldPool);
          }
        }
      }
      instanceOwnerIds.keySet().retainAll(seenInstanceOwners);
      if (staticInstances == null && worldCandidates.size > 0)
        staticInstances = new StaticInstanceBatch(256);
      if (staticInstances != null)
        worldOrdinary.addAll(
            staticInstances.render(
                worldCandidates, camera.camera, environment, worldCandidateOwners.toArray()));
      models.begin(camera.camera);
      try {
        for (Renderable renderable : worldOrdinary) {
          renderable.environment = environment;
          models.render(renderable);
        }
      } finally {
        models.end();
      }
    } finally {
      worldOrdinary.clear();
      worldCandidates.clear();
      worldPool.flush();
    }
  }

  private void collectInstances(
      ModelInstance instance, Node node, IdentityHashMap<NodePart, Long> parts) {
    for (NodePart part : node.parts)
      if (part.enabled) {
        long id = parts.get(part);
        worldCandidates.add(instance.getRenderable(worldPool.obtain(), node, part));
        worldCandidateOwners.add(id);
      }
    for (Node child : node.getChildren()) collectInstances(instance, child, parts);
  }

  static void refreshInstancePartIds(
      ModelInstance instance,
      IdentityHashMap<NodePart, Long> ids,
      java.util.function.LongSupplier nextId) {
    Set<NodePart> attached = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Node node : instance.nodes) collectAttachedPartIds(node, ids, nextId, attached);
    ids.keySet().retainAll(attached);
  }

  private static void collectAttachedPartIds(
      Node node,
      IdentityHashMap<NodePart, Long> ids,
      java.util.function.LongSupplier nextId,
      Set<NodePart> attached) {
    for (NodePart part : node.parts) {
      attached.add(part);
      if (part.enabled) ids.computeIfAbsent(part, ignored -> nextId.getAsLong());
    }
    for (Node child : node.getChildren()) collectAttachedPartIds(child, ids, nextId, attached);
  }

  /** Statistics for opt-in world candidates in the most recently completed view. */
  public InstanceStats instanceStats() {
    return staticInstances == null ? InstanceStats.EMPTY : staticInstances.stats();
  }

  /** Reason counts for rejected opt-in world candidates in the latest completed view. */
  public Map<String, Integer> instanceFallbackReasons() {
    return staticInstances == null ? Map.of() : staticInstances.fallbackReasons();
  }

  /** Call outside active batches before rendering a source mesh edited by its owner. */
  public void invalidateGeometry(Mesh source) {
    if (staticInstances != null) staticInstances.invalidateGeometry(source);
  }

  private Matrix4 presentedBase(Transform3D transform, GameObject object) {
    modelBase.set(transform.worldMatrix());
    requireFinite(modelBase, "canonical model matrix is not representable");
    Presentation3D presentation = object.getComponent(Presentation3D.class);
    if (presentation == null || !object.containsComponent(presentation)) return modelBase;
    presentation.snapshot(presentationPose);
    return composePresentedBase(
        modelBase, presentationPose, modelBase, presentationLocal, presentationCandidate);
  }

  static Matrix4 composePresentedBase(
      Matrix4 canonicalWorld,
      Presentation3D.Pose pose,
      Matrix4 out,
      Matrix4 localScratch,
      Matrix4 candidateScratch) {
    Objects.requireNonNull(canonicalWorld, "canonicalWorld");
    Objects.requireNonNull(pose, "pose");
    Objects.requireNonNull(out, "out");
    Objects.requireNonNull(localScratch, "localScratch");
    Objects.requireNonNull(candidateScratch, "candidateScratch");
    requireFinite(canonicalWorld, "canonical model matrix is not representable");
    requireFinite(pose.localMatrix(localScratch), "local presentation matrix is not representable");
    candidateScratch.set(canonicalWorld).mul(localScratch);
    requireFinite(candidateScratch, "composed presented model matrix is not representable");
    return out.set(candidateScratch);
  }

  static void requireFinite(Matrix4 matrix, String message) {
    for (float value : matrix.val)
      if (!Float.isFinite(value)) throw new IllegalArgumentException(message);
  }

  /**
   * Preflights the same rigid/skinned node transforms that ModelInstance exposes as renderables.
   */
  static void validateModelInstanceComposition(
      ModelInstance instance, Matrix4 presentedBase, Matrix4 candidate) {
    Objects.requireNonNull(instance, "instance");
    Objects.requireNonNull(presentedBase, "presentedBase");
    Objects.requireNonNull(candidate, "candidate");
    requireFinite(presentedBase, "presented model base is not representable");
    for (int i = 0; i < instance.nodes.size; i++)
      validateNodeComposition(instance.nodes.get(i), presentedBase, candidate);
  }

  private static void validateNodeComposition(Node node, Matrix4 presentedBase, Matrix4 candidate) {
    for (int i = 0; i < node.parts.size; i++) {
      NodePart part = node.parts.get(i);
      if (!part.enabled) continue;
      if (part.bones == null) {
        candidate.set(presentedBase).mul(node.globalTransform);
        requireFinite(candidate, "presented rigid renderable matrix is not representable");
      } else {
        // ModelInstance uses the instance base as the Renderable world transform for skinned
        // parts. Bone palette entries are vertex-selected and are not renderer-owned transforms.
        requireFinite(presentedBase, "presented skinned renderable base is not representable");
      }
    }
    for (int i = 0; i < node.getChildCount(); i++)
      validateNodeComposition(node.getChild(i), presentedBase, candidate);
  }

  /** Draws the existing 2D world/screen stages after any explicit model pass. */
  public void render2D(Game game) {
    Objects.requireNonNull(game, "game");
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

  /** Draws a final depth-isolated 3D overlay pass (the framebuffer color is preserved). */
  public void renderViewmodel(Camera camera, RenderableProvider provider) {
    Objects.requireNonNull(camera, "camera");
    Objects.requireNonNull(provider, "provider");
    if (provider instanceof AvatarView view) view.validateShaderCapacity(maxBones, maxBoneWeights);
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    Gdx.gl.glDepthMask(true);
    Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
    models.begin(camera);
    try {
      models.render(provider, environment);
    } finally {
      models.end();
    }
  }

  private void validateShaderCapacity(ModelRenderer renderer) {
    for (var root : renderer.instance.nodes) validateNodeCapacity(root);
  }

  private void validateNodeCapacity(com.badlogic.gdx.graphics.g3d.model.Node node) {
    for (int i = 0; i < node.parts.size; i++) {
      var part = node.parts.get(i);
      if (part.enabled)
        AvatarBinding.validateCapacity(part, node.id + "[" + i + "]", maxBones, maxBoneWeights);
    }
    for (var child : node.getChildren()) validateNodeCapacity(child);
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
    if (staticInstances != null) staticInstances.dispose();
    models.dispose();
    sprites.dispose();
    shapes.dispose();
    if (smoothShapes != null) smoothShapes.dispose();
    assets.dispose();
  }
}
