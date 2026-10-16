package org.javity.render;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.math.collision.BoundingBox;
import java.util.*;

/** Immutable per-part positive-influence boxes. Source mesh edits require explicit invalidation. */
final class SkinnedBounds {
  final BoundingBox[] bones;

  SkinnedBounds(Renderable r) {
    Mesh mesh = r.meshPart.mesh;
    int stride = mesh.getVertexSize() / 4;
    int position = mesh.getVertexAttribute(VertexAttributes.Usage.Position).offset / 4;
    List<Integer> weights = new ArrayList<>();
    Set<Integer> units = new HashSet<>();
    for (VertexAttribute a : mesh.getVertexAttributes())
      if (a.usage == VertexAttributes.Usage.BoneWeight) {
        if (!units.add(a.unit) || !a.alias.equals("a_boneWeight" + a.unit))
          throw new IllegalArgumentException("INVALID_BONE_LAYOUT");
        weights.add(a.offset / 4);
      }
    for (int unit = 0; unit < units.size(); unit++)
      if (!units.contains(unit)) throw new IllegalArgumentException("SPARSE_BONE_WEIGHTS");
    if (weights.isEmpty()) throw new IllegalArgumentException("MISSING_BONE_WEIGHTS");
    bones = new BoundingBox[r.bones.length];
    for (int i = 0; i < bones.length; i++) bones[i] = new BoundingBox().inf();
    float[] vertices = mesh.getVertices(new float[mesh.getNumVertices() * stride]);
    short[] indices = new short[mesh.getNumIndices()];
    if (indices.length > 0) mesh.getIndices(indices);
    for (int at = r.meshPart.offset; at < r.meshPart.offset + r.meshPart.size; at++) {
      int vertex = indices.length == 0 ? at : indices[at] & 0xffff;
      if (vertex >= mesh.getNumVertices())
        throw new IllegalArgumentException("INVALID_VERTEX_INDEX");
      int base = vertex * stride;
      float x = vertices[base + position],
          y = vertices[base + position + 1],
          z = vertices[base + position + 2];
      if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))
        throw new IllegalArgumentException("NONFINITE_VERTEX");
      double sum = 0;
      for (int offset : weights) {
        float index = vertices[base + offset], weight = vertices[base + offset + 1];
        // Even zero weights fetch a matrix in DefaultShader, so every index must be in range.
        if (!Float.isFinite(index)
            || index < 0
            || index >= bones.length
            || index != (int) index
            || !Float.isFinite(weight)
            || weight < 0) throw new IllegalArgumentException("INVALID_BONE_WEIGHT");
        sum += weight;
        if (weight > 0) bones[(int) index].ext(x, y, z);
      }
      if (!(sum > 0) || !Double.isFinite(sum))
        throw new IllegalArgumentException("INVALID_BONE_WEIGHT_SUM");
    }
  }

  BoundingBox world(Renderable r) {
    BoundingBox result = new BoundingBox().inf();
    for (int i = 0; i < bones.length; i++) {
      Matrix4 matrix = r.bones[i];
      if (matrix == null) throw new IllegalArgumentException("INVALID_SKIN_MATRIX");
      for (float value : matrix.val)
        if (!Float.isFinite(value)) throw new IllegalArgumentException("NONFINITE_SKIN_MATRIX");
      float[] v = matrix.val;
      if (v[3] != 0 || v[7] != 0 || v[11] != 0 || v[15] != 1)
        throw new IllegalArgumentException("NONAFFINE_SKIN_MATRIX");
      if (bones[i].isValid()) result.ext(new BoundingBox(bones[i]).mul(matrix));
    }
    // Positive weight sums divide out in homogeneous projection: the result is a convex union.
    result.mul(r.worldTransform);
    if (!result.isValid()
        || !Float.isFinite(result.min.x)
        || !Float.isFinite(result.min.y)
        || !Float.isFinite(result.min.z)
        || !Float.isFinite(result.max.x)
        || !Float.isFinite(result.max.y)
        || !Float.isFinite(result.max.z))
      throw new IllegalArgumentException("UNREPRESENTABLE_SKIN_BOUNDS");
    return result;
  }
}
