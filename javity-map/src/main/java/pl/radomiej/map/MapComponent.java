package pl.radomiej.map;

import java.util.ArrayList;
import java.util.List;

import org.geojson.Point;
import org.javity.components.LineRenderer;
import org.javity.components.RectangleCollider;
import org.javity.components.Rigidbody;
import org.javity.components.SpriteRenderer;
import org.javity.engine.JCamera;
import org.javity.engine.JComponent;
import org.javity.engine.JGUI;
import org.javity.engine.JGameObject;
import org.javity.engine.JGameObjectImpl;
import org.javity.engine.JInput;
import org.javity.engine.JSceneManager;
import org.javity.engine.JTime;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

import galaxy.rapid.components.ShapeComponent;

public class MapComponent extends JComponent {

	public float zoomSpeed = 1;
	int minZoom = 2;
	int maxZoom = 17;
	int currentZoom;
	int oldZoom;
	int zoomDelta;
	long size;

	private float draggedSpeed = 1;
	private List<LayerComponent> layers = new ArrayList<LayerComponent>();
	private float targetCameraZoom = 1;
	private float currentZoomSpeed = 0f;

	public MapComponent() {
		this(2, 17);
	}

	public MapComponent(int minZoom, int maxZoom) {
		this.maxZoom = maxZoom;
		this.minZoom = minZoom;
		currentZoom = minZoom;
		zoomDelta = maxZoom - minZoom;
		size = (long) Math.pow(2, maxZoom);
	}

	@Override
	public void start() {
		JCamera.getMain().setZoom(32000f);

		System.out.println("start Map");
		for (int x = minZoom; x <= maxZoom; x++) {
			int zoomLevel = (maxZoom - minZoom) - (x - minZoom);
			int localScale = (int) Math.pow(2, zoomLevel);

			JGameObject layer = JSceneManager.current.instantiateGameObject(getTransform().getPosition());
			LayerComponent layerComponent = new LayerComponent(x);
			layer.addComponent(layerComponent);
			layer.getTransform().setParent(getGameObject());
			layer.getTransform().setLocalScale(localScale, localScale);
			Gdx.app.debug("layers scale", "" + layer.getTransform().getScale());

			layers.add(layerComponent);
			// if(x == minZoom){
			// draggedSpeed = (float) Math.pow(2, zoomLevel);
			// layer.getComponent(LayerComponent.class).show();
			// }
		}
		System.out.println("layers create");

		oldZoom = minZoom;
		currentZoom = minZoom;
		changeZoom(minZoom);
	}

	@Override
	public void update() {
		if (JInput.getMouseWheelDelta() != 0) {
			int scrollDelta = JInput.getMouseWheelDelta() > 0 ? 1 : -1;
			int newZoom = currentZoom - scrollDelta;
			if (newZoom <= minZoom)
				newZoom = minZoom;
			if (newZoom > maxZoom)
				newZoom = maxZoom;

			changeZoom(newZoom);

		}
		if (JCamera.getMain().getZoom() != targetCameraZoom) {

			boolean end = false;
			float deltaChange = JCamera.getMain().getZoom() - targetCameraZoom;
			int dir = deltaChange > 0 ? -1 : 1;
			float tmpZoomSpeed = currentZoomSpeed * JTime.INSTANCE.getDelta();
			if (Math.abs(deltaChange) < tmpZoomSpeed) {
				tmpZoomSpeed = Math.abs(deltaChange);
				end = true;
			}
			tmpZoomSpeed *= (float) dir;

			float newZoom = JCamera.getMain().getZoom() + tmpZoomSpeed;
			// Gdx.app.log(this.getClass().getSimpleName(), "target zoom: " +
			// targetCameraZoom + "zoom new: " + newZoom + " currentZoom: " +
			// JCamera.getMain().getZoom());
			JCamera.getMain().setZoom(newZoom);
			
			if(end){
				currentZoomSpeed = 0;
			}
		}
	}

	public void setZoom(int zoom) {
		if (zoom <= minZoom)
			zoom = minZoom;
		if (zoom > maxZoom)
			zoom = maxZoom;

		changeZoom(zoom);
	}

	public int getZoom() {
		return currentZoom;
	}

	private void changeZoom(int newZoom) {
		if (currentZoom != oldZoom) {
			zoomLayer(oldZoom).hide();
		}
		zoomLayer(oldZoom).getTransform().setZ(oldZoom);

		currentLayer().getTransform().setZ(currentZoom);
		oldZoom = currentZoom;
		currentZoom = newZoom;

		int zoomLevel = getZoomLevel();
		draggedSpeed = (float) Math.pow(2, zoomLevel);
		targetCameraZoom = (int) Math.pow(2, zoomLevel);
		float newChangeZoomSpeed = zoomSpeed * (float) Math.pow(2, getZoomLevel());
		if(currentZoomSpeed < newChangeZoomSpeed) currentZoomSpeed = newChangeZoomSpeed;
		
		System.out.println("Zoom: " + currentZoom + " zoom level: " + zoomLevel + " target zoom: " + targetCameraZoom);

		if (currentZoom < oldZoom) {
			currentLayer().getTransform().setZ(oldZoom + 1);
		} else {
			currentLayer().getTransform().setZ(currentZoom);
		}
		currentLayer().show(JCamera.getMain().getPosition());
		
		
	}

