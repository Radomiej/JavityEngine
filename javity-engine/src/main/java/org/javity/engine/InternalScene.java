package org.javity.engine;

import java.util.Collection;
import java.util.List;

import galaxy.rapid.common.EntityEngine;
import galaxy.rapid.eventbus.RapidBus;

public interface InternalScene extends JScene{
	
	void initialize();
	void setNativeRapidBus(RapidBus nativeRapidBus);
	void registerInRapidBusAllNativeComponents(JGameObject gameObject);
	void awakeGameObject(JGameObject gameObject);
	void startGameObject(JGameObject gameObject);
	void setWorld(EntityEngine world);
	List<JGameObject> getObjectToRemove();
	List<JGameObject> getObjectToAdd();
	void proccessGameObjectDestroy(JGameObject gameObject);
	void proccessGameObjectAdd(JGameObject gameObject);
	void enableGameObject(JGameObject gameObject);
	boolean isRun();
	void clearObjectsToDestroy();
	void clearObjectsToAdd();
}
