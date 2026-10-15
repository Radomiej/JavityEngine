package org.javity.voxel.gpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.render.ModelRenderer;
import org.javity.voxel.AtlasLayout;
import org.javity.voxel.ChunkPos;
import org.javity.voxel.VoxelMeshData;
import org.javity.voxel.VoxelMesher;
import org.javity.voxel.VoxelWorld;

/**
 * GL-thread adapter that owns one generated model for each nonempty loaded chunk. It is the sole
 * consumer of a world's shared dirty queue while active; create a replacement only after closing
 * the previous adapter.
 */
public final class VoxelChunkRenderer implements AutoCloseable {
  private static final int CHUNK_SIZE = VoxelWorld.CHUNK_SIZE;
  private static final int MAX_UNSIGNED_SHORT_VERTICES = 65_536;

  private final Game game;
  private final VoxelWorld world;
  private final Texture borrowedAtlas;
  private final AtlasLayout atlasLayout;
  private final Thread renderThread;
  private final Map<ChunkPos, RenderedChunk> rendered = new LinkedHashMap<>();
  private final Map<ChunkPos, Integer> rebuildCounts = new LinkedHashMap<>();
  private final Map<ChunkPos, Long> loadedGenerations = new LinkedHashMap<>();
  private final Vector3 boundsCenter = new Vector3();
  private final Vector3 boundsSize = new Vector3(CHUNK_SIZE, CHUNK_SIZE, CHUNK_SIZE);
  private PerspectiveCamera lastCamera;
  private boolean cullingEnabled = true;
  private boolean closed;
  private int faceCount;
  private int totalRebuilds;
  private int lastRebuilds;

  /** The atlas remains owned by the caller and must stay alive until this adapter is closed. */
  public VoxelChunkRenderer(
      Game game, VoxelWorld world, Texture borrowedAtlas, AtlasLayout atlasLayout) {
    this.game = Objects.requireNonNull(game, "game");
    this.world = Objects.requireNonNull(world, "world");
    this.borrowedAtlas = Objects.requireNonNull(borrowedAtlas, "borrowedAtlas");
    this.atlasLayout = Objects.requireNonNull(atlasLayout, "atlasLayout");
    if (Gdx.gl20 == null || Gdx.app == null)
      throw new IllegalStateException("VoxelChunkRenderer requires a live OpenGL application");
    renderThread = Thread.currentThread();
    // A retained CPU world may already be clean because a previous adapter consumed its queue.
    // Queue all loaded chunks for deferred work; actual uploads still happen only in update().
    for (ChunkPos position : world.loadedChunks()) world.markDirty(position);
  }

  /**
   * Removes unloaded renderables, then attempts at most {@code maxRebuilds} FIFO dirty chunks.
   * Failed chunks return to the tail of the world queue and do not consume successful counters.
   */
  public void update(PerspectiveCamera camera, int maxRebuilds) {
    requireOpenAndRenderThread();
    Objects.requireNonNull(camera, "camera");
    if (maxRebuilds < 0) throw new IllegalArgumentException("maxRebuilds must be nonnegative");
    if (game.isDisposed()) throw new IllegalStateException("Cannot update voxel rendering after Game.close");
    lastCamera = camera;
    int successful = 0;
    lastRebuilds = 0;
    try {
      synchronizeLoadedGenerations();
      int attemptBudget = Math.min(maxRebuilds, world.dirtyChunkCount());
      for (int attempt = 0; attempt < attemptBudget; attempt++) {
        List<ChunkPos> dirty = world.dirtyChunks();
        if (dirty.isEmpty()) break;
        ChunkPos position = dirty.get(0);
        if (!world.isLoaded(position)) {
          world.clearDirty(position);
          continue;
        }
        try {
          rebuild(position);
          world.clearDirty(position);
          successful++;
          totalRebuilds++;
          rebuildCounts.merge(position, 1, Integer::sum);
        } catch (CommittedCleanupException committedCleanup) {
          world.clearDirty(position);
          successful++;
          totalRebuilds++;
          rebuildCounts.merge(position, 1, Integer::sum);
          throw committedCleanup;
        } catch (RuntimeException | Error failure) {
          // Moving the failed request behind its peers prevents a repeatedly invalid chunk
          // from blocking all later chunks on the next update call.
          world.clearDirty(position);
          world.markDirty(position);
          throw failure;
        }
      }
      updateVisibility(camera);
    } finally {
      lastRebuilds = successful;
    }
  }

