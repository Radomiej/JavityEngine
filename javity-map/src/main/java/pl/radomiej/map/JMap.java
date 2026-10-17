package pl.radomiej.map;

import org.javity.components.RectangleCollider;
import org.javity.components.Rigidbody;
import org.javity.components.SpriteRenderer;
import org.javity.engine.JGameObject;
import org.javity.engine.JSceneManager;

import com.badlogic.gdx.math.Vector2;

public class JMap {
	public static JGameObject createMapObject(){
		long size = (long) Math.pow(2, 28);
		JGameObject map = JSceneManager.current.instantiateGameObject(new Vector2(0, 0));
		map.addComponent(new MapComponent());
		map.addComponent(new MapCache());
		map.addComponent(new RectangleCollider(size, size));
		map.addComponent(new Rigidbody(true));
		map.getTransform().setScale(new Vector2(1, 1));		
		
		return map;
	}
}