	private int getZoomLevel() {
		return (maxZoom - minZoom) - (currentZoom - minZoom);
	}

	public LayerComponent currentLayer() {
		return layers.get(currentZoom - minZoom);
	}

	public LayerComponent zoomLayer(int zoom) {
		return layers.get(zoom - minZoom);
	}

	@Override
	public void onMouseClicked() {

		LayerComponent layer = zoomLayer(maxZoom);
		Vector2 clickWorld = JCamera.getMain().screenToWorldPoint(JInput.getMousePosition());
		Point geoMap = getGeoFromWorldPosition(clickWorld);
		Point geo = layer.getGeoFromWorldPosition(clickWorld);
		Vector2 world = layer.getWorldFromGeoPosition(geo.getCoordinates().getLatitude(),
				geo.getCoordinates().getLongitude());

		System.out.println("click world: " + clickWorld);
		System.out.println("geo: " + geo);
		System.out.println("geoMap" + geoMap);
		System.out.println("world: " + world);

	}

	@Override
	public void onMouseDragged(Vector2 draggedDelta) {
		if (JGUI.INSTANCE.isStageHandleInput()) {
			// System.out.println("dragged blocked ");
			return;
		}
		// System.out.println("camera pos: " + JCamera.getMain().getPosition());
		// System.out.println("dragged: " + draggedSpeed);
		Vector2 position = JCamera.getMain().getPosition();
		position.add(-draggedDelta.x * draggedSpeed, draggedDelta.y * draggedSpeed);
		JCamera.getMain().setPosition(position);
		this.currentLayer().show(JCamera.getMain().getPosition());
	}

	public void centerOn(double latitude, double longitude) {
		LayerComponent layerComponent = zoomLayer(maxZoom);
		Vector2 world = layerComponent.getWorldFromGeoPosition(latitude, longitude);
		JCamera.getMain().setPosition(world);
		// int zoom = maxZoom - minZoom + 1;
		// Vector2 tile = getTileVector(latitude, longitude, zoom);
		// System.out.println("center on: " + tile + " zoom: " + zoom);
		// long size = (long) Math.pow(2, zoom);
		// tile.x -= size / 2;
		// tile.y -= size / 2;
		//
		// tile.x *= 256;
		// tile.y *= 256;
		// System.out.println("camera pos: " + tile);
		// JCamera.getMain().setPosition(tile);
	}

	public void addPath(String name, List<Vector2> path, Color color) {

		JGameObject line = instantiateGameObject(getTransform().getPosition());
		line.getTransform().setZ(100);
		line.setName(name);
		line.getTransform().setParent(getGameObject());
		LineRenderer lineRenderer = new LineRenderer();
		lineRenderer.setColor(color);
		lineRenderer.setWidth(100);
		line.addComponent(lineRenderer);

		for (Vector2 step : path) {
			step = getWorldFromGeoPosition(step.x, step.y);
			lineRenderer.addPoint(step);
		}
	}

	public void addMarker(String name, Marker marker) {
		JGameObject markerObj = instantiateGameObject(getTransform().getPosition());

		Vector2 step = getWorldFromGeoPosition(marker.getLatitude(), marker.getLongitude());
		markerObj.getTransform().setZ(100);
		markerObj.getTransform().setPosition(step);
		markerObj.getTransform().setParent(getGameObject());
		markerObj.setName(name);
		SpriteRenderer spriteRenderer = new SpriteRenderer(marker.getResource());
		markerObj.addComponent(spriteRenderer);
		markerObj.addComponent(marker);
	}

	public Point getGeoFromWorldPosition(Vector2 worldPosition) {
		Point point = new Point(tile2lon((int) getTileVector(worldPosition).x, maxZoom),
				tile2lat((int) getTileVector(worldPosition).y, maxZoom));
		return point;
	}

	public Point getGeoFromTilePosition(int tileX, int tileY) {
		Point point = new Point(tile2lon(tileY, maxZoom), tile2lat(tileX, maxZoom));
		return point;
	}

	public Vector2 getWorldFromGeoPosition(double lat, double lon) {
		Vector2 tile = getTileNumber(lat, lon, maxZoom);
		Vector2 world = getWorldVector(tile);

		return world;
	}

	static private Vector2 getTileNumber(final double lat, final double lon, final int zoom) {
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
		return new Vector2((float) xtile, (float) ytile);
	}

	static private double tile2lon(int x, int z) {
		return x / Math.pow(2.0, z) * 360.0f - 180f;
	}

	static private double tile2lat(int y, int z) {
		double n = Math.PI - (2.0 * Math.PI * y) / Math.pow(2.0, z);
		return Math.toDegrees(Math.atan(Math.sinh(n)));
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
}
