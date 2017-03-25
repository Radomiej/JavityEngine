package org.javity.engine;

import galaxy.rapid.eventbus.RapidBus;

public enum JEngine {
	INSTANCE;
	RapidBus rapidEventBus;
	
	public RapidBus getEventBus(){
		return rapidEventBus;
	}
}
