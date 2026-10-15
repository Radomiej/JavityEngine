package galaxy.rapid.common;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import com.artemis.Component;

public class HashComponentsBag {
	@SuppressWarnings("rawtypes")
	private HashMap<Class, Component> componentsMap;

	@SuppressWarnings("rawtypes")
	public HashComponentsBag() {
		componentsMap = new HashMap<Class, Component>();
	}

	public HashComponentsBag(Component... componentsTab) {
		this();
		for (Component component : componentsTab) {
			componentsMap.put(component.getClass(), component);
		}
	}

	public HashComponentsBag(Set<Component> components) {
		this();
		for (Component component : components) {
			componentsMap.put(component.getClass(), component);
		}
	}

	public HashComponentsBag(ComponentsBag setComponentsBag) {
		this();
		for (Component component : setComponentsBag.getComponents()) {
			componentsMap.put(component.getClass(), component);
		}
	}
	
	public Component[] getComponentsTab() {
		Component[] componentsTab = new Component[componentsMap.values().size()];
		componentsTab = componentsMap.values().toArray(componentsTab);
		return componentsTab;
	}

	public Set<Component> getComponents() {
		return new HashSet<Component>(componentsMap.values());
	}

	public void setComponents(HashSet<Component> components) {
		componentsMap.clear();
		for (Component component : components) {
			componentsMap.put(component.getClass(), component);
		}
	}

	public void addComponent(Component component) {
		componentsMap.put(component.getClass(), component);
	}

	public Component getComponentByClass(Class<? extends Component> class1) {
		return componentsMap.get(class1);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((componentsMap == null) ? 0 : componentsMap.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		HashComponentsBag other = (HashComponentsBag) obj;
		if (componentsMap == null) {
			if (other.componentsMap != null)
				return false;
		} else if (!componentsMap.equals(other.componentsMap))
			return false;
		return true;
	}

	public void removeComponent(Component oldComponent) {
		componentsMap.remove(oldComponent.getClass());
	}
}
