package org.javity.components;

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
		spine = new SpineResource(spinePath);
	}
	
	public SpineRenderer(SpineResource spineResource) {
		spine = spineResource;
	}
	
	@Override
	public void awake() {
		renderComponent = new RenderComponent();
		addNativeComponent(renderComponent);
		

		RapidAsset.INSTANCE.loadSpine(spine.getResourcePath());
		SpineAssetModel spineAssetModel = RapidAsset.INSTANCE.getSpine(spine.getResourcePath(), "bandit");
		spineComponent = new SpineComponent(spineAssetModel);
		addNativeComponent(spineComponent);
	}

	public SpineResource getSpine() {
		return spine;
	}
}
