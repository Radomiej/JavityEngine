package org.javity.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.utils.*;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import java.nio.*;
import java.util.*;
import org.javity.render.InstanceGpuTransport.*;

/**
 * GL-thread-owned opt-in static batching. Call outside any active ModelBatch. Renderables and
 * source Mesh/Texture remain borrowed. Final world transforms are culled before compact packing;
 * returned fallback renderables retain their original identity. Geometry must remain immutable
 * until invalidateGeometry(source), called before the next render after an edit.
 */
public final class StaticInstanceBatch implements Disposable {
  private final int capacity;
  private final Thread owner = Thread.currentThread();
  private InstanceCapabilities capabilities;
  private final Map<InstanceGroupKey, Group> groups = new LinkedHashMap<>();
  private final IdentityHashMap<Mesh, Geometry> geometries = new IdentityHashMap<>();
  private final Map<String, Integer> reasons = new LinkedHashMap<>();
  private InstanceStats stats = InstanceStats.EMPTY;
  private boolean disposed;

  public StaticInstanceBatch(int capacity) {
    if (capacity < 1 || capacity > Integer.MAX_VALUE / 64)
      throw new IllegalArgumentException("capacity must be positive and bounded");
    this.capacity = capacity;
    capabilities = InstanceCapabilities.detect();
  }

  public InstanceStats stats() {
    return stats;
  }

  public Map<String, Integer> fallbackReasons() {
    return Map.copyOf(reasons);
  }

  /**
   * One-shot submissions use positional identities; persistent owners should use the ID overload.
   */
  public Array<Renderable> render(
      Array<Renderable> candidates, Camera camera, Environment environment) {
    long[] ids = new long[candidates.size];
    for (int i = 0; i < ids.length; i++) ids[i] = i;
    return render(candidates, camera, environment, ids);
  }

