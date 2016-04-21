package org.javity.engine;

import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.Manifold;

public interface Component {
	public void setGameObject(GameObject gameObject);
	public GameObject getGameObject();
	public void awake();
	public void start();
	public void update();
	public void remove();
	public void onCollisionEnter(Contact contact);
	public void onCollisionExit(Contact contact);
	public void onCollisionPreSolve(Contact contact, Manifold oldManifold);
	/**
	 * In Box2d is PostSolve
	 * @param contact
	 * @param impulse 
	 */
	public void onCollisionStay(Contact contact, ContactImpulse impulse);
	public void onCollisionTriggerEnter(Contact contact);
	public void onCollisionTriggerExit(Contact contact);
	public void onCollisionTriggerStay(Contact contact);
}
