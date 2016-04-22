package org.javity.engine;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

public class Prefabs {

	public static GameObject createGameObject() {
		return new GameObject();
	}

	public static void addComponent(GameObject logo, Component component) {
		logo.addComponent(component);
	}

	private List<GameObject> prefabs = new ArrayList<GameObject>();

	public void addPrefab(GameObject gameObject) {
		prefabs.add(gameObject);
	}

	public void removePrefab(GameObject gameObject) {
		prefabs.remove(gameObject);
	}

	public static GameObject createGameObjectFromResource(String resource) {
		
		FileHandle file = Gdx.files.local(resource);
		if(!file.exists()){
			file = Gdx.files.internal(resource);
		}
		
		String json = file.readString();
		GameObject jsonObject = SceneManager.json.fromJson(GameObject.class, json);
		return jsonObject;
	}
}