  /**
   * IDs must be unique stable component/part identities, never identities of pooled Renderables.
   */
  public Array<Renderable> render(
      Array<Renderable> candidates, Camera camera, Environment environment, long[] stableOwnerIds) {
    requireOwner();
    if (stableOwnerIds.length != candidates.size)
      throw new IllegalArgumentException("One owner ID per candidate required");
    IdentityHashMap<Renderable, Long> ownerIds = new IdentityHashMap<>();
    Set<Long> unique = new HashSet<>();
    for (int i = 0; i < candidates.size; i++) {
      if (!unique.add(stableOwnerIds[i])) throw new IllegalArgumentException("Duplicate owner ID");
      ownerIds.put(candidates.get(i), stableOwnerIds[i]);
    }
    Objects.requireNonNull(candidates);
    Objects.requireNonNull(camera);
    reasons.clear();
    Array<Renderable> fallback = new Array<>();
    Map<InstanceGroupKey, Array<Renderable>> visible = new LinkedHashMap<>();
    long culled = 0, instanced = 0, draws = 0, uploaded = 0;
    // Keep the existing ordinary path's no-culling policy for rejected features. Supported
    // candidates use conservative final-world AABBs (whole source mesh, including node bases).
    for (Renderable r : candidates) {
      String reason =
          capabilities.supported()
              ? InstanceGroupKey.rejectionReason(r, environment)
              : capabilities.reason();
      if (reason != null) {
        reject(fallback, r, reason);
        continue;
      }
      BoundingBox bounds = r.meshPart.mesh.calculateBoundingBox().mul(r.worldTransform);
      if (!camera.frustum.boundsInFrustum(bounds)) {
        culled++;
        continue;
      }
      visible.computeIfAbsent(InstanceGroupKey.from(r, environment), k -> new Array<>()).add(r);
    }
    if (!capabilities.supported()) {
      prune(Set.of());
      stats = new InstanceStats(candidates.size, candidates.size, 0, 0, fallback.size, 0, 0, 0);
      return fallback;
    }
    State state = new State();
    try {
      prune(visible.keySet());
      for (var entry : visible.entrySet()) {
        Array<Renderable> members = entry.getValue();
        Group group = groups.get(entry.getKey());
        try {
          if (group == null) {
            Geometry geometry = geometries.get(members.first().meshPart.mesh);
            if (geometry == null) {
              geometry = new Geometry(members.first().meshPart.mesh);
              geometries.put(members.first().meshPart.mesh, geometry);
            }
            group = new Group(geometry, members.first(), environment);
            groups.put(entry.getKey(), group);
          }
          uploaded += group.prepare(members, environment, ownerIds);
        } catch (RuntimeException failure) {
          Gdx.app.error(
              "StaticInstanceBatch", "GPU preparation failed; using ordinary rendering", failure);
          // Only preparation may fall back. Never redraw a partially submitted group.
          if (group != null) {
            groups.remove(entry.getKey());
            group.dispose();
          }
          for (Renderable r : members)
            reject(fallback, r, "GPU_PREPARATION_FAILED:" + failure.getClass().getSimpleName());
          continue;
        }
        RenderContext context =
            new RenderContext(new DefaultTextureBinder(DefaultTextureBinder.LRU, 0, 1));
        context.begin();
        boolean begun = false;
        try {
          group.shader.begin(camera, context);
          begun = true;
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
      pruneUnusedGeometry();
    } finally {
      state.restore();
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

  /** Source owner calls after geometry edits, outside active ModelBatch. */
  public void invalidateGeometry(Mesh source) {
    requireOwner();
    for (Iterator<Group> it = groups.values().iterator(); it.hasNext(); ) {
      Group g = it.next();
      if (g.geometry.source == source) {
        it.remove();
        g.dispose();
      }
    }
    Geometry geometry = geometries.remove(source);
    if (geometry != null) geometry.dispose();
  }

  /**
   * After libGDX's managed Mesh/ShaderProgram restoration in the new context, outside batches.
   * Retained CPU vertex/index arrays back the managed geometry. Raw instance names are abandoned
   * (never deleted in the new namespace), then rebuilt from retained compact matrices on render.
   * Programs are recreated so all attribute/uniform locations are resolved anew.
   */
  public void contextRestored() {
    requireOwner();
    capabilities = InstanceCapabilities.detect();
    for (Group group : groups.values()) {
      InstanceShader shader = group.shader;
      group.shader = null;
      discardRecoveredShader(
          shader,
          () -> {
            for (Chunk chunk : group.chunks) {
              chunk.buffer = 0;
              chunk.uploaded = false;
            }
          });
    }
  }

  static void discardRecoveredShader(Disposable shader, Runnable invalidateRawInstances) {
    try {
      if (shader != null) shader.dispose();
    } finally {
      invalidateRawInstances.run();
    }
  }

  private void prune(Set<InstanceGroupKey> keep) {
    for (var it = groups.entrySet().iterator(); it.hasNext(); ) {
      var entry = it.next();
      if (!keep.contains(entry.getKey())) {
        entry.getValue().dispose();
        it.remove();
      }
    }
    pruneUnusedGeometry();
  }

  private void pruneUnusedGeometry() {
    Set<Geometry> used = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Group group : groups.values()) used.add(group.geometry);
    for (var it = geometries.values().iterator(); it.hasNext(); ) {
      Geometry geometry = it.next();
      if (!used.contains(geometry)) {
        geometry.dispose();
        it.remove();
      }
    }
  }

  private void requireOwner() {
    if (disposed) throw new IllegalStateException("StaticInstanceBatch disposed");
    if (Thread.currentThread() != owner)
      throw new IllegalStateException("StaticInstanceBatch requires its GL owner thread");
  }

  @Override
  public void dispose() {
    if (disposed) return;
    requireOwner();
    for (Group g : groups.values()) g.dispose();
    groups.clear();
    for (Geometry g : geometries.values()) g.dispose();
    geometries.clear();
    disposed = true;
  }

  private final class Group implements Disposable {
    final Geometry geometry;
    final Renderable representative = new Renderable();
    final List<Chunk> chunks = new ArrayList<>();
    final InstanceSlots slots = new InstanceSlots(capacity);
    final Set<Long> previousOwners = new HashSet<>();
    InstanceShader shader;

    Group(Geometry geometry, Renderable source, Environment environment) {
      this.geometry = geometry;
      representative.set(source);
      representative.meshPart.mesh = geometry.mesh;
      representative.material = new Material(source.material);
      representative.worldTransform.idt();
      representative.environment = environment;
      shader = InstanceShader.create(representative);
    }

    long prepare(
        Array<Renderable> members, Environment environment, IdentityHashMap<Renderable, Long> ids) {
      // A retained representative must never observe a removed member's subsequent mutations.
      representative.material = new Material(members.first().material);
      representative.environment = environment;
      if (shader == null) shader = InstanceShader.create(representative);
      Set<Long> current = new HashSet<>();
      for (Renderable member : members) current.add(ids.get(member));
      for (long old : previousOwners) if (!current.contains(old)) slots.remove(old);
      for (Renderable member : members) slots.put(ids.get(member), member.worldTransform);
      previousOwners.clear();
      previousOwners.addAll(current);
      List<InstanceSlots.Chunk> packed = slots.pack(id -> true);
      while (chunks.size() > packed.size()) chunks.remove(chunks.size() - 1).dispose();
      long bytes = 0;
      for (int c = 0; c < packed.size(); c++) {
        if (c == chunks.size()) chunks.add(new Chunk(capacity));
        InstanceSlots.Chunk values = packed.get(c);
        bytes += chunks.get(c).update(values.transforms(), values.ownerIds().length);
      }
      slots.clearDirty();
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
