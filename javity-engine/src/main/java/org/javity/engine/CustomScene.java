package org.javity.engine;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;

public class CustomScene implements Scene {

	public List<GameObject> gameObjects = new ArrayList<GameObject>();
	
	@Override
	public void initialize() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public GameObject instantiateGameObject(GameObject prefab) {
		GameObject newObject = prefab;
		gameObjects.add(newObject);
		return newObject;
	}

	@Override
	public List<GameObject> getGameObjects() {
		return gameObjects;
	}

}
