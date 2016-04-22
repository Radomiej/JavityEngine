package org.javity.components;

import org.javity.engine.JCamera;
import org.javity.engine.NativeComponent;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

import galaxy.rapid.components.PositionComponent;

public class Camera extends NativeComponent {
	
	
	@Override
	public void awake() {
	}
	
	@Override
	public void start() {
		Vector2 positionCam = JCamera.getMain().getPosition();
//		getTransform().setPosition(positionCam);
	}
	
	@Override
	public void update() {
		JCamera.getMain().setPosition(getTransform().getPosition());
	}
	
}
