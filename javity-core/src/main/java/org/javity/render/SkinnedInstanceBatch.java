package org.javity.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.utils.*;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import java.util.*;
import org.javity.render.InstanceGpuTransport.*;

/**
 * GL-thread-owned animated batching over canonical Renderable.bones. No animation service or
 * playback mutation is required. Owns only copied geometry, programs, instance buffers and
 * palettes. Source meshes must remain immutable until invalidateGeometry. Call outside active
 * ModelBatch. maxPaletteRows is a total per-view budget across all bone counts; overflow remains
 * ordinary.
 */
public final class SkinnedInstanceBatch implements Disposable {
  private static final int PALETTE_UNIT = 1;
  private final int capacity, maxPaletteRows;
  private final Thread owner = Thread.currentThread();
  private InstanceCapabilities capabilities;
  private final Map<Key, Group> groups = new LinkedHashMap<>();
  private final Map<Part, SkinnedBounds> bounds = new HashMap<>();
  private final IdentityHashMap<Mesh, Geometry> geometry = new IdentityHashMap<>();
  private final Map<Integer, BonePalette> palettes = new HashMap<>();
  private final Map<String, Integer> reasons = new LinkedHashMap<>();
  private InstanceStats stats = InstanceStats.EMPTY;
  private long epoch;
  private boolean disposed;

  private record Part(Mesh mesh, int offset, int size, int bones) {}

  private record Key(InstanceGroupKey materialPart, int bones, int page) {}

  private record Item(Renderable renderable, BonePalette.Row row, long owner) {}

  public SkinnedInstanceBatch(int capacity, int maxPaletteRows) {
    if (capacity < 1 || capacity > Integer.MAX_VALUE / 68 || maxPaletteRows < 1)
      throw new IllegalArgumentException("Positive bounded capacity and palette budget required");
    this.capacity = capacity;
    this.maxPaletteRows = maxPaletteRows;
    capabilities = InstanceCapabilities.detect();
  }

  public InstanceStats stats() {
    return stats;
  }

  public Map<String, Integer> fallbackReasons() {
    return Map.copyOf(reasons);
  }

  public Array<Renderable> render(
      Array<Renderable> candidates, Camera camera, Environment environment) {
    long[] ids = new long[candidates.size];
    for (int i = 0; i < ids.length; i++) ids[i] = i;
    return render(candidates, camera, environment, ids);
  }

