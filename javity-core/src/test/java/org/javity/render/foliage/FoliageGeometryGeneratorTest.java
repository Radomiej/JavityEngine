package org.javity.render.foliage;

import static org.junit.Assert.*;

import com.badlogic.gdx.math.collision.BoundingBox;
import java.util.Arrays;
import org.junit.Test;

public class FoliageGeometryGeneratorTest {
  @Test
  public void seededGeometryIsRepeatableAndSnapshotIndependent() {
    FoliagePatch3D patch = FoliagePatch3D.grass(FoliagePatch3DTest.atlas()).count(17).seed(42);
    FoliagePatch3D.Snapshot snapshot = patch.snapshot();
    FoliageGeometryGenerator.Geometry first = FoliageGeometryGenerator.generate(snapshot);
    assertEquals(17 * 12, first.vertexCount());
    assertEquals(17 * 18, first.indexCount());
    patch.seed(43).count(1);
    FoliageGeometryGenerator.Geometry second = FoliageGeometryGenerator.generate(snapshot);
    assertArrayEquals(first.chunks().get(0).vertices(), second.chunks().get(0).vertices(), 0);
    assertArrayEquals(first.chunks().get(0).indices(), second.chunks().get(0).indices());
    assertFalse(
        Arrays.equals(
            first.chunks().get(0).vertices(),
            FoliageGeometryGenerator.generate(patch.snapshot()).chunks().get(0).vertices()));
    FoliageGeometryGenerator.Geometry changedSeed =
        FoliageGeometryGenerator.generate(
            FoliagePatch3D.grass(FoliagePatch3DTest.atlas()).count(17).seed(43).snapshot());
    assertFalse(
        Arrays.equals(first.chunks().get(0).vertices(), changedSeed.chunks().get(0).vertices()));
  }

  @Test
  public void splitsAtUnsignedVertexRangeInsteadOfIndexCount() {
    FoliageGeometryGenerator.Geometry grass =
        FoliageGeometryGenerator.generate(
            FoliagePatch3D.grass(FoliagePatch3DTest.atlas()).count(4096).snapshot());
    assertEquals(1, grass.chunks().size());
    assertTrue(grass.indexCount() > 65535);
    FoliageGeometryGenerator.Geometry bushes =
        FoliageGeometryGenerator.generate(
            FoliagePatch3D.bushes(FoliagePatch3DTest.atlas()).count(4096).snapshot());
    assertEquals(3, bushes.chunks().size());
    assertEquals(196608, bushes.vertexCount());
    boolean usesUpperUnsignedHalf = false;
    for (FoliageGeometryGenerator.Chunk chunk : bushes.chunks()) {
      assertEquals(65536, chunk.vertexCount());
      assertTrue(chunk.indexCount() > 65535);
      for (short index : chunk.indices()) {
        int unsigned = index & 0xffff;
        assertTrue(unsigned < chunk.vertexCount());
        usesUpperUnsignedHalf |= unsigned > 32767;
      }
    }
    assertTrue(usesUpperUnsignedHalf);
  }

