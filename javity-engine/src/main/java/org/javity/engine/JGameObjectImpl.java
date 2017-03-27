package org.javity.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.javity.components.RectangleCollider;
import org.javity.components.Transform;
import org.javity.engine.gui.JCanvas;

import com.artemis.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad.TouchpadStyle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap.Values;
import com.badlogic.gdx.utils.OrderedMap;

import galaxy.rapid.event.RemoveEntityEvent;
import galaxy.rapid.eventbus.RapidBus;

public class JGameObjectImpl extends JGameObject {
	private transient boolean started, notDestroyOnLoad;
	private transient Entity entity;
	private boolean enabled = true;
	private boolean destroy, prefab = false;
	// private Map<Class<? extends Component>, Component> componentsMap =
	// Collections.synchronizedMap(new HashMap<Class<? extends Component>,
	// Component>());//new HashMap<Class<? extends Component>, Component>();
	private Map<String, Component> componentsMap = new HashMap<String, Component>();

	private transient Transform transform;

	public JGameObjectImpl(String name) {
		this();
		this.name = name;
	}

	public JGameObjectImpl() {
		objectId = UUID.randomUUID().toString();
		createTransform();
	}

	public void addComponent(JComponent component) {
		if(hasComponent(component.getClass())) {
			Gdx.app.error("JGameObject", "Component current exist in this object: " + this + " component: " + component);
			return;
		}
		
		component.setGameObject(this);
		componentsMap.put(component.getClass().getName(), component);

		//TODO move invoke these status to next frame? 
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
			if (component.getClass().isAssignableFrom(componentType)) {
				validComponents.add((T) component);
			}
		}
		return validComponents;
	}

	@Override
	public Collection<Component> getAllComponents() {
		ArrayList<Component> components = new ArrayList<Component>();
		
		if(componentsMap == null){
			Gdx.app.error("JGameObject", "componentsMap are null!");
			return components;
		}
		
		Collection<Component> values = componentsMap.values();
		if(values == null){
			Gdx.app.error("JGameObject", "values in components are null!");
			return components;
		}
		for(Component c : values){
			components.add(c);
		}
		
		return components;
		// return componentsMap.values();
	}

	@Override
	public <T extends Component> T getComponent(Class<T> componentType) {
		return componentType.cast(componentsMap.get(componentType.getName()));
	}

	@Override
	public Component getComponent(String componentName) {
		for(Component component : componentsMap.values()){
			if(component.getClass().getSimpleName().equalsIgnoreCase(componentName)){
				return component;
			}
		}
		return null;
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
		if(componentToRemove.isEnabled()) componentToRemove.onDisable();
		componentToRemove.remove();
		componentToRemove.getGameObject().removeComponent(componentToRemove);
	}

	@Override
	void removeComponent(Component componentToRemove) {
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
		return "GameObject name: " + getName() + " tag: " + getTag() + " uuid: " + getObjectId();
	}

	@Override
	public Transform getTransform() {
		return transform;
	}

	@Override
	public void setNotDestroyOnLoad(boolean notDestroyOnLoad) {
		this.notDestroyOnLoad = notDestroyOnLoad;
	}

	@Override
	public boolean isDontDestroy() {
		return notDestroyOnLoad;
	}

	@Override
	public boolean isStarted() {
		return started;
	}

	public String getTag() {
		return tag;
	}

	public void setTag(String tag) {
		this.tag = tag;
	}

	public void destroy(RapidBus nativeBus) {
		destroy = true;
		
		for (Component component : getAllComponents()) {
			destroy(component);
		}
		
		RemoveEntityEvent removeEvent = new RemoveEntityEvent(entity);
		nativeBus.post(removeEvent);
		componentsMap.clear();
//		componentsMap = null;
	}

	@Override
	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled)
			return;

		this.enabled = enabled;
		if (enabled) {
			for (Component component : componentsMap.values()) {
				component.onEnabled();
			}
		} else {
			for (Component component : componentsMap.values()) {
				component.onDisable();
			}
		}
	}

	@Override
	public boolean isDestroy() {
		return destroy;
	}

	// TODO change to Prefab Object allows use child-parent structure
	public boolean isPrefab() {
		return prefab;
	}

	public void setTransform(Transform transform2) {
		this.transform = transform2;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

}
