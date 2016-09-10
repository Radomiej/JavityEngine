package org.javity.engine.serializer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import org.javity.components.RectangleCollider;
import org.javity.components.Rigidbody;
import org.javity.components.SpriteRenderer;
import org.javity.components.Transform;
import org.javity.engine.CustomScene;
import org.javity.engine.JGameObjectImpl;

import com.artemis.annotations.UnstableApi;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.OrderedMap;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import com.badlogic.gdx.utils.ObjectMap.Keys;

@UnstableApi
//TODO link prefabs and reuse object
public class JsonSceneSerializer {
	public static Json json;
	static Json sceneReadJson;
	private static HashMap<UUID, JGameObjectImpl> loadSceneObjects = new HashMap<UUID, JGameObjectImpl>();
	static {
		json = new Json();
		sceneReadJson = new Json();
		prepareJsonParser(json);
		prepareJsonParser(sceneReadJson);
	}

	private static void prepareJsonParser(Json jsonToPrepare) {
		jsonToPrepare.setOutputType(OutputType.javascript);
		jsonToPrepare.setUsePrototypes(false);
		jsonToPrepare.addClassTag("Scene", CustomScene.class);
		jsonToPrepare.addClassTag("SpriteRenderer", SpriteRenderer.class);
		jsonToPrepare.addClassTag("Rigidbody", Rigidbody.class);
		jsonToPrepare.addClassTag("RectangleCollider", RectangleCollider.class);
		jsonToPrepare.addClassTag("Transform", Transform.class);
		jsonToPrepare.setSerializer(OrderedMap.class, new Json.Serializer<OrderedMap>() {
			@Override
			public void write(Json json, OrderedMap object, Class knownType) {
				ArrayList<Object> keysRaw = new ArrayList<Object>();
				ArrayList<Object> valuesRaw = new ArrayList<Object>();
				Keys keys = object.keys();
				for (Object key : keys.iterator()) {
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
				for (int x = 0; x < rawMap.keysRaw.size(); x++) {
					map.put(rawMap.keysRaw.get(x), rawMap.valuesRaw.get(x));
				}

				return map;
			}
		});
	}

}
