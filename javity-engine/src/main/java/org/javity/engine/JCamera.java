package org.javity.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

import galaxy.rapid.camera.RapidCamera;

public class JCamera {
	
	private static JCamera main;

	/**
	 * Main camera of current scene
	 */
	public static JCamera getMain() {
		return main;
	}

	static void setMain(RapidCamera camera) {
		JCamera cameraNativeAdapter = new JCamera(camera);
		main = cameraNativeAdapter;
	}

	private RapidCamera nativeCamera;
	
	public JCamera(RapidCamera camera) {
		this.nativeCamera = camera;
	}

	public Vector2 getPosition() {
		Vector2 cameraPosition = new Vector2(nativeCamera.getPosition().x, nativeCamera.getPosition().y);
		return cameraPosition;
	}

	public void setPosition(Vector2 position) {
		nativeCamera.setPosition(position);
		nativeCamera.update();
	}
	
	public Vector2 screenToWorldPoint(Vector2 screenPoint){
		Vector3 worldPoint3 = nativeCamera.unproject(new Vector3(screenPoint.x, screenPoint.y, 0));
		Vector2 worldPoint = new Vector2(worldPoint3.x, worldPoint3.y);
		return worldPoint;
	}

	public Vector2 worldToScreenPoint(Vector2 worldPoint){
		Vector3 screenPoint3 = nativeCamera.project(new Vector3(worldPoint.x, worldPoint.y, 0));
		Vector2 screenPoint = new Vector2(screenPoint3.x, screenPoint3.y);
		return screenPoint;
	}

	
	public void setZoom(float zoom) {
		main.nativeCamera.setZoom(zoom);
	}

	public float getZoom() {
		return main.nativeCamera.getZoom();
	}
	
	public RapidCamera getNative(){
		return nativeCamera;
	}
	
}
