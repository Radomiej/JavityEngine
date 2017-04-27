package org.javity.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.javity.components.Transform;
import org.javity.engine.utilities.SceneSettings;

import com.artemis.Entity;
import com.badlogic.gdx.math.Vector2;

import galaxy.rapid.common.EntityEngine;
import galaxy.rapid.eventbus.RapidBus;

public class CustomScene implements InternalScene {

	private List<JGameObject> gameObjects = new ArrayList<JGameObject>();
	private HashMap<UUID, JGameObject> loadSceneObjects = new HashMap<UUID, JGameObject>();
	private RapidBus nativeRapidBus;
	private EntityEngine world;
	private boolean run;
	private transient List<JGameObject> objectToRemove = new ArrayList<JGameObject>();
	private transient List<JGameObject> objectToAdd = new ArrayList<JGameObject>();
	private SceneSettings settings = new SceneSettings();
	
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
		fullObject.setEnabled(false);
		fullObject.destroy(nativeRapidBus);
	}

	@Override
	public void proccessGameObjectAdd(JGameObject gameObject) {
		awakeGameObject(gameObject);
		gameObjects.add(gameObject);
		startGameObject(gameObject);
		enableGameObject(gameObject);
	}

	@Override
	public JGameObject instantiateGameObject(Vector2 position) {
		JGameObjectImpl newObject = new JGameObjectImpl();

		Transform transform = newObject.getComponent(Transform.class);
		transform.setPosition(position.cpy());

		/**
		 * If scene is running add to queue new game objects.
		 * Otherwise simple add to gameObjects list, then they will be auto prepare on initialize scene
		 * after run bulider method. 
		 */
		if (run) {
			objectToAdd.add(newObject);
		} else {
			gameObjects.add(newObject);
		}
		return newObject;
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
//				nativeRapidBus.register(nativeComponent);
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
			}
			for (Component component : gameObject.getAllComponents()) {
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
			}
			gameObject.start();
		}
	}
	
	@Override
	public void enableGameObject(JGameObject gameObject) {
		for (Component component : gameObject.getAllComponents()) {
			if (component.isEnabled()) {
				 component.onEnabled();
			}
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
	public void clearObjectsToDestroy() {
		objectToRemove.clear();
	}

	@Override
	public void clearObjectsToAdd() {
		objectToAdd.clear();		
	}

	@Override
	public List<JGameObject> findGameObjectsWithTag(String tag) {
		List<JGameObject> results = new ArrayList<JGameObject>();
		for (JGameObject gameObject : gameObjects) {
			if (gameObject.getTag() != null && gameObject.getTag().equalsIgnoreCase(tag))
				results.add(gameObject);
		}
		return results;
	}

	@Override
	public JGameObject findGameObjectWithTag(String tag) {
		for (JGameObject gameObject : gameObjects) {
			if (gameObject.getTag() != null && gameObject.getTag().equalsIgnoreCase(tag))
				return gameObject;
		}
		return null;
	}

	@Override
	public SceneSettings getSettings() {
		return settings;
	}

	public void dispose() {
		for(JGameObject go : gameObjects){
			destroyGameObject(go);
		}
		run = false;
	}

	@Override
	public boolean isRun() {
		return run;
	}

}
