package org.javity.render;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.Test;

public class SkinnedBoundsTest {
  private Renderable fixture(int vertexCount, short[] indices) {
    GdxNativesLoader.load();
    Renderable r = new Renderable();
    Mesh mesh =
        new Mesh(
            Mesh.VertexDataType.VertexArray,
            true,
            vertexCount,
            indices.length,
            VertexAttribute.Position(),
            VertexAttribute.Normal(),
            VertexAttribute.BoneWeight(0));
    float[] data = new float[vertexCount * 8];
    for (int i = 0; i < vertexCount; i++) {
      data[i * 8] = i == vertexCount - 1 ? 2 : 0;
      data[i * 8 + 1] = i == vertexCount - 2 ? 1 : 0;
      data[i * 8 + 5] = 1;
      data[i * 8 + 7] = 1;
    }
    mesh.setVertices(data);
    if (indices.length > 0) mesh.setIndices(indices);
    r.meshPart.mesh = mesh;
    r.meshPart.offset = 0;
    r.meshPart.size = indices.length > 0 ? indices.length : vertexCount;
    r.meshPart.primitiveType = GL20.GL_TRIANGLES;
    r.material = new Material();
    r.bones = new Matrix4[] {new Matrix4()};
    return r;
  }

  @Test
  public void unsignedIndicesAbove32767AndSelectedRange() {
    Renderable r = fixture(40000, new short[] {0, 0, 0, (short) 39998, (short) 39999, 0});
    try {
      r.meshPart.offset = 3;
      r.meshPart.size = 3;
      var box = new SkinnedBounds(r).world(r);
      assertEquals(2, box.max.x, 0);
      assertEquals(1, box.max.y, 0);
      r.meshPart.offset = 0;
      assertEquals(0, new SkinnedBounds(r).world(r).max.x, 0);
    } finally {
      r.meshPart.mesh.dispose();
    }
  }

  @Test
  public void animatedBoneLeavesBindBoundsAndReentersCamera() {
    Renderable r = fixture(3, new short[0]);
    try {
      OrthographicCamera camera = new OrthographicCamera(4, 4);
      camera.position.set(0, 0, 10);
      camera.lookAt(0, 0, 0);
      camera.near = .1f;
      camera.far = 20;
      camera.update();
      r.worldTransform.setToTranslation(20, 0, 0);
      SkinnedBounds b = new SkinnedBounds(r);
      assertFalse(camera.frustum.boundsInFrustum(b.world(r)));
      r.bones[0].setToTranslation(-20, 0, 0);
      assertTrue(camera.frustum.boundsInFrustum(b.world(r)));
      assertTrue(b.world(r).contains(new Vector3(2, 0, 0)));
    } finally {
      r.meshPart.mesh.dispose();
    }
  }

  @Test
  public void negativeWeightsAndInvalidIndicesReject() {
    Renderable r = fixture(3, new short[] {0, 1, 2});
    try {
      float[] data = r.meshPart.mesh.getVertices(new float[24]);
      data[7] = -1;
      r.meshPart.mesh.setVertices(data);
      assertThrows(IllegalArgumentException.class, () -> new SkinnedBounds(r));
      data[7] = 1;
      data[6] = 1;
      r.meshPart.mesh.setVertices(data);
      assertThrows(IllegalArgumentException.class, () -> new SkinnedBounds(r));
      data[6] = 0;
      r.meshPart.mesh.setVertices(data);
      r.meshPart.mesh.setIndices(new short[] {0, 1, 4});
      assertThrows(IllegalArgumentException.class, () -> new SkinnedBounds(r));
    } finally {
      r.meshPart.mesh.dispose();
    }
  }

  @Test
  public void uploadedRangeGuardsStaticAndAnimatedPaths() {
    Renderable r = fixture(3, new short[0]);
    try {
      for (int offset : new int[] {-1, 1, Integer.MAX_VALUE}) {
        r.meshPart.offset = offset;
        assertEquals("INVALID_MESH_RANGE", InstanceGroupKey.rejectionReason(r, null, true));
        r.bones = null;
        assertEquals("INVALID_MESH_RANGE", InstanceGroupKey.rejectionReason(r, null));
        r.bones = new Matrix4[] {new Matrix4()};
      }
    } finally {
      r.meshPart.mesh.dispose();
    }
  }

  @Test
  public void batchWidePaletteBudgetStillAllowsExactDuplicates() {
    BonePalette p = new BonePalette(1, 4);
    p.beginFrame(1);
    Matrix4[] first = {new Matrix4()}, other = {new Matrix4().setToTranslation(2, 0, 0)};
    assertNotNull(p.put(first, true));
    assertNotNull(p.put(first, false));
    assertNull(p.put(other, false));
    assertEquals(1, p.rowCount());
    p.dispose();
  }
}
