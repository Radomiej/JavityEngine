package org.javity.engine;

import org.javity.engine.resources.SingleSpriteResource;
import org.javity.engine.resources.SpriteAtlasResource;
import org.javity.engine.resources.SpriteResource;

public class JResources {

	public static SpriteResource getSprite(String assetPath) {
		if (assetPath.contains("#")) {
			return new SpriteAtlasResource(assetPath);
		} else {
			return new SingleSpriteResource(assetPath);
		}
	}

}
