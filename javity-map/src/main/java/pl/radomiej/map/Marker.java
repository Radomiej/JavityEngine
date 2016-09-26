package pl.radomiej.map;

import org.javity.components.SpriteRenderer;
import org.javity.engine.JCamera;
import org.javity.engine.JComponent;
import org.javity.engine.JResources;
import org.javity.engine.Resource;
import org.javity.engine.resources.SpritePivot;
import org.javity.engine.resources.SpriteResource;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector2;

public class Marker extends JComponent {
	private String name;
	private SpriteResource resource;
	private double latitude, longitude;
	private float scaleX = 1f, scaleY = 1f;

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
		float camerZoom = JCamera.getMain().getZoom();
		getTransform().setLocalScale(camerZoom * scaleX, camerZoom * scaleY);
		getGameObject().getComponent(SpriteRenderer.class).setPivot(SpritePivot.TOP);
	}

	@Override
	public void update() {
		float camerZoom = JCamera.getMain().getZoom();
		getTransform().setLocalScale(camerZoom * scaleX, camerZoom * scaleY);
		// System.out.println("camera Zoom x: " + camerZoom * scaleX + " y: " +
		// camerZoom * scaleY);
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
}
