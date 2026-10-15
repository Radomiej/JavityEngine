package galaxy.rapid.event;

import com.artemis.Entity;

import galaxy.rapid.components.ColliderComponent;

public class AddColliderComponent implements RapidEvent{
	public final ColliderComponent colliderComponent;
	public final Entity entity;
	
	public AddColliderComponent(ColliderComponent colliderComponent, Entity entity) {
		this.colliderComponent = colliderComponent;
		this.entity = entity;
	}
}
