package org.javity.engine.gui;

import org.javity.components.Transform;
import org.javity.engine.GUIComponent;
import org.javity.engine.JGameObject;
import org.javity.engine.JGameObjectImpl;
import org.javity.engine.gui.remote.RemoteInvoker;
import org.javity.engine.resources.SpriteResource;
import org.javity.engine.resources.TextureResource;
import org.jrenner.smartfont.SmartFontGenerator;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

import galaxy.rapid.common.DrawableHelper;
import galaxy.rapid.components.ActorComponent;

public class JTextButton extends GUIComponent {

	private transient ActorComponent actorComponent;
	private transient TextButton button;
	public float sizeX, sizeY;
	public SpriteResource up, down, checked;

	public JGameObject clickTarget;
	public RemoteInvoker clickInvoke;
	public String text;
	public boolean useSkin;

	@Override
	public void awake() {
		actorComponent = new ActorComponent();
		createButton();
		actorComponent.setActor(button);
		addNativeComponent(actorComponent);
		
		Gdx.app.log("JTextButton position", getTransform().getPosition().toString());
		
	}

	private void createButton() {

		Skin skin = getGameObject().getComponentInParent(JCanvas.class).getSkin();
		TextButtonStyle style = new TextButtonStyle(skin.get("default", TextButtonStyle.class));

		if (up != null) {
			style.up = DrawableHelper.getDrawableFromAsset(up.getResourcePath());
		}
		if (down != null) {
			style.down = DrawableHelper.getDrawableFromAsset(up.getResourcePath());
		}
		if (checked != null) {
			style.checked = DrawableHelper.getDrawableFromAsset(up.getResourcePath());
		}
		SmartFontGenerator fontGen = new SmartFontGenerator();
		FileHandle exoFile = Gdx.files.local("LiberationMono-Regular.ttf");
		BitmapFont fontSmall = fontGen.createFont(exoFile, "exo-small", 24);

		if (checked != null) {
			style.font = fontSmall;
		}

		button = new TextButton(text, style);
		button.addCaptureListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				if (clickTarget != null && clickInvoke != null) {
					clickInvoke.invoke(clickTarget);
				}
			}
		});
	}

	public void setText(String text) {
		this.text = text;
		if(button != null)button.setText(text);
	}

	public void setClickTarget(JGameObject target) {
		clickTarget = target;
	}
}
