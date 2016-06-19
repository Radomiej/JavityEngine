package org.javity.components;

import org.javity.engine.JTime;
import org.javity.engine.NativeComponent;
import org.javity.engine.resources.SpineResource;
import org.javity.engine.resources.SpriteAtlasResource;
import org.javity.engine.resources.SpriteResource;

import galaxy.rapid.asset.RapidAsset;
import galaxy.rapid.asset.SpineAssetModel;
import galaxy.rapid.components.RenderComponent;
import galaxy.rapid.components.SpineComponent;
import galaxy.rapid.components.SpriteComponent;

public class SpineRenderer extends NativeComponent {
	private SpineResource spine;
	private transient RenderComponent renderComponent;
	private transient SpineComponent spineComponent;
	
	public SpineRenderer() {
	}
	
	public SpineRenderer(String spinePath) {
		String[] spinePathParts = spinePath.split("#");
		spine = new SpineResource(spinePathParts[0], spinePathParts[1] != null ? spinePathParts[1] : "default");
	}
	
	public SpineRenderer(SpineResource spineResource) {
		spine = spineResource;
	}
	
	@Override
	public void awake() {
		renderComponent = new RenderComponent();
		addNativeComponent(renderComponent);
		

		RapidAsset.INSTANCE.loadSpine(spine.getResourcePath());
		SpineAssetModel spineAssetModel = RapidAsset.INSTANCE.getSpine(spine.getResourcePath(), spine.getSkinName());
		spineComponent = new SpineComponent(spineAssetModel);
		addNativeComponent(spineComponent);
	}

	public void setAnimation(String animationName, boolean loop){
		spineComponent.getAnimationState().setAnimation(0, animationName, loop);
	}
	
	public void setAnimation(int index, String animationName, boolean loop){
		spineComponent.getAnimationState().setAnimation(index, animationName, loop);
	}
	
	public void addAnimation(int index, String animationName, boolean loop, float delay){
		spineComponent.getAnimationState().addAnimation(index, animationName, loop, delay);
	}
	
	public SpineResource getSpine() {
		return spine;
	}
}
