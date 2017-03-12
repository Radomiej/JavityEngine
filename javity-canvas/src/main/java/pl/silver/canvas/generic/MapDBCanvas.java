package pl.silver.canvas.generic;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Bezier;
import com.badlogic.gdx.math.EarClippingTriangulator;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ShortArray;

import pl.silver.canvas.Color;
import pl.silver.canvas.LineDefinition;
import pl.silver.canvas.PointDefinition;
import pl.silver.canvas.PolygonDefinition;
import pl.silver.canvas.Position;
import pl.silver.canvas.ShapeHole;
import pl.silver.canvas.SilverCanvas;
import pl.silver.canvas.PointDefinition.PointType;

public class MapDBCanvas implements SilverCanvas {

	private ConcurrentMap<Long, Integer> canvas;
	private ConcurrentMap<Long, Integer> snapshotCanvas;
	private ConcurrentMap<Long, Integer> currentCanvas;

	private int width, height;

	private PolygonDefinition holeDefinition;
	private Bezier<Vector2> bezier;

	public MapDBCanvas(int size) {
		this(size, size);
	}

	public MapDBCanvas(int width, int height) {
		this.width = width;
		this.height = height;

		bezier = new Bezier<Vector2>();

		DB db = DBMaker.memoryDB().make();
		canvas = db.hashMap("canvas", Serializer.LONG, Serializer.INTEGER).createOrOpen();
		snapshotCanvas = db.hashMap("snapshotCanvas", Serializer.LONG, Serializer.INTEGER).createOrOpen();
		currentCanvas = canvas;

		PointDefinition pointsDefinition = new PointDefinition(PointType.DOT, 0, 0, Color.CLEAR, Color.CLEAR);
		holeDefinition = new PolygonDefinition(pointsDefinition, 1, 0, 0, Color.CLEAR, Color.CLEAR);
	}

	@Override
	public void drawLine(Position start, Position end, LineDefinition lineDefinition) {
		float lineWidth = Math.abs(lineDefinition.width);
		Vector2 top = new Vector2(0, lineWidth / 2);
		Vector2 down = new Vector2(0, -lineWidth / 2);
		Vector2 s = new Vector2(start.X, start.Y);
		Vector2 e = new Vector2(end.X, end.Y);

		float angle = e.cpy().add(-s.x, -s.y).angle();
		top = top.cpy().rotate(angle);
		down = down.cpy().rotate(angle);

		// Now we can compute the corner points...
		int xPoints[] = new int[4];
		int yPoints[] = new int[4];

		Vector2 topStart = top.cpy().add(s.cpy());
		Vector2 topEnd = top.cpy().add(e.cpy());
		Vector2 bottomStart = down.cpy().add(s.cpy());
		Vector2 bottomEnd = down.cpy().add(e.cpy());

		xPoints[0] = (int) topStart.x;
		yPoints[0] = (int) topStart.y;
		xPoints[1] = (int) topEnd.x;
		yPoints[1] = (int) topEnd.y;
		xPoints[2] = (int) bottomEnd.x;
		yPoints[2] = (int) bottomEnd.y;
		xPoints[3] = (int) bottomStart.x;
		yPoints[3] = (int) bottomStart.y;

		fillPolygon(xPoints, yPoints, 4, lineDefinition.color.toIntValue());
//		drawAALine(xPoints[0], yPoints[0], xPoints[1], yPoints[1], lineDefinition.color.toIntValue());
//		drawAALine(xPoints[1], yPoints[1], xPoints[2], yPoints[2], lineDefinition.color.toIntValue());
//		drawAALine(xPoints[2], yPoints[2], xPoints[3], yPoints[3], lineDefinition.color.toIntValue());
//		drawAALine(xPoints[3], yPoints[3], xPoints[0], yPoints[0], lineDefinition.color.toIntValue());
		
		
		if (lineDefinition.startPoint != null) {
			drawPoint(start, lineDefinition.startPoint);
		}
		if (lineDefinition.endPoint != null) {
			drawPoint(end, lineDefinition.endPoint);
		}
	}

