package org.javity.engine;

import java.util.List;

import com.badlogic.gdx.math.Vector2;

import galaxy.rapid.eventbus.RapidBus;

public interface Scene {

	public void initialize();
	public GameObject instantiateGameObject(GameObject gameObject, Vector2 position);
	public List<GameObject> getGameObjects();
}
