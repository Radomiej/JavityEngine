package pl.radomiej.map;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.geojson.Point;
import org.javity.components.SpriteRenderer;
import org.javity.engine.JComponent;
import org.javity.engine.JGameObject;
import org.javity.engine.JResources;
import org.javity.engine.JSceneManager;
import org.javity.engine.resources.SpriteResource;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;

public class LayerComponent extends JComponent {
	private int zoom;
	private long size;
	public int rangeShow = 2;
	private transient Map<String, File> tempsFiles = new HashMap<String, File>();
	private transient Map<Long, TileComponent> tiles = new HashMap<Long, TileComponent>();

	public LayerComponent() {
	}

	public LayerComponent(int x) {
		zoom = x;
	}

	@Override
	public void start() {
		size = (long) Math.pow(2, zoom);
		Gdx.app.log(LayerComponent.class.getSimpleName(), "Layer: " + zoom);
	}

	public void show(Vector2 showPosition) {
		showPosition.x /= getTransform().getScale().x;
		showPosition.y /= getTransform().getScale().y;

		Vector2 tilePos = getTileVector(showPosition);
		size = (long) Math.pow(2, zoom);

		// Gdx.app.log(LayerComponent.class.getSimpleName(), "Show Layer: " +
		// zoom);
		int minX = (int) Math.floor(tilePos.x - rangeShow);
		if (minX < 0)
			minX = 0;

		int maxX = (int) Math.ceil(tilePos.x + rangeShow);
		if (maxX >= size)
			maxX = (int) size;

		int minY = (int) Math.floor(tilePos.y - rangeShow);
		if (minY < 0)
			minY = 0;

		int maxY = (int) Math.ceil(tilePos.y + rangeShow);
		if (maxY >= size)
			maxY = (int) size;

		// System.out.println("minX: " + minX + " maxX: " + maxX + " minY: " +
		// minY + " maxY: " + maxY);
		for (int x = minX; x < maxX; x++) {
			for (int y = minY; y < maxY; y++) {
				Vector2 position = getTransform().getPosition();

				position.x += ((float) x - size / 2f) * 256f;
				position.y -= ((float) y - size / 2f) * 256f;

				position.x += 256 / 2f;
				position.y -= 256 / 2f;

				if (x == 0 && y == 0) {
					System.out.println("POS: " + position);
				}

				long tileId = getTileId(x, y);
				if (tiles.containsKey(tileId)) {
					tiles.get(tileId).getGameObject().setEnabled(true);
					continue;
				} else {
					final JGameObject tile = JSceneManager.current.instantiateGameObject(position);
					TileComponent tileComponent = new TileComponent(x, y, zoom);
					tile.addComponent(new SpriteRenderer("resources/atlas/images.atlas#babel"));
					tile.addComponent(tileComponent);
					tile.getTransform().setParent(getGameObject());

					tiles.put(tileId, tileComponent);
				}

			}
		}
	}

	private long getTileId(int x, int y) {
		long id = x * size + y;
		return id;
	}

	private Vector2 getTileVector(Vector2 showPosition) {
		Vector2 tilePosition = showPosition.cpy();
		tilePosition.x -= 256 / 2f;
		tilePosition.y += 256 / 2f;
		tilePosition.x /= 256;
		tilePosition.y /= -256;
		tilePosition.x += size / 2f;
		tilePosition.y += size / 2f;

		return tilePosition;
	}

	private Vector2 getWorldVector(Vector2 tile) {
		Vector2 worldPosition = tile.cpy();
		worldPosition.x -= size / 2f;
		worldPosition.y -= size / 2f;
		
		worldPosition.x *= 256;
		worldPosition.y *= -256;
		
		worldPosition.x += 256 / 2f + 192;
		worldPosition.y -= (256 + 16);

		return worldPosition;
	}

	private void addTempTiles(File tempFile, String tileName, JGameObject logoObject) {
		FileHandle fileHandler = Gdx.files.absolute(tempFile.getAbsolutePath());
		Texture texture = new Texture(fileHandler);
		SpriteResource spriteResource = JResources.addMemorySprite(tileName, texture);
		logoObject.getComponent(SpriteRenderer.class).setSprite(spriteResource);
	}

	public static Vector2 getTileNumber(final double lat, final double lon, final int zoom) {
		double xtile = Math.floor((lon + 180) / 360 * (1 << zoom));
		double ytile = Math
				.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2
						* (1 << zoom));
		if (xtile < 0)
			xtile = 0;
		if (xtile >= (1 << zoom))
			xtile = ((1 << zoom) - 1);
		if (ytile < 0)
			ytile = 0;
		if (ytile >= (1 << zoom))
			ytile = ((1 << zoom) - 1);
		return new Vector2((float)xtile, (float)ytile);
	}

	static double tile2lon(int x, int z) {
		return x / Math.pow(2.0, z) * 360.0f - 180f;
	}

	static double tile2lat(int y, int z) {
		double n = Math.PI - (2.0 * Math.PI * y) / Math.pow(2.0, z);
		return Math.toDegrees(Math.atan(Math.sinh(n)));
	}

	public void hide() {
		for (TileComponent tile : tiles.values()) {
			tile.getGameObject().setEnabled(false);
		}
	}

	public Point getGeoFromWorldPosition(Vector2 worldPosition) {

		System.out.println("tile: " + getTileVector(worldPosition));
		Point point = new Point(tile2lon((int) getTileVector(worldPosition).x, zoom), tile2lat((int) getTileVector(worldPosition).y, zoom));
		return point;
	}

	public Point getGeoFromTilePosition(int tileX, int tileY) {

		Point point = new Point(tile2lon(tileY, zoom), tile2lat(tileX, zoom));
		return point;
	}
	
	public Vector2 getWorldFromGeoPosition(double lat, double lon) {

		Vector2 tile = getTileNumber(lat, lon, zoom);
		Vector2 world = getWorldVector(tile);
		
		return world;
	}
}
