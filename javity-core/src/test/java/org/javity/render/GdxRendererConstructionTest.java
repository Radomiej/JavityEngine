package org.javity.render;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxNativesLoader;
import java.util.ArrayList;
import java.util.List;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.presentation.Presentation3D;
import org.junit.Test;

public class GdxRendererConstructionTest {
  static {
    GdxNativesLoader.load();
  }

  @Test
  public void failedConstructionCleanupContinuesAndSuppressesCleanupErrors() {
    RuntimeException original = new RuntimeException("construction failed");
    RuntimeException cleanup = new RuntimeException("dispose failed");
    List<String> disposed = new ArrayList<>();
    Disposable first = () -> disposed.add("first");
    Disposable broken =
        () -> {
          disposed.add("broken");
          throw cleanup;
        };
    Disposable last = () -> disposed.add("last");

    GdxRenderer.disposeAfterConstructionFailure(original, first, broken, last);

    assertEquals(List.of("first", "broken", "last"), disposed);
    assertSame(cleanup, original.getSuppressed()[0]);
    assertEquals("construction failed", original.getMessage());
  }

  @Test
  public void composedModelMatrixRejectsOverflowBeforeChangingOutputAndKeepsLargeFiniteInput() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("finite presentation");
      Transform3D transform = object.addComponent(new Transform3D());
      transform.scale.x = 256;
      Presentation3D provider = object.addComponent(new Presentation3D());
      game.start();
      Presentation3D.Lease lease = provider.contribute();
      lease.set(Float.MAX_VALUE / 128f, 0, 0, 0, 0, 0, 0, 0, 0);
      Presentation3D.Pose pose = new Presentation3D.Pose();
      provider.snapshot(pose);
      Matrix4 canonical = new Matrix4(transform.worldMatrix());
      for (float value : pose.localMatrix(new Matrix4()).val) assertTrue(Float.isFinite(value));
      Matrix4 out = new Matrix4().setToTranslation(9, 8, 7);
      Matrix4 before = new Matrix4(out);
      assertThrows(
          IllegalArgumentException.class,
          () ->
              GdxRenderer.composePresentedBase(canonical, pose, out, new Matrix4(), new Matrix4()));
      assertArrayEquals(before.val, out.val, 0f);

