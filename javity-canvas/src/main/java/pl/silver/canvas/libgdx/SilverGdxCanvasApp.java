package pl.silver.canvas.libgdx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import pl.silver.canvas.SilverCanvas;
import pl.silver.canvas.Color;
import pl.silver.canvas.LineDefinition;
import pl.silver.canvas.PointDefinition;
import pl.silver.canvas.PointDefinition.PointType;
import pl.silver.canvas.PolygonDefinition;
import pl.silver.canvas.Position;
import pl.silver.canvas.ShapeHole;



public class SilverGdxCanvasApp extends ApplicationAdapter {
	
	SilverCanvas canvas;
	boolean draw;
	
	@Override
	public void create () {
		canvas = new LibGdxCanvas();
	}

	@Override
	public void render () {
		
		if(draw) return;
//		draw = true;
		
		canvas.clear();
		
		Position posStart = new Position(50f, 300f);
		Position posEnd = new Position(200f, 400f);
		
		PointDefinition startPoint = new PointDefinition(PointType.DOT, 21, 10, Color.BLUE, Color.GREEN);
		PointDefinition endPoint = new PointDefinition(PointType.DOT, 21, 10, Color.BLUE, Color.GREEN);
		
		LineDefinition lineDef = new LineDefinition(startPoint, endPoint, 20, 0, 0, Color.WHITE, Color.WHITE);
		canvas.drawLine(posStart, posEnd, lineDef);
		
		List<Position> positions = new ArrayList<Position>();
		positions.add(new Position(0, 0));
		positions.add(new Position(0, 120));
		positions.add(new Position(120, 120));
		positions.add(new Position(180, 60));
		positions.add(new Position(180, 40));
		positions.add(new Position(120, 0));
		
		
		PointDefinition pointsDefinition = new PointDefinition(PointType.DOT, 21, 4, Color.CHARTREUSE, Color.GREEN);
		PolygonDefinition polygonDefinition = new PolygonDefinition(pointsDefinition, 8, 0, 0, Color.RED, Color.BLUE);
		canvas.drawPolygon(positions, polygonDefinition);
		
		
		List<Position> shapePositions = new ArrayList<Position>();
		shapePositions.add(new Position(200, 0));
		shapePositions.add(new Position(200, 200));
		shapePositions.add(new Position(650, 200));
		shapePositions.add(new Position(650, 0));
		shapePositions.add(new Position(100, 0));
		
		ShapeHole hole1 = new ShapeHole();
		hole1.addVertexPosition(new Position(250, 40));
		hole1.addVertexPosition(new Position(300, 80));
		hole1.addVertexPosition(new Position(350, 40));
		hole1.addVertexPosition(new Position(320, 100));
		hole1.addVertexPosition(new Position(350, 160));		
		hole1.addVertexPosition(new Position(300, 180));
		hole1.addVertexPosition(new Position(250, 160));
		hole1.addVertexPosition(new Position(220, 100));
		hole1.addVertexPosition(new Position(250, 40));
		//Holes 2
		
		ShapeHole hole2 = new ShapeHole();
		hole2.addVertexPosition(new Position(500, 40));
		hole2.addVertexPosition(new Position(500, 80));
		hole2.addVertexPosition(new Position(600, 80));
		hole2.addVertexPosition(new Position(600, 40));
		hole2.addVertexPosition(new Position(500, 40));
		
		ShapeHole hole3 = new ShapeHole();
		hole3.addVertexPosition(new Position(300, 40));
		hole3.addVertexPosition(new Position(300, 80));
		hole3.addVertexPosition(new Position(500, 80));
		hole3.addVertexPosition(new Position(500, 40));
		hole3.addVertexPosition(new Position(300, 40));
		
		List<ShapeHole> shapeHoles = new ArrayList<ShapeHole>();
		shapeHoles.add(hole1);
		shapeHoles.add(hole2);
		shapeHoles.add(hole3);
		
		long time = System.nanoTime();
		canvas.drawShape(shapePositions, shapeHoles, polygonDefinition);
		time = System.nanoTime() - time;
		time /= 100000;
		
		System.out.println("time of execution: " + time);
	}
	
	@Override
	public void dispose () {
	}
}
