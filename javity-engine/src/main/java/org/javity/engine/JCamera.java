package org.javity.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

public class JCamera {
	
	private static JCamera main;

	/**
	 * Main camera of current scene
	 */
	public static JCamera getMain() {
		return main;
	}

	static void setMain(com.badlogic.gdx.graphics.Camera nativeCamera) {
		JCamera cameraNativeAdapter = new JCamera(nativeCamera);
		main = cameraNativeAdapter;
	}

	private com.badlogic.gdx.graphics.Camera nativeCamera;
	
	public JCamera(com.badlogic.gdx.graphics.Camera nativeCamera) {
		this.nativeCamera = nativeCamera;
	}

	public Vector2 getPosition() {
		Vector2 cameraPosition = new Vector2(nativeCamera.position.x, nativeCamera.position.y);
		return cameraPosition;
	}

	public void setPosition(Vector2 position) {
		nativeCamera.position.x = position.x;
		nativeCamera.position.y = position.y;
		nativeCamera.update();
	}
	
	public Vector2 screenToWorldPoint(Vector2 screenPoint){
		Vector3 worldPoint3 = nativeCamera.unproject(new Vector3(screenPoint.x, screenPoint.y, 0));
		Vector2 worldPoint = new Vector2(worldPoint3.x, worldPoint3.y);
		return worldPoint;
	}
	
}
