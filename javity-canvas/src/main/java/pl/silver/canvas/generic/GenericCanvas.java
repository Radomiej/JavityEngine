package pl.silver.canvas.generic;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.EarClippingTriangulator;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ShortArray;

import pl.silver.canvas.Color;
import pl.silver.canvas.LineDefinition;
import pl.silver.canvas.PointDefinition;
import pl.silver.canvas.PolygonDefinition;
import pl.silver.canvas.Position;
import pl.silver.canvas.ShapeHole;
import pl.silver.canvas.SilverCanvas;

public class GenericCanvas implements SilverCanvas {

	// ConcurrentMap<Long, Integer> canvas;
	private Color[][] canvas;
	private int width, height;

	public GenericCanvas(int size) {
		this(size, size);
	}

	public GenericCanvas(int width, int height) {
		this.width = width;
		this.height = height;

		// DB db = DBMaker.memoryDB().make();
		// canvas = db.hashMap("canvas", Serializer.LONG,
		// Serializer.INTEGER).createOrOpen();

		canvas = new Color[width][];
		for (int x = 0; x < width; x++) {
			canvas[x] = new Color[height];
			for (int y = 0; y < height; y++) {
				canvas[x][y] = new Color(0, 0, 0, 0);
			}
		}
	}

	@Override
	public void clear() {

		canvas = new Color[width][];
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				canvas[x][y] = new Color(0, 0, 0, 0);
			}
		}

		// Set<Long> keys = canvas.keySet();
		// for(Long key : keys){
		// canvas.put(key, 0);
		// }
	}

	@Override
	public void drawLine(Position start, Position end, LineDefinition lineDefinition) {
		System.out.println("draw Line");

		float lineWidth = Math.abs(lineDefinition.width);
		Vector2 top = new Vector2(0, lineWidth / 2);
		Vector2 down = new Vector2(0, -lineWidth / 2);
		Vector2 s = new Vector2(start.X, start.Y);
		Vector2 e = new Vector2(end.X, end.Y);

		float angle = e.cpy().add(-s.x, -s.y).angle();
		System.out.println("angle: " + angle + " s: " + s + " e: " + e);
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

		if (lineDefinition.startPoint != null) {
			drawPoint(start, lineDefinition.startPoint);
		}
		if (lineDefinition.endPoint != null) {
			drawPoint(end, lineDefinition.endPoint);
		}
	}

	@Override
	public void drawPolyline(List<Position> positions, LineDefinition lineDefinition) {
		// TODO Auto-generated method stub

	}

	@Override
	public void drawPoint(Position position, PointDefinition pointDefinition) {
		// TODO Auto-generated method stub

	}

	@Override
	public void drawPolygon(List<Position> positions, PolygonDefinition polygonDefinition) {
		// TODO Auto-generated method stub

	}

	@Override
	public void drawShape(List<Position> positions, List<ShapeHole> hole, PolygonDefinition polygonDefinition) {
		// TODO Auto-generated method stub

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
		int d = (5 - radius * 4) / 4;
		int x = 0;
		int y = radius;

		do {
			putpixel(centerX + x, centerY + y, color);
			putpixel(centerX + x, centerY - y, color);
			putpixel(centerX - x, centerY + y, color);
			putpixel(centerX - x, centerY - y, color);
			putpixel(centerX + y, centerY + x, color);
			putpixel(centerX + y, centerY - x, color);
			putpixel(centerX - y, centerY + x, color);
			putpixel(centerX - y, centerY - x, color);
			if (d < 0) {
				d += 2 * x + 1;
			} else {
				d += 2 * (x - y) + 1;
				y--;
			}
			x++;
		} while (x <= y);

	}

	private void drawWheel(final int centerX, final int centerY, final int radius, final int color) {
		int d = (5 - radius * 4) / 4;
		int x = 0;
		int y = radius;

		do {
			line(centerX + x, centerY + y, centerX + x, centerY - y, color);
			line(centerX - x, centerY + y, centerX - x, centerY - y, color);
			line(centerX + y, centerY + x, centerX + y, centerY - x, color);
			line(centerX - y, centerY + x, centerX - y, centerY - x, color);
			if (d < 0) {
				d += 2 * x + 1;
			} else {
				d += 2 * (x - y) + 1;
				y--;
			}
			x++;
		} while (x <= y);

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
		System.out.println("v1" + v1 + "v2" + v2 + "v3" + v3);
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

		System.out.println("v1" + v1 + "v2" + v2 + "v3" + v3);
	}

	private void putpixel(int x, int y, int color) {
		if (x < 0 || y < 0 || x >= width || y >= height) {
			// System.out.println("not pixel x: " + x + " y: " + y);
			return;
		}
		// System.out.println("put pixel x: " + x + " y: " + y + " color: " +
		// color);
		// long index = getIndex(x, y);
		// canvas.put(index, color);

		canvas[x][y] = new Color(color);
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
//				long index = getIndex(x, y);
				// if(!canvas.containsKey(index)) continue;

				int color = canvas[x][y].toIntValue();
				if (color != 0) {
					// System.out.println("color to pixmap: " + color);
					pixmap.drawPixel(x, y, color);
				}

			}
		}
		Texture textureSolid = new Texture(pixmap);
		pixmap.dispose();
		return textureSolid;
	}

}
