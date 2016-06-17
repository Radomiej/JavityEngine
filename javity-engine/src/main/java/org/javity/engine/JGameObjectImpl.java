package org.javity.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.javity.components.RectangleCollider;
import org.javity.components.Transform;
import org.javity.engine.gui.JCanvas;

import com.artemis.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.OrderedMap;

public class JGameObjectImpl extends JGameObject{
	private transient boolean started, notDestroyOnLoad;
	private String tag;
	private transient Entity entity;
	// private Map<Class<? extends Component>, Component> componentsMap =
	// Collections.synchronizedMap(new HashMap<Class<? extends Component>,
	// Component>());//new HashMap<Class<? extends Component>, Component>();
	OrderedMap<String, Component> componentsMap = new OrderedMap<String, Component>();

	private transient Transform transform;

	public JGameObjectImpl(String name) {
		this();
		this.name = name;
	}

	public JGameObjectImpl() {
		objectId = UUID.randomUUID().toString();
		createTransform();
	}

	public void addComponent(Component component) {
		component.setGameObject(this);
		componentsMap.put(component.getClass().getName(), component);

		if (started) {
			component.awake();
			component.start();
		}
	}

	@Override
	public void start() {
		started = true;
	}
	
	@Override
	public void awake() {
		transform = getComponent(Transform.class);
	}

	private void createTransform() {
		transform = new Transform();
		addComponent(transform);
	}

	public Entity getEntity() {
		return entity;
	}

	public void setEntity(Entity entity) {
		this.entity = entity;
	}

	@Override
	public <T extends Component> T getComponentInParent(Class<T> class1) {
		return transform.getParent().getComponent(class1);
	}
	
	@Override
	public <T extends Component> Iterable<T> getComponentsInParent(Class<T> class1) {
		return transform.getParent().getComponents(class1);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends Component> Iterable<T> getComponents(Class<T> componentType) {
		ArrayList<T> validComponents = new ArrayList<T>();
		for (Component component : componentsMap.values()) {
			if (componentType.equals(component)) {
				validComponents.add((T) component);
			}
		}
		return validComponents;
	}

	@Override
	public Iterable<Component> getAllComponents() {
		// return new ArrayList<Component>(componentsMap.values());
		return componentsMap.values();
	}
	
	@Override
	public <T extends Component> T getComponent(Class<T> componentType) {
		return componentType.cast(componentsMap.get(componentType.getName()));
	}

	static void destroy(Component componentToRemove) {
		if (componentToRemove == null) {
			Gdx.app.error("GameObject:destroy", "componentToRemove is null");
			return;
		}
		if (componentToRemove.getGameObject() == null) {
			Gdx.app.error("GameObject:destroy", "GameObject is null for Component");
			return;
		}
		componentToRemove.onDisable();
		componentToRemove.remove();
		componentToRemove.getGameObject().removeComponent(componentToRemove);
	}

	private void removeComponent(Component componentToRemove) {
		Component remove = componentsMap.remove(componentToRemove.getClass().getName());
		if (remove == null) {
			Gdx.app.error("GameObject:removeComponent", "Remove components isn`t exist");
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return getName();
	}

	@Override
	public Transform getTransform() {
		return transform;
	}

	void setNotDestroyOnLoad(boolean notDestroyOnLoad) {
		this.notDestroyOnLoad = notDestroyOnLoad;
	}

	boolean isDontDestroy() {
		return notDestroyOnLoad;
	}
	
	@Override
	public boolean isStarted() {
		return started;
	}

	public static void dontDestroyOnLoad(JGameObjectImpl gameObject) {
		gameObject.setNotDestroyOnLoad(true);
	}

	public String getTag() {
		return tag;
	}

	public void setTag(String tag) {
		this.tag = tag;
	}
}
