package org.javity.render;

import static org.junit.Assert.*;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.glutils.GLVersion;
import com.badlogic.gdx.math.Matrix4;
import java.lang.reflect.Proxy;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.javity.animation.ExactPoseKey;
import org.junit.Test;

public class BonePaletteTest {
  @Test
  public void copiesColumnMajorMatricesWithoutGl() {
    BonePalette palette = new BonePalette(2, 3);
    palette.beginFrame(10);
    Matrix4[] source = {matrix(1), matrix(17)};
    BonePalette.Row row = palette.put(source);
    assertEquals(8, palette.width());
    assertEquals(3, palette.pageHeight(0));
    float[] expected = new float[32];
    for (int i = 0; i < 32; i++) expected[i] = i + 1;
    assertArrayEquals(expected, palette.copyRow(row), 0);
    source[0].val[0] = 999;
    float[] copy = palette.copyRow(row);
    copy[1] = 999;
    assertArrayEquals(expected, palette.copyRow(row), 0);
    palette.dispose();
  }

  @Test
  public void verifiesActualMatricesEvenWithSamePoseKey() {
    BonePalette palette = new BonePalette(1, 4);
    palette.beginFrame(1);
    ExactPoseKey key = new ExactPoseKey.Builder().tag(1).build();
    BonePalette.Row a = palette.put(key, new Matrix4[] {matrix(1)});
    BonePalette.Row same = palette.put(key, new Matrix4[] {matrix(1)});
    BonePalette.Row differentBind = palette.put(key, new Matrix4[] {matrix(2)});
    assertEquals(a.index(), same.index());
    assertNotEquals(a.index(), differentBind.index());
    assertEquals(a.index(), palette.put(new Matrix4[] {matrix(1)}).index());
    // A signed zero differs bit-for-bit; hash collisions cannot alias rows either.
    Matrix4 plus = new Matrix4(), minus = new Matrix4();
    minus.val[1] = -0f;
    assertNotEquals(
        palette.put(new Matrix4[] {plus}).index(), palette.put(new Matrix4[] {minus}).index());
    palette.dispose();
  }

  @Test
  public void equalHashesStillCompareEveryMatrixComponent() {
    BonePalette palette = new BonePalette(1, 2);
    palette.beginFrame(0);
    Matrix4 a = new Matrix4(), b = new Matrix4();
    a.val[0] = Float.intBitsToFloat(1);
    a.val[1] = Float.intBitsToFloat(31);
    b.val[0] = 0;
    b.val[1] = Float.intBitsToFloat(62);
    assertEquals(Arrays.hashCode(a.val), Arrays.hashCode(b.val));
    assertNotEquals(palette.put(new Matrix4[] {a}).index(), palette.put(new Matrix4[] {b}).index());
    palette.dispose();
  }

  @Test
  public void zeroMatrixInPreviouslyUninitializedTextureRowStillUploads() {
    try (GlCalls gl = new GlCalls()) {
      BonePalette palette = new BonePalette(1, 2);
      palette.beginFrame(0);
      palette.put(new Matrix4[] {matrix(1)});
      assertEquals(64, palette.bindPage(0, 1));
      Matrix4 zero = new Matrix4();
      Arrays.fill(zero.val, 0);
      palette.put(new Matrix4[] {zero});
      assertEquals(64, palette.bindPage(0, 1));
      palette.dispose();
    }
  }

  @Test
  public void sparseChangesCountOnlyActualRowBytes() {
    try (GlCalls gl = new GlCalls()) {
      BonePalette palette = new BonePalette(1, 3);
      palette.beginFrame(0);
      for (int i = 0; i < 3; i++) palette.put(new Matrix4[] {matrix(i)});
      assertEquals(192, palette.bindPage(0, 1));
      palette.beginFrame(1);
      palette.put(new Matrix4[] {matrix(10)});
      palette.put(new Matrix4[] {matrix(1)});
      palette.put(new Matrix4[] {matrix(20)});
      assertEquals(128, palette.bindPage(0, 1));
      assertEquals(3, gl.subUploads);
      palette.dispose();
    }
  }

  @Test
  public void gles3NeedsNoOesFloatOrFloatRenderTargetExtension() {
    try (GlCalls gl = new GlCalls()) {
      gl.gles = true;
      BonePalette palette = new BonePalette(1, 1);
      palette.beginFrame(0);
      palette.put(new Matrix4[] {matrix(1)});
      assertEquals(64, palette.bindPage(0, 1));
      assertEquals(GL30.GL_RGBA32F, gl.internalFormat);
      palette.dispose();
    }
  }

