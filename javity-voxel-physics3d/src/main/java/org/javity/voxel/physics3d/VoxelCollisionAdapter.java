package org.javity.voxel.physics3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.javity.core.GameObject;
import org.javity.core.GameSystem;
import org.javity.core.Transform3D;
import org.javity.physics3d.CharacterReadiness3D;
import org.javity.physics3d.CompoundCollider3D;
import org.javity.physics3d.Physics3D;
import org.javity.physics3d.RigidBody3D;
import org.javity.physics3d.StaticCompoundCollider3D;
import org.javity.voxel.ChunkPos;
import org.javity.voxel.VoxelWorld;

/** Bounded CPU voxel occupancy adapter. Register after its Physics3D system. */
public final class VoxelCollisionAdapter extends GameSystem {
  private record Applied(long generation, long revision) {}

  private record Plan(
      ChunkPos position,
      long generation,
      long revision,
      List<CompoundCollider3D.Child> boxes,
      boolean unload) {}

  private record Candidate(ChunkPos position, long generation, long revision, boolean unload) {}

  private static final class ChunkBody {
    final GameObject object;
    StaticCompoundCollider3D collider;
    long pendingGeneration;
    long pendingRevision;
    boolean pending;

    ChunkBody(GameObject object) {
      this.object = object;
    }
  }

  private final VoxelWorld voxels;
  private final Physics3D physics;
  private final int maxChunkAttemptsPerFrame;
  private final Map<ChunkPos, Applied> applied = new LinkedHashMap<>();
  private final Map<ChunkPos, ChunkBody> bodies = new LinkedHashMap<>();
  private final List<Plan> prepared = new ArrayList<>();
  private AutoCloseable preparation;
  private int cursor;
  private long publicationFrame = Long.MIN_VALUE;
  private int publicationAttempts;

  /** Maximum chunks whose collision geometry may be rebuilt or retired per Game frame. */
  public VoxelCollisionAdapter(VoxelWorld voxels, Physics3D physics, int maxChunkAttemptsPerFrame) {
    this.voxels = Objects.requireNonNull(voxels, "voxels");
    this.physics = Objects.requireNonNull(physics, "physics");
    if (maxChunkAttemptsPerFrame <= 0)
      throw new IllegalArgumentException("maxChunkAttemptsPerFrame must be positive");
    this.maxChunkAttemptsPerFrame = Math.min(maxChunkAttemptsPerFrame, voxels.maxLoadedChunks());
  }

  public VoxelCollisionAdapter(VoxelWorld voxels, Physics3D physics) {
    this(voxels, physics, 1);
  }

  @Override
  public void onCreate() {
    if (getGame().getSystem(Physics3D.class) != physics)
      throw new IllegalStateException(
          "VoxelCollisionAdapter requires its Physics3D in the same Game");
    preparation = physics.beforeFixedStep(this::commitPrepared);
  }

  @Override
  public void onBeforeUpdate(float delta) {
    publishReadyCandidates();
    prepareWork();
  }

  /** True after the requested loaded-chunk generation and revision have a committed binding. */
  public boolean isChunkCollisionReady(ChunkPos position) {
    Objects.requireNonNull(position, "position");
    if (!voxels.isLoaded(position)) return false;
    Applied value = applied.get(position);
    if (value == null
        || value.generation != voxels.chunkGeneration(position)
        || value.revision != voxels.contentRevision(position)) return false;
    ChunkBody body = bodies.get(position);
    return body == null
        || (body.collider != null
            && physics.isCollisionPublished(body.object, List.of(body.collider)));
  }

