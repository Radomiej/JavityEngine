package galaxy.rapid.event;

import galaxy.rapid.components.ColliderComponent;

public class RemoveColliderComponent implements RapidEvent{
	public final ColliderComponent colliderComponent;
	
	public RemoveColliderComponent(ColliderComponent colliderComponent2) {
		this.colliderComponent = colliderComponent2;
	}
}
