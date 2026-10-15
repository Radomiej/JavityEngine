package galaxy.rapid.screen;

import com.badlogic.gdx.graphics.Color;

public enum RapidScreenSettings {
	INSTANCE;
	
	private Color clearColor = Color.LIGHT_GRAY;

	public Color getClearColor() {
		return clearColor;
	}

	public void setClearColor(Color clearColor) {
		this.clearColor = clearColor;
	}
}
