package org.javity.engine;

import galaxy.rapid.screen.ChangeScreenEvent;
import galaxy.rapid.screen.RapidScreen;

public class JavityInitialScreen extends RapidScreen{

	private String jsonScene;
	public JavityInitialScreen() {
		
	}
	
	@Override
	public void render(float delta) {
		
	}

	@Override
	public void dispose() {
		
	}

	@Override
	protected void create() {
		Scene scene = SceneManager.loadSceneFromJson(jsonScene);
		super.eventBus.post(new ChangeScreenEvent(new JavityScreen(scene)));
	}
}
