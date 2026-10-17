package galaxy.rapid.components;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

public class PolygonColliderComponent extends ColliderComponentImpl{
	private long seedHash = System.nanoTime();
	private Array<Vector2> vertex;
	private float offsetX = 0, offsetY = 0;
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Float.floatToIntBits(offsetX);
		result = prime * result + Float.floatToIntBits(offsetY);
		result = prime * result + (int) (seedHash ^ (seedHash >>> 32));
		result = prime * result + ((vertex == null) ? 0 : vertex.hashCode());
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
		PolygonColliderComponent other = (PolygonColliderComponent) obj;
		if (Float.floatToIntBits(offsetX) != Float.floatToIntBits(other.offsetX))
			return false;
		if (Float.floatToIntBits(offsetY) != Float.floatToIntBits(other.offsetY))
			return false;
		if (seedHash != other.seedHash)
			return false;
		if (vertex == null) {
			if (other.vertex != null)
				return false;
		} else if (!vertex.equals(other.vertex))
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

	public Array<Vector2> getVertex() {
		return vertex;
	}

	public void setVertex(Array<Vector2> vertex) {
		this.vertex = vertex;
	}

	
}
