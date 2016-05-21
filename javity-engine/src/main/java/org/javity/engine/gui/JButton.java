package org.javity.engine.gui;

import org.javity.engine.GUIComponent;
import org.javity.engine.resources.SpriteResource;
import org.javity.engine.resources.TextureResource;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

import galaxy.rapid.common.DrawableHelper;
import galaxy.rapid.components.ActorComponent;

public class JButton extends GUIComponent {

	private transient ActorComponent actorComponent;
	private transient Button button;
	public float sizeX, sizeY;
	public SpriteResource up, down, checked;
	
	@Override
	public void awake() {
		actorComponent = new ActorComponent();
		createButton();
		actorComponent.setActor(button);
		addNativeComponent(actorComponent);
	}

	private void createButton() {
		
		Drawable upDrawable = up == null ? null : DrawableHelper.getDrawableFromAsset(up.getResourcePath());
		Drawable downDrawable = down == null ? null : DrawableHelper.getDrawableFromAsset(down.getResourcePath());
		Drawable checkedDrawable = checked == null ? null : DrawableHelper.getDrawableFromAsset(checked.getResourcePath());
		
		ButtonStyle style = new ButtonStyle(upDrawable, downDrawable, checkedDrawable);
		button = new Button(style);
	}

}
