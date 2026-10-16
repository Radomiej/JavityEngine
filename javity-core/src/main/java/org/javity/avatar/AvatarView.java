package org.javity.avatar;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.RenderableProvider;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import java.util.*;
import org.javity.render.ModelRenderer;

/** Independent cached visibility and base transform over one canonical animated instance. */
public final class AvatarView implements RenderableProvider {
  private final AvatarBinding binding;
  private AvatarPreset preset = AvatarPreset.FULL_BODY;
  private Set<AvatarPart> selectedParts;
  private final Map<String, Boolean> slotVisibility = new HashMap<>();
  private final Matrix4 baseTransform = new Matrix4();
  private final Matrix4 composedBase = new Matrix4();

  AvatarView(AvatarBinding binding) {
    this.binding = binding;
  }

  public AvatarView preset(AvatarPreset preset) {
    this.preset = Objects.requireNonNull(preset, "preset");
    selectedParts = null;
    return this;
  }

  /** Selects semantic regions directly. An empty set intentionally hides all semantic geometry. */
  public AvatarView parts(Set<AvatarPart> selected) {
    Objects.requireNonNull(selected, "selected");
    EnumSet<AvatarPart> copy = EnumSet.noneOf(AvatarPart.class);
    for (AvatarPart part : selected) copy.add(Objects.requireNonNull(part, "part"));
    selectedParts = Collections.unmodifiableSet(copy);
    preset = null;
    return this;
  }

  /** Slot overrides are per-view; an enabled source NodePart still cannot be resurrected. */
  public AvatarView slotVisible(String name, boolean visible) {
    binding.ensureForView();
    if (!binding.hasSlot(name)) throw new IllegalArgumentException("Unknown avatar slot: " + name);
    slotVisibility.put(name, visible);
    return this;
  }

  /**
   * Sets this view's base matrix. Direct RenderableProvider gathering uses it as the full base.
   * GdxRenderer.renderWorld composes the owning Transform3D first, then this view matrix.
   */
  public AvatarView setBaseTransform(Matrix4 base) {
    Objects.requireNonNull(base, "base");
    for (float value : base.val)
      if (!Float.isFinite(value))
        throw new IllegalArgumentException("Avatar base transform must be finite");
    baseTransform.set(base);
    return this;
  }

  public ModelRenderer renderer() {
    return binding.renderer();
  }

  public Matrix4 baseTransform() {
    return new Matrix4(baseTransform);
  }

  /**
   * Direct ModelBatch submission uses viewBase × nodeGlobal for rigid parts and viewBase for
   * skinned parts.
   */
  @Override
  public void getRenderables(Array<Renderable> out, Pool<Renderable> pool) {
    getRenderablesWithBase(out, pool, IDENTITY);
  }

  private static final Matrix4 IDENTITY = new Matrix4();

  /** Internal renderer seam that composes the owning object's world transform without mutation. */
  public void getRenderablesWithBase(
      Array<Renderable> out, Pool<Renderable> pool, Matrix4 objectBase) {
    getRenderablesWithBase(out, pool, objectBase, null);
  }

  /** Immutable source part and authored region identity; pooled Renderables are never keys. */
  public record RenderKey(PartKey part, String preparedRegion) {}

  /** Renderer collection seam. Keys, when supplied, append exactly once per emitted renderable. */
  public void getRenderablesWithBase(
      Array<Renderable> out, Pool<Renderable> pool, Matrix4 objectBase, Array<RenderKey> keys) {
    Objects.requireNonNull(out, "out");
    Objects.requireNonNull(pool, "pool");
    Objects.requireNonNull(objectBase, "objectBase");
    if (!binding.rendererLive()) return;
    composedBase.set(objectBase).mul(baseTransform);
    for (AvatarBinding.Entry entry : binding.entries()) {
      if (!entry.nodePart.enabled) continue;
      if (entry.preparedTarget) {
        for (var prepared : binding.preparedParts().entrySet()) {
          AvatarPart semantic = binding.regionSemantics().get(prepared.getKey());
          if (!selected(semantic, entry.slot)) continue;
          Renderable renderable =
              binding.instance().getRenderable(pool.obtain(), entry.node, entry.nodePart);
          renderable.meshPart.set(prepared.getValue());
          setTransform(renderable, entry);
          out.add(renderable);
          if (keys != null) keys.add(new RenderKey(entry.key, prepared.getKey()));
        }
      } else if (selected(entry.semantic, entry.slot)) {
        Renderable renderable =
            binding.instance().getRenderable(pool.obtain(), entry.node, entry.nodePart);
        setTransform(renderable, entry);
        out.add(renderable);
        if (keys != null) keys.add(new RenderKey(entry.key, null));
      }
    }
  }

  private void setTransform(Renderable renderable, AvatarBinding.Entry entry) {
    if (renderable.bones == null)
      renderable.worldTransform.set(composedBase).mul(entry.node.globalTransform);
    else renderable.worldTransform.set(composedBase);
  }

  /** Checks only draws selected by this view against a renderer's configured shader limits. */
  public void validateShaderCapacity(int maxBones, int maxBoneWeights) {
    if (maxBones < 1 || maxBoneWeights < 0)
      throw new IllegalArgumentException("Invalid shader capacity");
    if (!binding.rendererLive()) return;
    for (AvatarBinding.Entry entry : binding.entries()) {
      if (!entry.nodePart.enabled) continue;
      if (entry.preparedTarget) {
        boolean selectedRegion = false;
        for (AvatarPart semantic : binding.regionSemantics().values())
          if (selected(semantic, entry.slot)) selectedRegion = true;
        if (!selectedRegion) continue;
      } else if (!selected(entry.semantic, entry.slot)) continue;
      AvatarBinding.validateCapacity(
          entry.nodePart, entry.key.toString(), maxBones, maxBoneWeights);
    }
  }

  private boolean selected(AvatarPart semantic, String slot) {
    if (semantic != null) {
      boolean body =
          selectedParts != null
              ? selectedParts.contains(semantic)
              : switch (preset) {
                case FULL_BODY -> true;
                case ARMS_ONLY ->
                    semantic == AvatarPart.LEFT_ARM || semantic == AvatarPart.RIGHT_ARM;
                case BODY_WITHOUT_HEAD -> semantic != AvatarPart.HEAD;
              };
      if (!body) return false;
      Boolean override = slot == null ? null : slotVisibility.get(slot);
      return override == null || override;
    }
    if (slot != null) {
      Boolean override = slotVisibility.get(slot);
      if (override != null) return override;
      return preset == AvatarPreset.FULL_BODY || preset == AvatarPreset.BODY_WITHOUT_HEAD;
    }
    // Untagged ordinary geometry is part of the complete body, never implicitly leaked into
    // FPS/custom selections.
    return selectedParts == null && preset != AvatarPreset.ARMS_ONLY;
  }
}
