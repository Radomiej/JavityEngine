package org.javity.components;

import java.util.ArrayList;
import java.util.List;

import org.javity.engine.NativeComponent;

import com.artemis.Entity;

import galaxy.rapid.components.RectangleColliderComponent;
import galaxy.rapid.event.AddRectangleColliderComponent;
import galaxy.rapid.event.RemoveRectangleColliderComponent;

public class RectangleCollider extends NativeComponent {
	public float width = 100, height = 100;
	public float offsetX = 0, offsetY = 0;
	private transient RectangleColliderComponent rectangleColliderComponent;
	
	public RectangleCollider() {
		this(100, 100);
	}

	public RectangleCollider(float width, float height) {
		this.width = width;
		this.height = height;
	}

	@Override
	public void awake() {
		rectangleColliderComponent = new RectangleColliderComponent();
		rectangleColliderComponent.setWidth(width);
		rectangleColliderComponent.setHeight(height);
		// addNativeComponent(rectangleColliderComponent);
	}

	@Override
	public void start() {
	}
	
	@Override
	public void onEnabled() {
		System.out.println("GO: " + getGameObject());
		System.out.println("ENT: " + getGameObject().getEntity());
		Entity entity = getGameObject().getEntity();
		getRapidBus().post(new AddRectangleColliderComponent(rectangleColliderComponent, entity));
	}

	@Override
	public void onDisable() {
		getRapidBus().post(new RemoveRectangleColliderComponent(rectangleColliderComponent));
	}

	@Override
	public void remove() {
		// getRapidBus().post(new
		// RemoveRectangleColliderComponent(rectangleColliderComponent));
	}

}
