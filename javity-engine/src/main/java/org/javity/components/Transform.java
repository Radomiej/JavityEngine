package org.javity.components;

import org.javity.engine.GameObject;
import org.javity.engine.NativeComponent;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

import galaxy.rapid.components.PositionComponent;

public class Transform extends NativeComponent {
	private Vector2 position = Vector2.Zero;
	private Vector2 scale = new Vector2(1, 1);
	private float rotation;
	private int orderZ = 0;
	private transient PositionComponent positionComponent;
	private GameObject parent;
	private Vector2 localPosition = new Vector2();
	private float localRotation = 0;
	private float absoluteRotation = 0;
	private Vector2 localScale = new Vector2(1, 1);
	private int localOrderZ = 0;

	@Override
	public void awake() {
		positionComponent = new PositionComponent();
		positionComponent.setPosition(position);
		positionComponent.setRotation(rotation);
		positionComponent.setScale(scale);
		addNativeComponent(positionComponent);
	}

	@Override
	public void start() {

	}

	@Override
	public void update() {
		
		absoluteRotation = positionComponent.getRotation();
		if(absoluteRotation != rotation + localRotation){
			rotation = absoluteRotation - localRotation;
		}
		
		if (parent != null) {
			localRotation = parent.getTransform().rotation;
			positionComponent.setRotation(rotation + localRotation);
		}else{
			localRotation = 0;
		}
		
//		if(rotation + localRotation != positionComponent.getRotation()){
//			absolute = positionComponent.getRotation() - localRotation;
//		}
		
		if (position.equals(positionComponent.getPosition())) {
			if (parent != null) {
//				System.out.println("Pozycja rodzica: " + parent.getTransform().position);
				localRotation = parent.getTransform().rotation;
				Vector2 newPosition = localPosition.cpy().rotate(localRotation);
				newPosition.add(parent.getTransform().position);
				
				this.position.set(newPosition);
				positionComponent.setPosition(newPosition);
			}
		} else {//Physic is usage this object
			position.set(positionComponent.getPosition());
			if (parent != null)
				updateLocalPosition(parent);
		}

	}

	public Vector2 getPosition() {
		return position.cpy();
	}

	public void setPosition(Vector2 position) {
		this.position.set(position);
		if (positionComponent != null)
			positionComponent.setPosition(position);
		if (parent != null)
			updateLocalPosition(parent);
	}

	public void setPosition(Vector3 position3d) {
		Vector2 newPosition = new Vector2(position3d.x, position3d.y);
		setPosition(newPosition);
	}

	public Vector2 getScale() {
		return scale;
	}

	public void setScale(Vector2 newScale) {
		float scaleDeltaX = newScale.x / scale.x;
		float scaleDeltaY = newScale.y / scale.y;
		this.scale.set(newScale);
		if (positionComponent != null){
			localPosition.x *= scaleDeltaX;
			localPosition.y *= scaleDeltaY;
		}
	}

	public float getRotation() {
		return rotation;
	}

	public void setRotation(float rotation) {
		this.rotation = rotation;
//		if (positionComponent != null)
//			positionComponent.setRotation(rotation);
	}

	public void setParent(GameObject parent) {
		this.parent = parent;
		localScale.set(parent.getTransform().scale);
		updateLocalPosition(parent);
	}

	public GameObject getParent() {
		return parent;
	}

	private void updateLocalPosition(GameObject parent) {
		Vector2 parentPosition = parent.getTransform().position;
		Vector2 thisPosition = this.position;
		localPosition.x = thisPosition.x - parentPosition.x;
		localPosition.y = thisPosition.y - parentPosition.y;
	}

	public Vector2 getLocalPosition() {
		return localPosition;
	}

	public void setLocalPosition(Vector2 localPosition) {
		this.localPosition = localPosition;
	}
}