  @Test
  public void geometryHasFiniteNormalsUvNondegenerateTrianglesAndCompleteBounds() {
    for (FoliagePatch3D.Preset preset : FoliagePatch3D.Preset.values()) {
      FoliagePatch3D patch =
          preset == FoliagePatch3D.Preset.GRASS
              ? FoliagePatch3D.grass(FoliagePatch3DTest.atlas())
              : FoliagePatch3D.bushes(FoliagePatch3DTest.atlas());
      FoliageGeometryGenerator.Geometry geometry =
          FoliageGeometryGenerator.generate(patch.area(2, 3).count(32).scaleRange(2, 2).snapshot());
      BoundingBox bounds = geometry.bounds();
      assertTrue(bounds.isValid());
      assertTrue(bounds.max.y > (preset == FoliagePatch3D.Preset.GRASS ? .6f : 1.4f));
      for (FoliageGeometryGenerator.Chunk chunk : geometry.chunks()) {
        float[] vertices = chunk.vertices();
        for (int i = 0; i < vertices.length; i += 8) {
          for (int k = 0; k < 8; k++) assertTrue(Float.isFinite(vertices[i + k]));
          assertTrue(vertices[i] >= bounds.min.x && vertices[i] <= bounds.max.x);
          assertTrue(vertices[i + 1] >= bounds.min.y && vertices[i + 1] <= bounds.max.y);
          assertTrue(vertices[i + 2] >= bounds.min.z && vertices[i + 2] <= bounds.max.z);
          assertEquals(
              1,
              vertices[i + 3] * vertices[i + 3]
                  + vertices[i + 4] * vertices[i + 4]
                  + vertices[i + 5] * vertices[i + 5],
              1e-5);
          assertTrue(vertices[i + 4] > 0);
          assertTrue(vertices[i + 6] >= 0 && vertices[i + 6] <= 1);
          assertTrue(vertices[i + 7] >= 0 && vertices[i + 7] <= 1);
        }
        short[] indices = chunk.indices();
        for (int i = 0; i < indices.length; i += 3) {
          int a = (indices[i] & 0xffff) * 8,
              b = (indices[i + 1] & 0xffff) * 8,
              c = (indices[i + 2] & 0xffff) * 8;
          float ax = vertices[b] - vertices[a],
              ay = vertices[b + 1] - vertices[a + 1],
              az = vertices[b + 2] - vertices[a + 2];
          float bx = vertices[c] - vertices[a],
              by = vertices[c + 1] - vertices[a + 1],
              bz = vertices[c + 2] - vertices[a + 2];
          float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
          assertTrue(nx * nx + ny * ny + nz * nz > 1e-8f);
          assertTrue(nx * vertices[a + 3] + ny * vertices[a + 4] + nz * vertices[a + 5] > 0);
        }
      }
    }
  }

  @Test
  public void zeroCountHasNoChunksAndReturnedBuffersAndBoundsAreDefensiveCopies() {
    FoliageGeometryGenerator.Geometry empty =
        FoliageGeometryGenerator.generate(
            FoliagePatch3D.grass(FoliagePatch3DTest.atlas()).count(0).snapshot());
    assertTrue(empty.chunks().isEmpty());
    assertEquals(0, empty.vertexCount());
    assertEquals(0, empty.indexCount());
    assertEquals(0, empty.bounds().max.len(), 0);
    FoliageGeometryGenerator.Geometry geometry =
        FoliageGeometryGenerator.generate(
            FoliagePatch3D.bushes(FoliagePatch3DTest.atlas()).count(1).snapshot());
    float[] cardVertices = geometry.chunks().get(0).vertices();
    // Root samples the atlas bottom; the growing tip samples its top.
    assertTrue(cardVertices[7] > cardVertices[23]);
    assertEquals(cardVertices[6], cardVertices[30], 0);
    assertEquals(cardVertices[14], cardVertices[22], 0);
    float original = cardVertices[0];
    geometry.chunks().get(0).vertices()[0] = Float.NaN;
    geometry.chunks().get(0).indices()[0] = 9;
    geometry.bounds().max.setZero();
    assertEquals(original, geometry.chunks().get(0).vertices()[0], 0);
    assertEquals(0, geometry.chunks().get(0).indices()[0]);
    assertTrue(geometry.bounds().max.y > .5f);
    assertThrows(UnsupportedOperationException.class, () -> geometry.chunks().clear());
  }

  @Test
  public void largestAcceptedDimensionsProduceOnlyFiniteCoordinatesAndBounds() {
    FoliageGeometryGenerator.Geometry geometry =
        FoliageGeometryGenerator.generate(
            FoliagePatch3D.bushes(FoliagePatch3DTest.atlas())
                .count(2)
                .area(Float.MAX_VALUE / 16, Float.MAX_VALUE / 16)
                .scaleRange(Float.MAX_VALUE / 16, Float.MAX_VALUE / 16)
                .snapshot());
    for (float value : geometry.chunks().get(0).vertices()) assertTrue(Float.isFinite(value));
    BoundingBox bounds = geometry.bounds();
    assertTrue(Float.isFinite(bounds.min.x));
    assertTrue(Float.isFinite(bounds.max.y));
    assertTrue(Float.isFinite(bounds.max.z));
  }
}
