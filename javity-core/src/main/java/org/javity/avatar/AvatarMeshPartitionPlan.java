package org.javity.avatar;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import java.util.*;

/** CPU-only validation and stable unsigned-short index regrouping for one authored NodePart. */
final class AvatarMeshPartitionPlan {
  final float[] vertices;
  final VertexAttribute[] attributes;
  final short[] indices;
  final LinkedHashMap<String, Range> ranges;
  final Mesh sourceMesh;
  final MeshPart sourcePart;
  final String layoutSignature;
  final List<String> paletteIds;
  final int vertexCount;
  final int sourceVertexSize;
  final int sourceOffset;
  final int sourceSize;
  final int primitiveType;

  record Range(int offset, int size) {}

  /**
   * Primitive metadata allows the planner tests to exercise all checks without constructing Mesh/GL
   * objects.
   */
  record CpuInput(
      VertexAttribute[] attributes,
      int vertexSize,
      float[] vertices,
      short[] indices,
      int vertexCount,
      int partOffset,
      int partSize,
      int primitiveType,
      List<String> paletteIds) {}

  private AvatarMeshPartitionPlan(
      CpuInput input,
      LinkedHashMap<String, Range> ranges,
      short[] indices,
      Mesh sourceMesh,
      MeshPart sourcePart,
      String layoutSignature) {
    this.vertices = input.vertices().clone();
    this.attributes = copyAttributes(input.attributes());
    this.indices = indices;
    this.ranges = ranges;
    this.sourceMesh = sourceMesh;
    this.sourcePart = sourcePart;
    this.layoutSignature = layoutSignature;
    this.paletteIds = List.copyOf(input.paletteIds());
    this.vertexCount = input.vertexCount();
    this.sourceVertexSize = input.vertexSize();
    this.sourceOffset = input.partOffset();
    this.sourceSize = input.partSize();
    this.primitiveType = input.primitiveType();
  }

  static AvatarMeshPartitionPlan create(NodePart target, Map<String, int[]> requested) {
    Objects.requireNonNull(target, "target");
    MeshPart part = target.meshPart;
    if (part == null || part.mesh == null) throw fail("target has no indexed source MeshPart");
    Mesh mesh = part.mesh;
    if (mesh.isInstanced()) throw fail("instanced meshes are unsupported");
    List<String> palette = paletteIdsFromBindMap(target);
    var va = mesh.getVertexAttributes();
    int vertexCount = mesh.getNumVertices();
    int floatCount = Math.multiplyExact(vertexCount, va.vertexSize / 4);
    float[] vertices = mesh.getVertices(new float[floatCount]);
    short[] indices = new short[mesh.getNumIndices()];
    mesh.getIndices(indices);
    CpuInput input =
        new CpuInput(
            copyAttributes(attributesArray(va)),
            va.vertexSize,
            vertices,
            indices,
            vertexCount,
            part.offset,
            part.size,
            part.primitiveType,
            palette);
    AvatarMeshPartitionPlan result = create(input, requested);
    return new AvatarMeshPartitionPlan(
        input, result.ranges, result.indices, mesh, part, result.layoutSignature);
  }

  static List<String> paletteIdsFromBindMap(NodePart target) {
    if (target.invBoneBindTransforms == null || target.invBoneBindTransforms.size == 0)
      throw fail("target NodePart has no bone bind map");
    List<String> palette = new ArrayList<>(target.invBoneBindTransforms.size);
    Set<String> uniquePalette = new HashSet<>();
    for (int i = 0; i < target.invBoneBindTransforms.size; i++) {
      Node bone = target.invBoneBindTransforms.getKeyAt(i);
      if (bone == null) throw fail("bone bind-map key at index " + i + " is null");
      String id = bone.id;
      if (id == null || id.isBlank() || !uniquePalette.add(id))
        throw fail("ambiguous bone bind-map id '" + id + "'");
      var inverseBind = target.invBoneBindTransforms.getValueAt(i);
      if (inverseBind == null) throw fail("inverse bind transform for bone '" + id + "' is null");
      for (float value : inverseBind.val)
        if (!Float.isFinite(value))
          throw fail("inverse bind transform for bone '" + id + "' contains a nonfinite value");
      palette.add(id);
    }
    return List.copyOf(palette);
  }

