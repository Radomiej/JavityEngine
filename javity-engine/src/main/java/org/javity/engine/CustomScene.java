package org.javity.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.javity.components.Transform;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;

public class CustomScene implements Scene {

	private List<GameObject> gameObjects = new ArrayList<GameObject>();
	private HashMap<UUID, GameObject> loadSceneObjects = new HashMap<UUID, GameObject>();
	
	@Override
	public void initialize() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public List<GameObject> getGameObjects() {
		return gameObjects;
	}

	@Override
	public GameObject instantiateGameObject(GameObject gameObject, Vector2 position) {
		Json json = JSceneManager.json;
		String gameObjectJson = json.toJson(gameObject);
		System.out.println("parse objet: " + gameObjectJson);
		GameObject newObject = json.fromJson(GameObject.class, gameObjectJson);
		Transform transform = newObject.getComponent(Transform.class);
		transform.setPosition(position);
		
		gameObjects.add(newObject);
		return newObject;
	}

	public HashMap<UUID, GameObject> getLoadSceneObjects() {
		return loadSceneObjects;
	}

}
