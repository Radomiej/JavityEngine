package galaxy.rapid.systems;

import com.artemis.annotations.Wire;
import com.google.common.eventbus.Subscribe;

import galaxy.rapid.event.RemoveEntityEvent;
import galaxy.rapid.eventbus.RapidBus;

public class DeleteEventSystem extends PassiveSystem {

	@Wire
	private RapidBus eventSystem;
	
	@Override
	protected void initialize() {
		eventSystem.register(this);
	}
	
	@Subscribe()
	public void removeEventListener(RemoveEntityEvent removeEntity){
		world.deleteEntity(removeEntity.getRemoveEntity());
	}
	
}
