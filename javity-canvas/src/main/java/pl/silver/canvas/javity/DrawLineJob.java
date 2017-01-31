package pl.silver.canvas.javity;

import com.badlogic.gdx.math.Vector2;

import pl.silver.canvas.Color;
import pl.silver.canvas.LineDefinition;
import pl.silver.canvas.PointDefinition;
import pl.silver.canvas.Position;
import pl.silver.canvas.SilverCanvas;

public class DrawLineJob extends DrawJobAbstractImpl {

	private LineDefinition lineDef;
	private Position posStart;
	private Position posEnd;
	
	public DrawLineJob(Vector2 start, Vector2 end, PointDefinition startPoint, PointDefinition endPoint,  float width, float gap, float continuity, Color startColor, Color endColor) {
		posStart = new Position(start.x, start.y);
		posEnd = new Position(end.x, end.y);
		lineDef = new LineDefinition(startPoint, endPoint, width, gap, continuity, startColor, endColor);
	}
	
	@Override
	public void draw(SilverCanvas canvas) {
		canvas.drawLine(posStart, posEnd, lineDef);
	}

}
