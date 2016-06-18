package org.javity.engine;

import org.javity.engine.resources.MemorySpriteResource;
import org.javity.engine.resources.SingleSpriteResource;
import org.javity.engine.resources.SpriteAtlasResource;
import org.javity.engine.resources.SpriteResource;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;

import galaxy.rapid.asset.RapidAsset;

public class JResources {

	public static SpriteResource getSprite(String assetPath) {
		if (assetPath.contains("#")) {
			return new SpriteAtlasResource(assetPath);
		} else if(assetPath.contains("%")){
			return new MemorySpriteResource(assetPath);
		}else {
			return new SingleSpriteResource(assetPath);
		}
	}

	public static MemorySpriteResource addMemorySprite(String tileName, Texture texture) {
		RapidAsset.INSTANCE.addMemoryTexture("%" + tileName, new Sprite(texture));
		return new MemorySpriteResource("%" + tileName);
	}

}
