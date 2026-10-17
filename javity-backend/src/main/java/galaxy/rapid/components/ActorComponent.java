package galaxy.rapid.components;

import com.artemis.Component;
import com.badlogic.gdx.scenes.scene2d.Actor;

public class ActorComponent extends Component {

	private Actor actor;

	public Actor getActor() {
		return actor;
	}

	public void setActor(Actor actor) {
		this.actor = actor;
	}
}
