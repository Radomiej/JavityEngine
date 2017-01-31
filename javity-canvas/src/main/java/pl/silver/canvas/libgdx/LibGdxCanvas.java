package pl.silver.canvas.libgdx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Gdx2DPixmap;
import com.badlogic.gdx.graphics.g2d.PolygonRegion;
import com.badlogic.gdx.graphics.g2d.PolygonSprite;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.DelaunayTriangulator;
import com.badlogic.gdx.math.EarClippingTriangulator;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.ShortArray;

import pl.silver.canvas.SilverCanvas;
import pl.silver.canvas.LineDefinition;
import pl.silver.canvas.PointDefinition;
import pl.silver.canvas.PolygonDefinition;
import pl.silver.canvas.Position;
import pl.silver.canvas.ShapeHole;
import pl.silver.canvas.libgdx.triangulation.VanGoghAlgorithm;
import pl.silver.canvas.libgdx.triangulation.primitives.Point;
import pl.silver.canvas.libgdx.triangulation.primitives.Polygon;
import pl.silver.canvas.libgdx.triangulation.primitives.Triangle;

public class LibGdxCanvas implements SilverCanvas {

	private ShapeRenderer batch;
	private PolygonSpriteBatch polyBatch;
	private SpriteBatch spriteBatch;

	private OrthographicCamera camera;
	private Rectangle clipBounds;
	private Rectangle scissors = new Rectangle();

