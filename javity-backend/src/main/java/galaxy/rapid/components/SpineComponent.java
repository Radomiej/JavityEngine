package galaxy.rapid.components;

import com.artemis.Component;
import com.badlogic.gdx.math.Vector2;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.Skeleton;

import galaxy.rapid.asset.SpineAssetModel;

public class SpineComponent extends Component {
	private Skeleton skeleton;
	private AnimationState state;
	private Vector2 offset = new Vector2();

	public SpineComponent() {
	}

	public SpineComponent(SpineAssetModel spine) {
		skeleton = spine.getSkeleton();
		state = spine.getAnimationState();
	}

	public Skeleton getSkeleton() {
		return skeleton;
	}

	public void setSkeleton(Skeleton skeleton) {
		this.skeleton = skeleton;
	}

	public AnimationState getAnimationState() {
		return state;
	}

	public void setAnimationState(AnimationState state) {
		this.state = state;
	}

	/**
	 * 
	 * @return copy of offset vector
	 */
	public Vector2 getOffset() {
		return offset.cpy();
	}

	/**
	 * Set Offset if offset param is non-null. Otherwise do nothing.
	 * @param offset 
	 */
	public void setOffset(Vector2 offset) {
		if (offset != null) {
			this.offset.set(offset);
		}
	}
}
