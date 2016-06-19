package org.javity.engine;

import java.util.List;

import com.badlogic.gdx.math.Vector2;

import galaxy.rapid.eventbus.RapidBus;

public interface Scene {

	public void initialize();
	public JGameObject instantiateGameObject(JGameObject gameObject, Vector2 position);
	public List<JGameObject> getGameObjects();
	public void destroyGameObject(JGameObject gameObject);
}