	public LibGdxCanvas() {
		this.batch = new ShapeRenderer();
		this.polyBatch = new PolygonSpriteBatch();
		this.spriteBatch = new SpriteBatch();

		camera = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		camera.setToOrtho(true, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

		clipBounds = new Rectangle(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		
		camera.update();
		batch.setProjectionMatrix(camera.combined);
		polyBatch.setProjectionMatrix(camera.combined);
		spriteBatch.setProjectionMatrix(camera.combined);
	}

	@Override
	public void drawLine(Position start, Position end, LineDefinition lineDefinition) {

		ScissorStack.calculateScissors(camera, batch.getTransformMatrix(), clipBounds, scissors);
		ScissorStack.pushScissors(scissors);

		batch.begin(ShapeType.Filled);
		batch.rectLine(start.X, start.Y, end.X, end.Y, lineDefinition.width,
				new Color(lineDefinition.startColor.toIntValue()), new Color(lineDefinition.endColor.toIntValue()));

		batch.flush();
		ScissorStack.popScissors();
		batch.end();

		if (lineDefinition.startPoint != null) {
			drawPoint(start, lineDefinition.startPoint);
		}
		if (lineDefinition.endPoint != null) {
			drawPoint(end, lineDefinition.endPoint);
		}
	}

	@Override
	public void drawPolyline(List<Position> positions, LineDefinition lineDefinition) {

		ScissorStack.calculateScissors(camera, batch.getTransformMatrix(), clipBounds, scissors);
		ScissorStack.pushScissors(scissors);

		batch.begin(ShapeType.Filled);
		for (int i = 0; i < positions.size() - 1; i++) {
			Position start = positions.get(i);
			Position end = positions.get(i + 1);
			batch.rectLine(start.X, start.Y, end.X, end.Y, lineDefinition.width,
					new Color(lineDefinition.startColor.toIntValue()), new Color(lineDefinition.endColor.toIntValue()));
		}

		batch.flush();
		ScissorStack.popScissors();
		batch.end();

		for (int i = 0; i < positions.size(); i++) {
			Position position = positions.get(i);
			if (i < positions.size() - 1)
				drawPoint(position, lineDefinition.startPoint);
			else
				drawPoint(position, lineDefinition.endPoint);
		}
	}

	@Override
	public void drawPoint(Position position, PointDefinition pointDefinition) {

		ScissorStack.calculateScissors(camera, batch.getTransformMatrix(), clipBounds, scissors);
		ScissorStack.pushScissors(scissors);
		batch.begin(ShapeType.Filled);

		batch.setColor(new Color(pointDefinition.borderColor.toIntValue()));
		batch.circle(position.X, position.Y, pointDefinition.pointSize / 2);

		batch.setColor(new Color(pointDefinition.color.toIntValue()));
		batch.circle(position.X, position.Y, (pointDefinition.pointSize - pointDefinition.borderSize) / 2);

		batch.setColor(Color.WHITE);
		batch.flush();
		ScissorStack.popScissors();
		batch.end();
	}

	@Override
	public void drawPolygon(List<Position> positions, PolygonDefinition polygonDefinition) {
		Pixmap pix = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
		pix.setColor(0xDEADBEFF); // DE is red, AD is green and BE is blue.
		pix.fill();
		Texture textureSolid = new Texture(pix);
		TextureRegion textureRegion = new TextureRegion(textureSolid);
		drawPolygon(positions, polygonDefinition, textureRegion);

		textureSolid.dispose();
		pix.dispose();
	}

	private void drawPolygon(List<Position> positions, PolygonDefinition polygonDefinition,
			TextureRegion textureRegion) {

		Polygon p = new Polygon();
		for (Position position : positions) {
			p.add(new Point(position.X, position.Y));
			// System.out.println("add: " + polygonPoint);
		}

		ArrayList<Triangle> triangles = VanGoghAlgorithm.fast(p);

		float[] vect = new float[triangles.size() * 6];
		short[] vertIndices = new short[triangles.size() * 3];

		int x = 0;
		for (Triangle triangle : triangles) {
			vect[(x * 6) + 0] = (float) triangle.getA().getX();
			vect[(x * 6) + 1] = (float) triangle.getA().getY();
			vect[(x * 6) + 2] = (float) triangle.getB().getX();
			vect[(x * 6) + 3] = (float) triangle.getB().getY();
			vect[(x * 6) + 4] = (float) triangle.getC().getX();
			vect[(x * 6) + 5] = (float) triangle.getC().getY();

			vertIndices[(x * 3) + 0] = (short) ((x * 3) + 0);
			vertIndices[(x * 3) + 1] = (short) ((x * 3) + 1);
			vertIndices[(x * 3) + 2] = (short) ((x * 3) + 2);
			x++;
		}

		PolygonRegion polyReg = new PolygonRegion(textureRegion, vect, vertIndices);
		PolygonSprite poly = new PolygonSprite(polyReg);

		ScissorStack.calculateScissors(camera, polyBatch.getTransformMatrix(), clipBounds, scissors);
		ScissorStack.pushScissors(scissors);
		polyBatch.begin();
		poly.draw(polyBatch);
		polyBatch.flush();
		ScissorStack.popScissors();
		polyBatch.end();

		LineDefinition lineDefinition = new LineDefinition(polygonDefinition.pointsDefinition,
				polygonDefinition.pointsDefinition, polygonDefinition.width, polygonDefinition.gap,
				polygonDefinition.continuity, polygonDefinition.borderColor, polygonDefinition.borderColor);

		drawPolyline(positions, lineDefinition);
	}

	@Override
	public void drawShape(List<Position> positions, List<ShapeHole> holes, PolygonDefinition polygonDefinition) {

		// Rectangle cutFragment = getBoundingBox(positions, polygonDefinition);
		// Rectangle drawFragment = new Rectangle(cutFragment);
		//
		// cutFragment.y = Gdx.graphics.getHeight() - cutFragment.y -
		// cutFragment.height;
		// cutFragment.width += 120;
		//
		// System.out.println(cutFragment);
		// TextureRegion region = ScreenUtils.getFrameBufferTexture((int)
		// cutFragment.x, (int) cutFragment.y,
		// (int) cutFragment.width, (int) cutFragment.height);
		// Sprite sprite = new Sprite(region);
		// sprite.flip(false, true);
		// sprite.setPosition(drawFragment.x, drawFragment.y);
		//
		// Pixmap pix = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
		// pix.setColor(Color.CHARTREUSE); // DE is red, AD is green and BE is
		// // blue.
		// pix.fill();
		// Texture textureSolid = new Texture(pix);
		// TextureRegion textureRegion = new TextureRegion(new
		// Texture("badlogic.jpg"));
		//
		// drawPolygon(positions, polygonDefinition);
		// for (ShapeHole shapeHole : holes) {
		// drawPolygon(shapeHole.getVertexs(), polygonDefinition,
		// textureRegion);
		// }

		Sprite sprite = generateShapeSprite(positions, holes, polygonDefinition);

		ScissorStack.calculateScissors(camera, spriteBatch.getTransformMatrix(), clipBounds, scissors);
		ScissorStack.pushScissors(scissors);
		spriteBatch.begin();
		sprite.draw(spriteBatch);
		polyBatch.flush();
		ScissorStack.popScissors();
		spriteBatch.end();
	}

	private Sprite generateShapeSprite(List<Position> positions, List<ShapeHole> holes,
			PolygonDefinition polygonDefinition) {

		Color transparent = Color.BLACK;

		Rectangle bounding = getBoundingBox(positions, polygonDefinition);
		int width = (int) bounding.width;
		int height = (int) bounding.height;

		Pixmap pix = new Pixmap(width, height, Pixmap.Format.RGBA8888);
		pix.setColor(transparent);
		pix.fill();

		drawPolygonOnPixmap(positions, bounding, pix, new Color(polygonDefinition.fillColor.toIntValue()));
		for (ShapeHole shapeHole : holes) {
			drawPolygonOnPixmap(shapeHole.getVertexs(), bounding, pix, transparent);
		}

		Pixmap pixResult = new Pixmap(width, height, Pixmap.Format.RGBA8888);
		pixResult.setColor(Color.CLEAR);
		pixResult.fill();

		int transparentColor = Color.rgba8888(transparent);
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				if (pix.getPixel(x, y) != transparentColor) {
					pixResult.drawPixel(x, y, pix.getPixel(x, y));
				}
			}
		}

		Texture textureSolid = new Texture(pixResult);
		Sprite sprite = new Sprite(textureSolid);
		sprite.flip(false, true);
		sprite.setPosition(bounding.x, bounding.y);
		return sprite;
	}

