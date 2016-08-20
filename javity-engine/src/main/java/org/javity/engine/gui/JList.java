package org.javity.engine.gui;

import org.javity.engine.GUIComponent;
import org.javity.engine.resources.BitmapFontResource;
import org.javity.engine.resources.SpriteResource;
import org.jrenner.smartfont.SmartFontGenerator;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Event;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton.ImageButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;

import galaxy.rapid.common.DrawableHelper;
import galaxy.rapid.components.ActorComponent;

public class JList<T> extends GUIComponent {
	private transient ActorComponent actorComponent;
	private transient List<T> list;
	private transient ScrollPane scrollPane;
	public float sizeX, sizeY;
	public Color fontColorSelected, fontColorUnselected;
	public BitmapFontResource fontResource;
	public int fontSize = 24;
	public SpriteResource selection, background;

	private Array<T> tempItems = new Array<T>();
	
	@Override
	public void awake() {
		if (fontResource == null) {
			fontResource = new BitmapFontResource(JLabel.defaultFontResource);
		}

		actorComponent = new ActorComponent();
		createList();
		actorComponent.setActor(scrollPane);
		addNativeComponent(actorComponent);
	}

	private void createList() {
		Skin skin = getGameObject().getComponentInParent(JCanvas.class).getSkin();
		ListStyle style = new ListStyle(skin.get("default", ListStyle.class));

		int realSize = (int) (((getTransform().getScale().x + getTransform().getScale().y) / 2) * fontSize);
		Gdx.app.log("JLabel", "font size: " + realSize);

		SmartFontGenerator fontGen = new SmartFontGenerator();
		FileHandle exoFile = Gdx.files.internal(fontResource.getResourcePath());
		BitmapFont fontBitmap = fontGen.createFont(exoFile, fontResource.getResourcePath() + realSize, realSize);

		if (fontBitmap != null) {
			style.font = fontBitmap;
		}
		if (fontColorSelected != null) {
			style.fontColorSelected = fontColorSelected;
		}
		if (fontColorSelected != null) {
			style.fontColorUnselected = fontColorUnselected;
		}

		if (selection != null) {
			style.selection = DrawableHelper.getDrawableFromAsset(selection.getResourcePath());
		}
		if (background != null) {
			style.background = DrawableHelper.getDrawableFromAsset(background.getResourcePath());
		}

		list = new List<T>(style);
		list.setItems(tempItems);
		list.getSelection().setMultiple(false);
		list.getSelection().setRequired(true);
		if(list.getItems().size > 0){
			list.setSelectedIndex(1);
		}
		
		list.addListener(new EventListener() {
			
			@Override
			public boolean handle(Event event) {
				Gdx.app.log("JList", "event: " + event);;
				
				return false;
			}
		});
		list.addCaptureListener(new EventListener() {
			
			@Override
			public boolean handle(Event event) {
				Gdx.app.log("JList", "capture event: " + event);;
				return false;
			}
		});
		
		scrollPane = new ScrollPane(list, skin);
		scrollPane.setFlickScroll(false);
	}

	@Override
	public void onEnabled() {
		list.setVisible(true);
	}

	@Override
	public void onDisable() {
		list.setVisible(false);
	}

	public void addElement(T elementToAdd) {
		if(list == null){
			tempItems.add(elementToAdd);
			return;
		}
		
		Array<T> items = list.getItems();
		items.add(elementToAdd);
		list.setItems(items);
	}

	/**
	 * Removes the first instance of the specified value in the array.
	 * 
	 * @param elementToRemove
	 *            May be null.
	 * @param useIdentity
	 *            If true, == comparison will be used. If false, .equals()
	 *            comparison will be used.
	 * @return true if value was found and removed, false otherwise
	 */
	public boolean removeElement(T elementToRemove, boolean useIdentity) {
		if(list == null){
			return tempItems.removeValue(elementToRemove, useIdentity);
		}
		
		Array<T> items = list.getItems();
		boolean remove = items.removeValue(elementToRemove, useIdentity);
		list.setItems(items);
		return remove;
	}

	public Array<T> getAllElements() {
		if(list == null){
			return tempItems;
		}
		
		return list.getItems();
	}

	public T getElement(int index) {
		if(list == null){
			return tempItems.get(index);
		}
		
		return list.getItems().get(index);
	}

	public T getSelectedElement() {
		if(list == null){
			return null;
		}
		return list.getSelected();
	}
	
	public void addChangeSelectionItemEvent(){
	}
}
