package org.javity.engine;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

public class Prefabs {

	public static JGameObject createGameObject() {
		return new JGameObjectImpl(true);
	}

	public static void addComponent(JGameObject logo, Component component) {
		logo.addComponent(component);
	}

	private List<JGameObjectImpl> prefabs = new ArrayList<JGameObjectImpl>();

	public void addPrefab(JGameObjectImpl gameObject) {
		prefabs.add(gameObject);
	}

	public void removePrefab(JGameObjectImpl gameObject) {
		prefabs.remove(gameObject);
	}

	public static JGameObjectImpl createGameObjectFromResource(String resource) {
		
		FileHandle file = Gdx.files.local(resource);
		if(!file.exists()){
			file = Gdx.files.internal(resource);
		}
		
		String json = file.readString();
		JGameObjectImpl jsonObject = JSceneManager.json.fromJson(JGameObjectImpl.class, json);
		return jsonObject;
	}
}
