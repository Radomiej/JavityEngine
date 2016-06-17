package org.javity.engine;

import galaxy.rapid.screen.ChangeScreenEvent;
import galaxy.rapid.screen.RapidScreen;

public class JavityInitialScreen extends RapidScreen{

	private SceneBulider sceneBulider;
	
	public JavityInitialScreen(SceneBulider sceneBulider) {
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
		InternalScene scene = (InternalScene) JSceneManager.loadSceneFromJson(sceneBulider.getSceneJson());
		super.masterEventBus.post(new ChangeScreenEvent(new JavityScreen(scene, null)));
	}
}
