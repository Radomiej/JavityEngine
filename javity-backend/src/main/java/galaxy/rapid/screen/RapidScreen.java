package galaxy.rapid.screen;

import com.badlogic.gdx.Screen;

import galaxy.rapid.event.ChangeScreenEvent;
import galaxy.rapid.eventbus.RapidBus;

public abstract class RapidScreen implements Screen, EventBusInjector {
	private boolean initialize = false;

	protected RapidBus masterEventBus;

	public void show() {
		if (!initialize) {
			initialize = true;
			create();
		}
	}

	protected abstract void create();

	public void hide() {
	}

	public void pause() {
	}

	public void resume() {
	}

	public void resize(int width, int height) {
	}

	public void injectEventBus(RapidBus globalEventBus) {
		masterEventBus = globalEventBus;
	}
	
	protected void changeScreen(Screen screen){
		ChangeScreenEvent changeScreenEvent = new ChangeScreenEvent(screen);
		masterEventBus.post(changeScreenEvent);
	}
}