	@Override
	public void drawPolyline(List<Position> positions, LineDefinition lineDefinition) {
		for (int i = 0; i < positions.size() - 1; i++) {
			Position start = positions.get(i);
			Position end = positions.get(i + 1);
			drawLine(start, end, lineDefinition);
		}
	}

	@Override
	public void drawPoint(Position position, PointDefinition pointDefinition) {
		drawWheel((int) position.X, (int) position.Y, (int) (pointDefinition.borderSize + pointDefinition.pointSize),
				pointDefinition.borderColor.toIntValue());
		drawWheel((int) position.X, (int) position.Y, (int) pointDefinition.pointSize,
				pointDefinition.color.toIntValue());
	}

	@Override
	public void drawPolygon(List<Position> positions, PolygonDefinition polygonDefinition) {
		int[] xPoints = new int[positions.size()];
		int[] yPoints = new int[positions.size()];

		int i = 0;
		for (Position position : positions) {
			xPoints[i] = (int) position.X;
			yPoints[i] = (int) position.Y;
			i++;
		}
		System.out.println("drawPolygon");
		fillPolygon(xPoints, yPoints, positions.size(), polygonDefinition.fillColor.toIntValue());

		LineDefinition lineDefinition = new LineDefinition(polygonDefinition.pointsDefinition,
				polygonDefinition.pointsDefinition, polygonDefinition.width, polygonDefinition.gap,
				polygonDefinition.continuity, polygonDefinition.borderColor);

		drawPolyline(positions, lineDefinition);
	}

	@Override
	public void drawShape(List<Position> positions, List<ShapeHole> holes, PolygonDefinition polygonDefinition) {
		currentCanvas = snapshotCanvas;

		drawPolygon(positions, polygonDefinition);
		for (ShapeHole hole : holes) {
			drawPolygon(hole.getVertexs(), holeDefinition);
		}

		currentCanvas = canvas;

		Set<Long> keys = snapshotCanvas.keySet();
		for (Long key : keys) {
			int color = snapshotCanvas.get(key);
			if (color != 0) {
				canvas.put(key, color);
			}
		}
		snapshotCanvas.clear();
	}

	@Override
	public void clear() {
		canvas.clear();
		// Set<Long> keys = canvas.keySet();
		// for (Long key : keys) {
		// canvas.put(key, 0);
		// }
	}

	private void line(int x, int y, int x2, int y2, int color) {
		// System.out.println("draw line");
		int w = x2 - x;
		int h = y2 - y;
		int dx1 = 0, dy1 = 0, dx2 = 0, dy2 = 0;
		if (w < 0)
			dx1 = -1;
		else if (w > 0)
			dx1 = 1;
		if (h < 0)
			dy1 = -1;
		else if (h > 0)
			dy1 = 1;
		if (w < 0)
			dx2 = -1;
		else if (w > 0)
			dx2 = 1;
		int longest = Math.abs(w);
		int shortest = Math.abs(h);
		if (!(longest > shortest)) {
			longest = Math.abs(h);
			shortest = Math.abs(w);
			if (h < 0)
				dy2 = -1;
			else if (h > 0)
				dy2 = 1;
			dx2 = 0;
		}
		int numerator = longest >> 1;
		for (int i = 0; i <= longest; i++) {
			putpixel(x, y, color);
			numerator += shortest;
			if (!(numerator < longest)) {
				numerator -= longest;
				x += dx1;
				y += dy1;
			} else {
				x += dx2;
				y += dy2;
			}
		}
	}

	private void drawCircle(final int centerX, final int centerY, final int radius, final int color) {
		int err = 0;
		int x = radius;
		int y = 0;

		while (x >= y) {
			putpixel(centerX + x, centerY + y, color);
			putpixel(centerX + x, centerY - y, color);
			putpixel(centerX - x, centerY + y, color);
			putpixel(centerX - x, centerY - y, color);
			putpixel(centerX + y, centerY + x, color);
			putpixel(centerX + y, centerY - x, color);
			putpixel(centerX - y, centerY + x, color);
			putpixel(centerX - y, centerY - x, color);

			if (err <= 0) {
				y += 1;
				err += 2 * y + 1;
			}
			if (err > 0) {
				x -= 1;
				err -= 2 * x + 1;
			}
		}
	}

