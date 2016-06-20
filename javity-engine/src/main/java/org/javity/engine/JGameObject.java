package org.javity.engine;

import java.util.Collection;

import org.javity.components.Transform;

import com.artemis.Entity;

public abstract class JGameObject {
	
	public static void dontDestroyOnLoad(JGameObject gameObject) {
		gameObject.setNotDestroyOnLoad(true);
	}
	
	protected String name = "GameObject";
	protected String tag;
	protected String objectId;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getTag() {
		return tag;
	}

	public void setTag(String tag) {
		this.tag = tag;
	}

	public String getObjectId() {
		return objectId;
	}

	public void setObjectId(String prefabId) {
		this.objectId = prefabId;
	}

	abstract void start();

	abstract void awake();

	public abstract Collection<Component> getAllComponents();
	
	public abstract <T extends Component> T getComponentInParent(Class<T> class1);

	public abstract <T extends Component> Iterable<T> getComponentsInParent(Class<T> class1);

	public abstract <T extends Component> Iterable<T> getComponents(Class<T> componentType);

	public abstract <T extends Component> T getComponent(Class<T> componentType);

	public abstract boolean isStarted();
	
	public abstract Transform getTransform();
	
	public abstract void addComponent(Component component);
	
	abstract void setEntity(Entity entity);

	public abstract boolean isDontDestroy();

	abstract void removeComponent(Component componentToRemove);

	public abstract void setNotDestroyOnLoad(boolean notDestroyOnLoad);

	public abstract Entity getEntity();

	public abstract void setEnabled(boolean enabled);
	
	public abstract boolean isEnabled();

	public abstract boolean isDestroy();
	

}
