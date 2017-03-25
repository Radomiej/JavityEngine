package org.javity.engine;

import com.badlogic.gdx.Gdx;

import galaxy.rapid.event.ChangeScreenEvent;

public class JSceneManager {
	public static Scene current;

	public static void loadScene(SceneBulider sceneBulider) {
		JTime.INSTANCE.clearTasks();
		CustomScene old = (CustomScene) current;
		old.dispose();
		
		InternalScene newScene = new CustomScene();
		current = newScene;
		sceneBulider.buildScene(newScene);
		JEngine.INSTANCE.rapidEventBus.post(new ChangeScreenEvent(new JavityScreen(newScene, old)));
	}
}
