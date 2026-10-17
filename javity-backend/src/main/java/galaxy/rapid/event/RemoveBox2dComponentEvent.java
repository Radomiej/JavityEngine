package galaxy.rapid.event;

import galaxy.rapid.components.Box2dComponent;
import galaxy.rapid.components.RectangleColliderComponent;

public class RemoveBox2dComponentEvent implements RapidEvent{
	public Box2dComponent box2dComponent;
	
	public RemoveBox2dComponentEvent(Box2dComponent box2dComponent) {
		this.box2dComponent = box2dComponent;
	}
}
