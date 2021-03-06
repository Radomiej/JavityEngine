package org.javity.components;

import org.javity.engine.NativeComponent;

import com.artemis.Entity;

import galaxy.rapid.components.CircleColliderComponent;
import galaxy.rapid.event.AddColliderComponent;
import galaxy.rapid.event.RemoveColliderComponent;

public class CircleCollider extends NativeComponent {
	public float radius = 100;
	public float offsetX = 0, offsetY = 0;
	private float friction = 0.5f, density = 0.3f, restitution = 0.3f;
	private transient CircleColliderComponent circleColliderComponent;
	
	public CircleCollider() {
		this(50);
	}

	public CircleCollider(float radius) {
		this.radius = radius;
	}

	@Override
	public void awake() {
		circleColliderComponent = new CircleColliderComponent();
		circleColliderComponent.setRadius(radius);
		circleColliderComponent.setDensity(density);
		circleColliderComponent.setFriction(friction);
		circleColliderComponent.setRestitution(restitution);
	}

	@Override
	public void start() {
	}
	
	@Override
	public void onEnabled() {
//		System.out.println("GO: " + getGameObject());
//		System.out.println("ENT: " + getGameObject().getEntity());
		Entity entity = getGameObject().getEntity();
		getRapidBus().post(new AddColliderComponent(circleColliderComponent, entity));
	}

	@Override
	public void onDisable() {
		getRapidBus().post(new RemoveColliderComponent(circleColliderComponent));
	}

	@Override
	public void remove() {
		// getRapidBus().post(new
		// RemoveRectangleColliderComponent(rectangleColliderComponent));
	}

}
