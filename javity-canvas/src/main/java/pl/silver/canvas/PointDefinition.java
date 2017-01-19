package pl.silver.canvas;

public class PointDefinition {
	public enum PointType {
		DOT, RECTANGLE, TRIANGLE
	}

	public final PointType pointType;
	public final float pointSize, borderSize;
	public final Color color, borderColor;

	public PointDefinition(PointType pointType, float pointSize, float borderSize, Color color, Color borderColor) {
		this.pointType = pointType;
		this.pointSize = pointSize;
		this.borderSize = borderSize;
		this.color = color;
		this.borderColor = borderColor;
	}
}