	private void drawWheel(final int centerX, final int centerY, final int radius, final int color) {

		int err = 0;
		int x = radius;
		int y = 0;

		while (x >= y) {
			line(centerX + x, centerY + y, centerX + x, centerY - y, color);
			line(centerX - x, centerY + y, centerX - x, centerY - y, color);
			line(centerX + y, centerY + x, centerX + y, centerY - x, color);
			line(centerX - y, centerY + x, centerX - y, centerY - x, color);

			if (err <= 0) {
				y += 1;
				err += 2 * y + 1;
			}
			if (err > 0) {
				x -= 1;
				err -= 2 * x + 1;
			}
		}
	}

	private void fillPolygon(int[] xPoints, int[] yPoints, int count, int color) {
		float[] vect = new float[count * 2];
		for (int i = 0; i < count; i++) {
			vect[(i * 2) + 0] = xPoints[i];
			vect[(i * 2) + 1] = yPoints[i];
		}

		EarClippingTriangulator triangulator = new EarClippingTriangulator();
		ShortArray ind = triangulator.computeTriangles(vect);

		for (int i = 0; i < ind.size; i += 3) {

			int v1 = ind.get(i) * 2;
			int v2 = ind.get(i + 1) * 2;
			int v3 = ind.get(i + 2) * 2;

			Vector2 vt1 = new Vector2(vect[v1 + 0], vect[v1 + 1]);
			Vector2 vt2 = new Vector2(vect[v2 + 0], vect[v2 + 1]);
			Vector2 vt3 = new Vector2(vect[v3 + 0], vect[v3 + 1]);

			fillTriangle(vt1, vt2, vt3, color);
		}

	}

	private void fillTriangle(Vector2 v1, Vector2 v2, Vector2 v3, int color) {
		System.out.println("fillTriangle: " + v1 + v2 + v3);
		/*
		 * at first sort the three vertices by y-coordinate ascending so v1 is
		 * the topmost vertice
		 */
		sortVerticesAscendingByY(v1, v2, v3);

		/* here we know that v1.y <= v2.y <= v3.y */
		/* check for trivial case of bottom-flat triangle */
		if (v2.y == v3.y) {
			fillBottomFlatTriangle(v1, v2, v3, color);
		}
		/* check for trivial case of top-flat triangle */
		else if (v1.y == v2.y) {
			fillTopFlatTriangle(v1, v2, v3, color);
		} else {
			/*
			 * general case - split the triangle in a topflat and bottom-flat
			 * one
			 */
			Vector2 v4 = new Vector2((int) (v1.x + ((float) (v2.y - v1.y) / (float) (v3.y - v1.y)) * (v3.x - v1.x)),
					v2.y);
			fillBottomFlatTriangle(v1, v2, v4, color);
			fillTopFlatTriangle(v2, v4, v3, color);
		}
	}

	private void fillTopFlatTriangle(Vector2 v1, Vector2 v2, Vector2 v3, int color) {
		float invslope1 = (v3.x - v1.x) / (v3.y - v1.y);
		float invslope2 = (v3.x - v2.x) / (v3.y - v2.y);

		float curx1 = v3.x;
		float curx2 = v3.x;

		for (int scanlineY = (int) v3.y; scanlineY > v1.y; scanlineY--) {
			line((int) curx1, scanlineY, (int) curx2, scanlineY, color);
			curx1 -= invslope1;
			curx2 -= invslope2;
		}
	}

	private void fillBottomFlatTriangle(Vector2 v1, Vector2 v2, Vector2 v3, int color) {
		float invslope1 = (v2.x - v1.x) / (v2.y - v1.y);
		float invslope2 = (v3.x - v1.x) / (v3.y - v1.y);

		float curx1 = v1.x;
		float curx2 = v1.x;

		for (int scanlineY = (int) v1.y; scanlineY <= v2.y; scanlineY++) {
			line((int) curx1, scanlineY, (int) curx2, scanlineY, color);
			curx1 += invslope1;
			curx2 += invslope2;
		}
	}

