package galaxy.rapid.components;

import com.artemis.Component;

public class RectangleColliderComponent extends ColliderComponentImpl{
	private long seedHash = System.nanoTime();
	private float width, height;
	private float offsetX = 0, offsetY = 0;
	/**
	 * @return the width
	 */
	public float getWidth() {
		return width;
	}

	/**
	 * @param width
	 *            the width to set
	 */
	public void setWidth(float width) {
		this.width = width;
	}

	/**
	 * @return the height
	 */
	public float getHeight() {
		return height;
	}

	/**
	 * @param height
	 *            the height to set
	 */
	public void setHeight(float height) {
		this.height = height;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Float.floatToIntBits(height);
		result = prime * result + Float.floatToIntBits(offsetX);
		result = prime * result + Float.floatToIntBits(offsetY);
		result = prime * result + (int) (seedHash ^ (seedHash >>> 32));
		result = prime * result + Float.floatToIntBits(width);
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RectangleColliderComponent other = (RectangleColliderComponent) obj;
		if (Float.floatToIntBits(height) != Float.floatToIntBits(other.height))
			return false;
		if (Float.floatToIntBits(offsetX) != Float.floatToIntBits(other.offsetX))
			return false;
		if (Float.floatToIntBits(offsetY) != Float.floatToIntBits(other.offsetY))
			return false;
		if (seedHash != other.seedHash)
			return false;
		if (Float.floatToIntBits(width) != Float.floatToIntBits(other.width))
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
	 * @param seedHash the seedHash to set
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
	 * @param offsetX the offsetX to set
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
	 * @param offsetY the offsetY to set
	 */
	public void setOffsetY(float offsetY) {
		this.offsetY = offsetY;
	}
}
