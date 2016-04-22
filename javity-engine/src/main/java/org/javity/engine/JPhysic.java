package org.javity.engine;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.World;

import galaxy.rapid.event.PhysicDebugEnterChangeEvent;
import galaxy.rapid.eventbus.RapidBus;

public class JPhysic {
	
	private static JPhysic physic;
	static void setPhysic(JPhysic newPhysic){
		physic = newPhysic;
	}
	
	private World physicWorld;
	private RapidBus eventBus;
	
	public JPhysic(World physicWorld, RapidBus eventBus) {
		this.eventBus = eventBus;
		this.physicWorld = physicWorld;
	}
	
	public static void setGravity(Vector2 gravityVector){
		physic.physicWorld.setGravity(gravityVector);
	}
	
	public static void setDebugRender(boolean debugRender){
		physic.eventBus.post(new PhysicDebugEnterChangeEvent(debugRender));
	}
}
