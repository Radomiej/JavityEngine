package org.javity.render.foliage;

import com.badlogic.gdx.graphics.g3d.Material;
import java.util.Objects;
import org.javity.core.Component;
import org.javity.render.RecoverableShaderProvider;

/**
 * CPU-only settings for a bounded static patch. Local ground is Y=0; area is centered on XZ. Count
 * means tufts or shrubs, never GameObjects. Atlas textures and template texture attributes remain
 * borrowed and must outlive rendering. Generated GPU geometry belongs to the renderer. Area
 * dimensions and scales must be positive and at most Float.MAX_VALUE/16, leaving numerical headroom
 * for plant geometry and its bounds. Settings belong to the scene owner thread.
 */
public final class FoliagePatch3D extends Component {
  public static final int MAX_COUNT = 4096;

  public enum Preset {
    GRASS,
    BUSHES
  }

  private final FoliageAtlas atlas;
  private final Preset preset;
  private float width = 8, depth = 8, minScale, maxScale, cutoff = .5f;
  private int count;
  private long seed = 1, revision;
  private Material materialTemplate;
  private Class<? extends RecoverableShaderProvider> requiredProvider;

  private FoliagePatch3D(FoliageAtlas atlas, Preset preset) {
    this.atlas = Objects.requireNonNull(atlas, "atlas");
    this.preset = preset;
    count = preset == Preset.GRASS ? 256 : 12;
    minScale = preset == Preset.GRASS ? .8f : .85f;
    maxScale = preset == Preset.GRASS ? 1.2f : 1.15f;
  }

  public static FoliagePatch3D grass(FoliageAtlas atlas) {
    return new FoliagePatch3D(atlas, Preset.GRASS);
  }

  public static FoliagePatch3D bushes(FoliageAtlas atlas) {
    return new FoliagePatch3D(atlas, Preset.BUSHES);
  }

  public FoliagePatch3D area(float width, float depth) {
    positiveFinite(width, "width");
    positiveFinite(depth, "depth");
    if (this.width != width || this.depth != depth) {
      nextRevision();
      this.width = width;
      this.depth = depth;
    }
    return this;
  }

  public FoliagePatch3D count(int count) {
    if (count < 0 || count > MAX_COUNT)
      throw new IllegalArgumentException("Foliage count must be in [0,4096]");
    if (this.count != count) {
      nextRevision();
      this.count = count;
    }
    return this;
  }

  public FoliagePatch3D seed(long seed) {
    if (this.seed != seed) {
      nextRevision();
      this.seed = seed;
    }
    return this;
  }

  public FoliagePatch3D scaleRange(float min, float max) {
    positiveFinite(min, "minimum scale");
    positiveFinite(max, "maximum scale");
    if (min > max) throw new IllegalArgumentException("Minimum foliage scale exceeds maximum");
    if (minScale != min || maxScale != max) {
      nextRevision();
      minScale = min;
      maxScale = max;
    }
    return this;
  }

  public FoliagePatch3D cutoff(float cutoff) {
    if (!Float.isFinite(cutoff) || cutoff < 0 || cutoff > 1) {
      throw new IllegalArgumentException("Foliage cutoff must be finite and in [0,1]");
    }
    if (this.cutoff != cutoff) {
      nextRevision();
      this.cutoff = cutoff;
    }
    return this;
  }

  /**
   * Copies attributes; texture resources referenced by them remain borrowed. Null selects stock.
   */
  public FoliagePatch3D material(Material template) {
    return material(template, null);
  }

  /** Explicitly requires a compatible renderer provider for custom material attributes. */
  public FoliagePatch3D material(
      Material template, Class<? extends RecoverableShaderProvider> requiredProvider) {
    if (template == null && requiredProvider != null) {
      throw new IllegalArgumentException("A provider requirement needs a material template");
    }
    Material copy = template == null ? null : template.copy();
    if (!Objects.equals(materialTemplate, copy) || this.requiredProvider != requiredProvider) {
      nextRevision();
      materialTemplate = copy;
      this.requiredProvider = requiredProvider;
    }
    return this;
  }

  // This numerical ceiling leaves ample headroom for plant dimensions and conservative bounds.
  private static void positiveFinite(float value, String name) {
    if (!Float.isFinite(value) || value <= 0 || value > Float.MAX_VALUE / 16) {
      throw new IllegalArgumentException(
          name + " must be positive, finite and at most Float.MAX_VALUE/16");
    }
  }

  private void nextRevision() {
    if (revision == Long.MAX_VALUE) throw new IllegalStateException("Foliage revision exhausted");
    revision++;
  }

  public FoliageAtlas atlas() {
    return atlas;
  }

  public Preset preset() {
    return preset;
  }

  public float width() {
    return width;
  }

  public float depth() {
    return depth;
  }

  public int count() {
    return count;
  }

  public long seed() {
    return seed;
  }

  public float minScale() {
    return minScale;
  }

  public float maxScale() {
    return maxScale;
  }

  public float cutoff() {
    return cutoff;
  }

  public long revision() {
    return revision;
  }

  public Material materialTemplate() {
    return materialTemplate == null ? null : materialTemplate.copy();
  }

  public Class<? extends RecoverableShaderProvider> requiredProvider() {
    return requiredProvider;
  }

  public Snapshot snapshot() {
    return new Snapshot(this);
  }

  /** Renderer-facing immutable settings. Material reads return independent attribute copies. */
  public static final class Snapshot {
    private final FoliageAtlas atlas;
    private final Preset preset;
    private final float width, depth, minScale, maxScale, cutoff;
    private final int count;
    private final long seed, revision;
    private final Material materialTemplate;
    private final Class<? extends RecoverableShaderProvider> requiredProvider;

    private Snapshot(FoliagePatch3D patch) {
      atlas = patch.atlas;
      preset = patch.preset;
      width = patch.width;
      depth = patch.depth;
      count = patch.count;
      seed = patch.seed;
      minScale = patch.minScale;
      maxScale = patch.maxScale;
      cutoff = patch.cutoff;
      revision = patch.revision;
      materialTemplate = patch.materialTemplate();
      requiredProvider = patch.requiredProvider;
    }

    public FoliageAtlas atlas() {
      return atlas;
    }

    public Preset preset() {
      return preset;
    }

    public float width() {
      return width;
    }

    public float depth() {
      return depth;
    }

    public int count() {
      return count;
    }

    public long seed() {
      return seed;
    }

    public float minScale() {
      return minScale;
    }

    public float maxScale() {
      return maxScale;
    }

    public float cutoff() {
      return cutoff;
    }

    public long revision() {
      return revision;
    }

    public Class<? extends RecoverableShaderProvider> requiredProvider() {
      return requiredProvider;
    }

    public Material materialTemplate() {
      return materialTemplate == null ? null : materialTemplate.copy();
    }
  }
}
