package org.javity.avatar;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.ArrayMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class AvatarMeshPartitionTest {
  @Test
  public void readsOrdinarySourceBindMapBeforeModelInstanceCreatesBones() {
    NodePart sourcePart = new NodePart();
    sourcePart.invBoneBindTransforms = new ArrayMap<>(true, 1, Node.class, Matrix4.class);
    Node root = new Node();
    root.id = "root";
    sourcePart.invBoneBindTransforms.put(root, new Matrix4());

    assertNull(sourcePart.bones);
    assertEquals(List.of("root"), AvatarMeshPartitionPlan.paletteIdsFromBindMap(sourcePart));
    assertNull(sourcePart.bones);
  }

  @Test
  public void rejectsMalformedBindMapKeysAndMatricesWithoutMutatingValidBindData() {
    Node root = node("root");
    NodePart sourcePart = bindPart(root, new Matrix4().setToTranslation(2, -3, 4));
    float[] original = sourcePart.invBoneBindTransforms.getValueAt(0).val.clone();
    assertEquals(List.of("root"), AvatarMeshPartitionPlan.paletteIdsFromBindMap(sourcePart));
    assertArrayEquals(original, sourcePart.invBoneBindTransforms.getValueAt(0).val, 0);

    sourcePart.invBoneBindTransforms.setKey(0, null);
    assertTrue(partitionFailure(sourcePart).contains("bind-map key at index 0 is null"));

    sourcePart.invBoneBindTransforms.setKey(0, root);
    sourcePart.invBoneBindTransforms.setValue(0, null);
    assertTrue(
        partitionFailure(sourcePart).contains("inverse bind transform for bone 'root' is null"));

    Matrix4 nonfinite = new Matrix4();
    nonfinite.val[Matrix4.M00] = Float.NaN;
    sourcePart.invBoneBindTransforms.setValue(0, nonfinite);
    assertTrue(partitionFailure(sourcePart).contains("contains a nonfinite value"));
    nonfinite.val[Matrix4.M00] = Float.POSITIVE_INFINITY;
    assertTrue(partitionFailure(sourcePart).contains("contains a nonfinite value"));

    sourcePart.invBoneBindTransforms.setValue(0, new Matrix4().setToTranslation(2, -3, 4));
    float[] finalValid = sourcePart.invBoneBindTransforms.getValueAt(0).val.clone();
    assertEquals(List.of("root"), AvatarMeshPartitionPlan.paletteIdsFromBindMap(sourcePart));
    assertArrayEquals(finalValid, sourcePart.invBoneBindTransforms.getValueAt(0).val, 0);
    assertNull(sourcePart.bones);
  }

  @Test
  public void requiresReferencedBindNodesToBeUniqueMembersOfSourceModelTree() {
    Node root = node("root");
    Node arm = node("arm");
    root.addChild(arm);
    Model source = new Model();
    source.nodes.add(root);
    NodePart sourcePart = bindPart(arm, new Matrix4());
    AvatarMeshPartitionPlan.validatePaletteMembership(source, sourcePart);

    Node outside = node("outside");
    sourcePart.invBoneBindTransforms.setKey(0, outside);
    assertTrue(
        membershipFailure(source, sourcePart).contains("outside the source Model node tree"));

    sourcePart.invBoneBindTransforms.setKey(0, arm);
    root.addChild(node("arm"));
    assertTrue(membershipFailure(source, sourcePart).contains("ambiguous in the source Model"));
  }

  @Test
  public void preservesFullVertexPayloadAndPartitionsOnlyNonzeroSourceRange() {
    AvatarMeshPartitionPlan.CpuInput input =
        input(4, new short[] {3, 2, 1, 0, 1, 2, 2, 3, 0, 1, 2, 3}, 3, 6);
    float[] vertices = input.vertices();
    for (int vertex = 0; vertex < 4; vertex++) {
      int base = vertex * 13;
      vertices[base] = vertex;
      vertices[base + 2] = .25f;
      vertices[base + 3] = 0;
      vertices[base + 5] = 1;
      vertices[base + 6] = Float.intBitsToFloat(0x7fc01234 + vertex);
      vertices[base + 7] = vertex * .1f;
      vertices[base + 8] = .9f;
      vertices[base + 9] = 0;
      vertices[base + 10] = .25f;
      vertices[base + 11] = 0;
      vertices[base + 12] = 0;
    }
    input =
        new AvatarMeshPartitionPlan.CpuInput(
            input.attributes(),
            input.vertexSize(),
            vertices,
            input.indices(),
            4,
            3,
            6,
            GL20.GL_TRIANGLES,
            List.of("bone"));
    LinkedHashMap<String, int[]> regions = new LinkedHashMap<>();
    regions.put("arms", new int[] {1});
    regions.put("body", new int[] {0});

    AvatarMeshPartitionPlan plan = AvatarMeshPartitionPlan.create(input, regions);
    assertEquals(4, plan.vertexCount);
    assertEquals(52, plan.vertices.length);
    assertEquals(0x7fc01234, Float.floatToRawIntBits(plan.vertices[6]));
    assertEquals(0x7fc01237, Float.floatToRawIntBits(plan.vertices[45]));
    assertEquals(3, plan.ranges.get("arms").size());
    assertEquals(3, plan.ranges.get("body").size());
    assertEquals(2, plan.indices[0] & 0xffff);
    assertEquals(3, plan.indices[1] & 0xffff);
    assertEquals(0, plan.indices[2] & 0xffff);
    assertEquals(0, plan.indices[3] & 0xffff);
    assertEquals(1, plan.indices[4] & 0xffff);
    assertEquals(2, plan.indices[5] & 0xffff);
    assertEquals(6, plan.attributes.length);
    assertEquals(VertexAttribute.BoneWeight(1), plan.attributes[5]);
  }

  @Test
  public void supportsUnsignedIndicesAbove32767AndIndexCountsAbove65535() {
    int count = 32769;
    float[] vertices = new float[count * 13];
    for (int vertex = 0; vertex < count; vertex++) {
      int base = vertex * 13;
      vertices[base + 2] = 1;
      vertices[base + 5] = 1;
      vertices[base + 9] = 0;
      vertices[base + 10] = 1;
      vertices[base + 11] = 0;
      vertices[base + 12] = 0;
    }
    int indexCount = 66000;
    short[] indices = new short[indexCount];
    for (int i = 0; i < indexCount; i += 3) {
      indices[i] = (short) 32768;
      indices[i + 1] = (short) 1;
      indices[i + 2] = (short) 2;
    }
    AvatarMeshPartitionPlan.CpuInput input = input(count, indices, 0, indexCount);
    input =
        new AvatarMeshPartitionPlan.CpuInput(
            input.attributes(),
            input.vertexSize(),
            vertices,
            indices,
            count,
            0,
            indexCount,
            GL20.GL_TRIANGLES,
            List.of("bone"));
    AvatarMeshPartitionPlan plan =
        AvatarMeshPartitionPlan.create(input, Map.of("all", ordinals(indexCount / 3)));
    assertEquals(indexCount, plan.indices.length);
    assertEquals(32768, plan.indices[0] & 0xffff);
  }

  @Test
  public void rejectsIncompleteOverlappingAndInvalidLayoutsBeforeUpload() {
    AvatarMeshPartitionPlan.CpuInput input = input(4, new short[] {0, 1, 2, 2, 3, 0}, 0, 6);
    float[] vertices = new float[52];
    for (int vertex = 0; vertex < 4; vertex++) {
      vertices[vertex * 13 + 2] = 1;
      vertices[vertex * 13 + 5] = 1;
      vertices[vertex * 13 + 9] = 0;
      vertices[vertex * 13 + 10] = 1;
      vertices[vertex * 13 + 11] = 0;
      vertices[vertex * 13 + 12] = 0;
    }
    input =
        new AvatarMeshPartitionPlan.CpuInput(
            input.attributes(),
            input.vertexSize(),
            vertices,
            input.indices(),
            4,
            0,
            6,
            GL20.GL_TRIANGLES,
            List.of("bone"));
    assertTrue(failure(input, Map.of("a", new int[] {0})).contains("unassigned"));
    LinkedHashMap<String, int[]> overlap = new LinkedHashMap<>();
    overlap.put("a", new int[] {0});
    overlap.put("b", new int[] {0, 1});
    assertTrue(failure(input, overlap).contains("assigned more than once"));
    assertTrue(
        failure(input, Map.of("a", new int[] {2}, "b", new int[] {0, 1})).contains("out-of-range"));

    AvatarMeshPartitionPlan.CpuInput malformed =
        input(
            3,
            new short[] {0, 1, 2},
            0,
            3,
            new VertexAttribute(
                VertexAttributes.Usage.Position, 2, GL20.GL_FLOAT, false, "a_position"),
            VertexAttribute.BoneWeight(0));
    assertTrue(failure(malformed, Map.of("a", new int[] {0})).contains("float vec3"));
    assertTrue(
        failure(input, Map.of(" padded ", new int[] {0, 1})).contains("surrounding whitespace"));
  }

  private static String failure(
      AvatarMeshPartitionPlan.CpuInput target, Map<String, int[]> regions) {
    try {
      AvatarMeshPartitionPlan.create(target, regions);
      fail("Expected partition preflight failure");
      return "";
    } catch (IllegalArgumentException expected) {
      return expected.getMessage();
    }
  }

  private static String partitionFailure(NodePart part) {
    try {
      AvatarMeshPartitionPlan.paletteIdsFromBindMap(part);
      fail("Expected bind-map preflight failure");
      return "";
    } catch (IllegalArgumentException expected) {
      return expected.getMessage();
    }
  }

  private static String membershipFailure(Model model, NodePart part) {
    try {
      AvatarMeshPartitionPlan.validatePaletteMembership(model, part);
      fail("Expected source-tree membership failure");
      return "";
    } catch (IllegalArgumentException expected) {
      return expected.getMessage();
    }
  }

  private static NodePart bindPart(Node bone, Matrix4 inverseBind) {
    NodePart part = new NodePart();
    part.invBoneBindTransforms = new ArrayMap<>(true, 1, Node.class, Matrix4.class);
    part.invBoneBindTransforms.put(bone, inverseBind);
    return part;
  }

  private static Node node(String id) {
    Node node = new Node();
    node.id = id;
    return node;
  }

  private static int[] ordinals(int count) {
    int[] values = new int[count];
    for (int i = 0; i < count; i++) values[i] = i;
    return values;
  }

  private static AvatarMeshPartitionPlan.CpuInput input(
      int vertices, short[] indices, int offset, int size, VertexAttribute... attributes) {
    if (attributes.length == 0)
      attributes =
          new VertexAttribute[] {
            VertexAttribute.Position(),
            VertexAttribute.Normal(),
            VertexAttribute.ColorPacked(),
            VertexAttribute.TexCoords(0),
            VertexAttribute.BoneWeight(0),
            VertexAttribute.BoneWeight(1)
          };
    VertexAttributes layout = new VertexAttributes(attributes);
    return new AvatarMeshPartitionPlan.CpuInput(
        attributes,
        layout.vertexSize,
        new float[vertices * (layout.vertexSize / 4)],
        indices,
        vertices,
        offset,
        size,
        GL20.GL_TRIANGLES,
        List.of("bone"));
  }
}
