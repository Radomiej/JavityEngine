package org.javity.engine;

import galaxy.rapid.common.EntityEngine;
import galaxy.rapid.screen.RapidArtemisScreen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.artemis.Entity;
import com.artemis.WorldConfiguration;

public class JavityScreen extends RapidArtemisScreen {

	private Scene scene;

	public JavityScreen(Scene scene) {
		this.scene = scene;
		SceneManager.current = scene;
	}

	@Override
	protected void processWorldConfiguration(WorldConfiguration worldConfiguration) {
	}

	@Override
	protected void processBeforeRenderWorldConfiguration(WorldConfiguration worldConfiguration) {
		worldConfiguration.setSystem(new JavitySystem());
	}
	
	@Override
	protected void injectWorld(EntityEngine world) {
		System.out.println("injectWorld");
		Camera.INSTANCE.setMain(camera);
		
		for (GameObject gameObject : scene.getGameObjects()) {
			gameObject.start();
			awakesComponents(gameObject);
			Collection<com.artemis.Component> artemisComponents = getArtemisComponents(gameObject);
			registerInRapidBusAllNativeComponents(gameObject);
			
			Entity entity = createEntity(artemisComponents, world);
			gameObject.setEntity(entity);
			startsComponents(gameObject);
		}
		
		
	}

	private void registerInRapidBusAllNativeComponents(GameObject gameObject) {
		for(Component component : gameObject.getComponents()){
			if(component instanceof NativeComponent){
				NativeComponent nativeComponent = (NativeComponent) component;
				nativeComponent.setRapidBus(rapidBus);
				rapidBus.register(nativeComponent);
			}
		}
	}

	private void awakesComponents(GameObject gameObject) {
		for(Component component : gameObject.getComponents()){
			component.awake();
		}
	}
	
	private void startsComponents(GameObject gameObject) {
		for(Component component : gameObject.getComponents()){
			component.start();
		}
	}

	private Entity createEntity(Collection<com.artemis.Component> artemisComponents, EntityEngine world) {
		Entity entity = world.createEntity();
		for (com.artemis.Component nativeComponent : artemisComponents) {
			System.out.println("Dodaje: " + nativeComponent.getClass().getSimpleName());
			entity.edit().add(nativeComponent);
		}
		return entity;
	}

	private Collection<com.artemis.Component> getArtemisComponents(GameObject gameObject) {
		List<com.artemis.Component> artemisComponents = new ArrayList<com.artemis.Component>();
		for (Component javityComponent : gameObject.getComponents()) {
			System.out.println("check component: " + javityComponent.getClass().getSimpleName());
			if (javityComponent instanceof NativeComponent) {
				NativeComponent nativeComponent = (NativeComponent) javityComponent;
				artemisComponents.addAll(nativeComponent.getNativeComponents());
			}
		}
		return artemisComponents;
	}

	@Override
	public void render(float delta) {
		for (GameObject gameObject : scene.getGameObjects()) {
			Iterable<Component> components = gameObject.getComponents();
			for(Component component : components){
				component.update();
			}
		}
		super.render(delta);
	}
}
