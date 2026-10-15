package galaxy.rapid.common;

import java.util.HashSet;
import java.util.Set;

import com.artemis.Component;

public class ComponentsBag {
	private Set<Component> components;

	public ComponentsBag() {
		components = new HashSet<Component>();
	}

	public ComponentsBag(Component... componentsTab) {
		components = new HashSet<Component>();
		for (Component component : componentsTab) {
			components.add(component);
		}
	}

	public ComponentsBag(Set<Component> componentToRemove) {
		components = componentToRemove;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((components == null) ? 0 : components.hashCode());
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
		ComponentsBag other = (ComponentsBag) obj;
		if (components == null) {
			if (other.components != null)
				return false;
		} else if (!components.equals(other.components))
			return false;
		return true;
	}

	public Component[] getComponentsTab() {
		Component[] componentsTab = new Component[components.size()];
		componentsTab = components.toArray(componentsTab);
		return componentsTab;
	}

	public Set<Component> getComponents() {
		return components;
	}

	public void setComponents(Set<Component> components) {
		this.components = components;
	}

	public void addComponent(Component component) {
		components.add(component);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "ComponentsBag [" + (components != null ? "components=" + components : "") + "]";
	}
}
