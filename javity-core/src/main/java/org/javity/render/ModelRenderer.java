package org.javity.render;

import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.javity.animation.AnimationTransition;
import org.javity.animation.ModelAnimator;
import org.javity.animation.ModelPoseAnimator;
import org.javity.animation.PlayMode;
import org.javity.avatar.AvatarBinding;
import org.javity.core.Component;

/** Shared Model resource, per-object instance, material copies and animation state. */
public final class ModelRenderer extends Component {
  public final ModelInstance instance;
  public final AnimationController animation;
  private ModelPoseAnimator observedPoseOwner;
  private final List<AvatarBinding> avatarBindings = new ArrayList<>();

  public ModelRenderer(Model model) {
    instance = new ModelInstance(Objects.requireNonNull(model));
    animation = new AnimationController(instance);
  }

  public void play(String name, boolean loop) {
    ModelPoseAnimator poseAnimator =
        getGameObject() == null ? null : getComponent(ModelPoseAnimator.class);
    if (poseAnimator != null && poseAnimator.isAssignedRenderer(this)) {
      poseAnimator.play(AnimationTransition.clip(name).mode(loop ? PlayMode.LOOP : PlayMode.ONCE));
      return;
    }
    ModelAnimator animator = getGameObject() == null ? null : getComponent(ModelAnimator.class);
    if (animator != null) {
      animator.play(name, loop ? PlayMode.LOOP : PlayMode.ONCE);
      return;
    }
    animation.setAnimation(name, loop ? -1 : 1);
  }

  @Override
  public void onUpdate(float delta) {
    if (synchronizePoseOwner()) return;
    ModelPoseAnimator poseOwner = getComponent(ModelPoseAnimator.class);
    if ((poseOwner == null || !poseOwner.isAssignedRenderer(this))
        && getComponent(ModelAnimator.class) == null) animation.update(delta);
  }

  /** Reconciles component ownership after post-WORLD changes and before this instance is drawn. */
  public void prepareForRender() {
    synchronizePoseOwner();
  }

  /** Internal ownership cache used to observe deferred removal before a render call. */
  public void observePoseOwner(ModelPoseAnimator owner) {
    observedPoseOwner = owner;
  }

  /** Internal registration so renderer removal invalidates cached avatar views immediately. */
  public void registerAvatarBinding(AvatarBinding binding) {
    Objects.requireNonNull(binding);
    if (getGameObject() == null || !getGameObject().containsComponent(this))
      throw new IllegalStateException("Avatar binding renderer is not attached");
    if (!avatarBindings.contains(binding)) avatarBindings.add(binding);
  }

  public void unregisterAvatarBinding(AvatarBinding binding) {
    avatarBindings.remove(binding);
  }

  @Override
  protected void onRelease() {
    for (AvatarBinding binding : List.copyOf(avatarBindings)) binding.rendererReleased();
    avatarBindings.clear();
  }

  private boolean synchronizePoseOwner() {
    ModelPoseAnimator current = getComponent(ModelPoseAnimator.class);
    if (observedPoseOwner != null && observedPoseOwner != current) {
      observedPoseOwner.rendererOwnershipLost(this);
      observedPoseOwner = null;
    }
    if (current == null) return false;
    observedPoseOwner = current;
    return current.prepareForRenderer(this);
  }
}
