package org.javity.components;

import java.util.UUID;

import org.javity.engine.JGameObject;
import org.javity.engine.JGameObjectImpl;
import org.javity.engine.NativeComponent;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

import galaxy.rapid.common.UuidHelper;
import galaxy.rapid.components.PositionComponent;

public class Transform extends NativeComponent {
	private Vector2 position = new Vector2();
	private Vector2 scale = new Vector2(1, 1);
	private float rotation;
	private int orderZ = 0;
	private transient PositionComponent positionComponent;
	private JGameObject parent;
	private Vector2 localPosition = new Vector2();
	private float localRotation = 0;
	private float absoluteRotation = 0;
	private Vector2 localScale = new Vector2(1, 1);
	private Vector2 absoluteScale = new Vector2(1, 1);
	private int localOrderZ = 0;
	private UUID uuid = UUID.randomUUID();

	public Transform() {
		positionComponent = new PositionComponent();
		positionComponent.setPosition(position.cpy());
		positionComponent.setRotation(rotation);
		positionComponent.setScale(scale.cpy());
		addNativeComponent(positionComponent);
	}

	@Override
	public void awake() {

	}

	@Override
	public void start() {

	}

	@Override
	public void update() {
		
		// Scale
		if (parent != null) {
			Vector2 parentScale = parent.getTransform().getScale();
			absoluteScale.x = parentScale.x * localScale.x;
			absoluteScale.y = parentScale.y * localScale.y;
			scale.set(absoluteScale);
			
		} else {
			absoluteScale.set(scale);
		}
		positionComponent.getScale().set(absoluteScale);

		// Rotation
		absoluteRotation = positionComponent.getRotation();
		if (absoluteRotation != rotation + localRotation) {
			rotation = absoluteRotation - localRotation;
		}

		if (parent != null) {
			localRotation = parent.getTransform().rotation;
			positionComponent.setRotation(rotation + localRotation);
		} else {
			localRotation = 0;
		}

		// if(rotation + localRotation != positionComponent.getRotation()){
		// absolute = positionComponent.getRotation() - localRotation;
		// }

		if (position.equals(positionComponent.getPosition())) {
			if (parent != null) {
				// System.out.println("Pozycja rodzica: " +
				// parent.getTransform().position);
				localRotation = parent.getTransform().rotation;
				
				Vector2 parentScale = parent.getTransform().getScale();
				Vector2 newPosition = localPosition.cpy();
				newPosition.x *= parentScale.x;
				newPosition.y *= parentScale.y;
				newPosition = newPosition.rotate(localRotation);
				newPosition.add(parent.getTransform().position);

				this.position.set(newPosition);
				positionComponent.setPosition(newPosition);
			}
		} else {// Physic is usage this object
			position.set(positionComponent.getPosition());
			if (parent != null)
				updateLocalPosition(parent);
		}

	}

	public Vector2 getPosition() {
		return position.cpy();
	}

	public void setPosition(float x, float y){
		Vector2 newPosition = new Vector2(x, y);
		setPosition(newPosition);
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

	private void updateLocalPosition(JGameObject parent) {
		if (parent.isDestroy()) {
			setParent(null);
			return;
		}

		Vector2 parentPosition = parent.getTransform().position;
		Vector2 thisPosition = this.position;
		localPosition.x = thisPosition.x - parentPosition.x;
		localPosition.y = thisPosition.y - parentPosition.y;
	}

	public Vector2 getScale() {
		if(parent == null){
			return scale.cpy();
		}else{
			Vector2 parentScale = parent.getTransform().getScale();
			absoluteScale.x = parentScale.x * localScale.x;
			absoluteScale.y = parentScale.y * localScale.y;
			return absoluteScale.cpy();
		}
	}

	public void setScale(Vector2 newScale) {
		this.scale.set(newScale);
	}

	public float getRotation() {
		return rotation;
	}

	public void setRotation(float rotation) {
		this.rotation = rotation;
		// if (positionComponent != null)
		// positionComponent.setRotation(rotation);
	}

	public void setParent(JGameObject parent) {
		this.parent = parent;
		// localScale.set(getParentScale(parent));
		if (parent != null) {
			updateLocalPosition(parent);
		}
	}

	private Vector2 getParentScale(JGameObject parent) {
		return parent != null ? parent.getTransform().scale : new Vector2(1, 1);
	}

	public JGameObject getParent() {
		return parent;
	}

	public Vector2 getLocalPosition() {
		return localPosition;
	}

	public void setLocalPosition(Vector2 localPosition) {
		this.localPosition = localPosition;
	}

	public void setLocalScale(float scaleX, float scaleY) {
		localScale.set(scaleX, scaleX);
	}

	public void setZ(int z) {
		orderZ = z;
	}

	public int getOrderZ() {
		int realZ = orderZ;
		if(parent != null) realZ += parent.getTransform().getOrderZ();
		return realZ;
	}
}
