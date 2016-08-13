package org.javity.engine;

import galaxy.rapid.screen.ChangeScreenEvent;

public class JSceneManager {
	public static Scene current;

	public static void loadScene(SceneBulider sceneBulider) {
		InternalScene newScene = (InternalScene) sceneBulider.getScene();
		JEngine.rapidEventBus.post(new ChangeScreenEvent(new JavityScreen(newScene, (InternalScene) current)));
	}
}