  /** Enables or disables conservative chunk AABB culling. */
  public void setCullingEnabled(boolean enabled) {
    requireOpenAndRenderThread();
    cullingEnabled = enabled;
    if (!enabled) {
      for (RenderedChunk chunk : rendered.values()) setVisible(chunk, true);
    } else if (lastCamera != null) {
      updateVisibility(lastCamera);
    }
  }

  /** Number of nonempty loaded chunks with an owned model. */
  public int renderedChunkCount() {
    return rendered.size();
  }

  /** Number of chunk render components enabled after the last culling update. */
  public int visibleChunkCount() {
    int count = 0;
    for (RenderedChunk chunk : rendered.values()) if (chunk.visible) count++;
    return count;
  }

  /** Number of visible block faces across all current chunk models, including culled models. */
  public int faceCount() {
    return faceCount;
  }

  /** Number of successfully committed chunk rebuilds since construction. */
  public int totalRebuilds() {
    return totalRebuilds;
  }

  /** Number of successful commits made by the most recent update call. */
  public int lastRebuilds() {
    return lastRebuilds;
  }

  /** Number of successful commits for this position since its current load. */
  public int rebuildCount(ChunkPos position) {
    return rebuildCounts.getOrDefault(Objects.requireNonNull(position, "position"), 0);
  }

  @Override
  public void close() {
    if (closed) return;
    requireRenderThread();
    closed = true;
    Throwable failure = null;
    for (RenderedChunk chunk : List.copyOf(rendered.values())) {
      rendered.remove(chunk.position);
      faceCount -= chunk.faceCount;
      try {
        releaseChunk(chunk);
      } catch (RuntimeException | Error cleanupFailure) {
        failure = append(failure, cleanupFailure);
      }
    }
    if (failure != null) rethrow(failure);
  }

  private void rebuild(ChunkPos position) {
    VoxelMeshData meshData =
        VoxelMesher.build(world.snapshot(position), world.registry(), atlasLayout);
    int nextFaceCount = meshData.faceCount();
    Model candidate = nextFaceCount == 0 ? null : buildModel(meshData);
    RenderedChunk current = rendered.get(position);
    if (candidate == null) {
      if (current == null) return;
      rendered.remove(position);
      faceCount -= current.faceCount;
      try {
        releaseChunk(current);
      } catch (RuntimeException | Error failure) {
        throw new CommittedCleanupException(failure);
      }
      return;
    }

    if (current == null) {
      RenderedChunk replacement = createRenderedChunk(position, candidate, nextFaceCount);
      rendered.put(position, replacement);
      faceCount += nextFaceCount;
      updateChunkVisibility(replacement, lastCamera);
      return;
    }

    ModelRenderer candidateRenderer;
    try {
      candidateRenderer = new ModelRenderer(candidate);
    } catch (RuntimeException | Error failure) {
      disposeAfterFailure(candidate, failure);
      throw failure;
    }
    boolean attached = false;
    try {
      current.gameObject.addComponent(candidateRenderer);
      attached = true;
      if (!current.gameObject.removeComponent(current.renderer))
        throw new IllegalStateException("Previous chunk ModelRenderer was detached during replacement");
    } catch (RuntimeException | Error failure) {
      if (attached || current.gameObject.containsComponent(candidateRenderer)) {
        try {
          current.gameObject.removeComponent(candidateRenderer);
        } catch (RuntimeException | Error cleanupFailure) {
          failure.addSuppressed(cleanupFailure);
        }
      }
      if (!current.gameObject.containsComponent(current.renderer)) {
        // GameObject.removeComponent mutates first and notifies listeners second. A listener
        // may throw after detaching the old component, so restore a new component over the
        // still-live old Model instead of trying to reattach the removed instance.
        ModelRenderer restoredRenderer = null;
        try {
          restoredRenderer = new ModelRenderer(current.model);
          current.gameObject.addComponent(restoredRenderer);
          restoredRenderer.setEnabled(current.visible);
          current.renderer = restoredRenderer;
        } catch (RuntimeException | Error restoreFailure) {
          if (restoredRenderer != null && current.gameObject.containsComponent(restoredRenderer)) {
            current.renderer = restoredRenderer;
            restoredRenderer.setEnabled(current.visible);
          }
          failure.addSuppressed(restoreFailure);
        }
      }
      disposeAfterFailure(candidate, failure);
      throw failure;
    }

    Model previousModel = current.model;
    int previousFaceCount = current.faceCount;
    current.model = candidate;
    current.renderer = candidateRenderer;
    current.faceCount = nextFaceCount;
    faceCount += nextFaceCount - previousFaceCount;
    updateChunkVisibility(current, lastCamera);
    try {
      previousModel.dispose();
    } catch (RuntimeException | Error cleanupFailure) {
      throw new CommittedCleanupException(cleanupFailure);
    }
  }

