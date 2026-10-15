package galaxy.rapid.screen;

import galaxy.rapid.eventbus.RapidBus;

public interface EventBusInjector {
	public void injectEventBus(RapidBus globalEventBus);
}
