package org.javity.engine.resources;

import org.javity.engine.Resource;

import galaxy.rapid.asset.RapidAsset;

public class SoundResource implements Resource {

	private String resourcePath;
	
	public SoundResource() {
	}
	
	public SoundResource(String resourcePath){
		this.resourcePath = resourcePath;
		RapidAsset.INSTANCE.loadSound(resourcePath);
	}
	
	@Override
	public String getResourcePath() {
		return resourcePath;
	}

}