  @Test
  public void actualCapabilityRefusalHappensBeforeGlAllocation() {
    try (GlCalls gl = new GlCalls()) {
      BonePalette palette = new BonePalette(1, 1);
      palette.beginFrame(0);
      BonePalette.Row row = palette.put(new Matrix4[] {matrix(1)});
      gl.vertexUnits = 0;
      assertReason("PALETTE_VERTEX_TEXTURE_UNITS", () -> palette.bind(row, 1));
      assertEquals(0, gl.created);
      gl.vertexUnits = 1;
      gl.textureSize = 3;
      assertReason("PALETTE_TEXTURE_SIZE", () -> palette.bind(row, 1));
      assertEquals(0, gl.created);
      gl.textureSize = 4096;
      assertReason("PALETTE_COMBINED_TEXTURE_UNITS", () -> palette.bind(row, 16));
      assertEquals(0, gl.created);
      Gdx.gl30 = null;
      assertReason("GL30_UNAVAILABLE", () -> palette.bind(row, 1));
      palette.dispose();
    }
  }

  @Test
  public void splitsDeterministicallyAndEnforcesTotalBudget() {
    BonePalette palette = new BonePalette(1, 257);
    palette.beginFrame(0);
    BonePalette.Row row = null;
    for (int i = 0; i < 257; i++) row = palette.put(new Matrix4[] {matrix(i)});
    assertEquals(1, row.page());
    assertEquals(0, row.index());
    assertEquals(256, palette.pageHeight(0));
    assertEquals(1, palette.pageHeight(1));
    assertEquals(2, palette.pageCount());
    assertNull(palette.put(new Matrix4[] {matrix(1000)}));
    assertNotNull(palette.put(new Matrix4[] {matrix(0)}));
    palette.dispose();
  }

  @Test
  public void handlesRequireOwnerAndCurrentGeneration() {
    BonePalette palette = new BonePalette(1, 1), other = new BonePalette(1, 1);
    palette.beginFrame(1);
    other.beginFrame(1);
    BonePalette.Row row = palette.put(new Matrix4[] {matrix(1)});
    assertTrue(palette.isValid(row));
    assertFalse(other.isValid(row));
    assertFalse(palette.isValid(null));
    palette.beginFrame(1);
    assertTrue(palette.isValid(row));
    palette.beginFrame(2);
    assertFalse(palette.isValid(row));
    assertThrows(IllegalArgumentException.class, () -> palette.copyRow(row));
    assertThrows(IllegalArgumentException.class, () -> palette.bind(row, 0));
    BonePalette.Row current = palette.put(new Matrix4[] {matrix(1)});
    palette.contextRestored();
    palette.contextRestored();
    assertFalse(palette.isValid(current));
    BonePalette.Row restored = palette.put(new Matrix4[] {matrix(1)});
    assertArrayEquals(matrix(1).val, palette.copyRow(restored), 0);
    palette.dispose();
    palette.dispose();
    assertFalse(palette.isValid(restored));
    assertThrows(IllegalStateException.class, () -> palette.beginFrame(3));
    other.dispose();
  }

  @Test
  public void rejectsInvalidDimensionsAndMatricesBeforeAllocating() {
    assertThrows(IllegalArgumentException.class, () -> new BonePalette(0, 1));
    assertThrows(IllegalArgumentException.class, () -> new BonePalette(1, 0));
    assertThrows(IllegalArgumentException.class, () -> new BonePalette(Integer.MAX_VALUE, 1));
    assertThrows(IllegalArgumentException.class, () -> new BonePalette(1000, Integer.MAX_VALUE));
    BonePalette palette = new BonePalette(1, 2);
    assertThrows(IllegalStateException.class, () -> palette.put(new Matrix4[] {matrix(1)}));
    palette.beginFrame(0);
    assertThrows(IllegalArgumentException.class, () -> palette.put((Matrix4[]) null));
    assertThrows(IllegalArgumentException.class, () -> palette.put(new Matrix4[0]));
    assertThrows(IllegalArgumentException.class, () -> palette.put(new Matrix4[] {null}));
    Matrix4 invalid = matrix(1);
    invalid.val[0] = Float.NaN;
    assertThrows(IllegalArgumentException.class, () -> palette.put(new Matrix4[] {invalid}));
    invalid.val[0] = Float.POSITIVE_INFINITY;
    assertThrows(IllegalArgumentException.class, () -> palette.put(new Matrix4[] {invalid}));
    assertThrows(IllegalArgumentException.class, () -> palette.bindPage(0, 0));
    palette.dispose();
  }

