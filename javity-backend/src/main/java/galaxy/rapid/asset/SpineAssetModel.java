package galaxy.rapid.asset;

import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.Skeleton;

public class SpineAssetModel {
	private Skeleton skeleton;
	private AnimationState animationState;

	public Skeleton getSkeleton() {
		return skeleton;
	}

	public void setSkeleton(Skeleton skeleton) {
		this.skeleton = skeleton;
	}

	public AnimationState getAnimationState() {
		return animationState;
	}

	public void setAnimationState(AnimationState animationState) {
		this.animationState = animationState;
	}

}
