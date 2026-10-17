package galaxy.rapid.components;

import com.artemis.Component;

public class CircleColliderComponent extends ColliderComponentImpl {
	private long seedHash = System.nanoTime();
	private float radius;
	private float offsetX = 0, offsetY = 0;

	public float getRadius() {
		return radius;
	}

	public void setRadius(float radius) {
		this.radius = radius;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Float.floatToIntBits(offsetX);
		result = prime * result + Float.floatToIntBits(offsetY);
		result = prime * result + Float.floatToIntBits(radius);
		result = prime * result + (int) (seedHash ^ (seedHash >>> 32));
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
		CircleColliderComponent other = (CircleColliderComponent) obj;
		if (Float.floatToIntBits(offsetX) != Float.floatToIntBits(other.offsetX))
			return false;
		if (Float.floatToIntBits(offsetY) != Float.floatToIntBits(other.offsetY))
			return false;
		if (Float.floatToIntBits(radius) != Float.floatToIntBits(other.radius))
			return false;
		if (seedHash != other.seedHash)
			return false;
		return true;
	}

	/**
	 * @return the seedHash
	 */
	public long getSeedHash() {
		return seedHash;
	}

	/**
	 * @param seedHash
	 *            the seedHash to set
	 */
	public void setSeedHash(long seedHash) {
		this.seedHash = seedHash;
	}

	/**
	 * @return the offsetX
	 */
	public float getOffsetX() {
		return offsetX;
	}

	/**
	 * @param offsetX
	 *            the offsetX to set
	 */
	public void setOffsetX(float offsetX) {
		this.offsetX = offsetX;
	}

	/**
	 * @return the offsetY
	 */
	public float getOffsetY() {
		return offsetY;
	}

	/**
	 * @param offsetY
	 *            the offsetY to set
	 */
	public void setOffsetY(float offsetY) {
		this.offsetY = offsetY;
	}
}
