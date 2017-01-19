package pl.silver.canvas;

import java.util.Collection;
import java.util.List;

public interface SilverCanvas {
	void drawLine(Position start, Position end, LineDefinition lineDefinition);
	void drawPolyline(List<Position> positions, LineDefinition lineDefinition);
	void drawPoint(Position position, PointDefinition pointDefinition);
	void drawPolygon(List<Position> positions, PolygonDefinition polygonDefinition);
	void drawShape(List<Position> positions, List<ShapeHole> hole, PolygonDefinition polygonDefinition);
	void clear();
}
