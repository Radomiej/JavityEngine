package org.javity.engine;

import org.javity.engine.rapid.systems.Scene2dSystem;

import com.badlogic.gdx.scenes.scene2d.Stage;

public enum JGUI {
	INSTANCE;
	
	Scene2dSystem guiSystem;
	
	public boolean isStageHandleInput(){
		return guiSystem.isHandleInput();
	}

	public Stage getStage() {
		return guiSystem.getStage();
	}
}
