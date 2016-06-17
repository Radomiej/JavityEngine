package org.javity.engine;

import java.util.UUID;

import org.javity.components.Transform;

public abstract class JGameObject {
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

}
