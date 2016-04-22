package org.javity.engine;

import java.io.NotSerializableException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.javity.components.RectangleCollider;
import org.javity.components.Rigidbody;
import org.javity.components.SpriteRenderer;
import org.javity.components.Transform;
import org.javity.components.reflection.GameObjectsMonoReference;
import org.javity.engine.serializer.JsonSceneSerializer;
import org.javity.engine.serializer.KryoSceneSerializer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import com.badlogic.gdx.utils.ObjectMap.Keys;

import jdk.nashorn.internal.runtime.JSONListAdapter;

import com.badlogic.gdx.utils.OrderedMap;

public class SceneManager {
	public static Scene current;

	static Json json = JsonSceneSerializer.json;
	
	private static KryoSceneSerializer kryoSceneSerializer = new KryoSceneSerializer();
	
	public static String saveToJson(Scene scene) {
//		return kryoSceneSerializer.serialize(scene);
		return json.prettyPrint(scene);
	}

	public static Scene loadSceneFromJson(String jsonScene) {
//		System.out.println("load json: " + jsonScene);
		Scene scene = json.fromJson(CustomScene.class, jsonScene);
//		Scene scene = kryoSceneSerializer.deserialize(jsonScene);
		
		prepareReferenceToGameObject(scene);
		relinkComponentsToGameObject(scene);
		return scene;
	}

	private static void prepareReferenceToGameObject(Scene scene) {
		new GameObjectsMonoReference((CustomScene) scene).procces();
	}

	private static void relinkComponentsToGameObject(Scene scene) {
		for (GameObject gameObject : scene.getGameObjects()) {
			for (Component component : gameObject.getComponents()) {
				component.setGameObject(gameObject);
			}
		}
	}
}
