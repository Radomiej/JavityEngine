package org.javity.engine.resources;

import org.javity.engine.Resource;

import galaxy.rapid.asset.RapidAsset;

public class SingleSpriteResource implements SpriteResource{

	private String resourcePath;
	
	public SingleSpriteResource() {
	}
	
	public SingleSpriteResource(String resourcePath){
		this.resourcePath = resourcePath;
		RapidAsset.INSTANCE.loadSprite(resourcePath);
	}
	
	@Override
	public String getResourcePath() {
		return resourcePath;
	}

}
