package org.javity.engine.ext;

import org.javity.engine.JavityInitialScreen;
import org.javity.engine.SceneBuilder;

import galaxy.rapid.run.RapidApp;

public class JavityApplication extends RapidApp{

	public JavityApplication(SceneBuilder firstSceneBuilder) {
		super(new JavityInitialScreen(firstSceneBuilder));
	}

}
