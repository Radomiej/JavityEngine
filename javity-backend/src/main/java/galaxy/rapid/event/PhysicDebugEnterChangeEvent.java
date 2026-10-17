package galaxy.rapid.event;

public class PhysicDebugEnterChangeEvent implements RapidEvent {
	public final boolean enableDebugRender;

	public PhysicDebugEnterChangeEvent(boolean debugRender) {
		this.enableDebugRender = debugRender;
	}
}
