package org.javity.engine;

import galaxy.rapid.event.ChangeScreenEvent;

public class JSceneManager {
	public static JScene current;

	public static void loadScene(SceneBuilder sceneBuilder) {
		JTime.INSTANCE.clearTasks();
		CustomScene old = (CustomScene) current;
		old.dispose();
		
		InternalScene newScene = new CustomScene();
		current = newScene;
		sceneBuilder.buildScene(newScene);
		JEngine.INSTANCE.rapidEventBus.post(new ChangeScreenEvent(new JavityScreen(newScene, old)));
	}
}
