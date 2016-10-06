package pl.radomiej.map;

import org.javity.engine.JCamera;
import org.javity.engine.JComponent;
import org.javity.engine.JGUI;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

public class ToastComponent extends JComponent {
	private Marker parent;
	private String message;
	private Actor actor;

	public ToastComponent(Marker marker, String message) {
		parent = marker;
		this.message = message;
	}

	@Override
	public void start() {
		Stage stage = JGUI.INSTANCE.getStage();
		Label label = new Label(message, new Skin(Gdx.files.internal("internal/ui/uiskin.json")));
		actor = label;
		stage.addActor(label);
	}

	@Override
	public void update() {
		Vector2 stagePosition = JCamera.getMain().worldToScreenPoint(getTransform().getPosition());
		actor.setPosition(stagePosition.x, stagePosition.y);
	}
}
