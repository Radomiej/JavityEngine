package org.javity.engine;

import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.Manifold;

public abstract class DefaultComponent implements Component {
	private transient GameObject gameObject;

	@Override
	public void setGameObject(GameObject gameObject) {
		this.gameObject = gameObject;
	}

	@Override
	public GameObject getGameObject() {
		return gameObject;
	}

	@Override
	public void awake() {
	}

	@Override
	public void start() {
	}

	@Override
	public void update() {
	}

	@Override
	public void remove() {
	}

	@Override
	public void onCollisionEnter(Contact contact) {
	}

	@Override
	public void onCollisionExit(Contact contact) {
	}

	@Override
	public void onCollisionPreSolve(Contact contact, Manifold oldManifold) {
	}

	@Override
	public void onCollisionStay(Contact contact, ContactImpulse impulse) {
	}

	@Override
	public void onCollisionTriggerEnter(Contact contact) {
	}

	@Override
	public void onCollisionTriggerExit(Contact contact) {
	}

	@Override
	public void onCollisionTriggerStay(Contact contact) {
	}
}
