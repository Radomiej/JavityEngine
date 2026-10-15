package galaxy.rapid.components;

import com.artemis.Component;

public class KeyboardComponent extends Component {
	private float knockX;
	private float knockY;

	public float getKnockX() {
		return knockX;
	}

	public void setKnockX(float knockX) {
		this.knockX = knockX;
	}

	public float getKnockY() {
		return knockY;
	}

	public void setKnockY(float knockY) {
		this.knockY = knockY;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Float.floatToIntBits(knockX);
		result = prime * result + Float.floatToIntBits(knockY);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		KeyboardComponent other = (KeyboardComponent) obj;
		if (Float.floatToIntBits(knockX) != Float.floatToIntBits(other.knockX)) {
			return false;
		}
		if (Float.floatToIntBits(knockY) != Float.floatToIntBits(other.knockY)) {
			return false;
		}
		return true;
	}
}
