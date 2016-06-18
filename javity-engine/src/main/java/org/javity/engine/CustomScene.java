package org.javity.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.javity.components.Transform;
import org.javity.components.reflection.GameObjectProxator;

import com.artemis.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;

import galaxy.rapid.common.EntityEngine;
import galaxy.rapid.eventbus.RapidBus;

public class CustomScene implements InternalScene {

	private List<JGameObject> gameObjects = new ArrayList<JGameObject>();
	private HashMap<UUID, JGameObject> loadSceneObjects = new HashMap<UUID, JGameObject>();
	private RapidBus nativeRapidBus;
	private EntityEngine world;
	private boolean run;
	private transient GameObjectProxator proxator = new GameObjectProxator();
	
	@Override
	public void initialize() {
		run = true;
	}

	@Override
	public List<JGameObject> getGameObjects() {
		return gameObjects;
	}

	@Override
	public JGameObjectImpl instantiateGameObject(JGameObject gameObject, Vector2 position) {
		Json json = JSceneManager.json;
		proxyGameObject(gameObject);
		String gameObjectJson = json.toJson(gameObject);
		unproxyGameObject(gameObject);
		
		JGameObjectImpl newObject = json.fromJson(JGameObjectImpl.class, gameObjectJson);
		unproxyGameObject(newObject);
		Transform transform = newObject.getComponent(Transform.class);
		transform.setPosition(position);
		registerInRapidBusAllNativeComponents(gameObject);

		if (run) {
			awakeGameObject(newObject);
		}

		gameObjects.add(newObject);

		if (run) {
			startGameObject(newObject);
		}
		return newObject;
	}

	private void unproxyGameObject(JGameObject gameObject) {
		proxator.unproxy(gameObject, this);
	}

	private void proxyGameObject(JGameObject gameObject) {
		proxator.proxy(gameObject, this);
	}

	public HashMap<UUID, JGameObject> getLoadSceneObjects() {
		return loadSceneObjects;
	}

	public void registerInRapidBusAllNativeComponents(JGameObject gameObject) {
		if (nativeRapidBus == null)
			return;

		for (Component component : gameObject.getAllComponents()) {
			if (component instanceof NativeComponent) {
				NativeComponent nativeComponent = (NativeComponent) component;
				nativeComponent.setRapidBus(nativeRapidBus);
				nativeRapidBus.register(nativeComponent);
			}
		}
	}

	public RapidBus getNativeRapidBus() {
		return nativeRapidBus;
	}

	@Override
	public void setNativeRapidBus(RapidBus nativeRapidBus) {
		this.nativeRapidBus = nativeRapidBus;
	}

	@Override
	public void awakeGameObject(JGameObject gameObject) {
		if (!gameObject.isStarted()) {
			gameObject.awake();
			for (Component component : gameObject.getAllComponents()) {
				component.setGameObject(gameObject);
				component.awake();
			}
		}
		Collection<com.artemis.Component> artemisComponents = getArtemisComponents(gameObject);
		registerInRapidBusAllNativeComponents(gameObject);
		Entity entity = createEntity(artemisComponents, world);
		gameObject.setEntity(entity);

	}

	private Collection<com.artemis.Component> getArtemisComponents(JGameObject gameObject) {
		List<com.artemis.Component> artemisComponents = new ArrayList<com.artemis.Component>();
		for (Component javityComponent : gameObject.getAllComponents()) {
			// System.out.println("check component: " +
			// javityComponent.getClass().getSimpleName());
			if (javityComponent instanceof NativeComponent) {
				NativeComponent nativeComponent = (NativeComponent) javityComponent;
				artemisComponents.addAll(nativeComponent.getNativeComponents());
			}
		}
		return artemisComponents;
	}

	private Entity createEntity(Collection<com.artemis.Component> artemisComponents, EntityEngine world) {
		Entity entity = world.createEntity();
		for (com.artemis.Component nativeComponent : artemisComponents) {
//			 System.out.println("Dodaje: " +
//			 nativeComponent.getClass().getSimpleName());
			entity.edit().add(nativeComponent);
		}
		return entity;
	}

	@Override
	public void startGameObject(JGameObject gameObject) {
		if (!gameObject.isStarted()) {
			for (Component component : gameObject.getAllComponents()) {
				component.start();
				if(component.isEnabled()) component.onEnabled();
			}
			gameObject.start();
		}
	}

	@Override
	public void setWorld(EntityEngine world) {
		this.world = world;
	}

}
