package pl.radomiej.map.demo;

import java.util.ArrayList;
import java.util.List;

import org.javity.components.RectangleCollider;
import org.javity.components.Rigidbody;
import org.javity.components.SpriteRenderer;
import org.javity.engine.JGameObject;
import org.javity.engine.Scene;
import org.javity.engine.SceneBulider;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;

import pl.radomiej.map.MapCache;
import pl.radomiej.map.MapComponent;
import pl.radomiej.map.Marker;

public class SceneMapBulider implements SceneBulider {

	@Override
	public void buildScene(Scene scene) {
		
		long size = (long) Math.pow(2, 28);
		JGameObject map = scene.instantiateGameObject(new Vector2(100, 100));
		map.addComponent(new SpriteRenderer("badlogic.jpg"));
		map.addComponent(new MapComponent());
		map.addComponent(new MapCache());
		map.addComponent(new RectangleCollider(size, size));
		map.addComponent(new Rigidbody(true));
		map.getTransform().setScale(new Vector2(1, 1));	
		
		List<Vector2> path = new ArrayList<Vector2>();
		path.add(new Vector2(52, 17));
		path.add(new Vector2(52, 18));
		path.add(new Vector2(53, 18));
		path.add(new Vector2(53, 17));
		MapComponent mapComponent = map.getComponent(MapComponent.class);
		mapComponent.addPath("line-viaToll", path, Color.GREEN);
		mapComponent.addMarker("viaToll", new Marker( 52.5d, 17.5d, "badlogic.jpg", new Vector2(0.1f, 0.1f)));
	}

}
