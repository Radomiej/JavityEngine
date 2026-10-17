package galaxy.rapid.components;

import java.util.UUID;

import com.artemis.Component;

public class TargetComponent extends Component{
	private UUID target;

	public TargetComponent() {
	}
	
	public TargetComponent(UUID uuidFromEntity) {
		target = uuidFromEntity;
	}

	public UUID getTarget() {
		return target;
	}

	public void setTarget(UUID target) {
		this.target = target;
	}
}
