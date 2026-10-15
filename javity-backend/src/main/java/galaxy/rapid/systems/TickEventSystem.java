package galaxy.rapid.systems;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.artemis.BaseSystem;
import com.artemis.annotations.Wire;

import galaxy.rapid.common.TickEvent;
import galaxy.rapid.eventbus.RapidBus;

public class TickEventSystem extends BaseSystem {

	private List<TickEvent> tickets;

	@Wire
	private RapidBus eventBus;

	@Override
	protected void initialize() {
		tickets = new ArrayList<TickEvent>();
	}

	@Override
	protected void processSystem() {
		List<TickEvent> immutableTickets = new ArrayList<TickEvent>(tickets);
		for (TickEvent tick : immutableTickets) {
			decramentTimeToInvoke(tick);
			if (tick.getInvokeTime() <= 0) {
				sendPostObject(tick);
				proccesTicket(tick);
			}
		}
	}

	private void decramentTimeToInvoke(TickEvent tick) {
		tick.setInvokeTime(tick.getInvokeTime() - world.getDelta());
	}

	private void proccesTicket(TickEvent tick) {
		if (tick.getRepeatCount() <= 0) {
			removeTicket(tick);
		} else {
			prepareToNextTick(tick);
		}
	}

	private void removeTicket(TickEvent tick) {
		tickets.remove(tick);
	}

	private void prepareToNextTick(TickEvent tick) {
		tick.setRepeatCount(tick.getRepeatCount() - 1);
		tick.setInvokeTime(tick.getRepeatDeley());
	}

	private void sendPostObject(TickEvent tick) {
		if (tick.getPostingObject() != null) {
			eventBus.post(tick.getPostingObject());
		}
	}

	public void postTicket(TickEvent tickEvent) {
		tickets.add(tickEvent);
	}

}
