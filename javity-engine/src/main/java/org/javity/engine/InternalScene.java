package org.javity.engine;

import galaxy.rapid.common.EntityEngine;
import galaxy.rapid.eventbus.RapidBus;

public interface InternalScene extends Scene{
	public void setNativeRapidBus(RapidBus nativeRapidBus);
	public void registerInRapidBusAllNativeComponents(GameObject gameObject);
	public void awakeGameObject(GameObject gameObject);
	public void startGameObject(GameObject gameObject);
	public void setWorld(EntityEngine world);
}
