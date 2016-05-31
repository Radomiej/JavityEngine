package org.javity.engine;

import org.javity.components.reflection.GameObjectsMonoReference;
import org.javity.engine.serializer.JsonSceneSerializer;
import org.javity.engine.serializer.KryoSceneSerializer;

import com.badlogic.gdx.utils.Json;

import galaxy.rapid.screen.ChangeScreenEvent;

public class JSceneManager {
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
			for (Component component : gameObject.getAllComponents()) {
				component.setGameObject(gameObject);
			}
		}
	}

	public static void loadScene(SceneBulider sceneBulider) {
		Scene newScene = loadSceneFromJson(sceneBulider.getSceneJson());
		JEngine.rapidEventBus.post(new ChangeScreenEvent(new JavityScreen(newScene, current)));
	}
}
