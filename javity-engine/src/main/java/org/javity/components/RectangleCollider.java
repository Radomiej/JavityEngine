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
	private List<String> logs = new ArrayList<String>();
	
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
		logs.add("AWAKE go: " + getGameObject());
		// addNativeComponent(rectangleColliderComponent);
	}

	@Override
	public void start() {
		logs.add("START go: " + getGameObject());
	}
	
	@Override
	public void onEnabled() {
		System.out.println("GO: " + getGameObject());
		System.out.println("ENT: " + getGameObject().getEntity());
		Entity entity = getGameObject().getEntity();
		getRapidBus().post(new AddRectangleColliderComponent(rectangleColliderComponent, entity));
		logs.add("onEnabled go: " + getGameObject());
	}

	@Override
	public void onDisable() {
		getRapidBus().post(new RemoveRectangleColliderComponent(rectangleColliderComponent));
		logs.add("onDisable go: " + getGameObject());
	}

	@Override
	public void remove() {
		// getRapidBus().post(new
		// RemoveRectangleColliderComponent(rectangleColliderComponent));
	}

}
