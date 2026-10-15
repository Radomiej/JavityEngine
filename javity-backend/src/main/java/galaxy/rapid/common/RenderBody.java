package galaxy.rapid.common;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

public class RenderBody
{
	private Rectangle bodyRectangle;
	private float rotation, originX, originY;
	private float z;

	public RenderBody() {
		this(0, 0, 0, 0);
	}

	public RenderBody(float x, float y, float width, float height) {
		bodyRectangle = new Rectangle(x, y, width, height);
	}

	public RenderBody(Rectangle rawBody) {
		this(rawBody.x, rawBody.y, rawBody.width, rawBody.height);
	}

	/**
	 * 
	 * @return referencje do pozycji obiektu.
	 */
	public Rectangle getBodyRectangle() {
		return bodyRectangle;
	}

	public void setBodyRectangle(Rectangle bodyRectangle) {
		this.bodyRectangle = bodyRectangle;
	}

	public float getRotation() {
		return rotation;
	}

	public void setRotation(float rotation) {
		this.rotation = rotation;
	}

	public RenderBody setOrigin(int originX, int originY) {
		this.originX = originX;
		this.originY = originY;

		return this;
	}

	public float getOriginX() {
		return originX;
	}

	public void setOriginX(float originX) {
		this.originX = originX;
	}

	public float getOriginY() {
		return originY;
	}

	public void setOriginY(float originY) {
		this.originY = originY;
	}

	/**
	 * 
	 * @return pozycja środka obiektu
	 */
	public Vector2 getPosition() {
		Vector2 pos = new Vector2();
		pos.x = bodyRectangle.x + bodyRectangle.width / 2;
		pos.y = bodyRectangle.y + bodyRectangle.height / 2;
		return pos;
	}

	public float getZ() {
		return z;
	}

	public void setZ(float z) {
		this.z = z;
	}

	public float getX() {
		return bodyRectangle.x;
	}

	public float getY() {
		return bodyRectangle.y;
	}

	public float getWidth() {
		return bodyRectangle.width;
	}

	public float getHeight() {
		return bodyRectangle.height;
	}
}
