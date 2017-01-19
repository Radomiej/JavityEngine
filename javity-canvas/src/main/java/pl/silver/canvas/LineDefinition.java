package pl.silver.canvas;

public class LineDefinition {
	public final PointDefinition startPoint, endPoint;
	public final float width;
	public final float gap, continuity;
	public final Color startColor, endColor;

	public LineDefinition(PointDefinition startPoint, PointDefinition endPoint, float width, float gap,
			float continuity, Color startColor, Color endColor) {
		this.startPoint = startPoint;
		this.endPoint = endPoint;
		this.width = width;
		this.gap = gap;
		this.continuity = continuity;
		this.startColor = startColor;
		this.endColor = endColor;
	}

	@Override
	public String toString() {
		return "LineDefinition [startPoint=" + startPoint + ", endPoint=" + endPoint + ", width=" + width + ", gap="
				+ gap + ", continuity=" + continuity + ", startColor=" + startColor + ", endColor=" + endColor + "]";
	}
	
	
}
