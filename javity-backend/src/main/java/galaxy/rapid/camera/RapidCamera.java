package galaxy.rapid.camera;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

public class RapidCamera {
	private final OrthographicCamera camera;
	private float rotation = 0;
	
	public RapidCamera(OrthographicCamera camera) {
		this.camera = camera;
	}

	public OrthographicCamera getCamera() {
		return camera;
	}

	public Matrix4 getCombined() {
		return camera.combined;
	}

	public void setPosition(float posX, float posY) {
		camera.position.x = posX;
		camera.position.y = posY;
	}

	public void setPosition(Vector2 position) {
		setPosition(position.x,  position.y);
	}

	public Vector2 getPosition() {
		return new Vector2(camera.position.x, camera.position.y);
	}

	public void update() {
		camera.update();
	}

	public float getRotation() {
		return rotation;
	}

	public void setRotation(float degress) {
		float rotateDelta = degress - rotation;
		camera.rotate(rotateDelta);
		rotation = degress;
	}

	public Vector3 unproject(Vector3 vector3) {
		return camera.unproject(vector3);
	}

	public Vector3 project(Vector3 vector3) {
		return camera.project(vector3);
	}

	public Vector2 getViewport() {
		return new Vector2(camera.viewportWidth, camera.viewportHeight);
	}

	public void setViewport(Vector2 viewport) {
		camera.viewportWidth = viewport.x;
		camera.viewportHeight = viewport.y;
	}

	public void setZoom(float zoom) {
		camera.zoom = zoom;
		update();
	}

	public float getZoom() {
		return camera.zoom;
	}
}
