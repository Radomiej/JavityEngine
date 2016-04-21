package org.javity.components;

import org.javity.engine.NativeComponent;

import com.badlogic.gdx.math.Vector2;

import galaxy.rapid.components.PositionComponent;

public class Transform extends NativeComponent {
	private Vector2 position = Vector2.Zero; 
	private Vector2 scale = new Vector2(1, 1); 
	private float rotation;
	private transient PositionComponent positionComponent;
	
	@Override
	public void awake() {
		positionComponent = new PositionComponent();
		positionComponent.setPosition(position);
		positionComponent.setRotation(getRotation());
		addNativeComponent(positionComponent);
	}
	
	@Override
	public void start() {
	
	}
	
	@Override
	public void update() {
		position = positionComponent.getPosition();
	}
	
	public Vector2 getPosition() {
		return position;
	}

	public void setPosition(Vector2 position) {
		this.position = position;
		if(positionComponent != null) positionComponent.setPosition(position);
	}

	public Vector2 getScale() {
		return scale;
	}

	public void setScale(Vector2 scale) {
		this.scale = scale;
	}

	public float getRotation() {
		return rotation;
	}

	public void setRotation(float rotation) {
		this.rotation = rotation;
		if(positionComponent != null) positionComponent.setRotation(rotation);
	}
}
