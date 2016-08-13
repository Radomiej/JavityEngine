package org.javity.engine;

import java.util.Collection;

import galaxy.rapid.common.EntityEngine;
import galaxy.rapid.eventbus.RapidBus;

public interface InternalScene extends Scene{
	public void setNativeRapidBus(RapidBus nativeRapidBus);
	public void registerInRapidBusAllNativeComponents(JGameObject gameObject);
	public void awakeGameObject(JGameObject gameObject);
	public void startGameObject(JGameObject gameObject);
	public void setWorld(EntityEngine world);
	public Collection<JGameObject> getObjectToRemove();
	public Collection<JGameObject> getObjectToAdd();
	public void proccessGameObjectDestroy(JGameObject gameObject);
	public void proccessGameObjectAdd(JGameObject gameObject);
}
