package org.javity.engine;

import org.javity.components.reflection.GameObjectsMonoReference;
import org.javity.engine.serializer.JsonSceneSerializer;
import org.javity.engine.serializer.KryoSceneSerializer;

import com.badlogic.gdx.utils.Json;

import galaxy.rapid.screen.ChangeScreenEvent;

public class JSceneManager {
	public static Scene current;

	static Json json = JsonSceneSerializer.json;
	
	public static String saveToJson(Scene scene) {
		prepareReferenceToGameObjectProxyInComponentsVariables(scene);
		String jsonScene = json.prettyPrint(scene);
		prepareReferenceToGameObject(scene);
		return jsonScene;
	}

	public static Scene loadSceneFromJson(String jsonScene) {
//		System.out.println("load json: " + jsonScene);
		Scene scene = json.fromJson(CustomScene.class, jsonScene);
		
		prepareReferenceToGameObject(scene);
		relinkComponentsToGameObject(scene);
		return scene;
	}

	private static void prepareReferenceToGameObject(Scene scene) {
		new GameObjectsMonoReference((CustomScene) scene).procces();
	}

	private static void relinkComponentsToGameObject(Scene scene) {
		for (JGameObjectImpl gameObject : scene.getGameObjects()) {
			for (Component component : gameObject.getAllComponents()) {
				component.setGameObject(gameObject);
			}
		}
	}

	private static void prepareReferenceToGameObjectProxyInComponentsVariables(Scene scene) {
		new GameObjectsMonoReference((CustomScene) scene).proxyProcces();		
	}

	
	public static void loadScene(SceneBulider sceneBulider) {
		InternalScene newScene = (InternalScene) loadSceneFromJson(sceneBulider.getSceneJson());
		JEngine.rapidEventBus.post(new ChangeScreenEvent(new JavityScreen(newScene, (InternalScene) current)));
	}
}
