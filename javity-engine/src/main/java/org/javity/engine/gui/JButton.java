package org.javity.engine.gui;

import org.javity.engine.GUIComponent;
import org.javity.engine.resources.SpriteResource;
import org.javity.engine.resources.TextureResource;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

import galaxy.rapid.common.DrawableHelper;

public class JButton extends GUIComponent {

	private transient Button button;
	public float sizeX, sizeY;
	public SpriteResource up, down, checked;
	
	@Override
	protected Actor getActor() {
		if(button == null) createButton();
		return button;
	}


	private void createButton() {
		
		Drawable upDrawable = DrawableHelper.getDrawableFromAsset(up.getResourcePath());
		Drawable downDrawable = DrawableHelper.getDrawableFromAsset(down.getResourcePath());
		Drawable checkedDrawable = DrawableHelper.getDrawableFromAsset(checked.getResourcePath());
		
		ButtonStyle style = new ButtonStyle(upDrawable, downDrawable, checkedDrawable);
		button = new Button(style);
	}

}