  @Test
  public void refusesInsufficientVertexCombinedAndTextureLimitsWithoutGl() {
    BonePalette palette = new BonePalette(2, 257);
    assertReason("PALETTE_TEXTURE_SIZE", () -> palette.validateCapabilities(7, 1, 2, 1));
    assertReason("PALETTE_TEXTURE_SIZE", () -> palette.validateCapabilities(255, 1, 2, 1));
    assertReason("PALETTE_VERTEX_TEXTURE_UNITS", () -> palette.validateCapabilities(256, 0, 2, 1));
    assertReason(
        "PALETTE_COMBINED_TEXTURE_UNITS", () -> palette.validateCapabilities(256, 1, 1, 1));
    assertReason(
        "PALETTE_COMBINED_TEXTURE_UNITS", () -> palette.validateCapabilities(256, 1, 2, -1));
    palette.validateCapabilities(256, 1, 2, 1);
    palette.dispose();
  }

  @Test
  public void lazyUploadsOnlyChangedDataAndRestoresActiveUnit() {
    try (GlCalls gl = new GlCalls()) {
      BonePalette palette = new BonePalette(1, 2);
      palette.beginFrame(0);
      BonePalette.Row row = palette.put(new Matrix4[] {matrix(1)});
      assertEquals(0, gl.created);
      assertEquals(64, palette.bind(row, 1));
      assertEquals(GL30.GL_RGBA32F, gl.internalFormat);
      assertEquals(GL20.GL_TEXTURE0 + 3, gl.active);
      gl.assertUnpackRestored();
      assertEquals(1, gl.created);
      assertEquals(0, palette.bindPage(0, 1));
      palette.beginFrame(1);
      palette.put(new Matrix4[] {matrix(1)});
      assertEquals(0, palette.bindPage(0, 1));
      palette.put(new Matrix4[] {matrix(2)});
      assertEquals(64, palette.bindPage(0, 1));
      assertEquals(2, gl.subUploads);
      palette.contextRestored();
      palette.contextRestored();
      assertEquals(0, gl.deleted.size());
      palette.beginFrame(1);
      assertTrue(palette.isValid(palette.put(new Matrix4[] {matrix(1)})));
      assertEquals(128, palette.bindPage(0, 1));
      assertEquals(2, gl.created);
      palette.dispose();
      assertEquals(List.of(74), gl.deleted);
    }
  }

  @Test
  public void failedUploadDeletesOnlyNewTextureAndCanRetryCpuData() {
    try (GlCalls gl = new GlCalls()) {
      BonePalette palette = new BonePalette(1, 1);
      palette.beginFrame(0);
      Matrix4 borrowed = matrix(1);
      BonePalette.Row row = palette.put(new Matrix4[] {borrowed});
      gl.failUpload = true;
      assertThrows(IllegalStateException.class, () -> palette.bind(row, 1));
      assertEquals(List.of(73), gl.deleted);
      assertEquals(GL20.GL_TEXTURE0 + 3, gl.active);
      assertEquals(12, gl.binding);
      gl.assertUnpackRestored();
      assertArrayEquals(matrix(1).val, borrowed.val, 0);
      gl.failUpload = false;
      assertEquals(64, palette.bind(row, 1));
      palette.dispose();
      assertEquals(List.of(73, 74), gl.deleted);
    }
  }

  private static void assertReason(String reason, Runnable action) {
    assertEquals(reason, assertThrows(BonePalette.CapabilityException.class, action::run).reason());
  }

  private static Matrix4 matrix(int start) {
    Matrix4 matrix = new Matrix4();
    for (int i = 0; i < 16; i++) matrix.val[i] = start + i;
    return matrix;
  }

  /**
   * Dispatch-only fake: these tests establish CPU lifetime/packing, not real GPU upload support.
   */
  private static final class GlCalls implements AutoCloseable {
    private final GL20 previousGl = Gdx.gl, previousGl20 = Gdx.gl20;
    private final GL30 previousGl30 = Gdx.gl30;
    private final Graphics previousGraphics = Gdx.graphics;
    int active = GL20.GL_TEXTURE0 + 3, binding = 12, created, internalFormat, subUploads;
    int unpackBuffer = 19, alignment = 8, rowLength = 7, skipRows = 2, skipPixels = 3;
    int vertexUnits = 4, textureSize = 4096;
    boolean gles;
    boolean failUpload;
    final List<Integer> deleted = new ArrayList<>();