	private void sortVerticesAscendingByY(Vector2 v1, Vector2 v2, Vector2 v3) {
		if (v2.y > v1.y) {
			Vector2 tmp = v1.cpy();
			v1.set(v2);
			v2.set(tmp);
		}
		if (v3.y > v1.y) {
			Vector2 tmp = v1.cpy();
			v1.set(v3);
			v3.set(tmp);
		}
		if (v3.y > v2.y) {
			Vector2 tmp = v2.cpy();
			v2.set(v3);
			v3.set(tmp);
		}

		Vector2 tmp = v1.cpy();
		v1.set(v3);
		v3.set(tmp);
	}

	private void putpixel(int x, int y, int color) {
		if (x < 0 || y < 0 || x >= width || y >= height) {
			return;
		}
		long index = getIndex(x, y);
		currentCanvas.put(index, color);
	}

	private long getIndex(int x, int y) {
		if (x < 0 || y < 0 || x >= width || y >= height) {
			System.out.println("not break index: " + x + " y: " + y);
			return -1;
		}
		return x * height + y;
	}

	@Override
	public Texture getTexture() {
		Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
		pixmap.setColor(Color.BLACK.toIntValue());
		pixmap.fill();

		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				long index = getIndex(x, y);
				if (!canvas.containsKey(index))
					continue;

				int color = canvas.get(index);
				if (color != 0) {
					pixmap.drawPixel(x, y, color);
				}

			}
		}
		Texture textureSolid = new Texture(pixmap);
		pixmap.dispose();
		return textureSolid;
	}

	private int ipart(double x) {
		return (int) x;
	}

	private double fpart(double x) {
		return x - Math.floor(x);
	}

	private double rfpart(double x) {
		return 1.0 - fpart(x);
	}

	void drawAALine(double x0, double y0, double x1, double y1, int color) {

		boolean steep = Math.abs(y1 - y0) > Math.abs(x1 - x0);
		if (steep)
			drawAALine(y0, x0, y1, x1, color);

		if (x0 > x1)
			drawAALine(x1, y1, x0, y0, color);

		double dx = x1 - x0;
		double dy = y1 - y0;
		double gradient = dy / dx;

		// handle first endpoint
		double xend = Math.round(x0);
		double yend = y0 + gradient * (xend - x0);
		double xgap = rfpart(x0 + 0.5);
		double xpxl1 = xend; // this will be used in the main loop
		double ypxl1 = ipart(yend);

		if (steep) {
			plot(ypxl1, xpxl1, rfpart(yend) * xgap, color);
			plot(ypxl1 + 1, xpxl1, fpart(yend) * xgap, color);
		} else {
			plot(xpxl1, ypxl1, rfpart(yend) * xgap, color);
			plot(xpxl1, ypxl1 + 1, fpart(yend) * xgap, color);
		}

		// first y-intersection for the main loop
		double intery = yend + gradient;

		// handle second endpoint
		xend = Math.round(x1);
		yend = y1 + gradient * (xend - x1);
		xgap = fpart(x1 + 0.5);
		double xpxl2 = xend; // this will be used in the main loop
		double ypxl2 = ipart(yend);

		if (steep) {
			plot(ypxl2, xpxl2, rfpart(yend) * xgap, color);
			plot(ypxl2 + 1, xpxl2, fpart(yend) * xgap, color);
		} else {
			plot(xpxl2, ypxl2, rfpart(yend) * xgap, color);
			plot(xpxl2, ypxl2 + 1, fpart(yend) * xgap, color);
		}

		// main loop
		for (double x = xpxl1 + 1; x <= xpxl2 - 1; x++) {
			if (steep) {
				plot(ipart(intery), x, rfpart(intery), color);
				plot(ipart(intery) + 1, x, fpart(intery), color);
			} else {
				plot(x, ipart(intery), rfpart(intery), color);
				plot(x, ipart(intery) + 1, fpart(intery), color);
			}
			intery = intery + gradient;
		}
	}

	private void plot(double x, double y, double alphaColor, int color) {
		com.badlogic.gdx.graphics.Color c = new com.badlogic.gdx.graphics.Color(color);
		c.a = (float)alphaColor;
		putpixel((int)x, (int)y, c.rgba8888(c));
	}
}