      lease.set(1024, 0, 0, 0, 0, 0, 0, 0, 0);
      provider.snapshot(pose);
      GdxRenderer.composePresentedBase(canonical, pose, out, new Matrix4(), new Matrix4());
      for (float value : out.val) assertTrue(Float.isFinite(value));
      assertEquals(262144, out.getTranslation(new com.badlogic.gdx.math.Vector3()).x, 0);
    }
  }

  @Test
  public void rigidNodeCompositionRejectsOverflowBeforeChangingInstanceBase() {
    Model model = new Model();
    Node source = new Node();
    source.id = "large rigid node";
    source.translation.x = Float.MAX_VALUE / 2f;
    NodePart sourcePart = new NodePart(new MeshPart(), new Material());
    sourcePart.enabled = true;
    source.parts.add(sourcePart);
    model.nodes.add(source);
    ModelInstance instance = new ModelInstance(model);
    Node node = instance.nodes.first();
    NodePart part = node.parts.first();
    Matrix4 priorBase = new Matrix4(instance.transform);
    Renderable before = instance.getRenderable(new Renderable(), node, part);
    assertTrue(allFinite(before.worldTransform));
    assertEquals(Float.MAX_VALUE / 2f, before.worldTransform.val[Matrix4.M03], 0);

    Matrix4 presentedScale = new Matrix4().setToScaling(4, 1, 1);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            GdxRenderer.validateModelInstanceComposition(instance, presentedScale, new Matrix4()));
    assertArrayEquals(priorBase.val, instance.transform.val, 0);
    Renderable after = instance.getRenderable(new Renderable(), node, part);
    assertArrayEquals(before.worldTransform.val, after.worldTransform.val, 0);
    assertTrue(allFinite(after.worldTransform));

    node.translation.x = 1_000_000;
    node.calculateTransforms(true);
    GdxRenderer.validateModelInstanceComposition(instance, presentedScale, new Matrix4());
    Matrix4 supportedOutput = new Matrix4(presentedScale).mul(node.globalTransform);
    assertTrue(allFinite(supportedOutput));
    assertArrayEquals(priorBase.val, instance.transform.val, 0);
  }

  @Test
  public void skinnedPaletteValidationFollowsActualVertexWeights() {
    Model model = new Model();
    Mesh mesh =
        new Mesh(
            Mesh.VertexDataType.VertexArray,
            false,
            3,
            3,
            VertexAttribute.Position(),
            VertexAttribute.BoneWeight(0));
    float[] vertices = {
      1, 0, 0, 0, 1,
      0, 1, 0, 0, 1,
      0, 0, 1, 0, 1
    };
    mesh.setVertices(vertices);
    mesh.setIndices(new short[] {0, 1, 2});
    Node source = new Node();
    source.id = "skinned node";
    MeshPart meshPart = new MeshPart("triangle", mesh, 0, 3, GL20.GL_TRIANGLES);
    NodePart sourcePart = new NodePart(meshPart, new Material());
    sourcePart.enabled = true;
    source.parts.add(sourcePart);
    model.nodes.add(source);
    try {
      ModelInstance instance = new ModelInstance(model);
      Node node = instance.nodes.first();
      NodePart part = node.parts.first();
      part.bones =
          new Matrix4[] {new Matrix4(), new Matrix4().setToTranslation(Float.MAX_VALUE / 2f, 0, 0)};
      Matrix4 priorBase = new Matrix4(instance.transform);
      float[] priorBones0 = part.bones[0].val.clone();
      float[] priorBones1 = part.bones[1].val.clone();
      Renderable renderable = instance.getRenderable(new Renderable(), node, part);
      assertNotNull(renderable.bones);
      assertTrue(allFinite(renderable.worldTransform));
      assertArrayEquals(priorBase.val, renderable.worldTransform.val, 0);

      Matrix4 presentedScale = new Matrix4().setToScaling(4, 1, 1);
      GdxRenderer.validateModelInstanceComposition(instance, presentedScale, new Matrix4());
      assertArrayEquals(priorBase.val, instance.transform.val, 0);
      assertArrayEquals(priorBones0, part.bones[0].val, 0);
      assertArrayEquals(priorBones1, part.bones[1].val, 0);
      assertArrayEquals(priorBase.val, renderable.worldTransform.val, 0);

      int weightOffset = mesh.getVertexAttributes().getOffset(Usage.BoneWeight, 0);
      float[] stored = new float[vertices.length];
      mesh.getVertices(stored);
      for (int vertex = 0; vertex < 3; vertex++) {
        int index = vertex * 5;
        assertEquals(0, stored[index + weightOffset], 0);
        assertEquals(1, stored[index + weightOffset + 1], 0);
        Vector3 result =
            new Vector3(stored[index], stored[index + 1], stored[index + 2])
                .mul(part.bones[0])
                .mul(presentedScale);
        assertTrue(Float.isFinite(result.x));
        assertTrue(Float.isFinite(result.y));
        assertTrue(Float.isFinite(result.z));
      }

      Node disabledNode = new Node();
      disabledNode.id = "disabled overflowing rigid node";
      disabledNode.translation.x = Float.MAX_VALUE / 2f;
      NodePart disabledPart = new NodePart(new MeshPart(), new Material());
      disabledPart.enabled = false;
      disabledNode.parts.add(disabledPart);
      instance.nodes.add(disabledNode);
      disabledNode.calculateTransforms(true);
      GdxRenderer.validateModelInstanceComposition(instance, presentedScale, new Matrix4());
      assertArrayEquals(priorBase.val, instance.transform.val, 0);
    } finally {
      mesh.dispose();
    }
  }

  private static boolean allFinite(Matrix4 matrix) {
    for (float value : matrix.val) if (!Float.isFinite(value)) return false;
    return true;
  }
}
