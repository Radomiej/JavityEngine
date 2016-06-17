package org.javity.engine.gui;

import org.javity.engine.GUIComponent;
import org.javity.engine.resources.BitmapFontResource;
import org.jrenner.smartfont.SmartFontGenerator;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;

import galaxy.rapid.components.ActorComponent;

public class JLabel extends GUIComponent {

	private transient ActorComponent actorComponent;
	private transient Label label;
	public float sizeX, sizeY;
	public Color fontColor;
	public BitmapFontResource fontResource;
	public String text;

	@Override
	public void awake() {
		actorComponent = new ActorComponent();
		createButton();
		actorComponent.setActor(label);
		addNativeComponent(actorComponent);
	}

	private void createButton() {

		SmartFontGenerator fontGen = new SmartFontGenerator();
		FileHandle exoFile = Gdx.files.local("LiberationMono-Regular.ttf");
		BitmapFont fontSmall = fontGen.createFont(exoFile, "exo-small", 24);
		
		LabelStyle style = new LabelStyle(fontSmall, fontColor);
		label = new Label(text, style);
	}

	public void setText(String text){
		this.text = text;
		if(label != null) label.setText(text);
	}
}
