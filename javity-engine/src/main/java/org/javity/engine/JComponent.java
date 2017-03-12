package org.javity.engine;

import org.javity.components.Transform;
import org.javity.engine.timer.Task;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.Manifold;

public abstract class JComponent implements Component {
	private transient JGameObject gameObject;
	private transient Transform transform;
	private boolean enabled = true;
	
	
	@Override
	public
	void setGameObject(JGameObject gameObject) {
		this.gameObject = gameObject;
		this.transform = gameObject.getComponent(Transform.class);
	}

	@Override
	public JGameObject getGameObject() {
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
	public void preRender() {}
	

	@Override
	public void postRender() {}

	@Override
	public void preGuiRender() {}

	@Override
	public void postGuiRender() {}

	@Override
	public void lateUpdate() {}

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
	public void onMouseClicked() {}
	
	@Override
	public void onMouseDragged(Vector2 draggedDelta) {}
	
	@Override
	public void onMousePressed() {}
	
	@Override
	public void onMouseRelased() {}
	
	@Override
	public void onMouseOver() {}
	
	@Override
	public JGameObject instantiateGameObject(Vector2 position) {
		return JSceneManager.current.instantiateGameObject(position);
	}
	
	@Override
	public void destroyGameObject(JGameObject gameObject) {
		JSceneManager.current.destroyGameObject(gameObject);		
	}
	
	@Override
	public void destroyGameObject(final JGameObject gameObject, float timeToDestroy) {
		JTime.INSTANCE.addTimer(new Task(){
			@Override
			public void invoke() {
				JSceneManager.current.destroyGameObject(gameObject);		
			}
			
		}, timeToDestroy);
	}
}
