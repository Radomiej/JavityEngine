package org.javity.voxel;

import java.util.Objects;

/** Immutable packed CPU mesh. Vertices use xyz, normal xyz, uv (8 floats per vertex). */
public final class VoxelMeshData {
  private final ChunkPos position;
  private final float[] vertices;
  private final short[] indices;
  private final int faceCount;

  VoxelMeshData(ChunkPos position, float[] vertices, short[] indices, int faceCount) {
    this.position = Objects.requireNonNull(position, "position");
    this.vertices = vertices.clone();
    this.indices = indices.clone();
    this.faceCount = faceCount;
  }

  public ChunkPos position() {
    return position;
  }

  public float[] vertices() {
    return vertices.clone();
  }

  public short[] indices() {
    return indices.clone();
  }

  public int faceCount() {
    return faceCount;
  }

  public int vertexCount() {
    return vertices.length / 8;
  }

  public int indexCount() {
    return indices.length;
  }
}
