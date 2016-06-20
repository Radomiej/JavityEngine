package org.javity.engine;

import java.util.Collection;

import org.javity.components.Transform;

import com.artemis.Entity;
import com.badlogic.gdx.Gdx;

public class GameObjectProxy extends JGameObject{

	private transient JGameObject orginalObject;
	public GameObjectProxy(JGameObject gameObject) {
		this.orginalObject = gameObject;
		objectId = gameObject.objectId;
	}
	
	public GameObjectProxy() {
	}

	private void initOrginalObject(){
		
	}
	
	@Override
	public void start() {
		Gdx.app.error("GameObjectProxy", "nothing start() mehtod is execute!"); 
	}

	@Override
	public void awake() {
		Gdx.app.error("GameObjectProxy", "nothing awake() mehtod is execute!"); 
	}

	@Override
	public <T extends Component> T getComponentInParent(Class<T> class1) {
		return orginalObject.getComponentInParent(class1);
	}

	@Override
	public <T extends Component> Iterable<T> getComponentsInParent(Class<T> class1) {
		return orginalObject.getComponentsInParent(class1);
	}

	@Override
	public <T extends Component> Iterable<T> getComponents(Class<T> componentType) {
		return orginalObject.getComponents(componentType);
	}

	@Override
	public <T extends Component> T getComponent(Class<T> componentType) {
		return orginalObject.getComponent(componentType);
	}

	@Override
	public boolean isStarted() {
		return orginalObject.isStarted();
	}

	@Override
	public Transform getTransform() {
		if(orginalObject == null) return null;
		return orginalObject.getTransform();
	}

	@Override
	public Collection<Component> getAllComponents() {
		return orginalObject.getAllComponents();
	}

	@Override
	public void addComponent(Component component) {
		orginalObject.addComponent(component);
	}

	@Override
	public void setEntity(Entity entity) {
		orginalObject.setEntity(entity);
	}

	@Override
	public boolean isDontDestroy() {
		return orginalObject.isDontDestroy();
	}

	@Override
	void removeComponent(Component componentToRemove) {
		orginalObject.removeComponent(componentToRemove);
	}

	@Override
	public void setNotDestroyOnLoad(boolean notDestroyOnLoad) {
		orginalObject.setNotDestroyOnLoad(notDestroyOnLoad);
	}

	@Override
	public Entity getEntity() {
		return orginalObject.getEntity();
	}

	@Override
	public void setEnabled(boolean enabled) {
		orginalObject.setEnabled(enabled);		
	}

	@Override
	public boolean isDestroy() {
		return orginalObject.isDestroy();
	}
	
	@Override
	public String toString() {
		return "GameObject Proxy uuid: " + getObjectId();
	}

	@Override
	public boolean isEnabled() {
		return orginalObject.isEnabled();
	}
}
