package org.javity.render;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.*;
import java.util.*;
import org.javity.core.*;
import org.javity.render.foliage.*;

/** Renderer-owned managed meshes; all operations happen outside active batches on the GL owner. */
final class FoliageRenderCache implements Disposable {
  interface MeshFactory {
    Mesh create(int vertices, int indices);
  }

  static final class Entry implements Disposable {
    final long revision;
    final BoundingBox bounds;
    final Material material;
    final List<Mesh> meshes;
    final List<MeshPart> parts;
    final long vertices, indices;

    Entry(
        long revision,
        BoundingBox bounds,
        Material material,
        List<Mesh> meshes,
        long vertices,
        long indices) {
      this.revision = revision;
      this.bounds = bounds;
      this.material = material;
      this.meshes = List.copyOf(meshes);
      List<MeshPart> parts = new ArrayList<>();
      for (Mesh mesh : meshes) {
        MeshPart part = new MeshPart("foliage", mesh, 0, mesh.getNumIndices(), GL20.GL_TRIANGLES);
        part.update();
        parts.add(part);
      }
      this.parts = List.copyOf(parts);
      this.vertices = vertices;
      this.indices = indices;
    }

    @Override
    public void dispose() {
      disposeAll(meshes);
    }
  }

  private static final class State {
    Entry entry;
    long failedRevision = Long.MIN_VALUE, nextRetryFrame;
    int failures;
  }

  private final IdentityHashMap<FoliagePatch3D, State> entries = new IdentityHashMap<>();
  private final MeshFactory meshFactory;
  private final BoundingBox worldBounds = new BoundingBox();
  private long builds, buildFailures;
  private int submitted, visible, culled, draws;

  FoliageRenderCache() {
    this(
        (vertices, indices) ->
            new Mesh(
                true,
                vertices,
                indices,
                VertexAttribute.Position(),
                VertexAttribute.Normal(),
                VertexAttribute.TexCoords(0)));
  }

  FoliageRenderCache(MeshFactory meshFactory) {
    this.meshFactory = Objects.requireNonNull(meshFactory);
  }

  static boolean eligible(Component component) {
    if (component == null || !component.isInitialized() || !component.isEnabled()) return false;
    GameObject owner = component.getGameObject();
    return owner != null
        && owner.isActive()
        && !owner.isDestroyed()
        && owner.containsComponent(component);
  }

  /** Prune even without a camera. Inactive patches cannot retain borrowed atlas references. */
  void reconcile(Game game) {
    List<Entry> retired = new ArrayList<>();
    var iterator = entries.entrySet().iterator();
    while (iterator.hasNext()) {
      var pair = iterator.next();
      FoliagePatch3D patch = pair.getKey();
      Transform3D transform = patch.getGameObject().getComponent(Transform3D.class);
      if (patch.getGame() != game || !eligible(patch) || !eligible(transform)) {
        if (pair.getValue().entry != null) retired.add(pair.getValue().entry);
        iterator.remove();
      }
    }
    disposeAll(retired);
  }

  static void validateShaderProvider(FoliagePatch3D patch, RecoverableShaderProvider provider) {
    Class<? extends RecoverableShaderProvider> required = patch.requiredProvider();
    if (required != null && !required.isInstance(provider))
      throw new IllegalArgumentException("Foliage materials require " + required.getName());
  }

  Entry prepare(FoliagePatch3D patch, long frame) {
    State state = entries.computeIfAbsent(patch, ignored -> new State());
    long revision = patch.revision();
    if (state.entry != null && state.entry.revision == revision) return state.entry;
    if (state.failedRevision == revision && frame < state.nextRetryFrame) return state.entry;
    Entry candidate;
    try {
      candidate = build(patch.snapshot());
    } catch (RuntimeException failure) {
      if (state.failedRevision != revision) state.failures = 0;
      state.failedRevision = revision;
      state.failures++;
      long delay = Math.min(60L, 1L << Math.min(state.failures, 6));
      state.nextRetryFrame = frame > Long.MAX_VALUE - delay ? Long.MAX_VALUE : frame + delay;
      buildFailures++;
      return state.entry;
    }
    Entry previous = state.entry;
    state.entry = candidate;
    state.failedRevision = Long.MIN_VALUE;
    state.failures = 0;
    builds++;
    if (previous != null) previous.dispose();
    return candidate;
  }

