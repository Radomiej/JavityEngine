package org.javity.components;

import org.javity.engine.NativeComponent;

import galaxy.rapid.components.RectangleColliderComponent;
import galaxy.rapid.event.RemoveRectangleColliderComponent;

public class RectangleCollider extends NativeComponent {
	public float width = 100, height = 100;
	private transient RectangleColliderComponent rectangleColliderComponent;

	@Override
	public void awake() {
		rectangleColliderComponent = new RectangleColliderComponent();
		rectangleColliderComponent.setWidth(width);
		rectangleColliderComponent.setHeight(height);

		addNativeComponent(rectangleColliderComponent);
	}

	@Override
	public void remove() {
		getRapidBus().post(new RemoveRectangleColliderComponent(rectangleColliderComponent));
	}


}
