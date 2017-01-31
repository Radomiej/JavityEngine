package org.javity.components;

import org.javity.engine.NativeComponent;

import com.artemis.Entity;

import galaxy.rapid.components.RectangleColliderComponent;
import galaxy.rapid.event.AddColliderComponent;
import galaxy.rapid.event.RemoveColliderComponent;

public class RectangleCollider extends NativeComponent {
	public float width = 100, height = 100;
	public float offsetX = 0, offsetY = 0;
	private float friction = 0.5f, density = 0.3f, restitution = 0.3f;
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
		rectangleColliderComponent.setDensity(density);
		rectangleColliderComponent.setFriction(friction);
		rectangleColliderComponent.setRestitution(restitution);
	}

	@Override
	public void start() {
	}
	
	@Override
	public void onEnabled() {
//		System.out.println("GO: " + getGameObject());
//		System.out.println("ENT: " + getGameObject().getEntity());
		Entity entity = getGameObject().getEntity();
		getRapidBus().post(new AddColliderComponent(rectangleColliderComponent, entity));
	}

	@Override
	public void onDisable() {
		getRapidBus().post(new RemoveColliderComponent(rectangleColliderComponent));
	}

	@Override
	public void remove() {
		// getRapidBus().post(new
		// RemoveRectangleColliderComponent(rectangleColliderComponent));
	}

}
