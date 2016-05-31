package org.javity.engine.gui;

import org.javity.engine.GUIComponent;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

public class JCanvas extends GUIComponent {
	public transient Skin skin;
	public String skinPath;
	
	@Override
	public void awake() {
		skin = new Skin(Gdx.files.internal("internal/ui/uiskin.json"));
	}
}
