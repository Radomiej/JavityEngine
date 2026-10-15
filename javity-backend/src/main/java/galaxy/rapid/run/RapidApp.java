package galaxy.rapid.run;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;

import galaxy.rapid.configuration.RapidConfig;
import galaxy.rapid.configuration.RapidConfiguration;
import galaxy.rapid.input.RapidInput;
import galaxy.rapid.screen.ScreenNavigator;
import galaxy.rapid.screen.RapidScreenSettings;

public class RapidApp extends ApplicationAdapter {
	private ScreenNavigator screenNavigator;
	private Screen startScreen;
	private RapidInput rapidInput = new RapidInput();
	
	public RapidApp(Screen startScreen) {		
		this.startScreen = startScreen;		
		
	}	

	@Override
	public void create() {
		Gdx.app.setLogLevel(Application.LOG_DEBUG);
		
		Gdx.app.debug(RapidApp.class.getSimpleName(), "Loading configuration...");
		RapidConfiguration.INSTANCE.load();
		RapidConfig rapidConfig = RapidConfiguration.INSTANCE.getRapidConfig();
		
		if(rapidConfig.debugMode){
			Gdx.app.setLogLevel(Gdx.app.LOG_DEBUG);
		}
		
		Gdx.app.debug(RapidApp.class.getSimpleName(), "Setting multiplexer...");
		InputMultiplexer inputMultiplexer = new InputMultiplexer();
		inputMultiplexer.addProcessor(rapidInput);
		Gdx.input.setInputProcessor(inputMultiplexer);
		
		Gdx.app.debug(RapidApp.class.getSimpleName(), "Run first scene...");
		screenNavigator = new ScreenNavigator(startScreen);
	}

	@Override
	public void render() {
		Color clearColor = RapidScreenSettings.INSTANCE.getClearColor();
		Gdx.gl.glClearColor(clearColor.r, clearColor.g, clearColor.b, clearColor.a);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		screenNavigator.updateCurrentScreen(Gdx.graphics.getDeltaTime());
		rapidInput.endRender();
	}

	@Override
	public void resize(int width, int height) {
		screenNavigator.resize(width, height);
	}

	@Override
	public void dispose() {
		screenNavigator.dispose();
	}

	@Override
	public void pause() {
		screenNavigator.pause();
	}

	@Override
	public void resume() {
		screenNavigator.resume();
	}
}
