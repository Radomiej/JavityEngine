package org.javity.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.glutils.GLVersion;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import org.javity.animation.ExactPoseKey;

/**
 * Bounded, CPU-backed matrix palette. Each row contains four RGBA texels per bone, in Matrix4's
 * column-major order. Construction, frame collection and row reads do not require GL. Matrices are
 * copied; no borrowed model, mesh or texture is owned here. Zero bones and non-finite matrix values
 * are rejected; finite matrices, including singular/zero matrices, are copied exactly.
 *
 * <p>Not thread safe. Bind/dispose must run on the current GL owner thread outside ModelBatch. Raw
 * textures are deliberately unmanaged: after actual context replacement, the owner MUST call
 * contextRestored before binding or disposing. That hook forgets obsolete names without deleting
 * potentially unrelated objects in the new namespace, and retains CPU data for lazy upload.
 */
public final class BonePalette implements Disposable {
  private static final int ROWS_PER_PAGE = 256;
  private final int boneCount, maxRows, width, floatsPerRow;
  private final Page[] pages;
  private final Map<MatrixContent, Integer> entries = new HashMap<>();
  private long generation, frameIndex;
  private int rowCount;
  private boolean started, disposed;

  public BonePalette(int boneCount, int maxRows) {
    if (boneCount <= 0 || maxRows <= 0)
      throw new IllegalArgumentException("boneCount and maxRows must be positive");
    long rowFloats = (long) boneCount * 16;
    // Bounds all CPU backing and every direct-buffer allocation before narrowing to int.
    if (rowFloats > Integer.MAX_VALUE / Float.BYTES
        || maxRows > Integer.MAX_VALUE / (rowFloats * Float.BYTES))
      throw new IllegalArgumentException("Palette exceeds supported CPU byte budget");
    this.boneCount = boneCount;
    this.maxRows = maxRows;
    this.width = boneCount * 4;
    this.floatsPerRow = (int) rowFloats;
    pages = new Page[(int) (((long) maxRows + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE)];
  }

  public int width() {
    return width;
  }

  /** Deterministic allocated height for this page, independent of GL limits. */
  public int pageHeight(int page) {
    if (page < 0 || page >= pages.length)
      throw new IllegalArgumentException("Invalid palette page");
    return Math.min(ROWS_PER_PAGE, maxRows - page * ROWS_PER_PAGE);
  }

  /** Number of pages used in the current frame (never more than ceil(maxRows / 256)). */
  public int pageCount() {
    return (rowCount + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE;
  }

  /** A repeated frame index preserves collection; a different index invalidates prior leases. */
  public void beginFrame(long frameIndex) {
    requireLive();
    if (started && this.frameIndex == frameIndex) return;
    generation = Math.incrementExact(generation);
    this.frameIndex = frameIndex;
    started = true;
    entries.clear();
    rowCount = 0;
  }

  /**
   * The pose key alone cannot identify skin matrices with mesh-part-specific inverse binds. Actual
   * matrix content is always compared, including when the key is equal. Equal matrices need only
   * one row even when obtained through different pose keys. Rig/vertex-layout grouping remains the
   * renderer's responsibility.
   */
  public Row put(ExactPoseKey key, Matrix4[] skinMatrices) {
    if (key == null) throw new IllegalArgumentException("Pose key cannot be null");
    return put(skinMatrices);
  }

  /** Returns null on total row-budget overflow; never evicts a current-frame row. */
  public Row put(Matrix4[] skinMatrices) {
    requireLive();
    if (!started) throw new IllegalStateException("Call beginFrame before collecting matrices");
    if (skinMatrices == null || skinMatrices.length != boneCount)
      throw new IllegalArgumentException("Expected exactly " + boneCount + " matrices");
    float[] values = new float[floatsPerRow];
    for (int bone = 0; bone < boneCount; bone++) {
      Matrix4 matrix = skinMatrices[bone];
      if (matrix == null) throw new IllegalArgumentException("Null skin matrix at " + bone);
      for (int component = 0; component < 16; component++) {
        float value = matrix.val[component];
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite skin matrix");
        values[bone * 16 + component] = value;
      }
    }
    MatrixContent content = new MatrixContent(values);
    Integer existing = entries.get(content);
    if (existing != null) return lease(existing);
    if (rowCount == maxRows) return null;
    int slot = rowCount, pageIndex = slot / ROWS_PER_PAGE, index = slot % ROWS_PER_PAGE;
    Page page = pages[pageIndex];
    if (page == null) pages[pageIndex] = page = new Page(pageHeight(pageIndex), floatsPerRow);
    int offset = index * floatsPerRow;
    boolean changed = false;
    for (int i = 0; i < floatsPerRow; i++) {
      if (Float.floatToRawIntBits(page.data.get(offset + i)) != Float.floatToRawIntBits(values[i]))
        changed = true;
      page.data.put(offset + i, values[i]);
    }
    if (changed || !page.uploaded.get(index)) page.dirty.set(index);
    entries.put(content, slot);
    rowCount++;
    return lease(slot);
  }

  public boolean isValid(Row row) {
    return !disposed
        && row != null
        && row.owner == this
        && row.generation == generation
        && row.page >= 0
        && row.index >= 0
        && row.index < pageHeight(row.page)
        && (long) row.page * ROWS_PER_PAGE + row.index < rowCount;
  }

  /** A defensive, column-major copy; rejects foreign, prior-frame and context-obsolete leases. */
  public float[] copyRow(Row row) {
    requireValid(row);
    float[] result = new float[floatsPerRow];
    FloatBuffer source = pages[row.page].data.duplicate();
    source.position(row.index * floatsPerRow);
    source.get(result);
    return result;
  }

  /** Validates the row lease before binding its page. */
  public long bind(Row row, int textureUnit) {
    requireValid(row);
    return bindPage(row.page, textureUnit);
  }

  /**
   * Binds the page on the requested combined texture unit and returns actual matrix upload bytes.
   * Empty texture storage allocation counts zero bytes. Unchanged rows upload nothing; dirty row
   * runs upload once until modified again. The caller must reserve this unit from its material
   * binder and restore its prior GL_TEXTURE_BINDING_2D at the pass boundary. The active texture
   * unit is restored here, including on errors. A failed bind also restores the touched binding.
   * Pixel-unpack buffer, alignment, row length and row/pixel skips are normalized for client memory
   * upload and restored before returning.
   *
   * <p>This page-only operation requires a current-frame page but cannot validate a particular Row;
   * validate every selected lease with isValid before drawing, or use bind(Row, unit).
   */
  public long bindPage(int pageIndex, int textureUnit) {
    requireLive();
    if (pageIndex < 0 || pageIndex >= pageCount())
      throw new IllegalArgumentException("Palette page has no current-frame rows");
    requireContext();
    validateCapabilities(
        limit(GL20.GL_MAX_TEXTURE_SIZE),
        limit(GL20.GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS),
        limit(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS),
        textureUnit);
    GL30 gl = Gdx.gl30;
    int previousActive = limit(GL20.GL_ACTIVE_TEXTURE);
    Page page = pages[pageIndex];
    int created = 0, previousBinding = 0;
    boolean touched = false, success = false;
    UnpackState unpack = null;
    try {
      gl.glActiveTexture(GL20.GL_TEXTURE0 + textureUnit);
      previousBinding = limit(GL20.GL_TEXTURE_BINDING_2D);
      touched = true;
      int firstDirty = page.dirty.nextSetBit(0);
      if (page.texture == 0 || (firstDirty >= 0 && firstDirty < usedRows(pageIndex))) {
        unpack = new UnpackState();
        unpack.normalize(gl);
      }
      if (page.texture == 0) {
        created = gl.glGenTexture();
        if (created == 0) throw new IllegalStateException("Could not allocate palette texture");
        gl.glBindTexture(GL20.GL_TEXTURE_2D, created);
        gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_NEAREST);
        gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_NEAREST);
        gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE);
        gl.glTexImage2D(
            GL20.GL_TEXTURE_2D,
            0,
            GL30.GL_RGBA32F,
            width,
            pageHeight(pageIndex),
            0,
            GL20.GL_RGBA,
            GL20.GL_FLOAT,
            null);
        page.dirty.set(0, usedRows(pageIndex));
      } else gl.glBindTexture(GL20.GL_TEXTURE_2D, page.texture);
      long bytes = 0;
      int used = usedRows(pageIndex);
      for (int first = page.dirty.nextSetBit(0); first >= 0 && first < used; ) {
        int end = Math.min(page.dirty.nextClearBit(first), used);
        FloatBuffer upload = page.data.duplicate();
        upload.position(first * floatsPerRow);
        upload.limit(end * floatsPerRow);
        gl.glTexSubImage2D(
            GL20.GL_TEXTURE_2D,
            0,
            0,
            first,
            width,
            end - first,
            GL20.GL_RGBA,
            GL20.GL_FLOAT,
            upload.slice());
        bytes += (long) (end - first) * floatsPerRow * Float.BYTES;
        first = page.dirty.nextSetBit(end);
      }
      int error = gl.glGetError();
      if (error != GL20.GL_NO_ERROR)
        throw new IllegalStateException("Palette upload GL error: 0x" + Integer.toHexString(error));
      if (created != 0) page.texture = created;
      page.dirty.clear(0, used);
      page.uploaded.set(0, used);
      success = true;
      return bytes;
    } finally {
      try {
        if (unpack != null) unpack.restore(gl);
      } finally {
        try {
          if (!success) {
            try {
              if (created != 0) gl.glDeleteTexture(created);
            } finally {
              if (touched) gl.glBindTexture(GL20.GL_TEXTURE_2D, previousBinding);
            }
          }
        } finally {
          gl.glActiveTexture(previousActive);
        }
      }
    }
  }

  /** CPU-testable size/unit checks. Float render-target and OES extensions are not needed. */
  public void validateCapabilities(
      int maxTextureSize, int vertexUnits, int combinedUnits, int textureUnit) {
    requireLive();
    if (maxTextureSize < width || maxTextureSize < Math.min(ROWS_PER_PAGE, maxRows))
      throw new CapabilityException("PALETTE_TEXTURE_SIZE");
    if (vertexUnits < 1) throw new CapabilityException("PALETTE_VERTEX_TEXTURE_UNITS");
    if (textureUnit < 0 || textureUnit >= combinedUnits)
      throw new CapabilityException("PALETTE_COMBINED_TEXTURE_UNITS");
  }

  /**
   * Actual-context-loss hook. Idempotently forgets raw names, retains rows and invalidates leases;
   * put returns fresh valid handles even with the same frame index after this hook.
   */
  public void contextRestored() {
    requireLive();
    generation = Math.incrementExact(generation);
    for (Page page : pages)
      if (page != null) {
        page.texture = 0;
        page.uploaded.clear();
      }
  }

  @Override
  public void dispose() {
    if (disposed) return;
    for (Page page : pages) {
      if (page != null && page.texture != 0) {
        if (Gdx.gl30 == null)
          throw new IllegalStateException("Dispose requires the owning GL context");
        Gdx.gl30.glDeleteTexture(page.texture);
        page.texture = 0;
      }
    }
    entries.clear();
    Arrays.fill(pages, null);
    rowCount = 0;
    disposed = true;
  }

  private int usedRows(int page) {
    return Math.min(pageHeight(page), rowCount - page * ROWS_PER_PAGE);
  }

  private Row lease(int slot) {
    return new Row(this, slot / ROWS_PER_PAGE, slot % ROWS_PER_PAGE, generation);
  }

  private void requireLive() {
    if (disposed) throw new IllegalStateException("BonePalette is disposed");
  }

  private void requireValid(Row row) {
    if (!isValid(row)) throw new IllegalArgumentException("Foreign or stale palette row");
  }

  private static void requireContext() {
    if (Gdx.gl30 == null || Gdx.graphics == null) throw new CapabilityException("GL30_UNAVAILABLE");
    GLVersion version = Gdx.graphics.getGLVersion();
    boolean supported =
        version.getType() == GLVersion.Type.OpenGL
            ? version.isVersionEqualToOrHigher(3, 3)
            : version.getType() == GLVersion.Type.GLES && version.isVersionEqualToOrHigher(3, 0);
    if (!supported) throw new CapabilityException("GL_VERSION_UNSUPPORTED");
  }

  private static int limit(int name) {
    IntBuffer value =
        ByteBuffer.allocateDirect(Integer.BYTES).order(ByteOrder.nativeOrder()).asIntBuffer();
    Gdx.gl30.glGetIntegerv(name, value);
    return value.get(0);
  }

  /** Immutable lease. Only its issuing palette can create or validate it. */
  public static final class Row {
    private final BonePalette owner;
    private final int page, index;
    private final long generation;

    private Row(BonePalette owner, int page, int index, long generation) {
      this.owner = owner;
      this.page = page;
      this.index = index;
      this.generation = generation;
    }

    public int page() {
      return page;
    }

    public int index() {
      return index;
    }

    public long generation() {
      return generation;
    }
  }

  public static final class CapabilityException extends IllegalStateException {
    private final String reason;

    private CapabilityException(String reason) {
      super(reason);
      this.reason = reason;
    }

    public String reason() {
      return reason;
    }
  }

  private static final class Page {
    final FloatBuffer data;
    final BitSet dirty = new BitSet();
    final BitSet uploaded = new BitSet();
    int texture;

    Page(int rows, int floatsPerRow) {
      data =
          ByteBuffer.allocateDirect(rows * floatsPerRow * Float.BYTES)
              .order(ByteOrder.nativeOrder())
              .asFloatBuffer();
    }
  }

  private static final class UnpackState {
    final int buffer = limit(GL30.GL_PIXEL_UNPACK_BUFFER_BINDING);
    final int alignment = limit(GL20.GL_UNPACK_ALIGNMENT);
    final int rowLength = limit(GL30.GL_UNPACK_ROW_LENGTH);
    final int skipRows = limit(GL30.GL_UNPACK_SKIP_ROWS);
    final int skipPixels = limit(GL30.GL_UNPACK_SKIP_PIXELS);

    void normalize(GL30 gl) {
      gl.glBindBuffer(GL30.GL_PIXEL_UNPACK_BUFFER, 0);
      gl.glPixelStorei(GL20.GL_UNPACK_ALIGNMENT, 4);
      gl.glPixelStorei(GL30.GL_UNPACK_ROW_LENGTH, 0);
      gl.glPixelStorei(GL30.GL_UNPACK_SKIP_ROWS, 0);
      gl.glPixelStorei(GL30.GL_UNPACK_SKIP_PIXELS, 0);
    }

    void restore(GL30 gl) {
      gl.glPixelStorei(GL20.GL_UNPACK_ALIGNMENT, alignment);
      gl.glPixelStorei(GL30.GL_UNPACK_ROW_LENGTH, rowLength);
      gl.glPixelStorei(GL30.GL_UNPACK_SKIP_ROWS, skipRows);
      gl.glPixelStorei(GL30.GL_UNPACK_SKIP_PIXELS, skipPixels);
      gl.glBindBuffer(GL30.GL_PIXEL_UNPACK_BUFFER, buffer);
    }
  }

  private static final class MatrixContent {
    final float[] values;
    final int hash;

    MatrixContent(float[] values) {
      this.values = values;
      hash = Arrays.hashCode(values);
    }

    @Override
    public int hashCode() {
      return hash;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof MatrixContent content && Arrays.equals(values, content.values);
    }
  }
}
