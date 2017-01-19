package pl.silver.canvas;

public class PolygonDefinition {
	public final PointDefinition pointsDefinition;
	public final float width;
	public final float gap, continuity;
	public final Color borderColor, fillColor;

	public PolygonDefinition(PointDefinition pointsDefinition, float width, float gap, float continuity,
			Color borderColor, Color fillColor) {
		this.pointsDefinition = pointsDefinition;
		this.width = width;
		this.gap = gap;
		this.continuity = continuity;
		this.borderColor = borderColor;
		this.fillColor = fillColor;
	}

}
