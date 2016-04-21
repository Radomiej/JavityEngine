package org.javity.engine;

import java.util.List;

public interface Scene {

	public void initialize();
	public GameObject instantiateGameObject(GameObject logo2);
	public List<GameObject> getGameObjects();

}
