package org.javity.engine.resources;

import org.javity.engine.Resource;

public class SpineResource implements Resource {

	private String resourceJsonPath;
	private String skinName;

	public SpineResource() {
	}

	public SpineResource(String resourceJsonPath, String skinName) {
		this.resourceJsonPath = resourceJsonPath;
		this.skinName = skinName;
	}

	@Override
	public String getResourcePath() {
		return resourceJsonPath;
	}

	public String getSkinName() {
		return skinName;
	}
}