  private Model buildModel(VoxelMeshData meshData) {
    int vertexCount = meshData.vertexCount();
    int indexCount = meshData.indexCount();
    if (vertexCount <= 0 || vertexCount > MAX_UNSIGNED_SHORT_VERTICES)
      throw new IllegalArgumentException("Chunk mesh exceeds unsigned-short vertex capacity: " + vertexCount);
    if (indexCount <= 0 || indexCount % 3 != 0)
      throw new IllegalArgumentException("Chunk mesh index count must be a positive triangle list");
    float[] vertices = meshData.vertices();
    short[] indices = meshData.indices();
    if (indices.length != indexCount || vertices.length != vertexCount * 8)
      throw new IllegalArgumentException("Voxel mesh arrays do not match their declared counts");
    for (int i = 0; i < indices.length; i++) {
      int unsignedIndex = Short.toUnsignedInt(indices[i]);
      if (unsignedIndex >= vertexCount)
        throw new IllegalArgumentException("Voxel mesh index " + unsignedIndex + " exceeds " + vertexCount);
    }

    Model model = new Model();
    Mesh mesh = null;
    boolean modelOwnsMesh = false;
    try {
      mesh =
          new Mesh(
              true,
              vertexCount,
              indexCount,
              VertexAttribute.Position(),
              VertexAttribute.Normal(),
              VertexAttribute.TexCoords(0));
      mesh.setVertices(vertices);
      mesh.setIndices(indices);
      model.manageDisposable(mesh);
      modelOwnsMesh = true;
      model.meshes.add(mesh);

      Material material = new Material(TextureAttribute.createDiffuse(borrowedAtlas));
      model.materials.add(material);
      MeshPart meshPart = new MeshPart("voxel-chunk", mesh, 0, indexCount, GL20.GL_TRIANGLES);
      meshPart.update();
      model.meshParts.add(meshPart);
      Node node = new Node();
      node.id = "voxel-chunk";
      node.parts.add(new NodePart(meshPart, material));
      model.nodes.add(node);
      model.calculateTransforms();
      return model;
    } catch (RuntimeException | Error failure) {
      if (modelOwnsMesh) {
        try {
          model.dispose();
        } catch (RuntimeException | Error cleanupFailure) {
          failure.addSuppressed(cleanupFailure);
        }
      } else if (mesh != null) {
        try {
          mesh.dispose();
        } catch (RuntimeException | Error cleanupFailure) {
          failure.addSuppressed(cleanupFailure);
        }
      }
      throw failure;
    }
  }

  private RenderedChunk createRenderedChunk(ChunkPos position, Model model, int faces) {
    GameObject object = game.createObject("voxel chunk " + position.x() + "," + position.y() + "," + position.z());
    Transform3D transform = null;
    ModelRenderer modelRenderer = null;
    try {
      transform = object.addComponent(new Transform3D());
      transform.position.set(position.originX(), position.originY(), position.originZ());
      modelRenderer = new ModelRenderer(model);
      object.addComponent(modelRenderer);
      return new RenderedChunk(
          position, world.chunkGeneration(position), object, transform, modelRenderer, model, faces);
    } catch (RuntimeException | Error failure) {
      if (modelRenderer != null) {
        try {
          object.removeComponent(modelRenderer);
        } catch (RuntimeException | Error cleanupFailure) {
          failure.addSuppressed(cleanupFailure);
        }
      }
      object.destroy();
      disposeAfterFailure(model, failure);
      throw failure;
    }
  }

