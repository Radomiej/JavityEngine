package org.javity.engine.ext;

import org.javity.engine.JavityInitialScreen;
import org.javity.engine.SceneBulider;

import com.badlogic.gdx.Screen;

import galaxy.rapid.run.RapidApp;

public class JavityApplication extends RapidApp{

	public JavityApplication(SceneBulider firstSceneBuilder) {
		super(new JavityInitialScreen(firstSceneBuilder));
	}

}
