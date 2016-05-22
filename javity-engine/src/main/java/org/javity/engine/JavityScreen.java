package org.javity.engine;

import galaxy.rapid.common.EntityEngine;
import galaxy.rapid.screen.RapidArtemisScreen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.javity.engine.physic.RaycastHit;
import org.javity.engine.rapid.systems.Scene2dSystem;

import com.artemis.Entity;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.math.Vector2;

public class JavityScreen extends RapidArtemisScreen {

	private final Scene scene;

	public JavityScreen(Scene newScene, Scene current) {
		this.scene = newScene;
		if (current != null)
			transformDontDestroyedObjects(current, newScene);
	}

	private void transformDontDestroyedObjects(Scene current, Scene newScene2) {
		for (GameObject gameObject : current.getGameObjects()) {
			if (gameObject.isDontDestroy()) {
				gameObject.getTransform().setParent(null);
				scene.getGameObjects().add(gameObject);
			}
		}
	}

	@Override
	protected void processWorldConfiguration(WorldConfiguration worldConfiguration) {
		JGUI.guiSystem = new Scene2dSystem();
		worldConfiguration.setSystem(JGUI.guiSystem);
	}

	@Override
	protected void processBeforeRenderWorldConfiguration(WorldConfiguration worldConfiguration) {
		worldConfiguration.setSystem(new JavitySystem());
	}

	@Override
	protected void injectWorld(EntityEngine world) {
		System.out.println("injectWorld");

		JSceneManager.current = scene;
		JEngine.rapidEventBus = masterEventBus;
		JCamera.setMain(camera);
		JPhysic.setPhysic(new JPhysic(physicWorld, rapidBus));

		// Awake all GameObjects
		for (GameObject gameObject : scene.getGameObjects()) {
			if (!gameObject.isStarted()) {
				gameObject.awake();
				awakesComponents(gameObject);
			}
			Collection<com.artemis.Component> artemisComponents = getArtemisComponents(gameObject);
			registerInRapidBusAllNativeComponents(gameObject);
			Entity entity = createEntity(artemisComponents, world);
			gameObject.setEntity(entity);
		}

		// Start all GameObjects
		for (GameObject gameObject : scene.getGameObjects()) {
			if (!gameObject.isStarted()) {
				startsComponents(gameObject);
				gameObject.start();
			}
		}

	}

	private void registerInRapidBusAllNativeComponents(GameObject gameObject) {
		for (Component component : gameObject.getComponents()) {
			if (component instanceof NativeComponent) {
				NativeComponent nativeComponent = (NativeComponent) component;
				nativeComponent.setRapidBus(rapidBus);
				rapidBus.register(nativeComponent);
			}
		}
	}

	private void awakesComponents(GameObject gameObject) {
		for (Component component : gameObject.getComponents()) {
			component.awake();
		}
	}

	private void startsComponents(GameObject gameObject) {
		for (Component component : gameObject.getComponents()) {
			component.start();
			component.onEnabled();
		}
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

	private Collection<com.artemis.Component> getArtemisComponents(GameObject gameObject) {
		List<com.artemis.Component> artemisComponents = new ArrayList<com.artemis.Component>();
		for (Component javityComponent : gameObject.getComponents()) {
			// System.out.println("check component: " +
			// javityComponent.getClass().getSimpleName());
			if (javityComponent instanceof NativeComponent) {
				NativeComponent nativeComponent = (NativeComponent) javityComponent;
				artemisComponents.addAll(nativeComponent.getNativeComponents());
			}
		}
		return artemisComponents;
	}

	@Override
	public void render(float delta) {
		// Update general variables
		JTime.delta = delta;

		// Update Mouse Input
		updateMouseXXX();

		// Update game objects
		for (GameObject gameObject : scene.getGameObjects()) {
			Iterable<Component> components = gameObject.getComponents();
			for (Component component : components) {
				component.update();
			}
		}
		for (GameObject gameObject : scene.getGameObjects()) {
			Iterable<Component> components = gameObject.getComponents();
			for (Component component : components) {
				component.lateUpdate();
			}
		}

		super.render(delta);
		JInput.saveOldStatus();
	}

	private Set<GameObject> pressedObjects = new HashSet<GameObject>();

	private void updateMouseXXX() {
		Vector2 worldPosition = JCamera.getMain().screenToWorldPoint(JInput.getMousePosition());
		List<RaycastHit> hits = JPhysic.raycastPoint(worldPosition);
		for (RaycastHit hit : hits) {

			GameObject hitGameObject = hit.collider.getGameObject();
			for (Component component : hitGameObject.componentsMap.values()) {
				if (JInput.isJustPressed()) {
					component.onMousePressed();
					pressedObjects.add(hitGameObject);
				} else if (JInput.isJustRelased()) {
					component.onMouseRelased();
					if (pressedObjects.contains(hitGameObject)) {
						component.onMouseClicked();
					}
				} else if (JInput.isClicked()) {
					component.onMouseDragged(JInput.getMouseDragged());
				}
			}
		}

		if (JInput.isJustRelased()) {
			pressedObjects.clear();
		}
	}

	@Override
	public void pause() {
		for (GameObject gameObject : scene.getGameObjects()) {
			Iterable<Component> components = gameObject.getComponents();
			for (Component component : components) {
				component.onPause();
			}
		}
	}

	@Override
	public void resume() {
		for (GameObject gameObject : scene.getGameObjects()) {
			Iterable<Component> components = gameObject.getComponents();
			for (Component component : components) {
				component.onResume();
			}
		}
	}
}
