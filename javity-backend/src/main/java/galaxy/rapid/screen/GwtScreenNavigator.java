package galaxy.rapid.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;

import galaxy.rapid.asset.AssetQueue;

public class GwtScreenNavigator {
	private Screen currentScreen;
	private AssetQueue assetQueue;

	public GwtScreenNavigator(Screen startScreen) {
		Gdx.app.log("ScreenNavigator" , "Initialize ScreenNavigator complete.");
		currentScreen = startScreen;
	}

	public void updateCurrentScreen(float delta) {
		currentScreen.render(delta);
	}

	public void changeScreen(Screen nextScreen) {
		currentScreen.dispose();
		currentScreen = nextScreen;
	}

	public void dispose() {
		currentScreen.dispose();
	}

	public void resize(int width, int height) {
		currentScreen.resize(width, height);
	}

	public void pause() {
		currentScreen.pause();
	}

	public void resume() {
		currentScreen.resume();
	}

	public Screen getCurrentScreen() {
		return currentScreen;
	}
}
