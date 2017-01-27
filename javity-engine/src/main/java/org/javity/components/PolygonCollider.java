package org.javity.components;

import java.util.ArrayList;
import java.util.List;

import org.javity.engine.NativeComponent;

import com.artemis.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

import galaxy.rapid.components.PolygonColliderComponent;
import galaxy.rapid.components.RectangleColliderComponent;
import galaxy.rapid.event.AddColliderComponent;
import galaxy.rapid.event.RemoveColliderComponent;

public class PolygonCollider extends NativeComponent {
	public float offsetX = 0, offsetY = 0;
	private transient PolygonColliderComponent polygonColliderComponent;
	private Array<Vector2> vertex;

	public PolygonCollider() {
		this(new Vector2(-50, -50), new Vector2(0, 50), new Vector2(50, -50), new Vector2(-50, -50));
	}

	public PolygonCollider(Vector2... edges) {
		if (edges.length <= 3) {
			Gdx.app.error(PolygonCollider.class.getSimpleName(),
					"Edges for create PolygonCollider is so low. Current: " + edges.length + " but min need 4");
			return;
		}
		vertex = new Array<Vector2>(edges);
	}

	@Override
	public void awake() {
		polygonColliderComponent = new PolygonColliderComponent();
		polygonColliderComponent.setVertex(vertex);
	}

	@Override
	public void start() {
	}

	@Override
	public void onEnabled() {
		// System.out.println("GO: " + getGameObject());
		// System.out.println("ENT: " + getGameObject().getEntity());
		Entity entity = getGameObject().getEntity();
		getRapidBus().post(new AddColliderComponent(polygonColliderComponent, entity));
	}

	@Override
	public void onDisable() {
		getRapidBus().post(new RemoveColliderComponent(polygonColliderComponent));
	}

	@Override
	public void remove() {
		// getRapidBus().post(new
		// RemoveRectangleColliderComponent(rectangleColliderComponent));
	}

}
