package org.javity.engine.gui;

import org.javity.engine.GUIComponent;
import org.javity.engine.JGameObjectImpl;
import org.javity.engine.gui.remote.RemoteInvoker;
import org.javity.engine.resources.SpriteResource;
import org.javity.engine.resources.TextureResource;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton.ImageButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

import galaxy.rapid.common.DrawableHelper;
import galaxy.rapid.components.ActorComponent;

public class JButton extends GUIComponent {

	private transient ActorComponent actorComponent;
	private transient Button button;
	public float sizeX, sizeY;
	public SpriteResource up, down, checked;
	
	public JGameObjectImpl clickTarget;
	public RemoteInvoker clickInvoke;
	
	@Override
	public void awake() {
		actorComponent = new ActorComponent();
		createButton();
		actorComponent.setActor(button);
		addNativeComponent(actorComponent);
	}

	private void createButton() {
		
		Skin skin = getGameObject().getComponentInParent(JCanvas.class).getSkin();
		ButtonStyle style = new ImageButtonStyle(skin.get("default", ButtonStyle.class));

		if (up != null) {
			style.up = DrawableHelper.getDrawableFromAsset(up.getResourcePath());
		}
		if (down != null) {
			style.down = DrawableHelper.getDrawableFromAsset(down.getResourcePath());
		}
		if (checked != null) {
			style.checked = DrawableHelper.getDrawableFromAsset(checked.getResourcePath());
		}
		
		button = new Button(style);
		button.setScale(getTransform().getScale().x, getTransform().getScale().y);
		button.addCaptureListener(new ClickListener(){
			@Override
			public void clicked(InputEvent event, float x, float y) {
				if(clickTarget != null && clickInvoke != null){
					clickInvoke.invoke(clickTarget);
				}
			}
		});
	}

	@Override
	public void onEnabled() {
		button.setVisible(true);
	}
	
	@Override
	public void onDisable() {
		button.setVisible(false);
	}
	
	
	public void addClickListener(ClickListener clickListener) {
		
	}

}
