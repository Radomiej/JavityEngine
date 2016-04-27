package org.javity.engine.resources;

import org.javity.engine.Resource;

import galaxy.rapid.asset.RapidAsset;

public class SpriteAtlasResource implements SpriteResource{
	
	private String resourcePath;
	private String atlasPath;
	private String regionName;
	
	public SpriteAtlasResource() {
	}
	
	public SpriteAtlasResource(String resourcePath){
		this.resourcePath = resourcePath;
		String[] path = resourcePath.split("#");
		atlasPath = path[0];
		regionName = path[1];
	}
	
	@Override
	public String getResourcePath() {
		return resourcePath;
	}

}
