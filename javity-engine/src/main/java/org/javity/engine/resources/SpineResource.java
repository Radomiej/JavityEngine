package org.javity.engine.resources;

import org.javity.engine.Resource;

public class SpineResource implements Resource {

	private String resourcePath;

	public SpineResource() {
	}

	public SpineResource(String resourcePath){
		this.resourcePath = resourcePath;
	}

	@Override
	public String getResourcePath() {
		return resourcePath;
	}

}
