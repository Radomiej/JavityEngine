package org.javity.engine.rapid.systems;

import org.javity.engine.Component;
import org.javity.engine.JGameObjectImpl;

import com.artemis.BaseSystem;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.Manifold;

import galaxy.rapid.systems.PhysicSystem;

public class JavityPhysicSystem extends BaseSystem implements ContactListener {

	private PhysicSystem physicSystem;
	
	@Override
	protected void initialize() {
		physicSystem.getPhysicWorld().setContactListener(this);
	}
	
	@Override
	protected void processSystem() {
		// TODO Auto-generated method stub

	}

	
	
	@Override
	public void beginContact(Contact contact) {

		Fixture fixtureA = contact.getFixtureA();
		Fixture fixtureB = contact.getFixtureB();

		JGameObjectImpl object1 = (JGameObjectImpl) fixtureA.getBody().getUserData();
		JGameObjectImpl object2 = (JGameObjectImpl) fixtureB.getBody().getUserData();

		if (fixtureA.isSensor() && fixtureB.isSensor()) {
			return;
		}
		if (fixtureA.isSensor()) {
			for (Component component : object1.getAllComponents()) {
				component.onCollisionTriggerEnter(contact);
			}
		} else if (fixtureB.isSensor()) {
			for (Component component : object2.getAllComponents()) {
				component.onCollisionTriggerEnter(contact);
			}
		} else {

			for (Component component : object1.getAllComponents()) {
				component.onCollisionEnter(contact);
			}
			for (Component component : object2.getAllComponents()) {
				component.onCollisionEnter(contact);
			}
		}
	}

	@Override
	public void endContact(Contact contact) {
		Fixture fixtureA = contact.getFixtureA();
		Fixture fixtureB = contact.getFixtureB();

		JGameObjectImpl object1 = (JGameObjectImpl) fixtureA.getBody().getUserData();
		JGameObjectImpl object2 = (JGameObjectImpl) fixtureB.getBody().getUserData();

		if (fixtureA.isSensor() && fixtureB.isSensor()) {
			return;
		}
		if (fixtureA.isSensor()) {
			for (Component component : object1.getAllComponents()) {
				component.onCollisionTriggerExit(contact);
			}
		} else if (fixtureB.isSensor()) {
			for (Component component : object2.getAllComponents()) {
				component.onCollisionTriggerExit(contact);
			}
		} else {

			for (Component component : object1.getAllComponents()) {
				component.onCollisionExit(contact);
			}
			for (Component component : object2.getAllComponents()) {
				component.onCollisionExit(contact);
			}
		}

	}

	@Override
	public void preSolve(Contact contact, Manifold oldManifold) {
		Fixture fixtureA = contact.getFixtureA();
		Fixture fixtureB = contact.getFixtureB();

		JGameObjectImpl object1 = (JGameObjectImpl) fixtureA.getBody().getUserData();
		JGameObjectImpl object2 = (JGameObjectImpl) fixtureB.getBody().getUserData();

		if (fixtureA.isSensor() || fixtureB.isSensor()) {
			return;
		}

		for (Component component : object1.getAllComponents()) {
			component.onCollisionPreSolve(contact, oldManifold);
		}
		for (Component component : object2.getAllComponents()) {
			component.onCollisionPreSolve(contact, oldManifold);
		}
	}

	@Override
	public void postSolve(Contact contact, ContactImpulse impulse) {
		Fixture fixtureA = contact.getFixtureA();
		Fixture fixtureB = contact.getFixtureB();

		JGameObjectImpl object1 = (JGameObjectImpl) fixtureA.getBody().getUserData();
		JGameObjectImpl object2 = (JGameObjectImpl) fixtureB.getBody().getUserData();

		if (fixtureA.isSensor() && fixtureB.isSensor()) {
			return;
		}
		if (fixtureA.isSensor()) {
			for (Component component : object1.getAllComponents()) {
				component.onCollisionTriggerStay(contact);
			}
		} else if (fixtureB.isSensor()) {
			for (Component component : object2.getAllComponents()) {
				component.onCollisionTriggerStay(contact);
			}
		} else {

			for (Component component : object1.getAllComponents()) {
				component.onCollisionStay(contact, impulse);
			}
			for (Component component : object2.getAllComponents()) {
				component.onCollisionStay(contact, impulse);
			}
		}

	}

}
