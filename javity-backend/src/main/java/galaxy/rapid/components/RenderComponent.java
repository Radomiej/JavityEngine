package galaxy.rapid.components;

import com.artemis.Component;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;

import galaxy.rapid.common.RenderBody;

public class RenderComponent extends Component {
	private Vector2 cornerPosition = new Vector2();
	private RenderBody renderBody;
	private Color color;
	private boolean flipX, flipY;
	private float scaleX, scaleY;
	private boolean render = true;
	private int orderZ;

	public RenderComponent() {
	}

	public RenderComponent(int orderZ) {
		this.orderZ = orderZ;
	}

	public int getOrderZ() {
		return orderZ;
	}

	public void setOrderZ(int orderZ) {
		this.orderZ = orderZ;
	}

	public boolean isFlipX() {
		return flipX;
	}

	public void setFlipX(boolean flipX) {
		this.flipX = flipX;
	}

	public boolean isFlipY() {
		return flipY;
	}

	public void setFlipY(boolean flipY) {
		this.flipY = flipY;
	}

	public Color getColor() {
		return color;
	}

	public void setColor(Color color) {
		this.color = color;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((color == null) ? 0 : color.hashCode());
		result = prime * result + ((cornerPosition == null) ? 0 : cornerPosition.hashCode());
		result = prime * result + (flipX ? 1231 : 1237);
		result = prime * result + (flipY ? 1231 : 1237);
		result = prime * result + orderZ;
		result = prime * result + (render ? 1231 : 1237);
		result = prime * result + ((renderBody == null) ? 0 : renderBody.hashCode());
		result = prime * result + Float.floatToIntBits(scaleX);
		result = prime * result + Float.floatToIntBits(scaleY);
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
		RenderComponent other = (RenderComponent) obj;
		if (color == null) {
			if (other.color != null)
				return false;
		} else if (!color.equals(other.color))
			return false;
		if (cornerPosition == null) {
			if (other.cornerPosition != null)
				return false;
		} else if (!cornerPosition.equals(other.cornerPosition))
			return false;
		if (flipX != other.flipX)
			return false;
		if (flipY != other.flipY)
			return false;
		if (orderZ != other.orderZ)
			return false;
		if (render != other.render)
			return false;
		if (renderBody == null) {
			if (other.renderBody != null)
				return false;
		} else if (!renderBody.equals(other.renderBody))
			return false;
		if (Float.floatToIntBits(scaleX) != Float.floatToIntBits(other.scaleX))
			return false;
		if (Float.floatToIntBits(scaleY) != Float.floatToIntBits(other.scaleY))
			return false;
		return true;
	}

	public boolean isRender() {
		return render;
	}

	public void setRender(boolean render) {
		this.render = render;
	}

	/**
	 * @return the scaleX
	 */
	public float getScaleX() {
		return scaleX;
	}

	/**
	 * @param scaleX the scaleX to set
	 */
	public void setScaleX(float scaleX) {
		this.scaleX = scaleX;
	}

	/**
	 * @return the scaleY
	 */
	public float getScaleY() {
		return scaleY;
	}

	/**
	 * @param scaleY the scaleY to set
	 */
	public void setScaleY(float scaleY) {
		this.scaleY = scaleY;
	}

}
