package org.javity.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.javity.components.Transform;
import org.javity.components.reflection.GameObjectProxator;
import org.javity.engine.serializer.JsonSceneSerializer;

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
	private transient List<JGameObject> objectToRemove = new ArrayList<JGameObject>();
	private transient List<JGameObject> objectToAdd = new ArrayList<JGameObject>();

	@Override
	public void initialize() {
		run = true;
	}

	@Override
	public List<JGameObject> getGameObjects() {
		return new ArrayList<JGameObject>(gameObjects);
	}

	@Override
	public void destroyGameObject(JGameObject gameObject) {
		objectToRemove.add(gameObject);
	}

	@Override
	public void proccessGameObjectDestroy(JGameObject gameObject) {
		gameObjects.remove(gameObject);
		JGameObjectImpl fullObject = (JGameObjectImpl) gameObject;
		gameObject.setEnabled(false);
		fullObject.destroy(nativeRapidBus);
	}

	@Override
	public void proccessGameObjectAdd(JGameObject gameObject) {
		awakeGameObject(gameObject);
		gameObjects.add(gameObject);
		startGameObject(gameObject);
	}

	@Override
	public JGameObjectImpl instantiateGameObject(JGameObject gameObject, Vector2 position) {
		JGameObjectImpl fullObject = (JGameObjectImpl) gameObject;
		Json json = JsonSceneSerializer.json;
		proxyGameObject(gameObject);
		String gameObjectJson = json.toJson(gameObject);
		unproxyGameObject(gameObject);

		JGameObjectImpl newObject = json.fromJson(JGameObjectImpl.class, gameObjectJson);
		unproxyGameObject(newObject);

		Transform transform = newObject.getComponent(Transform.class);
		newObject.setTransform(transform);
		if (fullObject.isPrefab())
			transform.setParent(null);
		transform.setPosition(position);
		
		// TODO przenieœc to do managera inicjializacji obiektów w nastepnej
		// frame
		if (run) {
			awakeGameObject(newObject);
		}
		
		gameObjects.add(newObject);

		if (run) {
			startGameObject(newObject);
		}
		return newObject;
	}

	@Override
	public JGameObject instantiateGameObject(Vector2 position) {
		JGameObjectImpl newObject = new JGameObjectImpl();

		Transform transform = newObject.getComponent(Transform.class);
		transform.setPosition(position.cpy());

		if (run) {
			objectToAdd.add(newObject);
		} else {
			gameObjects.add(newObject);
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
		for(com.artemis.Component component : artemisComponents){
			System.out.print(component.getClass().getSimpleName() + " ");
		}
		System.out.println();
		
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
			// System.out.println("Dodaje: " +
			// nativeComponent.getClass().getSimpleName());
			entity.edit().add(nativeComponent);
		}
		return entity;
	}

	@Override
	public void startGameObject(JGameObject gameObject) {
		if (!gameObject.isStarted()) {
			for (Component component : gameObject.getAllComponents()) {
				component.start();
				if (component.isEnabled())
					component.onEnabled();
			}
			gameObject.start();
		}
	}

	@Override
	public void setWorld(EntityEngine world) {
		this.world = world;
	}

	/**
	 * @return the objectToRemove
	 */
	@Override
	public List<JGameObject> getObjectToRemove() {
		return objectToRemove;
	}

	/**
	 * @return the objectToAdd
	 */
	@Override
	public List<JGameObject> getObjectToAdd() {
		return objectToAdd;
	}

	@Override
	public List<JGameObject> getGameObjectsByTag(String tag) {
		List<JGameObject> results = new ArrayList<JGameObject>();
		for(JGameObject gameObject : gameObjects){
			if(gameObject.getTag() != null && gameObject.getTag().equalsIgnoreCase(tag)) results.add(gameObject);
		}
		return results;
	}

	@Override
	public JGameObject getGameObjectByTag(String tag) {
		for(JGameObject gameObject : gameObjects){
			if(gameObject.getTag() != null && gameObject.getTag().equalsIgnoreCase(tag)) return gameObject;
		}
		return null;
	}

}
