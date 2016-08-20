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
	public static String defaultFontResource = "LiberationMono-Regular.ttf";
	
	private transient ActorComponent actorComponent;
	private transient Label label;
	public float sizeX, sizeY;
	public Color fontColor;
	public BitmapFontResource fontResource;
	public int fontSize = 24;
	public String text;

	@Override
	public void awake() {
		if (fontResource == null) {
			fontResource = new BitmapFontResource(defaultFontResource);
		}

		actorComponent = new ActorComponent();
		createButton();
		actorComponent.setActor(label);
		addNativeComponent(actorComponent);
	}

	private void createButton() {

		int realSize = (int) (((getTransform().getScale().x + getTransform().getScale().y) / 2) * fontSize);
		Gdx.app.log("JLabel", "font size: " + realSize);

		SmartFontGenerator fontGen = new SmartFontGenerator();
		FileHandle exoFile = Gdx.files.internal(fontResource.getResourcePath());
		BitmapFont fontBitmap = fontGen.createFont(exoFile, fontResource.getResourcePath() + realSize, realSize);
		
		LabelStyle style = new LabelStyle(fontBitmap, fontColor);
		label = new Label(text, style);
		label.setScale(getTransform().getScale().x, getTransform().getScale().y);
	}

	@Override
	public void onEnabled() {
		label.setVisible(true);
	}
	
	@Override
	public void onDisable() {
		label.setVisible(false);
	}
	
	public void setText(String text) {
		this.text = text;
		if (label != null)
			label.setText(text);
	}
}
