package org.javity.render.foliage;

import com.badlogic.gdx.math.collision.BoundingBox;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** CPU-only generation of static cutout foliage cards. */
public final class FoliageGeometryGenerator {
  private FoliageGeometryGenerator() {}

  /** Position3, normal3, UV2, all floats. Indices are unsigned shorts. */
  public static final int FLOATS_PER_VERTEX = 8;

  public static final int MAX_VERTICES_PER_CHUNK = 65536;

  public static Geometry generate(FoliagePatch3D.Snapshot snapshot) {
    Objects.requireNonNull(snapshot, "snapshot");
    int count = snapshot.count();
    if (count < 0 || count > FoliagePatch3D.MAX_COUNT) {
      throw new IllegalArgumentException("Foliage count exceeds generation budget");
    }
    if (count == 0) return new Geometry(List.of(), new BoundingBox().clr());
    boolean grass = snapshot.preset() == FoliagePatch3D.Preset.GRASS;
    int cardsPerPlant = grass ? 3 : 12;
    int totalCards = Math.toIntExact(Math.multiplyExact((long) count, cardsPerPlant));
    Builder builder = new Builder(totalCards);
    Random random = new Random(snapshot.seed());
    int columns =
        Math.max(
            1,
            Math.min(
                count,
                (int)
                    Math.ceil(Math.sqrt(count * ((double) snapshot.width() / snapshot.depth())))));
    int rows = (count + columns - 1) / columns;
    List<FoliageAtlas.Region> regions =
        grass ? snapshot.atlas().grassRegions() : snapshot.atlas().leafRegions();
    for (int plant = 0; plant < count; plant++) {
      float x =
          ((plant % columns + .12f + random.nextFloat() * .76f) / columns - .5f) * snapshot.width();
      float z =
          ((plant / columns + .12f + random.nextFloat() * .76f) / rows - .5f) * snapshot.depth();
      float scale =
          snapshot.minScale() + random.nextFloat() * (snapshot.maxScale() - snapshot.minScale());
      float yaw = random.nextFloat() * (float) (Math.PI * 2);
      for (int card = 0; card < cardsPerPlant; card++) {
        FoliageAtlas.Region region = regions.get(random.nextInt(regions.size()));
        if (grass) {
          float angle = yaw + card * (float) (Math.PI / 3) + (random.nextFloat() - .5f) * .2f;
          float width = (.27f + random.nextFloat() * .15f) * scale;
          float height = (.4f + random.nextFloat() * .25f) * scale;
          float direction = yaw + card * 2.3999632f;
          float dx = (float) Math.cos(direction), dz = (float) Math.sin(direction);
          builder.card(
              x + dx * .035f * scale,
              0,
              z + dz * .035f * scale,
              width,
              height,
              angle,
              dx * .09f * scale,
              dz * .09f * scale,
              region);
        } else {
          int tier = card / 3;
          float direction = yaw + card * 2.3999632f;
          float dx = (float) Math.cos(direction), dz = (float) Math.sin(direction);
          float radius = (.12f + random.nextFloat() * .13f) * (1 - tier * .14f) * scale;
          float base = (.035f + tier * .18f) * scale;
          float width = (.5f + random.nextFloat() * .28f) * (1 - tier * .06f) * scale;
          float height = (.43f + random.nextFloat() * .25f) * scale;
          float angle = direction + (float) Math.PI / 2 + (random.nextFloat() - .5f) * .7f;
          builder.card(
              x + dx * radius,
              base,
              z + dz * radius,
              width,
              height,
              angle,
              dx * .12f * scale,
              dz * .12f * scale,
              region);
        }
      }
    }
    return builder.finish();
  }

  private static final class Builder {
    private final List<Chunk> chunks = new ArrayList<>();
    private final BoundingBox bounds = new BoundingBox().inf();
    private int remainingCards, vertexOffset, indexOffset;
    private float[] vertices;
    private short[] indices;

    Builder(int totalCards) {
      remainingCards = totalCards;
      allocate();
    }

