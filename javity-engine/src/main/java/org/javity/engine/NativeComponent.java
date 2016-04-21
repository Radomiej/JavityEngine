package org.javity.engine;

import java.util.ArrayList;
import java.util.Collection;

import com.artemis.Component;

import galaxy.rapid.eventbus.RapidBus;

public abstract class NativeComponent extends DefaultComponent {
	private transient Collection<Component> nativeComponents = new ArrayList<Component>();
	private transient RapidBus rapidBus;

	public void addNativeComponent(Component component) {
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
