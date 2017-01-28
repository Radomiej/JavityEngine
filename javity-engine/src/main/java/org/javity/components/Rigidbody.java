package org.javity.components;

import org.javity.engine.NativeComponent;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;

import galaxy.rapid.components.Box2dComponent;
import galaxy.rapid.event.RemoveBox2dComponentEvent;

public class Rigidbody extends NativeComponent {
	public enum RigidbodyType {
		DYNAMIC(BodyType.DynamicBody), STATIC(BodyType.StaticBody), KINEMATIC(BodyType.KinematicBody);
		
		private BodyType nativeBodyType;
		private RigidbodyType(BodyType nativeBodyType) {
			this.nativeBodyType = nativeBodyType;
		}
		
		private BodyType getNativeBodyType(){
			return nativeBodyType;
		}
	}

	private transient Box2dComponent box2dComponent;
	private RigidbodyType rigidbodyType = RigidbodyType.DYNAMIC;

	public Rigidbody() {
		this(false);
	}

	public Rigidbody(boolean isKinematic) {
		if (isKinematic) {
			rigidbodyType = RigidbodyType.KINEMATIC;
		} 
	}

	@Override
	public void awake() {
		box2dComponent = new Box2dComponent();
		box2dComponent.setUserData(getGameObject());
		box2dComponent.setFixtureData(this);
		box2dComponent.setBodyType(rigidbodyType.getNativeBodyType());
		addNativeComponent(box2dComponent);
	}

	@Override
	public void start() {
	}

	@Override
	public void remove() {
		getRapidBus().post(new RemoveBox2dComponentEvent(box2dComponent));
	}

	/**
	 * Access to box2d body object.
	 * 
	 * @return native box2d body
	 */
	public Body getNativeBody() {
		return box2dComponent.getBody();
	}
}
