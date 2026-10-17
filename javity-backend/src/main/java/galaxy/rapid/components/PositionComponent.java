package galaxy.rapid.components;

import com.artemis.Component;
import com.badlogic.gdx.math.Vector2;

public class PositionComponent extends Component{
	private Vector2 position;
	private Vector2 scale;
	private float rotation;

	public PositionComponent() {
		position = new Vector2();
		scale = new Vector2(1, 1);
	}
	
	public Vector2 getPosition() {
		return position;
	}

	public void setPosition(Vector2 position) {
		this.position.set(position);
	}

	public float getRotation() {
		return rotation;
	}

	public void setRotation(float rotation) {
		this.rotation = rotation;
	}

	@Override
	public String toString() {
		return "PositionComponent [position=" + position + ", rotation=" + rotation + "]";
	}

	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((position == null) ? 0 : position.hashCode());
		result = prime * result + Float.floatToIntBits(rotation);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PositionComponent other = (PositionComponent) obj;
		if (position == null) {
			if (other.position != null)
				return false;
		} else if (!position.equals(other.position))
			return false;
		if (Float.floatToIntBits(rotation) != Float.floatToIntBits(other.rotation))
			return false;
		return true;
	}

	public Vector2 getScale() {
		return scale;
	}

	public void setScale(Vector2 scale) {
		this.scale = scale;
	}
}
