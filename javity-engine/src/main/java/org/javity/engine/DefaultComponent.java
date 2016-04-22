package org.javity.engine;

import org.javity.components.Transform;

import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.Manifold;

public abstract class DefaultComponent implements Component {
	private transient GameObject gameObject;
	private transient Transform transform;
	private boolean enabled = true;
	
	
	@Override
	public void setGameObject(GameObject gameObject) {
		this.gameObject = gameObject;
		this.transform = gameObject.getComponent(Transform.class);
	}

	@Override
	public GameObject getGameObject() {
		return gameObject;
	}

	@Override
	public void setEnabled(boolean enable) {
		if(enable == enabled){
			return;
		}
		this.enabled = enable;
		if(enable){
			onEnabled();
		}else{
			onDisable();
		}
	}
	
	@Override
	public boolean isEnabled() {
		return enabled;
	}
	
	@Override
	public Transform getTransform() {
		return transform;
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
	
	@Override
	public void onDisable() {}
	
	@Override
	public void onEnabled() {}
	
	@Override
	public void onPause() {}
	
	@Override
	public void onResume() {}
	
	@Override
	public void lateUpdate() {}
}
