package galaxy.rapid.event;

import com.artemis.Entity;

public class RemoveEntityEvent implements RapidEvent {
	private Entity removeEntity;

	public RemoveEntityEvent() {
	}

	public RemoveEntityEvent(Entity removeEntity) {
		this.removeEntity = removeEntity;
	}

	public Entity getRemoveEntity() {
		return removeEntity;
	}

	public void setRemoveEntity(Entity removeEntity) {
		this.removeEntity = removeEntity;
	}

}
