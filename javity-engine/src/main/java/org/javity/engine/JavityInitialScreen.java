package org.javity.engine;

import galaxy.rapid.event.ChangeScreenEvent;
import galaxy.rapid.screen.RapidScreen;

public class JavityInitialScreen extends RapidScreen{

	private SceneBuilder sceneBulider;
	
	public JavityInitialScreen(SceneBuilder sceneBulider) {
		this.sceneBulider = sceneBulider;
	}
	
	@Override
	public void render(float delta) {
		
	}

	@Override
	public void dispose() {
		
	}

	@Override
	protected void create() {
		InternalScene newScene = new CustomScene();
		JSceneManager.current = newScene;
		sceneBulider.buildScene(newScene);
		masterEventBus.post(new ChangeScreenEvent(new JavityScreen(newScene, null)));
	}
}
