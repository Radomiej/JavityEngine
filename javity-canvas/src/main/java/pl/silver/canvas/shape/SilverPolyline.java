package pl.silver.canvas.shape;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.utils.FloatArray;

import pl.silver.canvas.Position;

public class SilverPolyline {

	private FloatArray vertices = new FloatArray(10);
	public void addPoint(float x, float y) {
		vertices.add(x);
		vertices.add(y);
	}
	
	public List<Position> getPositions() {
		List<Position> positions = new ArrayList<Position>();
		
		for(int x = 0; x < vertices.size; x += 2){
		
			Position position = new Position(vertices.get(x), vertices.get(x + 1));
			positions.add(position);
		}
		return positions;
	}

}
