package org.javity.engine.resources;

import org.javity.engine.Resource;

import galaxy.rapid.asset.RapidAsset;

public class BitmapFontResource implements Resource {

	private String resourcePath;

	public BitmapFontResource() {
	}

	public BitmapFontResource(String resourcePath){
		this.resourcePath = resourcePath;
		RapidAsset.INSTANCE.loadBitmapFont(resourcePath);
	}

	@Override
	public String getResourcePath() {
		return resourcePath;
	}

}
