package org.javity.engine.resources;

import org.javity.engine.Resource;

import galaxy.rapid.asset.RapidAsset;

public class MusicResource implements Resource {

	private String resourcePath;
	
	public MusicResource() {
	}
	
	public MusicResource(String resourcePath){
		this.resourcePath = resourcePath;
		RapidAsset.INSTANCE.loadMusic(resourcePath);
	}
	
	@Override
	public String getResourcePath() {
		return resourcePath;
	}

}
