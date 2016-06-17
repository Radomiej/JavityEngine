package org.javity.engine;

import galaxy.rapid.common.EntityEngine;
import galaxy.rapid.eventbus.RapidBus;

public interface InternalScene extends Scene{
	public void setNativeRapidBus(RapidBus nativeRapidBus);
	public void registerInRapidBusAllNativeComponents(JGameObjectImpl gameObject);
	public void awakeGameObject(JGameObjectImpl gameObject);
	public void startGameObject(JGameObjectImpl gameObject);
	public void setWorld(EntityEngine world);
}
