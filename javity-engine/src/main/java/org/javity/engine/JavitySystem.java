package org.javity.engine;

import com.artemis.BaseSystem;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.DestructionListener;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.Manifold;

import galaxy.rapid.systems.PhysicSystem;

public class JavitySystem extends BaseSystem implements ContactListener {

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

		GameObject object1 = (GameObject) fixtureA.getBody().getUserData();
		GameObject object2 = (GameObject) fixtureB.getBody().getUserData();

		if (fixtureA.isSensor() && fixtureB.isSensor()) {
			return;
		}
		if (fixtureA.isSensor()) {
			for (Component component : object1.getComponents()) {
				component.onCollisionTriggerEnter(contact);
			}
		} else if (fixtureB.isSensor()) {
			for (Component component : object2.getComponents()) {
				component.onCollisionTriggerEnter(contact);
			}
		} else {

			for (Component component : object1.getComponents()) {
				component.onCollisionEnter(contact);
			}
			for (Component component : object2.getComponents()) {
				component.onCollisionEnter(contact);
			}
		}
	}

	@Override
	public void endContact(Contact contact) {
		Fixture fixtureA = contact.getFixtureA();
		Fixture fixtureB = contact.getFixtureB();

		GameObject object1 = (GameObject) fixtureA.getBody().getUserData();
		GameObject object2 = (GameObject) fixtureB.getBody().getUserData();

		if (fixtureA.isSensor() && fixtureB.isSensor()) {
			return;
		}
		if (fixtureA.isSensor()) {
			for (Component component : object1.getComponents()) {
				component.onCollisionTriggerExit(contact);
			}
		} else if (fixtureB.isSensor()) {
			for (Component component : object2.getComponents()) {
				component.onCollisionTriggerExit(contact);
			}
		} else {

			for (Component component : object1.getComponents()) {
				component.onCollisionExit(contact);
			}
			for (Component component : object2.getComponents()) {
				component.onCollisionExit(contact);
			}
		}

	}

	@Override
	public void preSolve(Contact contact, Manifold oldManifold) {
		Fixture fixtureA = contact.getFixtureA();
		Fixture fixtureB = contact.getFixtureB();

		GameObject object1 = (GameObject) fixtureA.getBody().getUserData();
		GameObject object2 = (GameObject) fixtureB.getBody().getUserData();

		if (fixtureA.isSensor() || fixtureB.isSensor()) {
			return;
		}

		for (Component component : object1.getComponents()) {
			component.onCollisionPreSolve(contact, oldManifold);
		}
		for (Component component : object2.getComponents()) {
			component.onCollisionPreSolve(contact, oldManifold);
		}
	}

	@Override
	public void postSolve(Contact contact, ContactImpulse impulse) {
		Fixture fixtureA = contact.getFixtureA();
		Fixture fixtureB = contact.getFixtureB();

		GameObject object1 = (GameObject) fixtureA.getBody().getUserData();
		GameObject object2 = (GameObject) fixtureB.getBody().getUserData();

		if (fixtureA.isSensor() && fixtureB.isSensor()) {
			return;
		}
		if (fixtureA.isSensor()) {
			for (Component component : object1.getComponents()) {
				component.onCollisionTriggerStay(contact);
			}
		} else if (fixtureB.isSensor()) {
			for (Component component : object2.getComponents()) {
				component.onCollisionTriggerStay(contact);
			}
		} else {

			for (Component component : object1.getComponents()) {
				component.onCollisionStay(contact, impulse);
			}
			for (Component component : object2.getComponents()) {
				component.onCollisionStay(contact, impulse);
			}
		}

	}

}
