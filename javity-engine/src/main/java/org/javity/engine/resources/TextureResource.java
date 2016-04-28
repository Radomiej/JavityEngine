package org.javity.engine.resources;

import org.javity.engine.Resource;

public class TextureResource implements Resource {

	private String resourcePath;

	public TextureResource() {
	}

	public TextureResource(String resourcePath){
		this.resourcePath = resourcePath;
	}

	@Override
	public String getResourcePath() {
		return resourcePath;
	}

}
