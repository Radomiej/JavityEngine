package org.javity.engine;

public enum Camera {
	INSTANCE;
	
	private com.badlogic.gdx.graphics.Camera main;

	

	/**
	 * Main camera of current scene
	 */
	public com.badlogic.gdx.graphics.Camera getMain() {
		return main;
	}

	void setMain(com.badlogic.gdx.graphics.Camera main) {
		this.main = main;
	}
}
