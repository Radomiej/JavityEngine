package galaxy.rapid.eventbus;

import com.google.common.eventbus.EventBus;

import galaxy.rapid.event.RapidEvent;

public class RapidBus {
	private String name;
	private EventBus eventBus;
	
	/**
	 * Create new instance of EventBus with "Default" name
	 */
	public RapidBus() {
		this("Default");
	}

	/**
	 * Create new instance of EventBus
	 * @param name
	 */
	public RapidBus(String name) {
		this.setName(name);
		eventBus = new EventBus();
	}

	public void post(RapidEvent event) {
		eventBus.post(event);
	}

	public void register(Object registerObject) {
		eventBus.register(registerObject);
	}

	public void unregister(Object unregisterObject) {
		eventBus.unregister(unregisterObject);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