  private Entry build(FoliagePatch3D.Snapshot snapshot) {
    FoliageGeometryGenerator.Geometry geometry = FoliageGeometryGenerator.generate(snapshot);
    Material material = material(snapshot);
    List<Mesh> meshes = new ArrayList<>();
    try {
      for (var chunk : geometry.chunks()) {
        Mesh mesh = meshFactory.create(chunk.vertexCount(), chunk.indexCount());
        meshes.add(mesh);
        mesh.setVertices(chunk.vertices());
        mesh.setIndices(chunk.indices());
      }
      return new Entry(
          snapshot.revision(),
          geometry.bounds(),
          material,
          meshes,
          geometry.vertexCount(),
          geometry.indexCount());
    } catch (RuntimeException | Error failure) {
      try {
        disposeAll(meshes);
      } catch (RuntimeException | Error cleanup) {
        if (cleanup != failure) failure.addSuppressed(cleanup);
      }
      throw failure;
    }
  }

  static Material material(FoliagePatch3D.Snapshot snapshot) {
    Material result = snapshot.materialTemplate();
    if (result == null)
      result = new Material(TextureAttribute.createDiffuse(snapshot.atlas().texture()));
    result.set(new BlendingAttribute(false, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 1));
    result.set(FloatAttribute.createAlphaTest(snapshot.cutoff()));
    result.set(new DepthTestAttribute(GL20.GL_LEQUAL, true));
    result.set(IntAttribute.createCullFace(GL20.GL_NONE));
    return result;
  }

  void beginView() {
    submitted = visible = culled = draws = 0;
  }

  void collect(
      FoliagePatch3D patch,
      Entry entry,
      Matrix4 transform,
      Camera camera,
      Array<Renderable> output,
      Pool<Renderable> pool,
      IdentityHashMap<Renderable, Component> owners) {
    collect(patch, entry, transform, camera, output, null, pool, owners);
  }

  void collect(
      FoliagePatch3D patch,
      Entry entry,
      Matrix4 transform,
      Camera camera,
      Array<Renderable> output,
      Array<Renderable> passOnlyOutput,
      Pool<Renderable> pool,
      IdentityHashMap<Renderable, Component> owners) {
    submitted++;
    if (entry == null || entry.meshes.isEmpty()) return;
    worldBounds.set(entry.bounds).mul(transform);
    boolean colorVisible = !finite(worldBounds) || camera.frustum.boundsInFrustum(worldBounds);
    Array<Renderable> target = output;
    if (!colorVisible) {
      culled++;
      if (passOnlyOutput == null) return;
      target = passOnlyOutput;
    } else visible++;
    for (MeshPart part : entry.parts) {
      Renderable renderable = pool.obtain();
      renderable.worldTransform.set(transform);
      renderable.material = entry.material;
      renderable.meshPart.set(part);
      target.add(renderable);
      owners.put(renderable, patch);
      if (colorVisible) draws++;
    }
  }

  private static boolean finite(BoundingBox bounds) {
    return Float.isFinite(bounds.min.x)
        && Float.isFinite(bounds.min.y)
        && Float.isFinite(bounds.min.z)
        && Float.isFinite(bounds.max.x)
        && Float.isFinite(bounds.max.y)
        && Float.isFinite(bounds.max.z);
  }

  FoliageStats stats() {
    int patches = 0, meshes = 0;
    long vertices = 0, indices = 0;
    for (State state : entries.values())
      if (state.entry != null) {
        patches++;
        meshes += state.entry.meshes.size();
        vertices += state.entry.vertices;
        indices += state.entry.indices;
      }
    return new FoliageStats(
        patches,
        meshes,
        vertices,
        indices,
        vertices * 32 + indices * 2,
        submitted,
        visible,
        culled,
        draws,
        builds,
        buildFailures);
  }

  List<Mesh> meshes() {
    List<Mesh> result = new ArrayList<>();
    for (State state : entries.values()) if (state.entry != null) result.addAll(state.entry.meshes);
    return List.copyOf(result);
  }

  @Override
  public void dispose() {
    List<Entry> retired = new ArrayList<>();
    for (State state : entries.values()) if (state.entry != null) retired.add(state.entry);
    entries.clear();
    beginView();
    disposeAll(retired);
  }

  private static void disposeAll(Iterable<? extends Disposable> resources) {
    Throwable failure = null;
    for (Disposable resource : resources)
      try {
        resource.dispose();
      } catch (RuntimeException | Error problem) {
        if (failure == null) failure = problem;
        else if (failure != problem) failure.addSuppressed(problem);
      }
    if (failure instanceof RuntimeException problem) throw problem;
    if (failure instanceof Error problem) throw problem;
  }
}
