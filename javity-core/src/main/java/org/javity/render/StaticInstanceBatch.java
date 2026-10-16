package org.javity.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.utils.*;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import java.nio.*;
import java.util.*;

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
            new RenderContext(new DefaultTextureBinder(DefaultTextureBinder.LRU, 0));
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
      group.shader.dispose();
      group.shader = null;
      for (Chunk chunk : group.chunks) {
        chunk.buffer = 0;
        chunk.uploaded = false;
      }
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
        if (c == chunks.size()) chunks.add(new Chunk());
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

  private final class Chunk implements Disposable {
    int buffer, count;
    float[] cpu = new float[0];
    boolean uploaded;

    long update(float[] data, int count) {
      if (buffer == 0) {
        buffer = Gdx.gl.glGenBuffer();
        if (buffer == 0) throw new IllegalStateException("Instance buffer allocation failed");
        Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, buffer);
        Gdx.gl.glBufferData(GL20.GL_ARRAY_BUFFER, capacity * 64, null, GL20.GL_DYNAMIC_DRAW);
      }
      int start = 0, end = data.length;
      if (uploaded) {
        while (start < Math.min(cpu.length, data.length)
            && Float.floatToIntBits(cpu[start]) == Float.floatToIntBits(data[start])) start++;
        if (cpu.length == data.length)
          while (end > start
              && Float.floatToIntBits(cpu[end - 1]) == Float.floatToIntBits(data[end - 1])) end--;
      }
      long bytes = (end - start) * 4L;
      if (bytes > 0) {
        FloatBuffer upload = BufferUtils.newFloatBuffer(end - start);
        upload.put(data, start, end - start).flip();
        Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, buffer);
        Gdx.gl.glBufferSubData(GL20.GL_ARRAY_BUFFER, start * 4, (int) bytes, upload);
      }
      cpu = data;
      this.count = count;
      uploaded = true;
      return bytes;
    }

    @Override
    public void dispose() {
      if (buffer != 0) {
        Gdx.gl.glDeleteBuffer(buffer);
        buffer = 0;
      }
    }
  }

  private static final class Geometry implements Disposable {
    final Mesh source;
    final InstancedMesh mesh;

    Geometry(Mesh source) {
      this.source = source;
      float[] vertices =
          source.getVertices(new float[source.getNumVertices() * source.getVertexSize() / 4]);
      short[] indices = new short[source.getNumIndices()];
      source.getIndices(indices);
      InstancedMesh allocated = new InstancedMesh(source);
      try {
        allocated.setVertices(vertices);
        allocated.setIndices(indices);
      } catch (RuntimeException | Error failure) {
        allocated.dispose();
        throw failure;
      }
      mesh = allocated;
    }

    @Override
    public void dispose() {
      mesh.dispose();
    }
  }

  /** Owns a private VAO/geometry; chunks share geometry and switch only their instance buffer. */
  private static final class InstancedMesh extends Mesh {
    Chunk chunk;

    InstancedMesh(Mesh source) {
      super(
          VertexDataType.VertexBufferObjectWithVAO,
          true,
          source.getNumVertices(),
          source.getNumIndices(),
          source.getVertexAttributes());
    }

    @Override
    public void render(
        ShaderProgram shader, int primitive, int offset, int count, boolean autoBind) {
      int[] locations = new int[4];
      Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, chunk.buffer);
      try {
        for (int i = 0; i < 4; i++) {
          int location = locations[i] = shader.getAttributeLocation("a_i" + i);
          Gdx.gl.glEnableVertexAttribArray(location);
          Gdx.gl.glVertexAttribPointer(location, 4, GL20.GL_FLOAT, false, 64, i * 16);
          Gdx.gl30.glVertexAttribDivisor(location, 1);
        }
        if (getNumIndices() > 0)
          Gdx.gl30.glDrawElementsInstanced(
              primitive, count, GL20.GL_UNSIGNED_SHORT, offset * 2, chunk.count);
        else Gdx.gl30.glDrawArraysInstanced(primitive, offset, count, chunk.count);
      } finally {
        for (int location : locations) {
          Gdx.gl30.glVertexAttribDivisor(location, 0);
          Gdx.gl.glDisableVertexAttribArray(location);
        }
      }
    }
  }

  /** Restore caller state, including VAO/element binding and the texture units used by binder. */
  private static final class State {
    final int vao = integer(0x85B5),
        array = integer(GL20.GL_ARRAY_BUFFER_BINDING),
        element = integer(GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING);
    final int program = integer(GL20.GL_CURRENT_PROGRAM), active = integer(GL20.GL_ACTIVE_TEXTURE);
    final int depthFunc = integer(GL20.GL_DEPTH_FUNC), cullMode = integer(GL20.GL_CULL_FACE_MODE);
    final boolean blend = Gdx.gl.glIsEnabled(GL20.GL_BLEND),
        depth = Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST),
        cull = Gdx.gl.glIsEnabled(GL20.GL_CULL_FACE);
    final int srcRGB = integer(GL20.GL_BLEND_SRC_RGB),
        dstRGB = integer(GL20.GL_BLEND_DST_RGB),
        srcAlpha = integer(GL20.GL_BLEND_SRC_ALPHA),
        dstAlpha = integer(GL20.GL_BLEND_DST_ALPHA);
    final boolean mask = integer(GL20.GL_DEPTH_WRITEMASK) != 0;
    final FloatBuffer range = BufferUtils.newFloatBuffer(2);
    final int[] textures;

    State() {
      Gdx.gl.glGetFloatv(GL20.GL_DEPTH_RANGE, range);
      textures = new int[integer(GL20.GL_MAX_TEXTURE_IMAGE_UNITS)];
      for (int i = 0; i < textures.length; i++) {
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0 + i);
        textures[i] = integer(GL20.GL_TEXTURE_BINDING_2D);
      }
      Gdx.gl.glActiveTexture(active);
    }

    void restore() {
      Gdx.gl30.glBindVertexArray(vao);
      Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, array);
      Gdx.gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, element);
      Gdx.gl.glUseProgram(program);
      enabled(GL20.GL_BLEND, blend);
      enabled(GL20.GL_DEPTH_TEST, depth);
      enabled(GL20.GL_CULL_FACE, cull);
      Gdx.gl.glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
      Gdx.gl.glDepthFunc(depthFunc);
      Gdx.gl.glCullFace(cullMode);
      Gdx.gl.glDepthMask(mask);
      Gdx.gl.glDepthRangef(range.get(0), range.get(1));
      for (int i = 0; i < textures.length; i++) {
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0 + i);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, textures[i]);
      }
      Gdx.gl.glActiveTexture(active);
    }

    static void enabled(int state, boolean yes) {
      if (yes) Gdx.gl.glEnable(state);
      else Gdx.gl.glDisable(state);
    }

    static int integer(int name) {
      IntBuffer value = BufferUtils.newIntBuffer(1);
      Gdx.gl.glGetIntegerv(name, value);
      return value.get(0);
    }
  }
}
