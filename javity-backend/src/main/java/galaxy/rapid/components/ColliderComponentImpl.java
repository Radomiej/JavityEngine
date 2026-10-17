package galaxy.rapid.components;

import com.artemis.Component;

public abstract class ColliderComponentImpl extends Component implements ColliderComponent {

	private float friction = 0.5f, density = 0.3f, restitution = 0.3f;
	
	public void setFriction(float friction) {
		this.friction = friction;
	}

	public void setDensity(float density) {
		this.density = density;
	}

	public void setRestitution(float restitution) {
		this.restitution = restitution;
	}

	@Override
	public float getFriction() {
		return friction;
	}

	@Override
	public float getDensity() {
		return density;
	}

	@Override
	public float getRestitution() {
		return restitution;
	}

}