  /** Stable component/part/region IDs, never pooled Renderable identities. */
  public Array<Renderable> render(
      Array<Renderable> candidates, Camera camera, Environment environment, long[] ids) {
    requireOwner();
    Objects.requireNonNull(camera);
    if (ids.length != candidates.size)
      throw new IllegalArgumentException("One ID per candidate required");
    Set<Long> unique = new HashSet<>();
    for (long id : ids)
      if (!unique.add(id)) throw new IllegalArgumentException("Duplicate owner ID");
    reasons.clear();
    epoch = Math.incrementExact(epoch);
    for (BonePalette palette : palettes.values()) palette.beginFrame(epoch);
    Array<Renderable> fallback = new Array<>();
    Map<Key, List<Item>> visible = new LinkedHashMap<>();
    Set<Part> usedParts = new HashSet<>();
    Set<Integer> usedPalettes = new HashSet<>();
    long culled = 0, instanced = 0, draws = 0, uploaded = 0;
    int rows = 0;
    for (int i = 0; i < candidates.size; i++) {
      Renderable r = candidates.get(i);
      String reason =
          capabilities.supported()
              ? InstanceGroupKey.rejectionReason(r, environment, true)
              : capabilities.reason();
      if (reason == null && (r.bones == null || r.bones.length == 0))
        reason = "MISSING_SKIN_MATRICES";
      if (reason == null
          && r.meshPart.mesh.getVertexAttributes().size() + 5 > capabilities.maxVertexAttributes())
        reason = "VERTEX_ATTRIBUTE_LIMIT";
      if (reason != null) {
        reject(fallback, r, reason);
        continue;
      }
      Part part = new Part(r.meshPart.mesh, r.meshPart.offset, r.meshPart.size, r.bones.length);
      try {
        SkinnedBounds b = bounds.computeIfAbsent(part, ignored -> new SkinnedBounds(r));
        usedParts.add(part);
        if (!camera.frustum.boundsInFrustum(b.world(r))) {
          culled++;
          continue;
        }
        BonePalette palette = palettes.get(r.bones.length);
        if (palette == null) {
          if (rows == maxPaletteRows) {
            reject(fallback, r, "PALETTE_ROW_BUDGET");
            continue;
          }
          palette = new BonePalette(r.bones.length, maxPaletteRows);
          palette.beginFrame(epoch);
          palettes.put(r.bones.length, palette);
        }
        usedPalettes.add(r.bones.length);
        palette.validateCapabilities(
            capabilities.maxTextureSize(),
            State.integer(GL20.GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS),
            State.integer(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS),
            PALETTE_UNIT);
        int before = palette.rowCount();
        BonePalette.Row row = palette.put(r.bones, rows < maxPaletteRows);
        rows += palette.rowCount() - before;
        if (row == null) {
          reject(fallback, r, "PALETTE_ROW_BUDGET");
          continue;
        }
        Key key = new Key(InstanceGroupKey.from(r, environment, true), r.bones.length, row.page());
        visible.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new Item(r, row, ids[i]));
      } catch (BonePalette.CapabilityException failure) {
        reject(fallback, r, failure.reason());
      } catch (IllegalArgumentException failure) {
        reject(fallback, r, failure.getMessage());
      }
    }
    bounds.keySet().retainAll(usedParts);
    prune(visible.keySet());
    for (var it = palettes.entrySet().iterator(); it.hasNext(); ) {
      var entry = it.next();
      if (!usedPalettes.contains(entry.getKey())) {
        entry.getValue().dispose();
        it.remove();
      }
    }
    if (capabilities.supported() && !visible.isEmpty()) {
      State state = new State(2);
      // The material binder can touch only unit zero. Unit one belongs exclusively to the palette.
      RenderContext context =
          new RenderContext(new DefaultTextureBinder(DefaultTextureBinder.LRU, 0, 1));
      try {
        for (var entry : visible.entrySet()) {
          Key key = entry.getKey();
          List<Item> items = entry.getValue();
          items.sort(Comparator.comparingLong(Item::owner));
          BonePalette palette = palettes.get(key.bones);
          Group group = groups.get(key);
          try {
            if (group == null) {
              Mesh source = items.getFirst().renderable.meshPart.mesh;
              Geometry geo = geometry.get(source);
              if (geo == null) {
                geo = new Geometry(source);
                geometry.put(source, geo);
              }
              group = new Group(geo, items.getFirst().renderable, environment);
              groups.put(key, group);
            }
            for (Item item : items)
              if (!palette.isValid(item.row))
                throw new IllegalStateException("Stale or foreign palette lease");
            uploaded += group.prepare(items, environment);
            uploaded += palette.bindPage(key.page, PALETTE_UNIT);
          } catch (RuntimeException failure) {
            Gdx.app.error(
                "SkinnedInstanceBatch", "GPU preparation failed; ordinary fallback", failure);
            if (group != null) {
              groups.remove(key);
              group.dispose();
            }
            for (Item item : items)
              reject(
                  fallback,
                  item.renderable,
                  "GPU_PREPARATION_FAILED:" + failure.getClass().getSimpleName());
            continue;
          }
          context.begin();
          boolean begun = false;
          try {
            group.shader.begin(camera, context);
            begun = true;
            group.shader.paletteUnit(PALETTE_UNIT);
            for (Chunk chunk : group.chunks) {
              group.geometry.mesh.chunk = chunk;
              group.shader.render(group.representative);
              draws++;
              instanced += chunk.count;
            }
          } finally {
            try {
              if (begun) group.shader.end();
            } finally {
              context.end();
            }
          }
        }
        pruneGeometry();
      } finally {
        state.restore();
      }
    }
    stats =
        new InstanceStats(
            candidates.size,
            candidates.size - culled,
            culled,
            instanced,
            fallback.size,
            groups.size(),
            draws,
            uploaded);
    return fallback;
  }

  private void reject(Array<Renderable> fallback, Renderable r, String reason) {
    fallback.add(r);
    reasons.merge(reason, 1, Integer::sum);
  }

  private void prune(Set<Key> keep) {
    for (var it = groups.entrySet().iterator(); it.hasNext(); ) {
      var e = it.next();
      if (!keep.contains(e.getKey())) {
        e.getValue().dispose();
        it.remove();
      }
    }
    pruneGeometry();
  }

  private void pruneGeometry() {
    Set<Geometry> used = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Group group : groups.values()) used.add(group.geometry);
    for (var it = geometry.values().iterator(); it.hasNext(); ) {
      Geometry geo = it.next();
      if (!used.contains(geo)) {
        geo.dispose();
        it.remove();
      }
    }
  }

  public void invalidateGeometry(Mesh source) {
    requireOwner();
    bounds.keySet().removeIf(p -> p.mesh == source);
    for (var it = groups.values().iterator(); it.hasNext(); ) {
      Group g = it.next();
      if (g.geometry.source == source) {
        g.dispose();
        it.remove();
      }
    }
    pruneGeometry();
  }

  /** Call after platform managed restoration, before using this owner in a replacement context. */
  public void contextRestored() {
    requireOwner();
    capabilities = InstanceCapabilities.detect();
    for (BonePalette palette : palettes.values()) palette.contextRestored();
    for (Group group : groups.values()) {
      SkinnedInstanceShader shader = group.shader;
      group.shader = null;
      StaticInstanceBatch.discardRecoveredShader(
          shader,
          () -> {
            for (Chunk chunk : group.chunks) {
              chunk.buffer = 0;
              chunk.uploaded = false;
            }
          });
    }
  }

  private void requireOwner() {
    if (disposed) throw new IllegalStateException("SkinnedInstanceBatch disposed");
    if (Thread.currentThread() != owner)
      throw new IllegalStateException("GL owner thread required");
  }

  @Override
  public void dispose() {
    if (disposed) return;
    requireOwner();
    prune(Set.of());
    for (BonePalette palette : palettes.values()) palette.dispose();
    palettes.clear();
    bounds.clear();
    disposed = true;
  }

  private final class Group implements Disposable {
    final Geometry geometry;
    final Renderable representative = new Renderable();
    final List<Chunk> chunks = new ArrayList<>();
    SkinnedInstanceShader shader;

    Group(Geometry geometry, Renderable source, Environment environment) {
      this.geometry = geometry;
      representative.set(source);
      representative.meshPart.mesh = geometry.mesh;
      representative.worldTransform.idt();
      representative.material = new Material(source.material);
      representative.environment = environment;
      representative.bones = new Matrix4[source.bones.length];
      for (int i = 0; i < source.bones.length; i++) representative.bones[i] = new Matrix4();
      representative.userData = null;
      shader = SkinnedInstanceShader.create(representative);
    }

    long prepare(List<Item> items, Environment environment) {
      representative.material = new Material(items.getFirst().renderable.material);
      representative.environment = environment;
      if (shader == null) shader = SkinnedInstanceShader.create(representative);
      int count = (items.size() + capacity - 1) / capacity;
      while (chunks.size() > count) chunks.removeLast().dispose();
      long bytes = 0;
      for (int c = 0; c < count; c++) {
        int size = Math.min(capacity, items.size() - c * capacity);
        float[] data = new float[size * 17];
        for (int i = 0; i < size; i++) {
          Item item = items.get(c * capacity + i);
          System.arraycopy(item.renderable.worldTransform.val, 0, data, i * 17, 16);
          data[i * 17 + 16] = item.row.index();
        }
        if (c == chunks.size()) chunks.add(new Chunk(capacity, 17));
        bytes += chunks.get(c).update(data, size);
      }
      return bytes;
    }

    @Override
    public void dispose() {
      if (shader != null) {
        shader.dispose();
        shader = null;
      }
      for (Chunk chunk : chunks) chunk.dispose();
      chunks.clear();
    }
  }
}
