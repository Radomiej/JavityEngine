package org.javity.engine.gui;

import org.javity.engine.GUIComponent;
import org.javity.engine.resources.BitmapFontResource;
import org.jrenner.smartfont.SmartFontGenerator;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

import galaxy.rapid.components.ActorComponent;

public class JTextField extends GUIComponent {

	private transient ActorComponent actorComponent;
	private transient TextField textField;
	public float sizeX, sizeY;
	public Color fontColor = Color.WHITE;
	public String text;
	
	public BitmapFontResource fontResource;
	public int fontSize = 24;
	
	@Override
	public void awake() {
		if (fontResource == null) {
			fontResource = new BitmapFontResource(JLabel.defaultFontResource);
		}
		
		actorComponent = new ActorComponent();
		createButton();
		actorComponent.setActor(textField);
		addNativeComponent(actorComponent);
	}

	private void createButton() {

		Skin skin = getGameObject().getComponentInParent(JCanvas.class).getSkin();
//		TextFieldStyle style = new TextFieldStyle(skin.get("default", TextFieldStyle.class));
		
		int realSize = (int) (((getTransform().getScale().x + getTransform().getScale().y) / 2) * fontSize);
		SmartFontGenerator fontGen = new SmartFontGenerator();
		FileHandle exoFile = Gdx.files.internal(fontResource.getResourcePath());
		BitmapFont fontBitmap = fontGen.createFont(exoFile, fontResource.getResourcePath() + realSize, realSize);
	
		TextFieldStyle styleDefault = skin.get("default", TextFieldStyle.class);
		TextFieldStyle style = new TextFieldStyle(fontBitmap, fontColor, styleDefault.cursor, styleDefault.selection, styleDefault.background);
		
		style.fontColor = fontColor;
		textField = new TextField(text, style);
		textField.addListener(new ChangeListener() {

			@Override
			public void changed(ChangeEvent event, Actor actor) {
				text = textField.getText();				
			}

		});

	}

	@Override
	public void onEnabled() {
		textField.setVisible(true);
	}
	
	@Override
	public void onDisable() {
		textField.setVisible(false);
	}
	
	
	public void setText(String text) {
		this.text = text;
		textField.setText(text);
	}
}