  static void validatePaletteMembership(Model source, NodePart target) {
    List<String> palette = paletteIdsFromBindMap(target);
    Set<Node> sourceNodes = Collections.newSetFromMap(new IdentityHashMap<>());
    Map<String, Integer> idCounts = new HashMap<>();
    for (Node root : source.nodes) collectBoneNodes(root, sourceNodes, idCounts);
    for (int i = 0; i < target.invBoneBindTransforms.size; i++) {
      Node bone = target.invBoneBindTransforms.getKeyAt(i);
      String id = palette.get(i);
      if (!sourceNodes.contains(bone))
        throw fail("bone bind node '" + id + "' is outside the source Model node tree");
      if (idCounts.getOrDefault(id, 0) != 1)
        throw fail("bone bind-map id '" + id + "' is ambiguous in the source Model node tree");
    }
  }

  private static void collectBoneNodes(Node node, Set<Node> nodes, Map<String, Integer> idCounts) {
    if (!nodes.add(node)) return;
    if (node.id != null) idCounts.merge(node.id, 1, Integer::sum);
    for (Node child : node.getChildren()) collectBoneNodes(child, nodes, idCounts);
  }

  static AvatarMeshPartitionPlan create(CpuInput input, Map<String, int[]> requested) {
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(requested, "triangleRegions");
    if (input.primitiveType() != GL20.GL_TRIANGLES) throw fail("only GL_TRIANGLES is supported");
    if (input.partOffset() < 0
        || input.partSize() <= 0
        || input.partOffset() + input.partSize() > input.indices().length)
      throw fail(
          "invalid or empty MeshPart index range " + input.partOffset() + "+" + input.partSize());
    if (input.partSize() % 3 != 0) throw fail("MeshPart index count must be divisible by three");
    if (input.indices().length == 0) throw fail("nonindexed meshes are unsupported");
    int triangles = input.partSize() / 3;
    if (requested.isEmpty()) throw fail("at least one authored region is required");

    LinkedHashMap<String, int[]> assignments = new LinkedHashMap<>();
    int[] ownership = new int[triangles];
    Arrays.fill(ownership, -1);
    for (var region : requested.entrySet()) {
      String name = region.getKey();
      int[] ordinals = region.getValue();
      if (name == null || name.isBlank()) throw fail("region names must be nonempty");
      if (!name.equals(name.trim()))
        throw fail("region names must not contain surrounding whitespace: '" + name + "'");
      if (ordinals == null || ordinals.length == 0) throw fail("region '" + name + "' is empty");
      int[] copy = ordinals.clone();
      Arrays.sort(copy);
      for (int ordinal : copy) {
        if (ordinal < 0 || ordinal >= triangles)
          throw fail("region '" + name + "' has out-of-range triangle ordinal " + ordinal);
        if (ownership[ordinal] != -1)
          throw fail("triangle ordinal " + ordinal + " is assigned more than once");
        ownership[ordinal] = assignments.size();
      }
      assignments.put(name, copy);
    }
    List<Integer> missing = new ArrayList<>();
    for (int i = 0; i < ownership.length; i++) if (ownership[i] < 0) missing.add(i);
    if (!missing.isEmpty()) throw fail("unassigned triangle ordinals " + sample(missing));

    VertexAttribute[] sourceAttributes = input.attributes();
    if (input.vertexSize() <= 0 || input.vertexSize() % 4 != 0)
      throw fail("vertex stride must be a positive multiple of four bytes");
    if (input.vertices().length != input.vertexCount() * (input.vertexSize() / 4))
      throw fail("raw vertex buffer length does not match vertex count and stride");
    int positionCount = 0;
    int weightCount = 0;
    int expectedWeightUnit = 0;
    VertexAttributes recomputedLayout = new VertexAttributes(sourceAttributes);
    if (recomputedLayout.vertexSize != input.vertexSize())
      throw fail("copied vertex descriptors do not reproduce source stride");
    VertexAttribute[] attrs = sourceAttributes;
    StringBuilder signature = new StringBuilder();
    for (VertexAttribute attr : attrs) {
      signature
          .append(attr.usage)
          .append(':')
          .append(attr.type)
          .append(':')
          .append(attr.numComponents)
          .append(':')
          .append(attr.normalized)
          .append(':')
          .append(attr.alias)
          .append(':')
          .append(attr.unit)
          .append(':')
          .append(attr.offset)
          .append(';');
      if (attr.offset % 4 != 0 || attr.getSizeInBytes() % 4 != 0)
        throw fail("attribute '" + attr.alias + "' is not four-byte aligned");
      if (attr.usage == VertexAttributes.Usage.Position) {
        positionCount++;
        if (attr.type != GL20.GL_FLOAT || attr.numComponents != 3)
          throw fail("position must be float vec3");
      }
      if (attr.usage == VertexAttributes.Usage.BoneWeight) {
        if (attr.type != GL20.GL_FLOAT || attr.numComponents != 2)
          throw fail("BoneWeight" + attr.unit + " must be float vec2");
        if (attr.unit != expectedWeightUnit++)
          throw fail("BoneWeight units must be contiguous from zero");
        weightCount++;
      }
    }
    if (positionCount != 1) throw fail("exactly one float vec3 position attribute is required");
    if (weightCount == 0) throw fail("target NodePart is not a weighted skinned mesh");
    if (input.paletteIds() == null || input.paletteIds().isEmpty())
      throw fail("target NodePart has no bone bind map");
    Set<String> paletteUnique = new HashSet<>();
    for (String id : input.paletteIds())
      if (id == null || !paletteUnique.add(id))
        throw fail("ambiguous bone bind-map id '" + id + "'");

    int vertexCount = input.vertexCount();
    if (vertexCount <= 0 || vertexCount > 65536)
      throw fail("source vertex count exceeds unsigned-short range");
    int strideFloats = input.vertexSize() / 4;
    for (int i = input.partOffset(); i < input.partOffset() + input.partSize(); i++) {
      int vertex = input.indices()[i] & 0xffff;
      if (vertex >= vertexCount)
        throw fail("unsigned-short vertex index " + vertex + " exceeds " + vertexCount);
      int base = vertex * strideFloats + positionOffset(attrs);
      for (int component = 0; component < 3; component++)
        if (!Float.isFinite(input.vertices()[base + component]))
          throw fail("vertex " + vertex + " has a nonfinite position component");
      validateWeights(input.vertices(), vertex, strideFloats, attrs, input.paletteIds().size());
    }

    int[] ordered = new int[input.partSize()];
    LinkedHashMap<String, Range> ranges = new LinkedHashMap<>();
    int cursor = 0;
    for (var region : assignments.entrySet()) {
      int start = cursor;
      for (int ordinal : region.getValue()) {
        int triStart = input.partOffset() + ordinal * 3;
        for (int corner = 0; corner < 3; corner++) ordered[cursor++] = triStart + corner;
      }
      ranges.put(region.getKey(), new Range(start, cursor - start));
    }
    short[] regrouped = new short[ordered.length];
    for (int i = 0; i < ordered.length; i++) regrouped[i] = input.indices()[ordered[i]];
    return new AvatarMeshPartitionPlan(input, ranges, regrouped, null, null, signature.toString());
  }