  private void synchronizeLoadedGenerations() {
    List<ChunkPos> loadedChunks = world.loadedChunks();
    var loaded = new HashSet<>(loadedChunks);
    Throwable failure = null;
    for (RenderedChunk chunk : List.copyOf(rendered.values())) {
      if (world.chunkGeneration(chunk.position) == chunk.generation) continue;
      rendered.remove(chunk.position);
      faceCount -= chunk.faceCount;
      try {
        releaseChunk(chunk);
      } catch (RuntimeException | Error cleanupFailure) {
        failure = append(failure, cleanupFailure);
      }
    }
    rebuildCounts.keySet().retainAll(loaded);
    loadedGenerations.keySet().retainAll(loaded);
    for (ChunkPos position : loadedChunks) {
      long generation = world.chunkGeneration(position);
      Long previous = loadedGenerations.put(position, generation);
      if (previous == null || previous.longValue() != generation) rebuildCounts.remove(position);
    }
    if (failure != null) rethrow(failure);
  }

  private void releaseChunk(RenderedChunk chunk) {
    Throwable failure = null;
    try {
      releaseSceneObject(chunk);
    } catch (RuntimeException | Error cleanupFailure) {
      failure = append(failure, cleanupFailure);
    }
    try {
      chunk.model.dispose();
    } catch (RuntimeException | Error cleanupFailure) {
      failure = append(failure, cleanupFailure);
    }
    if (failure != null) rethrow(failure);
  }

  private static void releaseSceneObject(RenderedChunk chunk) {
    if (chunk.gameObject.isDestroyed()) return;
    Throwable failure = null;
    try {
      chunk.gameObject.removeComponent(chunk.renderer);
    } catch (RuntimeException | Error cleanupFailure) {
      failure = append(failure, cleanupFailure);
    }
    chunk.gameObject.destroy();
    if (failure != null) rethrow(failure);
  }

  private void updateVisibility(PerspectiveCamera camera) {
    for (RenderedChunk chunk : rendered.values()) updateChunkVisibility(chunk, camera);
  }

  private void updateChunkVisibility(RenderedChunk chunk, PerspectiveCamera camera) {
    boolean visible = true;
    if (cullingEnabled && camera != null) {
      boundsCenter.set(
          chunk.position.originX() + CHUNK_SIZE / 2f,
          chunk.position.originY() + CHUNK_SIZE / 2f,
          chunk.position.originZ() + CHUNK_SIZE / 2f);
      visible = camera.frustum.boundsInFrustum(boundsCenter, boundsSize);
    }
    setVisible(chunk, visible);
  }

  private static void setVisible(RenderedChunk chunk, boolean visible) {
    chunk.visible = visible;
    chunk.renderer.setEnabled(visible);
  }

  private void requireOpenAndRenderThread() {
    if (closed) throw new IllegalStateException("VoxelChunkRenderer is closed");
    requireRenderThread();
  }

  private void requireRenderThread() {
    if (Thread.currentThread() != renderThread)
      throw new IllegalStateException("VoxelChunkRenderer must be used on its creating GL thread");
  }

  private static void disposeAfterFailure(Model model, Throwable failure) {
    try {
      model.dispose();
    } catch (RuntimeException | Error cleanupFailure) {
      failure.addSuppressed(cleanupFailure);
    }
  }

  private static Throwable append(Throwable failure, Throwable next) {
    if (failure == null) return next;
    if (failure != next) failure.addSuppressed(next);
    return failure;
  }

  private static void rethrow(Throwable failure) {
    if (failure instanceof RuntimeException runtime) throw runtime;
    if (failure instanceof Error error) throw error;
    throw new IllegalStateException(failure);
  }

  private static final class RenderedChunk {
    private final ChunkPos position;
    private final long generation;
    private final GameObject gameObject;
    private final Transform3D transform;
    private ModelRenderer renderer;
    private Model model;
    private int faceCount;
    private boolean visible = true;

    private RenderedChunk(
        ChunkPos position,
        long generation,
        GameObject gameObject,
        Transform3D transform,
        ModelRenderer renderer,
        Model model,
        int faceCount) {
      this.position = position;
      this.generation = generation;
      this.gameObject = gameObject;
      this.transform = transform;
      this.renderer = renderer;
      this.model = model;
      this.faceCount = faceCount;
    }
  }

  private static final class CommittedCleanupException extends RuntimeException {
    private CommittedCleanupException(Throwable cause) {
      super("Chunk model committed, but its previous model could not be fully disposed", cause);
    }
  }
}
