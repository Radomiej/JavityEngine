package org.javity.engine;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.javity.engine.physic.RaycastHit;
import org.javity.engine.rapid.events.PostGuiRenderEvent;
import org.javity.engine.rapid.events.PreGuiRenderEvent;
import org.javity.engine.rapid.systems.JavityPhysicSystem;
import org.javity.engine.rapid.systems.Scene2dSystem;

import com.artemis.WorldConfiguration;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.google.common.eventbus.Subscribe;

import galaxy.rapid.common.EntityEngine;
import galaxy.rapid.event.PostRenderEvent;
import galaxy.rapid.event.PreRenderEvent;
import galaxy.rapid.screen.RapidArtemisScreen;

/**
 * Screen is hook to GalaxyRapidEngine backend. And is over JScene and provide
 * mix between libgdx(galaxyRapidEngine with artemis-ecs) and JavityAPI
 * 
 * @author Radomiej
 *
 */
public class JavityScreen extends RapidArtemisScreen {

	/**
	 * This is JScene customize by external user.
	 */
	private final InternalScene scene;

	public JavityScreen(InternalScene newScene, InternalScene current) {
		this.scene = newScene;
		if (current != null)
			transformDontDestroyedObjects(current, newScene);
	}

	private void transformDontDestroyedObjects(JScene current, JScene newScene2) {
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
		rapidBus.register(this);
	}

	@Subscribe
	public void preRenderListener(PreRenderEvent event) {
		for (JGameObject gameObject : scene.getGameObjects()) {
			if (!gameObject.isEnabled())
				continue;

			Iterable<Component> components = gameObject.getAllComponents();
			for (Component component : components) {
				if (component.isEnabled())
					component.preRender();
			}
		}
	}

	@Subscribe
	public void postRenderListener(PostRenderEvent event) {
		for (JGameObject gameObject : scene.getGameObjects()) {
			if (!gameObject.isEnabled())
				continue;

			Iterable<Component> components = gameObject.getAllComponents();
			for (Component component : components) {
				if (component.isEnabled())
					component.postRender();
			}
		}
	}

	@Subscribe
	public void preGuiRenderListener(PreGuiRenderEvent event) {
		for (JGameObject gameObject : scene.getGameObjects()) {
			if (!gameObject.isEnabled())
				continue;

			Iterable<Component> components = gameObject.getAllComponents();
			for (Component component : components) {
				if (component.isEnabled())
					component.preGuiRender();
			}
		}
	}

	@Subscribe
	public void postGuiRenderListener(PostGuiRenderEvent event) {
		for (JGameObject gameObject : scene.getGameObjects()) {
			if (!gameObject.isEnabled())
				continue;

			Iterable<Component> components = gameObject.getAllComponents();
			for (Component component : components) {
				if (component.isEnabled())
					component.postGuiRender();
			}
		}
	}

	@Override
	protected void processBeforeRenderWorldConfiguration(WorldConfiguration worldConfiguration) {
		worldConfiguration.setSystem(new JavityPhysicSystem());
	}

	@Override
	protected void injectWorld(EntityEngine world) {
		scene.setNativeRapidBus(rapidBus);
		scene.setWorld(world);
		scene.initialize();
		JEngine.INSTANCE.rapidEventBus = masterEventBus;
		JCamera.setMain(camera);
		JPhysic.setPhysic(new JPhysic(physicWorld, rapidBus));

		// Awake all GameObjects
		for (JGameObject gameObject : scene.getGameObjects()) {
			scene.awakeGameObject(gameObject);
		}

		// Start all GameObjects
		List<JGameObject> gameObjects = scene.getGameObjects();
		for (int x = 0; x < gameObjects.size(); x++) {
			JGameObject gameObject = gameObjects.get(x);
			scene.startGameObject(gameObject);
			scene.enableGameObject(gameObject);
		}
	}

	@Override
	public void render(float delta) {
		if (!scene.isRun()) {
			processDeleteScreen();
			return;
		}
		
		// Update general variables
		JTime.INSTANCE.delta = delta;
		JTime.INSTANCE.tick();

		updateGUIStage();
		// Update Mouse Input
		updateMouseXXX();

		updateGameObjects();

		destroyObjectsToScene();
		addObjectsToScene();

		super.render(delta);
		JInput.saveOldStatus();
	}

