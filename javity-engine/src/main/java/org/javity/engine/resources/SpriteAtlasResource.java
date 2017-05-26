package org.javity.engine.resources;

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
		RapidAsset.INSTANCE.loadTextureAtlas(atlasPath);
	}
	
	@Override
	public String getResourcePath() {
		return resourcePath;
	}

}
