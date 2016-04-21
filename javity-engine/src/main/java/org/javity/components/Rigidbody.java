package org.javity.components;

import org.javity.engine.NativeComponent;

import galaxy.rapid.components.Box2dComponent;
import galaxy.rapid.event.RemoveBox2dComponentEvent;

public class Rigidbody extends NativeComponent {
	private transient Box2dComponent box2dComponent;

	@Override
	public void awake() {
		box2dComponent = new Box2dComponent();
		box2dComponent.setUserData(getGameObject());
		box2dComponent.setFixtureData(this);
		addNativeComponent(box2dComponent);
	}

	@Override
	public void start() {
	}

	@Override
	public void remove() {
		getRapidBus().post(new RemoveBox2dComponentEvent(box2dComponent));
	}
}
