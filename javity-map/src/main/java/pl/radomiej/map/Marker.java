package pl.radomiej.map;

import java.util.ArrayList;
import java.util.List;

import org.javity.components.SpriteRenderer;
import org.javity.engine.JCamera;
import org.javity.engine.JComponent;
import org.javity.engine.JGUI;
import org.javity.engine.JGameObject;
import org.javity.engine.JInput;
import org.javity.engine.JResources;
import org.javity.engine.Resource;
import org.javity.engine.resources.SpritePivot;
import org.javity.engine.resources.SpriteResource;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

import pl.radomiej.map.events.ClickMarkerListener;

public class Marker extends JComponent {
	private String name;
	private SpriteResource resource;
	private double latitude, longitude;
	private double oldLatitude, oldLongitude;
	private float scaleX = 1f, scaleY = 1f;
	private MapComponent map;
	private SpritePivot spritePivot = SpritePivot.TOP;
	private List<ClickMarkerListener> clicksListeners = new ArrayList<ClickMarkerListener>();
	
	public Marker(double latitude, double longitude, String resourceName) {
		this(latitude, longitude, resourceName, new Vector2(1, 1));
	}

	public Marker(double latitude, double longitude, String resourceName, Vector2 scale) {
		setResource(JResources.getSprite(resourceName));
		setLatitude(latitude);
		setLongitude(longitude);
		scaleX = scale.x;
		scaleY = scale.y;
	}

	@Override
	public void start() {
		oldLatitude = latitude;
		oldLongitude = longitude;
		getGameObject().getComponent(SpriteRenderer.class).setPivot(spritePivot);
		updateWorldPosition();
	}

	@Override
	public void update() {
		updateWorldPosition();
		inputUpdate();
	}

	private void inputUpdate() {
		if (Gdx.input.justTouched()) {
			float posX = getTransform().getPosition().x;
			float posY = getTransform().getPosition().y;

			float width = getGameObject().getComponent(SpriteRenderer.class).getSpriteWidth()
					* getTransform().getScale().x;
			float height = getGameObject().getComponent(SpriteRenderer.class).getSpriteHeight()
					* getTransform().getScale().y;

			if (spritePivot.isLeft()) {
				posX -= 1.5 * width;
			} else if (spritePivot.isRight()) {
				posX += 0.5 * width;
			} else {
				posX -= 0.5 * width;
			}

			if (spritePivot.isBottom()) {
				posY += 1.5 * height;
			} else if (spritePivot.isTop()) {
				posY -= 0.5 * height;
			} else {
				posY += 0.5 * height;
			}

			Rectangle marker = new Rectangle(posX, posY, width, height);
			
			Vector2 worldPosition = JCamera.getMain().screenToWorldPoint(JInput.getMousePosition());
			Rectangle cursor = new Rectangle(worldPosition.x, worldPosition.y, 1, 1); 
			Rectangle intersector = new Rectangle();
			if (Intersector.intersectRectangles(marker, cursor, intersector)) {
//				sendMessage("OnMouseClick", intersector);
				onMouseClicked();
			}
		}
	}

	private void updateWorldPosition() {
		float cameraZoom = JCamera.getMain().getZoom();
		getTransform().setLocalScale(cameraZoom * scaleX, cameraZoom * scaleY);

		if (map != null && (oldLatitude != latitude || oldLongitude != longitude)) {
			GeoPoint worldPos = map.getWorldFromGeoPosition(latitude, longitude);
			// worldPos.lon += 104.0D;
			// worldPos.lat += 96.0D;
			getTransform().setPosition(worldPos.toVector2());
			oldLatitude = latitude;
			oldLongitude = longitude;
		}

	}

	@Override
	public void onMouseClicked() {
//		System.out.println("Click marker!");
		for(ClickMarkerListener clickMarkerListener : clicksListeners){
			clickMarkerListener.click();
		}
	}

	public void addClickMarkerListener(ClickMarkerListener clickMarkerListenr ){
		clicksListeners.add(clickMarkerListenr);
	}
	
	public void removeAllClickListeners(){
		clicksListeners.clear();
	}
	
	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name
	 *            the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	public SpriteResource getResource() {
		return resource;
	}

	public void setResource(SpriteResource resource) {
		this.resource = resource;
	}

	public void setGeoPosition(GeoPoint position) {
		setLatitude(position.getLat());
		setLongitude(position.getLon());
	}

	public GeoPoint getGeoPosition() {
		return new GeoPoint(latitude, longitude);
	}

	/**
	 * @return the latitude
	 */
	public double getLatitude() {
		return latitude;
	}

	/**
	 * @param latitude
	 *            the latitude to set
	 */
	public void setLatitude(double latitude) {
		this.latitude = latitude;
	}

	/**
	 * @return the longitude
	 */
	public double getLongitude() {
		return longitude;
	}

	/**
	 * @param longitude
	 *            the longitude to set
	 */
	public void setLongitude(double longitude) {
		this.longitude = longitude;
	}

	/**
	 * @return the scaleX
	 */
	public float getScaleX() {
		return scaleX;
	}

	/**
	 * @param scaleX
	 *            the scaleX to set
	 */
	public void setScaleX(float scaleX) {
		this.scaleX = scaleX;
	}

	/**
	 * @return the scaleY
	 */
	public float getScaleY() {
		return scaleY;
	}

	/**
	 * @param scaleY
	 *            the scaleY to set
	 */
	public void setScaleY(float scaleY) {
		this.scaleY = scaleY;
	}

	public MapComponent getMap() {
		return map;
	}

	public void setMap(MapComponent map) {
		this.map = map;
	}

	/**
	 * @return the oldLatitude
	 */
	public double getOldLatitude() {
		return oldLatitude;
	}

	/**
	 * @param oldLatitude
	 *            the oldLatitude to set
	 */
	public void setOldLatitude(double oldLatitude) {
		this.oldLatitude = oldLatitude;
	}

	/**
	 * @return the oldLongitude
	 */
	public double getOldLongitude() {
		return oldLongitude;
	}

	/**
	 * @param oldLongitude
	 *            the oldLongitude to set
	 */
	public void setOldLongitude(double oldLongitude) {
		this.oldLongitude = oldLongitude;
	}

	public SpritePivot getSpritePivot() {
		return spritePivot;
	}

	public void setSpritePivot(SpritePivot spritePivot) {
		this.spritePivot = spritePivot;
	}
}
