package org.javity.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.javity.components.RectangleCollider;
import org.javity.components.Transform;

import com.artemis.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.OrderedMap;

public class GameObject{
	private String name = "GameObject";
	private transient boolean started;
	private transient Entity entity;
//	private Map<Class<? extends Component>, Component> componentsMap = Collections.synchronizedMap(new HashMap<Class<? extends Component>, Component>());//new HashMap<Class<? extends Component>, Component>();
	OrderedMap<String, Component> componentsMap = new OrderedMap<String, Component>();
	
	public GameObject(String name) {
		this();
		this.name = name;
	}
	
	public GameObject() {
		createTransform();
	}
	
	public void addComponent(Component component) {
		component.setGameObject(this);
		componentsMap.put(component.getClass().getName(), component);

		if (started){
			component.awake();
			component.start();
		}
	}

	public void start() {
		started = true;
	}

	

	private void createTransform() {
		Transform transform = new Transform();
		addComponent(transform);
	}

	public Entity getEntity() {
		return entity;
	}

	public void setEntity(Entity entity) {
		this.entity = entity;
	}

	public Iterable<Component> getComponents() {
//		return new ArrayList<Component>(componentsMap.values());
		return componentsMap.values();
	}

	public <T extends Component> T getComponent(Class<T> componentType) {
	    return componentType.cast(componentsMap.get(componentType.getName()));
	}

	public static void destroy(Component componentToRemove) {
		if(componentToRemove.getGameObject() == null){
			Gdx.app.error("GameObject:destroy", "GameObject is null for Component");
		}
		componentToRemove.remove();
		componentToRemove.getGameObject().removeComponent(componentToRemove);
	}

	private void removeComponent(Component componentToRemove) {
		Component remove = componentsMap.remove(componentToRemove.getClass().getName());
		if(remove == null){
			Gdx.app.error("GameObject:removeComponent", "Remove components isn`t exist");
		}
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return getName();
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