  private static VertexAttribute[] copyAttributes(VertexAttribute[] input) {
    VertexAttribute[] result = new VertexAttribute[input.length];
    for (int i = 0; i < input.length; i++) result[i] = input[i].copy();
    return result;
  }

  private static VertexAttribute[] attributesArray(VertexAttributes attributes) {
    VertexAttribute[] result = new VertexAttribute[attributes.size()];
    for (int i = 0; i < result.length; i++) result[i] = attributes.get(i);
    return result;
  }

  private static int positionOffset(VertexAttribute[] attributes) {
    for (VertexAttribute attribute : attributes)
      if (attribute.usage == VertexAttributes.Usage.Position) return attribute.offset / 4;
    throw fail("missing position attribute");
  }

  private static void validateWeights(
      float[] vertices,
      int vertex,
      int strideFloats,
      VertexAttribute[] attributes,
      int paletteSize) {
    int base = vertex * strideFloats;
    float total = 0;
    for (VertexAttribute attribute : attributes) {
      if (attribute.usage != VertexAttributes.Usage.BoneWeight) continue;
      int offset = base + attribute.offset / 4;
      float paletteIndex = vertices[offset];
      float weight = vertices[offset + 1];
      if (!Float.isFinite(paletteIndex)
          || paletteIndex < 0
          || paletteIndex != Math.rint(paletteIndex)
          || paletteIndex >= paletteSize)
        throw fail("vertex " + vertex + " has invalid bone palette index " + paletteIndex);
      if (!Float.isFinite(weight) || weight < 0)
        throw fail("vertex " + vertex + " has invalid bone weight " + weight);
      total += weight;
    }
    if (!(total > 0) || !Float.isFinite(total))
      throw fail("vertex " + vertex + " has zero or invalid total bone weight");
  }

  private static String sample(List<Integer> items) {
    int count = Math.min(items.size(), 12);
    return items.subList(0, count) + (items.size() > count ? " (" + items.size() + " total)" : "");
  }

  private static IllegalArgumentException fail(String reason) {
    return new IllegalArgumentException("Avatar mesh partition preflight failed: " + reason);
  }
}
