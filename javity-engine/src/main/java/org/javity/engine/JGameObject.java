package org.javity.engine;

import java.util.UUID;

import org.javity.components.Transform;

import com.artemis.Entity;

public abstract class JGameObject {
	
	public static void dontDestroyOnLoad(JGameObject gameObject) {
		gameObject.setNotDestroyOnLoad(true);
	}
	
	protected String name = "GameObject";
	protected String objectId;

	public abstract void start();

	public abstract void awake();

	public abstract Iterable<Component> getAllComponents();
	
	public abstract <T extends Component> T getComponentInParent(Class<T> class1);

	public abstract <T extends Component> Iterable<T> getComponentsInParent(Class<T> class1);

	public abstract <T extends Component> Iterable<T> getComponents(Class<T> componentType);

	public abstract <T extends Component> T getComponent(Class<T> componentType);

	public abstract boolean isStarted();
	
	public abstract Transform getTransform();
	
	public abstract void addComponent(Component component);
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getObjectId() {
		return objectId;
	}

	public void setObjectId(String prefabId) {
		this.objectId = prefabId;
	}

	public abstract void setEntity(Entity entity);

	public abstract boolean isDontDestroy();

	abstract void removeComponent(Component componentToRemove);

	public abstract void setNotDestroyOnLoad(boolean notDestroyOnLoad);

	public abstract Entity getEntity();

	

}