	private void drawPolygonOnPixmap(List<Position> positions, Rectangle bounding, Pixmap pix, Color fillColor) {
		// Polygon p = new Polygon();
		// for (Position position : positions) {
		// p.add(new Point(position.X - bounding.x, position.Y - bounding.y));
		// }
		//
		// ArrayList<Triangle> triangles = VanGoghAlgorithm.fast(p);
		//
		// pix.setColor(fillColor);
		// for (Triangle triangle : triangles) {
		// pix.fillTriangle((int) triangle.getA().getX(), (int)
		// triangle.getA().getY(), (int) triangle.getB().getX(),
		// (int) triangle.getB().getY(), (int) triangle.getC().getX(), (int)
		// triangle.getC().getY());
		// }
		pix.setColor(fillColor);
		
		float[] vect = new float[positions.size() * 2];
		int x = 0;
		for (Position position : positions) {
			vect[(x * 2) + 0] = position.X - bounding.x;
			vect[(x * 2) + 1] = position.Y - bounding.y;
			x++;
		}

		EarClippingTriangulator triangulator = new EarClippingTriangulator();
		ShortArray ind = triangulator.computeTriangles(vect);

		for (int i = 0; i < ind.size; i += 3) {

			int v1 = ind.get(i) * 2;
			int v2 = ind.get(i + 1) * 2;
			int v3 = ind.get(i + 2) * 2;

			pix.fillTriangle((int) vect[v1 + 0], (int) vect[v1 + 1], (int) vect[v2 + 0],
					(int) vect[v2 + 1], (int) vect[v3 + 0], (int) vect[v3 + 1]);
		}

	}

	private Rectangle getBoundingBox(List<Position> positions, PolygonDefinition polygonDefinition) {
		float minX = Float.MAX_VALUE;
		float maxX = Float.MIN_VALUE;

		float minY = Float.MAX_VALUE;
		float maxY = Float.MIN_VALUE;

		for (Position pos : positions) {
			if (pos.X < minX) {
				minX = pos.X;
			}
			if (pos.Y < minY) {
				minY = pos.Y;
			}
			if (pos.X > maxX) {
				maxX = pos.X;
			}
			if (pos.Y > maxY) {
				maxY = pos.Y;
			}
		}

		Rectangle result = new Rectangle(minX, minY, maxX - minX, maxY - minY);
		return result;
	}

	@Override
	public void clear() {
		Gdx.gl.glClearColor(0.827f, 0.827f, 0.827f, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
	}

	public OrthographicCamera getCamera() {
		return camera;
	}

	public void setCamera(OrthographicCamera camera) {
		this.camera = camera;
	}

}
