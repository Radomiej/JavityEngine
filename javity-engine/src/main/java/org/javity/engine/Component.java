package org.javity.engine;

import org.javity.components.Transform;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.Manifold;

public interface Component {
	public void setGameObject(JGameObject gameObject);
	public JGameObject getGameObject();
	public void awake();
	public void start();
	public void update();
	public void lateUpdate();
	public void preRender();
	public void postRender();
	public void preGuiRender();
	public void postGuiRender();
	public void remove();
	public void setEnabled(boolean enable);
	public boolean isEnabled();
	public Transform getTransform();
	public void onDisable();
	public void onEnabled();
	public void onPause();
	public void onResume();
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
	public void onMousePressed();
	public void onMouseRelased();
	public void onMouseClicked();
	public void onMouseDragged(Vector2 draggedDelta);
	public void onMouseOver();
	
	public JGameObject instantiateGameObject(Vector2 position);
	public void destroyGameObject(JGameObject gameObject);
	public void destroyGameObject(JGameObject gameObject, float timeToDestroy);
}
