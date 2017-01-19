package pl.silver.canvas;

import java.util.ArrayList;
import java.util.List;

public class ShapeHole {
	private List<Position> shapeHolePositions = new ArrayList<Position>();
	
	public ShapeHole() {
	}
	
	public ShapeHole(List<Position> shapeHolePolygonVertex) {
		shapeHolePositions.addAll(shapeHolePolygonVertex);
	}
	
	public void addVertexPosition(Position position){
		shapeHolePositions.add(position);
	}
	
	public List<Position> getVertexs(){
		return shapeHolePositions;
	}
}
