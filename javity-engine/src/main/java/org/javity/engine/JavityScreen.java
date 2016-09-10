package org.javity.engine;

import galaxy.rapid.common.EntityEngine;
import galaxy.rapid.screen.RapidArtemisScreen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.javity.engine.physic.RaycastHit;
import org.javity.engine.rapid.systems.JavityPhysicSystem;
import org.javity.engine.rapid.systems.Scene2dSystem;

import com.artemis.Entity;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;

public class JavityScreen extends RapidArtemisScreen {

	private final InternalScene scene;

	public JavityScreen(InternalScene newScene, InternalScene current) {
		this.scene = newScene;
		if (current != null)
			transformDontDestroyedObjects(current, newScene);
	}

	private void transformDontDestroyedObjects(Scene current, Scene newScene2) {
		for (JGameObject gameObject : current.getGameObjects()) {
			if (gameObject.isDontDestroy()) {
				gameObject.getTransform().setParent(null);
				scene.getGameObjects().add(gameObject);
			}
		}
	}

	@Override
	protected void processWorldConfiguration(WorldConfiguration worldConfiguration) {
		JGUI.INSTANCE.guiSystem = new Scene2dSystem();
		worldConfiguration.setSystem(JGUI.INSTANCE.guiSystem);
	}

	@Override
	protected void processBeforeRenderWorldConfiguration(WorldConfiguration worldConfiguration) {
		worldConfiguration.setSystem(new JavityPhysicSystem());
	}

	@Override
	protected void injectWorld(EntityEngine world) {
		System.out.println("injectWorld");

		scene.setNativeRapidBus(rapidBus);
		scene.setWorld(world);
		scene.initialize();
		JEngine.rapidEventBus = masterEventBus;
		JCamera.setMain(camera);
		JPhysic.setPhysic(new JPhysic(physicWorld, rapidBus));

		System.out.println("awakes");
		// Awake all GameObjects
		for (JGameObject gameObject : scene.getGameObjects()) {
			scene.awakeGameObject(gameObject);
		}

		System.out.println("startes");
		// Start all GameObjects
		List<JGameObject> gameObjects = scene.getGameObjects();
		for (int x = 0; x < gameObjects.size(); x++) {
			JGameObject gameObject = gameObjects.get(x);
			scene.startGameObject(gameObject);
		}
	}

	@Override
	public void render(float delta) {
		// Update general variables
		JTime.delta = delta;

		// Add Objects to add
		for (JGameObject gameObject : scene.getObjectToAdd()) {
			scene.proccessGameObjectAdd(gameObject);
		}
		scene.getObjectToAdd().clear();

		updateGUIStage();

		// Update Mouse Input
		updateMouseXXX();

		// Update game objects
		for (JGameObject gameObject : scene.getGameObjects()) {
			Iterable<Component> components = gameObject.getAllComponents();
			for (Component component : components) {
				component.update();
			}
		}
		for (JGameObject gameObject : scene.getGameObjects()) {
			Iterable<Component> components = gameObject.getAllComponents();
			for (Component component : components) {
				component.lateUpdate();
			}
		}

		// Destroy Objects to remove
		for (JGameObject gameObject : scene.getObjectToRemove()) {
			scene.proccessGameObjectDestroy(gameObject);
		}
		scene.getObjectToRemove().clear();

		super.render(delta);
		JInput.saveOldStatus();
	}

	private void updateGUIStage() {
		JGUI.INSTANCE.guiSystem.getStage().act(JTime.delta);

		Vector2 stagePosition = JGUI.INSTANCE.guiSystem.getStage().screenToStageCoordinates(JInput.getMousePosition());
		Actor hitActor = JGUI.INSTANCE.guiSystem.getStage().hit(stagePosition.x, stagePosition.y, true);
		if (hitActor != null) {
			System.out.println("Set handled true");
			JGUI.INSTANCE.guiSystem.setHandleInput(true);
		}
	}

	private Set<JGameObject> pressedObjects = new HashSet<JGameObject>();

	private void updateMouseXXX() {
		Vector2 worldPosition = JCamera.getMain().screenToWorldPoint(JInput.getMousePosition());
		List<RaycastHit> hits = JPhysic.raycastPoint(worldPosition);
		for (RaycastHit hit : hits) {

			JGameObject hitGameObject = hit.collider.getGameObject();
			for (Component component : hitGameObject.getAllComponents()) {
				if (JInput.isJustPressed()) {
					component.onMousePressed();
					pressedObjects.add(hitGameObject);
				} else if (JInput.isJustRelased()) {
					component.onMouseRelased();
					if (pressedObjects.contains(hitGameObject)) {
						component.onMouseClicked();
					}
				} else if (JInput.isTouch()) {
					component.onMouseDragged(JInput.getMouseDragged());
				} else if (!JInput.isTouch()) {
					component.onMouseOver();
				}
			}
		}

		if (JInput.isJustRelased()) {
			pressedObjects.clear();
		}
	}

	@Override
	public void pause() {
		for (JGameObject gameObject : scene.getGameObjects()) {
			Iterable<Component> components = gameObject.getAllComponents();
			for (Component component : components) {
				component.onPause();
			}
		}
	}

	@Override
	public void resume() {
		for (JGameObject gameObject : scene.getGameObjects()) {
			Iterable<Component> components = gameObject.getAllComponents();
			for (Component component : components) {
				component.onResume();
			}
		}
	}
}
