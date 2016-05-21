package org.javity.engine.resources;

import org.javity.engine.Resource;

public class BitmapFontResource implements Resource {

	private String resourcePath;

	public BitmapFontResource() {
	}

	public BitmapFontResource(String resourcePath){
		this.resourcePath = resourcePath;
	}

	@Override
	public String getResourcePath() {
		return resourcePath;
	}

}
