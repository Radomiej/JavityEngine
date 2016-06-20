package org.javity.engine;

import java.util.ArrayList;
import java.util.Collection;

import com.artemis.Component;
import com.badlogic.gdx.Gdx;

import galaxy.rapid.eventbus.RapidBus;

public abstract class NativeComponent extends JComponent {
	private transient Collection<Component> nativeComponents = new ArrayList<Component>();
	private transient RapidBus rapidBus;

	public void addNativeComponent(Component component) {
		if(component == null){
			Gdx.app.error(getClass().getSimpleName(), "Try add null component for rapid");
			return;
		}
		nativeComponents.add(component);
	}

	public Collection<Component> getNativeComponents() {
		return nativeComponents;
	}

	protected RapidBus getRapidBus() {
		return rapidBus;
	}

	void setRapidBus(RapidBus rapidBus) {
		this.rapidBus = rapidBus;
	}
}
