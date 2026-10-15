package galaxy.rapid.screen;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.google.common.eventbus.Subscribe;

import galaxy.rapid.asset.AssetQueue;
import galaxy.rapid.event.ChangeScreenEvent;
import galaxy.rapid.eventbus.RapidBus;

public class ScreenNavigator {
	private Screen currentScreen;
	private AssetQueue currentAssetQueue;

	private Map<Class<? extends Screen>, Screen> activeOldScreens;
	private RapidBus eventBus;

	public ScreenNavigator(Screen startScreen) {
		this(startScreen, new RapidBus("GlobalAppNavigator"));
	}

	public ScreenNavigator(Screen startScreen, RapidBus eventBus) {
		this.eventBus = eventBus;
		this.eventBus.register(this);
		activeOldScreens = new HashMap<Class<? extends Screen>, Screen>();
		Gdx.app.log("ScreenNavigator", "Initialize ScreenNavigator complete.");
		changeScreen(startScreen, false);
	}

	public void updateCurrentScreen(float delta) {
		currentScreen.render(delta);
	}

	private void createCurrentScreenView() {
		currentScreen.show();
	}

	private void exitCurrentScreenView(boolean dispose) {
		if (currentScreen != null) {
			currentScreen.hide();
			if (dispose) {
				currentScreen.dispose();
				if (currentAssetQueue != null) {
					currentAssetQueue.unloadAll();
				}
			} else {
				activeOldScreens.put(currentScreen.getClass(), currentScreen);
			}
		}
	}

	@Subscribe()
	public void changeScreen(ChangeScreenEvent changeEvent) {
		Gdx.app.log("ScreenNavigator", "Incoming change screen event: ");
		try {
			changeScreen(changeEvent.getNewScene(), changeEvent.isDisposeCurrent());
			currentAssetQueue = changeEvent.getAssetQueue();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public void changeScreen(Screen newScene, boolean disposeOld) {
		Gdx.app.debug("ScreenNavigator", "Initial changeScene...");
		if (currentScreen != null && newScene.getClass().equals(currentScreen.getClass()) && !disposeOld) {
			Gdx.app.error("ScreenNavigator",
					"Invoke change without disposeOld=true, for the same Screen Class object`s!");
			return;
		}

		exitCurrentScreenView(disposeOld);
		setCurrentScreen(newScene);
		createCurrentScreenView();
	}

	public void dispose() {
		exitCurrentScreenView(true);
		disposeAllActiveOldScreen();
	}

	private void disposeAllActiveOldScreen() {
		for (Screen screen : activeOldScreens.values()) {
			screen.dispose();
		}
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

	public void setCurrentScreen(Screen newScene) {
		if (activeOldScreens.containsKey(newScene.getClass())) {
			newScene = activeOldScreens.get(newScene.getClass());
			activeOldScreens.remove(newScene.getClass());
		} else if (newScene instanceof EventBusInjector) {
			EventBusInjector eventBusInjector = (EventBusInjector) newScene;
			eventBusInjector.injectEventBus(eventBus);
		}
		this.currentScreen = newScene;
	}
}
