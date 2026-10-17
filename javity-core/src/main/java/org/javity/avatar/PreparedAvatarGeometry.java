package org.javity.avatar;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import java.util.*;

/**
 * Owned GPU sidecar for authored regions of one skinned NodePart. It borrows the source model and
 * texture resources; source model and GL context must outlive this owner and every binding lease.
 * All methods that acquire/release leases or dispose meshes are restricted to the creating GL
 * thread. Close the final binding after ModelBatch.end().
 */
public final class PreparedAvatarGeometry implements AutoCloseable {
  /** Immutable CPU report captured before any GPU allocation. */
  public record PreparationReport(
      PartKey target, int vertexCount, Map<String, Integer> triangleCounts) {
    public PreparationReport {
      triangleCounts = Collections.unmodifiableMap(new LinkedHashMap<>(triangleCounts));
    }
  }

  static final class GeometryLease implements AutoCloseable {
    private final PreparedAvatarGeometry owner;
    private boolean closed;

    GeometryLease(PreparedAvatarGeometry owner) {
      this.owner = owner;
    }

    MeshPart part(String name) {
      if (closed) throw new IllegalStateException("Avatar geometry lease is closed");
      return owner.regionParts.get(name);
    }

    Set<String> regions() {
      return owner.regionParts.keySet();
    }

    void requireOwnerThread() {
      owner.requireOwnerThread();
    }

    @Override
    public void close() {
      if (closed) return;
      owner.requireOwnerThread();
      closed = true;
      owner.releaseLease();
    }
  }

  private final Model source;
  private final PartKey target;
  private final Mesh sourceMesh;
  private final String layoutSignature;
  private final List<String> paletteIds;
  private final int sourceOffset;
  private final int sourceSize;
  private final int sourcePrimitiveType;
  private final int sourceVertexSize;
  private final PreparationReport report;
  private final Mesh generatedMesh;
  private final LinkedHashMap<String, MeshPart> regionParts;
  private final Thread ownerThread;
  private int leases;
  private boolean closeRequested;
  private boolean disposed;

  private PreparedAvatarGeometry(Model source, PartKey target, AvatarMeshPartitionPlan plan) {
    this.source = source;
    this.target = target;
    sourceMesh = plan.sourceMesh;
    layoutSignature = plan.layoutSignature;
    paletteIds = plan.paletteIds;
    sourceOffset = plan.sourceOffset;
    sourceSize = plan.sourceSize;
    sourcePrimitiveType = plan.primitiveType;
    sourceVertexSize = plan.sourceVertexSize;
    ownerThread = Thread.currentThread();
    VertexAttributes copiedLayout = new VertexAttributes(plan.attributes);
    if (copiedLayout.vertexSize != sourceVertexSize
        || !layoutSignature.equals(layoutSignature(copiedLayout)))
      throw new IllegalArgumentException(
          "Copied vertex descriptors do not reproduce source layout");
    GlBindings glBindings = GlBindings.capture();
    Mesh uploadedMesh = null;
    LinkedHashMap<String, MeshPart> parts = new LinkedHashMap<>();
    Throwable uploadFailure = null;
    try {
      int pendingError = Gdx.gl.glGetError();
      if (pendingError != GL20.GL_NO_ERROR)
        throw new IllegalStateException(
            "Cannot prepare avatar geometry while caller GL error is pending: " + pendingError);
      uploadedMesh = new Mesh(true, plan.vertexCount, plan.indices.length, plan.attributes);
      uploadedMesh.setVertices(plan.vertices);
      uploadedMesh.setIndices(plan.indices);
      uploadNow(uploadedMesh, plan.attributes.length);
      int uploadError = Gdx.gl.glGetError();
      if (uploadError != GL20.GL_NO_ERROR)
        throw new IllegalStateException(
            "Avatar geometry buffer upload failed with GL error: " + uploadError);
      for (var entry : plan.ranges.entrySet()) {
        AvatarMeshPartitionPlan.Range range = entry.getValue();
        MeshPart part =
            new MeshPart(
                entry.getKey(), uploadedMesh, range.offset(), range.size(), sourcePrimitiveType);
        part.update();
        parts.put(entry.getKey(), part);
      }
    } catch (Throwable failure) {
      uploadFailure = failure;
    }
    try {
      glBindings.restore();
    } catch (Throwable restoreFailure) {
      if (uploadFailure == null) uploadFailure = restoreFailure;
      else uploadFailure.addSuppressed(restoreFailure);
    }
    if (uploadFailure != null) {
      uploadFailure = disposeAfterFailure(uploadedMesh, uploadFailure);
      try {
        glBindings.restore();
      } catch (Throwable restoreFailure) {
        uploadFailure.addSuppressed(restoreFailure);
      }
    }
    if (uploadFailure != null) throw unchecked(uploadFailure);
    generatedMesh = uploadedMesh;
    regionParts = parts;
    Map<String, Integer> triangleCounts = new LinkedHashMap<>();
    plan.ranges.forEach((name, range) -> triangleCounts.put(name, range.size() / 3));
    report = new PreparationReport(target, plan.vertexCount, triangleCounts);
  }

