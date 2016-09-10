package org.javity.engine;

import org.javity.engine.rapid.systems.Scene2dSystem;

public enum JGUI {
	INSTANCE;
	
	Scene2dSystem guiSystem;
	
	public boolean isStageHandleInput(){
		return guiSystem.isHandleInput();
	}
}
