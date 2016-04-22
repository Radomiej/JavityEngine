package org.javity.components;

import org.javity.engine.NativeComponent;

import galaxy.rapid.components.RenderComponent;
import galaxy.rapid.components.SpriteComponent;

public class SpriteRenderer extends NativeComponent {

	private String sprite;
	
	private transient RenderComponent renderComponent;
	private transient SpriteComponent spriteComponent;
	
	public SpriteRenderer() {
	}
	
	public SpriteRenderer(String sprite){
		this.sprite = sprite;
	}
	
	@Override
	public void awake() {
		renderComponent = new RenderComponent();
		spriteComponent = new SpriteComponent();
		addNativeComponent(renderComponent);
		addNativeComponent(spriteComponent);
		
		spriteComponent.setSpriteAsset(sprite);
	}
	
	public String getSprite() {
		return sprite;
	}

	public void setSprite(String sprite) {
		this.sprite = sprite;
		if(spriteComponent != null) spriteComponent.setSpriteAsset(sprite);
	}
	
	
	@Override
	public void start() {
		
	}
	
	@Override
	public void remove() {
	}

}