    private void allocate() {
      int cards = Math.min(remainingCards, MAX_VERTICES_PER_CHUNK / 4);
      vertices = new float[Math.multiplyExact(cards, 4 * FLOATS_PER_VERTEX)];
      indices = new short[Math.multiplyExact(cards, 6)];
      vertexOffset = indexOffset = 0;
    }

    void card(
        float x,
        float y,
        float z,
        float width,
        float height,
        float yaw,
        float leanX,
        float leanZ,
        FoliageAtlas.Region region) {
      if (vertexOffset == vertices.length) {
        chunks.add(new Chunk(vertices, indices));
        allocate();
      }
      float c = (float) Math.cos(yaw), s = (float) Math.sin(yaw);
      float halfX = c * (width * .5f), halfZ = s * (width * .5f);
      // Soft upward normals retain the geometric face direction while lighting both card views.
      float nx = -s, ny = s * (leanX / height) - c * (leanZ / height), nz = c;
      float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
      nx /= length;
      ny = ny / length + .9f;
      nz /= length;
      length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
      nx /= length;
      ny /= length;
      nz /= length;
      int base = vertexOffset / FLOATS_PER_VERTEX;
      vertex(x - halfX, y, z - halfZ, nx, ny, nz, region.u(), region.v2());
      vertex(x + halfX, y, z + halfZ, nx, ny, nz, region.u2(), region.v2());
      vertex(x + halfX + leanX, y + height, z + halfZ + leanZ, nx, ny, nz, region.u2(), region.v());
      vertex(x - halfX + leanX, y + height, z - halfZ + leanZ, nx, ny, nz, region.u(), region.v());
      indices[indexOffset++] = (short) base;
      indices[indexOffset++] = (short) (base + 1);
      indices[indexOffset++] = (short) (base + 2);
      indices[indexOffset++] = (short) (base + 2);
      indices[indexOffset++] = (short) (base + 3);
      indices[indexOffset++] = (short) base;
      remainingCards--;
    }

    private void vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v) {
      if (!Float.isFinite(x)
          || !Float.isFinite(y)
          || !Float.isFinite(z)
          || !Float.isFinite(nx)
          || !Float.isFinite(ny)
          || !Float.isFinite(nz)) {
        throw new IllegalArgumentException("Foliage geometry exceeds finite coordinate range");
      }
      vertices[vertexOffset++] = x;
      vertices[vertexOffset++] = y;
      vertices[vertexOffset++] = z;
      vertices[vertexOffset++] = nx;
      vertices[vertexOffset++] = ny;
      vertices[vertexOffset++] = nz;
      vertices[vertexOffset++] = u;
      vertices[vertexOffset++] = v;
      bounds.ext(x, y, z);
    }

    Geometry finish() {
      chunks.add(new Chunk(vertices, indices));
      return new Geometry(chunks, bounds);
    }
  }

  public static final class Geometry {
    private final List<Chunk> chunks;
    private final BoundingBox bounds;

    private Geometry(List<Chunk> chunks, BoundingBox bounds) {
      this.chunks = List.copyOf(chunks);
      this.bounds = new BoundingBox(bounds);
    }

    public List<Chunk> chunks() {
      return chunks;
    }

    public BoundingBox bounds() {
      return new BoundingBox(bounds);
    }

    public int vertexCount() {
      return chunks.stream().mapToInt(Chunk::vertexCount).sum();
    }

    public int indexCount() {
      return chunks.stream().mapToInt(Chunk::indexCount).sum();
    }
  }

  public static final class Chunk {
    private final float[] vertices;
    private final short[] indices;

    private Chunk(float[] vertices, short[] indices) {
      this.vertices = vertices;
      this.indices = indices;
    }

    public float[] vertices() {
      return vertices.clone();
    }

    public short[] indices() {
      return indices.clone();
    }

    public int vertexCount() {
      return vertices.length / FLOATS_PER_VERTEX;
    }

    public int indexCount() {
      return indices.length;
    }
  }
}