    GlCalls() {
      GL30 gl =
          (GL30)
              Proxy.newProxyInstance(
                  GL30.class.getClassLoader(),
                  new Class<?>[] {GL30.class},
                  (proxy, method, args) -> {
                    switch (method.getName()) {
                      case "glGetIntegerv" -> {
                        int name = (int) args[0];
                        int value =
                            switch (name) {
                              case GL20.GL_ACTIVE_TEXTURE -> active;
                              case GL20.GL_TEXTURE_BINDING_2D -> binding;
                              case GL20.GL_MAX_TEXTURE_SIZE -> textureSize;
                              case GL20.GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS -> vertexUnits;
                              case GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS -> 16;
                              case GL30.GL_PIXEL_UNPACK_BUFFER_BINDING -> unpackBuffer;
                              case GL20.GL_UNPACK_ALIGNMENT -> alignment;
                              case GL30.GL_UNPACK_ROW_LENGTH -> rowLength;
                              case GL30.GL_UNPACK_SKIP_ROWS -> skipRows;
                              case GL30.GL_UNPACK_SKIP_PIXELS -> skipPixels;
                              default -> throw new AssertionError("Unexpected query: " + name);
                            };
                        ((IntBuffer) args[1]).put(0, value);
                      }
                      case "glGenTexture" -> {
                        return 72 + ++created;
                      }
                      case "glActiveTexture" -> active = (int) args[0];
                      case "glBindTexture" -> binding = (int) args[1];
                      case "glDeleteTexture" -> deleted.add((int) args[0]);
                      case "glBindBuffer" -> {
                        assertEquals(GL30.GL_PIXEL_UNPACK_BUFFER, args[0]);
                        unpackBuffer = (int) args[1];
                      }
                      case "glPixelStorei" -> {
                        switch ((int) args[0]) {
                          case GL20.GL_UNPACK_ALIGNMENT -> alignment = (int) args[1];
                          case GL30.GL_UNPACK_ROW_LENGTH -> rowLength = (int) args[1];
                          case GL30.GL_UNPACK_SKIP_ROWS -> skipRows = (int) args[1];
                          case GL30.GL_UNPACK_SKIP_PIXELS -> skipPixels = (int) args[1];
                          default -> throw new AssertionError("Unexpected pixel store");
                        }
                      }
                      case "glTexImage2D" -> {
                        assertUnpackNormalized();
                        internalFormat = (int) args[2];
                        assertNull(args[8]);
                        if (failUpload) throw new IllegalStateException("injected upload failure");
                      }
                      case "glTexSubImage2D" -> {
                        assertUnpackNormalized();
                        assertTrue(args[8] instanceof FloatBuffer);
                        subUploads++;
                      }
                      case "glGetError" -> {
                        return GL20.GL_NO_ERROR;
                      }
                      case "glTexParameteri" -> {}
                      default ->
                          throw new AssertionError("Unexpected GL dispatch: " + method.getName());
                    }
                    return null;
                  });
      Gdx.gl = Gdx.gl20 = gl;
      Gdx.gl30 = gl;
      Gdx.graphics =
          (Graphics)
              Proxy.newProxyInstance(
                  Graphics.class.getClassLoader(),
                  new Class<?>[] {Graphics.class},
                  (proxy, method, args) -> {
                    if (method.getName().equals("getGLVersion"))
                      return new GLVersion(
                          gles
                              ? com.badlogic.gdx.Application.ApplicationType.Android
                              : com.badlogic.gdx.Application.ApplicationType.Desktop,
                          gles ? "OpenGL ES 3.0" : "3.3",
                          "test",
                          "test");
                    throw new AssertionError("Unexpected graphics dispatch: " + method.getName());
                  });
    }

    void assertUnpackNormalized() {
      assertEquals(0, unpackBuffer);
      assertEquals(4, alignment);
      assertEquals(0, rowLength);
      assertEquals(0, skipRows);
      assertEquals(0, skipPixels);
    }

    void assertUnpackRestored() {
      assertEquals(19, unpackBuffer);
      assertEquals(8, alignment);
      assertEquals(7, rowLength);
      assertEquals(2, skipRows);
      assertEquals(3, skipPixels);
    }

    @Override
    public void close() {
      Gdx.gl = previousGl;
      Gdx.gl20 = previousGl20;
      Gdx.gl30 = previousGl30;
      Gdx.graphics = previousGraphics;
    }
  }
}
