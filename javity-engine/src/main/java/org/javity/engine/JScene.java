package org.javity.engine;

import java.util.List;

import org.javity.engine.utilities.SceneSettings;

import com.badlogic.gdx.math.Vector2;

import galaxy.rapid.eventbus.RapidBus;

public interface JScene {

	public JGameObject instantiateGameObject(Vector2 position);
	public List<JGameObject> getGameObjects();
	public List<JGameObject> findGameObjectsWithTag(String tag);
	public JGameObject findGameObjectWithTag(String tag);
	public void destroyGameObject(JGameObject gameObject);
	public SceneSettings getSettings();
}
