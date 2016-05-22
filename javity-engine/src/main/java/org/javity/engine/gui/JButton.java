package org.javity.engine.gui;

import org.javity.engine.GUIComponent;
import org.javity.engine.GameObject;
import org.javity.engine.gui.remote.RemoteInvoker;
import org.javity.engine.resources.SpriteResource;
import org.javity.engine.resources.TextureResource;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

import galaxy.rapid.common.DrawableHelper;
import galaxy.rapid.components.ActorComponent;

public class JButton extends GUIComponent {

	private transient ActorComponent actorComponent;
	private transient Button button;
	public float sizeX, sizeY;
	public SpriteResource up, down, checked;
	
	public GameObject clickTarget;
	public RemoteInvoker clickInvoke;
	
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
		button.addCaptureListener(new ClickListener(){
			@Override
			public void clicked(InputEvent event, float x, float y) {
				if(clickTarget != null && clickInvoke != null){
					clickInvoke.invoke(clickTarget);
				}
			}
		});
	}

	public void addClickListener(ClickListener clickListener) {
		
	}

}