  /**
   * Conservative admission check for a closed world-space AABB. Every touched chunk must be loaded
   * and have its current generation/revision published. Capsule movement code should pass the swept
   * capsule volume, not only its centre or destination. Missing chunks return false.
   */
  public boolean isCollisionReadyVolume(
      float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    if (!Float.isFinite(minX)
        || !Float.isFinite(minY)
        || !Float.isFinite(minZ)
        || !Float.isFinite(maxX)
        || !Float.isFinite(maxY)
        || !Float.isFinite(maxZ)
        || minX > maxX
        || minY > maxY
        || minZ > maxZ) return false;
    double low = -1_048_576d;
    double high = 1_048_575d;
    if (minX < low || minY < low || minZ < low || maxX > high || maxY > high || maxZ > high)
      return false;
    int minChunkX = Math.floorDiv((int) Math.floor(minX), 16);
    int minChunkY = Math.floorDiv((int) Math.floor(minY), 16);
    int minChunkZ = Math.floorDiv((int) Math.floor(minZ), 16);
    int maxChunkX = Math.floorDiv((int) Math.floor(maxX), 16);
    int maxChunkY = Math.floorDiv((int) Math.floor(maxY), 16);
    int maxChunkZ = Math.floorDiv((int) Math.floor(maxZ), 16);
    long chunkCount =
        (long) (maxChunkX - minChunkX + 1)
            * (maxChunkY - minChunkY + 1)
            * (maxChunkZ - minChunkZ + 1);
    if (chunkCount > voxels.maxLoadedChunks()) return false;
    for (int x = minChunkX; x <= maxChunkX; x++)
      for (int y = minChunkY; y <= maxChunkY; y++)
        for (int z = minChunkZ; z <= maxChunkZ; z++)
          if (!isChunkCollisionReady(new ChunkPos(x, y, z))) return false;
    return true;
  }

  /** Supplies the conservative full swept-capsule admission policy used by Bullet characters. */
  public CharacterReadiness3D characterReadiness() {
    return this::isCollisionReadyVolume;
  }

  public int getReadyChunkCount() {
    int count = 0;
    for (ChunkPos position : voxels.loadedChunks()) if (isChunkCollisionReady(position)) count++;
    return count;
  }

  public int getTrackedChunkCount() {
    return bodies.size();
  }

  /** Native collision publication attempts made during the current Game frame. */
  public int getLastFramePublicationAttempts() {
    return publicationFrame == getGame().getFrameIndex() ? publicationAttempts : 0;
  }

  private void publishReadyCandidates() {
    for (Map.Entry<ChunkPos, ChunkBody> entry : List.copyOf(bodies.entrySet())) {
      ChunkPos position = entry.getKey();
      ChunkBody body = entry.getValue();
      if (!body.pending) continue;
      if (physics.isCollisionPublished(body.object, List.of(body.collider))) {
        applied.put(position, new Applied(body.pendingGeneration, body.pendingRevision));
        body.pending = false;
      }
    }
  }

  private void prepareWork() {
    prepared.clear();
    List<ChunkPos> loaded =
        voxels.loadedChunks().stream()
            .sorted(
                Comparator.comparingInt(ChunkPos::x)
                    .thenComparingInt(ChunkPos::y)
                    .thenComparingInt(ChunkPos::z))
            .toList();
    List<Candidate> candidates = new ArrayList<>();
    for (ChunkPos position : loaded) {
      long generation = voxels.chunkGeneration(position);
      long revision = voxels.contentRevision(position);
      Applied committed = applied.get(position);
      ChunkBody pending = bodies.get(position);
      if (committed != null
          && committed.generation == generation
          && committed.revision == revision
          && isChunkCollisionReady(position)) continue;
      if (committed != null) applied.remove(position);
      if (pending != null
          && pending.pending
          && pending.pendingGeneration == generation
          && pending.pendingRevision == revision) {
        candidates.add(new Candidate(position, generation, revision, false));
        continue;
      }
      candidates.add(new Candidate(position, generation, revision, false));
    }
    Set<ChunkPos> tracked = new java.util.LinkedHashSet<>(applied.keySet());
    tracked.addAll(bodies.keySet());
    for (ChunkPos position : tracked)
      if (!voxels.isLoaded(position)) candidates.add(new Candidate(position, 0, 0, true));
    candidates.sort(
        Comparator.comparing(Candidate::unload)
            .reversed()
            .thenComparingInt(p -> p.position.x())
            .thenComparingInt(p -> p.position.y())
            .thenComparingInt(p -> p.position.z()));
    if (candidates.isEmpty()) return;
    int count = Math.min(maxChunkAttemptsPerFrame, candidates.size());
    int start = Math.floorMod(cursor, candidates.size());
    for (int i = 0; i < count; i++) {
      Candidate candidate = candidates.get((start + i) % candidates.size());
      List<CompoundCollider3D.Child> boxes = List.of();
      if (!candidate.unload) {
        ChunkBody pending = bodies.get(candidate.position);
        if (pending != null
            && pending.pending
            && pending.pendingGeneration == candidate.generation
            && pending.pendingRevision == candidate.revision
            && pending.collider != null) {
          boxes = pending.collider.getBoxes();
          prepared.add(
              new Plan(candidate.position, candidate.generation, candidate.revision, boxes, false));
          continue;
        }
        VoxelWorld.CollisionSnapshot snapshot = voxels.collisionSnapshot(candidate.position);
        if (snapshot.generation() != candidate.generation
            || snapshot.contentRevision() != candidate.revision) continue;
        boxes = VoxelCollisionGeometry.merge(snapshot);
      }
      prepared.add(
          new Plan(
              candidate.position,
              candidate.generation,
              candidate.revision,
              boxes,
              candidate.unload));
    }
    cursor = (start + count) % candidates.size();
  }

