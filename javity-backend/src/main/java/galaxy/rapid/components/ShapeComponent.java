package galaxy.rapid.components;

import java.util.ArrayList;
import java.util.List;

import com.artemis.Component;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector2;

public class ShapeComponent extends Component {
	private List<Vector2> polygonPoints = new ArrayList<Vector2>();
	private ShapeType shapeType = ShapeType.Filled;
	private float width = 1;
	private Color color = new Color(Color.WHITE);
	
	public List<Vector2> getPolygonPoints() {
		return polygonPoints;
	}

	public void setPolygonPoints(List<Vector2> polygonPoints) {
		this.polygonPoints = polygonPoints;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((polygonPoints == null) ? 0 : polygonPoints.hashCode());
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
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
		ShapeComponent other = (ShapeComponent) obj;
		if (polygonPoints == null) {
			if (other.polygonPoints != null)
				return false;
		} else if (!polygonPoints.equals(other.polygonPoints))
			return false;
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "ShapeComponent [polygonPoints=" + polygonPoints + "]";
	}

	public ShapeType getShapeType() {
		return shapeType;
	}

	public void setShapeType(ShapeType shapeType) {
		this.shapeType = shapeType;
	}

	public float getWidth() {
		return width;
	}

	public void setWidth(float width) {
		this.width = width;
	}

	public Color getColor() {
		return color;
	}

	public void setColor(Color color) {
		this.color.set(color);
	}

}