  private static Throwable disposeAfterFailure(Mesh mesh, Throwable failure) {
    if (mesh == null) return failure;
    try {
      mesh.dispose();
    } catch (Throwable cleanup) {
      failure.addSuppressed(cleanup);
    }
    return failure;
  }

  private static RuntimeException unchecked(Throwable failure) {
    if (failure instanceof RuntimeException runtime) return runtime;
    if (failure instanceof Error error) throw error;
    return new IllegalStateException(failure);
  }

  private static void uploadNow(Mesh mesh, int attributeCount) {
    int[] unusedLocations = new int[attributeCount];
    Arrays.fill(unusedLocations, -1);
    boolean bound = false;
    try {
      // libGDX 1.14.2's explicit-location VBO path uploads both buffers without shader access
      // when every attribute location is negative.
      mesh.bind(null, unusedLocations, null);
      bound = true;
      mesh.unbind(null, unusedLocations, null);
      bound = false;
    } finally {
      if (bound) {
        try {
          mesh.unbind(null, unusedLocations, null);
        } catch (Throwable ignored) {
        }
      }
    }
  }

  private record GlBindings(int arrayBuffer, int elementArrayBuffer, int vertexArray) {
    static GlBindings capture() {
      java.nio.IntBuffer value = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(1);
      Gdx.gl.glGetIntegerv(GL20.GL_ARRAY_BUFFER_BINDING, value);
      int array = value.get(0);
      value.clear();
      Gdx.gl.glGetIntegerv(GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING, value);
      int element = value.get(0);
      int vertexArray = 0;
      if (Gdx.gl30 != null) {
        value.clear();
        Gdx.gl30.glGetIntegerv(GL30.GL_VERTEX_ARRAY_BINDING, value);
        vertexArray = value.get(0);
      }
      return new GlBindings(array, element, vertexArray);
    }