  private void commitPrepared() {
    long frame = getGame().getFrameIndex();
    if (publicationFrame == frame) return;
    publicationFrame = frame;
    publicationAttempts = 0;
    for (Plan plan : List.copyOf(prepared)) {
      if (plan.unload) {
        if (voxels.isLoaded(plan.position)) continue;
        ChunkBody body = bodies.remove(plan.position);
        if (body != null) body.object.destroy();
        applied.remove(plan.position);
        continue;
      }
      if (!voxels.isLoaded(plan.position)
          || voxels.chunkGeneration(plan.position) != plan.generation
          || voxels.contentRevision(plan.position) != plan.revision) continue;
      if (plan.boxes.isEmpty()) {
        ChunkBody body = bodies.get(plan.position);
        if (body == null) {
          if (bodies.size() >= voxels.maxLoadedChunks() * 2L) continue;
          body = createChunkBody(plan.position, List.of());
        }
        body.collider.setBoxes(List.of());
        body.pendingGeneration = plan.generation;
        body.pendingRevision = plan.revision;
        body.pending = true;
        publicationAttempts++;
        physics.publishStaticCollision(body.object, body.collider);
        if (physics.isCollisionPublished(body.object, List.of(body.collider))) {
          applied.put(plan.position, new Applied(plan.generation, plan.revision));
          body.pending = false;
        }
        continue;
      }
      ChunkBody body = bodies.get(plan.position);
      if (body == null) {
        if (bodies.size() >= voxels.maxLoadedChunks() * 2L) continue;
        body = createChunkBody(plan.position, plan.boxes);
      } else {
        body.collider.setBoxes(plan.boxes);
      }
      body.pendingGeneration = plan.generation;
      body.pendingRevision = plan.revision;
      body.pending = true;
      publicationAttempts++;
      physics.publishStaticCollision(body.object, body.collider);
      if (physics.isCollisionPublished(body.object, List.of(body.collider))) {
        applied.put(plan.position, new Applied(plan.generation, plan.revision));
        body.pending = false;
      }
    }
    prepared.clear();
  }

  private ChunkBody createChunkBody(ChunkPos position, List<CompoundCollider3D.Child> boxes) {
    GameObject object =
        getGame()
            .createObject(
                "voxel-collision-" + position.x() + "-" + position.y() + "-" + position.z());
    Transform3D transform = object.addComponent(new Transform3D());
    transform.position.set(position.originX() + 8, position.originY() + 8, position.originZ() + 8);
    object.addComponent(new RigidBody3D(RigidBody3D.Type.STATIC));
    StaticCompoundCollider3D collider = new StaticCompoundCollider3D(boxes);
    object.addComponent(collider);
    ChunkBody body = new ChunkBody(object);
    body.collider = collider;
    bodies.put(position, body);
    return body;
  }

  @Override
  protected void onRelease() {
    Throwable failure = null;
    if (preparation != null) {
      try {
        preparation.close();
      } catch (Exception e) {
        failure = e;
      }
      preparation = null;
    }
    for (ChunkBody body : List.copyOf(bodies.values())) {
      try {
        body.object.destroy();
      } catch (RuntimeException | Error e) {
        if (failure == null) failure = e;
        else failure.addSuppressed(e);
      }
    }
    bodies.clear();
    applied.clear();
    prepared.clear();
    if (failure instanceof RuntimeException e) throw e;
    if (failure instanceof Error e) throw e;
  }
}
