package org.javity.engine;

import galaxy.rapid.screen.ChangeScreenEvent;

public class JSceneManager {
	public static Scene current;

	public static void loadScene(SceneBulider sceneBulider) {
		CustomScene old = (CustomScene) current;
		InternalScene newScene = new CustomScene();
		current = newScene;
		sceneBulider.buildScene(newScene);
		JEngine.rapidEventBus.post(new ChangeScreenEvent(new JavityScreen(newScene, old)));
	}
}
