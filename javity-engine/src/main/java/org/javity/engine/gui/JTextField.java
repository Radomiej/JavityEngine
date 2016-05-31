package org.javity.engine.gui;

import org.javity.engine.GUIComponent;
import org.javity.engine.resources.BitmapFontResource;
import org.javity.engine.resources.SpriteResource;
import org.jrenner.smartfont.SmartFontGenerator;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

import galaxy.rapid.common.DrawableHelper;
import galaxy.rapid.components.ActorComponent;

public class JTextField extends GUIComponent {

	private transient ActorComponent actorComponent;
	private transient TextField textField;
	public float sizeX, sizeY;
	public Color fontColor = Color.WHITE;
	public String text;
	
	@Override
	public void awake() {
		actorComponent = new ActorComponent();
		createButton();
		actorComponent.setActor(textField);
		addNativeComponent(actorComponent);
	}

	private void createButton() {

		
		Skin skin  = getGameObject().getComponentInParent(JCanvas.class).skin;
		TextFieldStyle style = new TextFieldStyle(skin.get("default", TextFieldStyle.class));
		style.fontColor = fontColor;
		textField = new TextField(text, style);
	}

	public void setText(String text){
		this.text = text;
		textField.setText(text);
	}
}
