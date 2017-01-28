package org.javity.engine.utilities;

import com.badlogic.gdx.graphics.Color;

import galaxy.rapid.screen.RapidScreenSettings;

public class SceneSettings {

	public Color getClearColor() {
		return RapidScreenSettings.INSTANCE.getClearColor().cpy();
	}

	public void setClearColor(Color clearColor) {
		 RapidScreenSettings.INSTANCE.setClearColor(clearColor.cpy());
	}
}
