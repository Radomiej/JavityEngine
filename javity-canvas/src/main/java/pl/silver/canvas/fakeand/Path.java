package pl.silver.canvas.fakeand;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.math.Bezier;
import com.badlogic.gdx.math.Vector2;

import pl.silver.canvas.fakeand.Path.FillType;
import pl.silver.canvas.shape.SilverPolyline;

public class Path {

	public enum FillType {
		WINDING, EVEN_ODD
	}

	private List<SilverPolyline> polylines = new ArrayList<SilverPolyline>();
	private SilverPolyline currentPolyline;
	private Vector2 lastAddPoint = new Vector2();

	public Path() {

	}

	public void transform(Matrix currentMatrix, Path transformedPath) {
		// TODO Auto-generated method stub

	}

	public void transform(Matrix transform) {
		// TODO Auto-generated method stub

	}

	public void computeBounds(RectF pathBounds, boolean b) {
		// TODO Auto-generated method stub

	}

	public void moveTo(float x, float y) {
		currentPolyline = new SilverPolyline();
		polylines.add(currentPolyline);
		currentPolyline.addPoint(x, y);
		lastAddPoint.set(x, y);
	}

	public void lineTo(float x, float y) {
		currentPolyline.addPoint(x, y);
		lastAddPoint.set(x, y);
	}

	public void cubicTo(float x1, float y1, float x2, float y2, float x3, float y3) {
		Vector2 startPoint = new Vector2(lastAddPoint);
		Vector2 bezierOnePoint = new Vector2(x1, y1);
		Vector2 bezierTwoPoint = new Vector2(x2, y2);
		Vector2 endPoint = new Vector2(x3, y3);
		Vector2 tmp = new Vector2();
		Vector2 bezierCurrentPoint = new Vector2();

		float distance = Vector2.dst(startPoint.x, startPoint.y, bezierOnePoint.x, bezierOnePoint.y);
		distance +=	Vector2.dst(bezierOnePoint.x, bezierOnePoint.y, bezierTwoPoint.x, bezierTwoPoint.y);
		distance +=	Vector2.dst(bezierTwoPoint.x, bezierTwoPoint.y, endPoint.x, endPoint.y);
		
		System.out.println("distance bezier: " + distance);
		
		
		float count = (int)(distance / 10);
		for (int x = 0; x <= count; x++) {
			float t = x / count;
			bezierCurrentPoint = Bezier.cubic(bezierCurrentPoint, t, startPoint, bezierOnePoint, bezierTwoPoint, endPoint, tmp);
			lineTo(bezierCurrentPoint.x, bezierCurrentPoint.y);
		}
	}

	public void quadTo(float x1, float y1, float x2, float y2) {
		Vector2 startPoint = new Vector2(lastAddPoint);
		Vector2 bezierPoint = new Vector2(x1, y1);
		Vector2 endPoint = new Vector2(x2, y2);
		Vector2 tmp = new Vector2();
		Vector2 bezierCurrentPoint = new Vector2();

		float distance = Vector2.dst(startPoint.x, startPoint.y, bezierPoint.x, bezierPoint.y);
		distance +=	Vector2.dst(bezierPoint.x, bezierPoint.y, endPoint.x, endPoint.y);
		
		System.out.println("distance bezier: " + distance);
		
		
		float count = (int)(distance / 10);
		for (int x = 0; x <= count; x++) {
			float t = x / count;
			bezierCurrentPoint = Bezier.quadratic(bezierCurrentPoint, t, startPoint, bezierPoint, endPoint, tmp);
			lineTo(bezierCurrentPoint.x, bezierCurrentPoint.y);
		}
	}

	public void close() {
		// TODO Auto-generated method stub

	}

	public void setFillType(FillType clipRuleFromState) {
		// TODO Auto-generated method stub

	}

	public void addPath(Path path, Matrix combinedPathMatrix) {
		// TODO Auto-generated method stub

	}

	public FillType getFillType() {
		// TODO Auto-generated method stub
		return null;
	}

	public void addPath(Path spanPath) {

	}

	public List<SilverPolyline> getPolylines() {
		return polylines;
	}

}
