package org.javity.engine;

import java.util.ArrayList;
import java.util.List;

import org.javity.components.RectangleCollider;
import org.javity.components.Rigidbody;
import org.javity.components.SpriteRenderer;
import org.javity.components.Transform;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import com.badlogic.gdx.utils.ObjectMap.Keys;

import jdk.nashorn.internal.runtime.JSONListAdapter;

import com.badlogic.gdx.utils.OrderedMap;

public class SceneManager {
	public static Scene current;

	private static Json json;

	static {
		json = new Json();
		json.setOutputType(OutputType.javascript);
		json.setUsePrototypes(false);
		json.addClassTag("Scene", CustomScene.class);
		json.addClassTag("SpriteRenderer", SpriteRenderer.class);
		json.addClassTag("Rigidbody", Rigidbody.class);
		json.addClassTag("RectangleCollider", RectangleCollider.class);
		json.addClassTag("Transform", Transform.class);

		json.setSerializer(OrderedMap.class, new Json.Serializer<OrderedMap>() {
			@Override
			public void write(Json json, OrderedMap object, Class knownType) {
				ArrayList<Object> keysRaw = new ArrayList<Object>();
				ArrayList<Object> valuesRaw = new ArrayList<Object>();
				Keys keys = object.keys();
				for(Object key : keys.iterator()){
					Object value = object.get(key);
					keysRaw.add(key);
					valuesRaw.add(value);
				}
				
				RawMap rawMap = new RawMap();
				rawMap.keysRaw = keysRaw;
				rawMap.valuesRaw = valuesRaw;
				
				
				json.writeObjectStart();
				json.writeValue("raw", rawMap);
				json.writeObjectEnd();
			}

			@Override
			public OrderedMap read(Json json, JsonValue jsonData, Class type) {
				RawMap rawMap = json.readValue(RawMap.class, jsonData.child());
				
				OrderedMap map = new OrderedMap();
				try {
					map = (OrderedMap) type.newInstance();
				} catch (InstantiationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				for(int x = 0; x < rawMap.keysRaw.size(); x++){
					map.put(rawMap.keysRaw.get(x), rawMap.valuesRaw.get(x));
				}
				
				return map;
			}
		});
	}

	public static String saveToJson(Scene scene) {
		return json.prettyPrint(scene);
	}

	public static Scene loadSceneFromJson(String jsonScene) {
		Scene scene = json.fromJson(CustomScene.class, jsonScene);
		relinkComponentsToGameObject(scene);
		return scene;
	}

	private static void relinkComponentsToGameObject(Scene scene) {
		for (GameObject gameObject : scene.getGameObjects()) {
			for (Component component : gameObject.getComponents()) {
				component.setGameObject(gameObject);
			}
		}
	}
}
