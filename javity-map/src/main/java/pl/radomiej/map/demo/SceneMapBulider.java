package pl.radomiej.map.demo;

import org.javity.components.RectangleCollider;
import org.javity.components.Rigidbody;
import org.javity.components.SpriteRenderer;
import org.javity.engine.JGameObject;
import org.javity.engine.Scene;
import org.javity.engine.SceneBulider;

import com.badlogic.gdx.math.Vector2;

import pl.radomiej.map.MapCache;
import pl.radomiej.map.MapComponent;

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
	}

}