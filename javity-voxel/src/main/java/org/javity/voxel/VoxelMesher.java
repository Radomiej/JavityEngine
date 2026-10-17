package org.javity.voxel;

import java.util.Objects;

/** Emits one quad for each opaque block face adjacent to air. */
public final class VoxelMesher {
  private static final int FLOATS_PER_VERTEX = 8;
  private static final int MAX_VERTICES = 16 * 16 * 16 * 12;
  private static final BlockFace[] FACES = BlockFace.values();

  private VoxelMesher() {}

  public static VoxelMeshData build(
      ChunkSnapshot snapshot, BlockRegistry registry, AtlasLayout atlas) {
    Objects.requireNonNull(snapshot, "snapshot");
    Objects.requireNonNull(registry, "registry");
    Objects.requireNonNull(atlas, "atlas");
    int faceCount = countFaces(snapshot, registry, atlas);
    float[] vertices = new float[faceCount * 4 * FLOATS_PER_VERTEX];
    short[] indices = new short[faceCount * 6];
    float[] corners = new float[12];
    int faceIndex = 0;
    for (int y = 0; y < 16; y++)
      for (int z = 0; z < 16; z++)
        for (int x = 0; x < 16; x++) {
          int blockId = snapshot.block(x, y, z);
          if (blockId == 0) continue;
          BlockDefinition definition = registry.get(blockId);
          for (BlockFace face : FACES) {
            if (snapshot.block(x + face.dx(), y + face.dy(), z + face.dz()) != 0) continue;
            appendFace(
                vertices,
                indices,
                corners,
                faceIndex++,
                x,
                y,
                z,
                face,
                atlas,
                definition.tile(face));
          }
        }
    return new VoxelMeshData(snapshot.position(), vertices, indices, faceCount);
  }

  private static int countFaces(ChunkSnapshot snapshot, BlockRegistry registry, AtlasLayout atlas) {
    int faces = 0;
    for (int y = 0; y < 16; y++)
      for (int z = 0; z < 16; z++)
        for (int x = 0; x < 16; x++) {
          int blockId = snapshot.block(x, y, z);
          if (blockId == 0) continue;
          BlockDefinition definition = registry.get(blockId);
          for (BlockFace face : FACES) {
            if (snapshot.block(x + face.dx(), y + face.dy(), z + face.dz()) != 0) continue;
            atlas.u0(definition.tile(face));
            faces++;
          }
        }
    return faces;
  }

  private static void appendFace(
      float[] vertices,
      short[] indices,
      float[] corners,
      int faceIndex,
      int x,
      int y,
      int z,
      BlockFace face,
      AtlasLayout atlas,
      int tile) {
    fillCorners(corners, x, y, z, face);
    int firstVertex = faceIndex * 4;
    if (firstVertex + 3 >= MAX_VERTICES || firstVertex + 3 > 0xffff) {
      throw new IllegalStateException("Chunk mesh exceeds unsigned-short vertex index capacity");
    }
    float nx = face.dx(), ny = face.dy(), nz = face.dz();
    float u0 = atlas.u0(tile), u1 = atlas.u1(tile), v0 = atlas.v0(tile), v1 = atlas.v1(tile);
    int vertexOffset = firstVertex * FLOATS_PER_VERTEX;
    for (int corner = 0; corner < 4; corner++) {
      int point = corner * 3;
      float px = corners[point], py = corners[point + 1], pz = corners[point + 2];
      float u = corner == 0 || corner == 3 ? u0 : u1;
      float v;
      if (face.dx() != 0 || face.dz() != 0) {
        v = py > y ? v0 : v1;
      } else {
        v = corner < 2 ? v0 : v1;
      }
      vertices[vertexOffset++] = px;
      vertices[vertexOffset++] = py;
      vertices[vertexOffset++] = pz;
      vertices[vertexOffset++] = nx;
      vertices[vertexOffset++] = ny;
      vertices[vertexOffset++] = nz;
      vertices[vertexOffset++] = u;
      vertices[vertexOffset++] = v;
    }
    int indexOffset = faceIndex * 6;
    indices[indexOffset] = (short) firstVertex;
    indices[indexOffset + 1] = (short) (firstVertex + 1);
    indices[indexOffset + 2] = (short) (firstVertex + 2);
    indices[indexOffset + 3] = (short) firstVertex;
    indices[indexOffset + 4] = (short) (firstVertex + 2);
    indices[indexOffset + 5] = (short) (firstVertex + 3);
  }

  private static void fillCorners(float[] corners, int x, int y, int z, BlockFace face) {
    switch (face) {
      case NEG_X -> set(corners, x, y, z, x, y, z + 1, x, y + 1, z + 1, x, y + 1, z);
      case POS_X ->
          set(corners, x + 1, y, z + 1, x + 1, y, z, x + 1, y + 1, z, x + 1, y + 1, z + 1);
      case NEG_Y -> set(corners, x, y, z, x + 1, y, z, x + 1, y, z + 1, x, y, z + 1);
      case POS_Y ->
          set(corners, x, y + 1, z + 1, x + 1, y + 1, z + 1, x + 1, y + 1, z, x, y + 1, z);
      case NEG_Z -> set(corners, x + 1, y, z, x, y, z, x, y + 1, z, x + 1, y + 1, z);
      case POS_Z ->
          set(corners, x, y, z + 1, x + 1, y, z + 1, x + 1, y + 1, z + 1, x, y + 1, z + 1);
    }
  }

  private static void set(
      float[] points,
      int x0,
      int y0,
      int z0,
      int x1,
      int y1,
      int z1,
      int x2,
      int y2,
      int z2,
      int x3,
      int y3,
      int z3) {
    points[0] = x0;
    points[1] = y0;
    points[2] = z0;
    points[3] = x1;
    points[4] = y1;
    points[5] = z1;
    points[6] = x2;
    points[7] = y2;
    points[8] = z2;
    points[9] = x3;
    points[10] = y3;
    points[11] = z3;
  }
}