    void restore() {
      if (Gdx.gl30 != null) Gdx.gl30.glBindVertexArray(vertexArray);
      Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, arrayBuffer);
      Gdx.gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, elementArrayBuffer);
    }
  }

  /**
   * Performs complete CPU validation before allocating the owned mesh. Must be called on GL thread.
   */
  public static PreparedAvatarGeometry prepare(
      Model source, PartKey target, Map<String, int[]> triangleRegions) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(triangleRegions, "triangleRegions");
    NodePart nodePart = findPart(source, target);
    AvatarMeshPartitionPlan.validatePaletteMembership(source, nodePart);
    AvatarMeshPartitionPlan plan = AvatarMeshPartitionPlan.create(nodePart, triangleRegions);
    return new PreparedAvatarGeometry(source, target, plan);
  }

  public PreparationReport report() {
    return report;
  }

  PartKey target() {
    return target;
  }

  Set<String> regionNames() {
    return Collections.unmodifiableSet(regionParts.keySet());
  }

  GeometryLease acquire(ModelInstance instance) {
    requireOwnerThread();
    if (closeRequested) throw new IllegalStateException("Prepared avatar geometry is closed");
    if (instance == null || instance.model != source)
      throw new IllegalArgumentException(
          "Avatar geometry can bind only instances of its exact source Model");
    NodePart part = findPart(instance, target);
    if (part.meshPart == null
        || part.meshPart.mesh != sourceMesh
        || part.meshPart.offset != sourceOffset
        || part.meshPart.size != sourceSize
        || part.meshPart.primitiveType != sourcePrimitiveType)
      throw new IllegalArgumentException(
          "Avatar target part no longer matches prepared source geometry: " + target);
    if (!layoutSignature.equals(layoutSignature(part.meshPart.mesh))
        || part.meshPart.mesh.getVertexSize() != sourceVertexSize)
      throw new IllegalArgumentException(
          "Avatar target vertex layout changed after preparation: " + target);
    List<String> instancePalette = paletteIds(part);
    if (part.bones == null
        || part.bones.length != paletteIds.size()
        || !paletteIds.equals(instancePalette))
      throw new IllegalArgumentException(
          "Avatar target bone palette changed after preparation: " + target);
    leases++;
    return new GeometryLease(this);
  }

  private void releaseLease() {
    if (leases <= 0) throw new IllegalStateException("Avatar geometry lease accounting underflow");
    leases--;
    if (closeRequested && leases == 0) disposeGeneratedMesh();
  }

  @Override
  public void close() {
    requireOwnerThread();
    if (closeRequested) return;
    closeRequested = true;
    if (leases == 0) disposeGeneratedMesh();
  }

  private void disposeGeneratedMesh() {
    if (disposed) return;
    disposed = true;
    generatedMesh.dispose();
  }

  private void requireOwnerThread() {
    if (Thread.currentThread() != ownerThread)
      throw new IllegalStateException(
          "Prepared avatar geometry must be used and closed on its creating GL thread");
  }

  private static NodePart findPart(Model source, PartKey key) {
    return findPart(source.nodes, key);
  }

  private static NodePart findPart(ModelInstance instance, PartKey key) {
    return findPart(instance.nodes, key);
  }

  private static NodePart findPart(com.badlogic.gdx.utils.Array<Node> roots, PartKey key) {
    Map<String, Node> paths = new HashMap<>();
    Set<String> ambiguous = new HashSet<>();
    for (Node node : roots) collect(node, node.id, paths, ambiguous);
    if (ambiguous.contains(key.nodePath()))
      throw new IllegalArgumentException("Ambiguous avatar node path: " + key.nodePath());
    Node node = paths.get(key.nodePath());
    if (node == null || key.partIndex() >= node.parts.size)
      throw new IllegalArgumentException(
          "Unknown avatar part " + key + "; available paths: " + paths.keySet());
    return node.parts.get(key.partIndex());
  }

  private static void collect(
      Node node, String path, Map<String, Node> paths, Set<String> ambiguous) {
    if (paths.putIfAbsent(path, node) != null) ambiguous.add(path);
    for (Node child : node.getChildren()) collect(child, path + "/" + child.id, paths, ambiguous);
  }

  private static String layoutSignature(Mesh mesh) {
    return layoutSignature(mesh.getVertexAttributes());
  }

  private static String layoutSignature(VertexAttributes attributes) {
    StringBuilder signature = new StringBuilder();
    for (var attribute : attributes)
      signature
          .append(attribute.usage)
          .append(':')
          .append(attribute.type)
          .append(':')
          .append(attribute.numComponents)
          .append(':')
          .append(attribute.normalized)
          .append(':')
          .append(attribute.alias)
          .append(':')
          .append(attribute.unit)
          .append(':')
          .append(attribute.offset)
          .append(';');
    return signature.toString();
  }

  private static List<String> paletteIds(NodePart part) {
    List<String> result = new ArrayList<>();
    if (part.invBoneBindTransforms != null)
      for (int i = 0; i < part.invBoneBindTransforms.size; i++)
        result.add(part.invBoneBindTransforms.getKeyAt(i).id);
    return result;
  }
}
