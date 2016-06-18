package org.javity.engine.resources;


public class MemorySpriteResource implements SpriteResource{

	private String resourcePath;
	
	public MemorySpriteResource() {
	}
	
	public MemorySpriteResource(String resourcePath){
		this.resourcePath = resourcePath;
	}
	
	@Override
	public String getResourcePath() {
		return resourcePath;
	}

}