	private void updateGameObjects() {
		// Pre update game objects
		for (JGameObject gameObject : scene.getGameObjects()) {
			if (!gameObject.isEnabled())
				continue;

			Iterable<Component> components = gameObject.getAllComponents();
			for (Component component : components) {
				if (component.isEnabled())
					component.preUpdate();
			}
		}
		// Update game objects
		for (JGameObject gameObject : scene.getGameObjects()) {
			if (!gameObject.isEnabled())
				continue;

			Iterable<Component> components = gameObject.getAllComponents();
			for (Component component : components) {
				if (component.isEnabled())
					component.update();
			}
		}
		for (JGameObject gameObject : scene.getGameObjects()) {
			if (!gameObject.isEnabled())
				continue;

			Iterable<Component> components = gameObject.getAllComponents();
			for (Component component : components) {
				if (component.isEnabled())
					component.lateUpdate();
			}
		}
	}

	private void processDeleteScreen() {
		destroyObjectsToScene();
	}

	private void destroyObjectsToScene() {
		// Destroy Objects to remove
		List<JGameObject> gameObjects = scene.getObjectToRemove();
		for (int x = 0; x < gameObjects.size(); x++) {
			JGameObject gameObject = gameObjects.get(x);
			scene.proccessGameObjectDestroy(gameObject);
		}
		scene.clearObjectsToDestroy();
	}

	private void addObjectsToScene() {
		// Add Objects to add
		List<JGameObject> gameObjects = scene.getObjectToAdd();
		for (int x = 0; x < gameObjects.size(); x++) {
			JGameObject gameObject = gameObjects.get(x);
			scene.proccessGameObjectAdd(gameObject);
		}
		scene.clearObjectsToAdd();
	}

	private void updateGUIStage() {

		JGUI.INSTANCE.guiSystem.getStage().act(JTime.INSTANCE.delta);

		Vector2 stagePosition = JGUI.INSTANCE.guiSystem.getStage().screenToStageCoordinates(JInput.getMousePosition());
		Actor hitActor = JGUI.INSTANCE.guiSystem.getStage().hit(stagePosition.x, stagePosition.y, true);
		if (hitActor != null) {
			// System.out.println("handle input true");
			JGUI.INSTANCE.guiSystem.setHandleInput(true);
		}
	}

	private Set<JGameObject> pressedObjects = new HashSet<JGameObject>();
	private Vector2 deltaMouse = new Vector2();

	private void updateMouseXXX() {
		if (JInput.isTouch())
			addDeltaMouse();

		Vector2 worldPosition = JCamera.getMain().screenToWorldPoint(JInput.getMousePosition());
		List<RaycastHit> hits = JPhysic.raycastPoint(worldPosition);
		for (RaycastHit hit : hits) {

			JGameObject hitGameObject = hit.collider.getGameObject();
			for (Component component : hitGameObject.getAllComponents()) {
				if (JInput.isJustPressed()) {
					deltaMouse.setZero();
					if (component.isEnabled())
						component.onMousePressed();
					pressedObjects.add(hitGameObject);
				} else if (JInput.isJustRelased()) {
					if (component.isEnabled())
						component.onMouseRelased();
					if (pressedObjects.contains(hitGameObject) && !isDragMouseGest(deltaMouse)) {
						if (component.isEnabled())
							component.onMouseClicked();
					}
				} else if (JInput.isTouch()) {
					if (component.isEnabled())
						component.onMouseDragged(JInput.getMouseDragged());
				} else if (!JInput.isTouch()) {
					if (component.isEnabled())
						component.onMouseOver();
				}
			}
		}

		if (JInput.isJustRelased()) {
			pressedObjects.clear();
		}
	}

	private void addDeltaMouse() {
		deltaMouse.add(JInput.getMouseDragged());
	}

	private boolean isDragMouseGest(Vector2 deltaMouse2) {
		return deltaMouse2.dst(0, 0) > 5;
	}

	@Override
	public void pause() {
		for (JGameObject gameObject : scene.getGameObjects()) {
			Iterable<Component> components = gameObject.getAllComponents();
			for (Component component : components) {
				if (component.isEnabled())
					component.onPause();
			}
		}
	}

	@Override
	public void resume() {
		for (JGameObject gameObject : scene.getGameObjects()) {
			Iterable<Component> components = gameObject.getAllComponents();
			for (Component component : components) {
				if (component.isEnabled())
					component.onResume();
			}
		}
	}
}
