package org.javity.components;

import org.javity.engine.NativeComponent;
import org.javity.engine.Resource;
import org.javity.engine.resources.SpriteAtlasResource;
import org.javity.engine.resources.SpriteResource;
import org.javity.engine.resources.SingleSpriteResource;

import galaxy.rapid.asset.RapidAsset;
import galaxy.rapid.components.RenderComponent;
import galaxy.rapid.components.SpriteComponent;

public class SpriteRenderer extends NativeComponent {

	private SpriteResource sprite;
	private transient RenderComponent renderComponent;
	private transient SpriteComponent spriteComponent;

	public SpriteRenderer() {
	}

	public SpriteRenderer(String sprite) {
		if (sprite.contains("#")) {
			this.sprite = new SpriteAtlasResource(sprite);
		} else {
			this.sprite = new SingleSpriteResource(sprite);
		}
	}

	public SpriteRenderer(SingleSpriteResource sprite) {
		this.sprite = sprite;
	}

	@Override
	public void awake() {
		renderComponent = new RenderComponent();
		spriteComponent = new SpriteComponent();
		addNativeComponent(renderComponent);
		addNativeComponent(spriteComponent);

		setSprite(sprite);
	}

	public SpriteResource getSprite() {
		return sprite;
	}

	public void setSprite(SpriteResource sprite) {
		this.sprite = sprite;
		if (spriteComponent != null) {
			spriteComponent.setSpriteAsset(sprite.getResourcePath());
			if (sprite instanceof SpriteAtlasResource) {
				spriteComponent.setAtlas(true);
				RapidAsset.INSTANCE.loadTextureAtlas(sprite.getResourcePath().split("#")[0]);
			}else{
				spriteComponent.setAtlas(false);
				RapidAsset.INSTANCE.loadSprite(sprite.getResourcePath());
			}
		}
	}

	public void setSprite(String spritePath) {
		this.sprite = new SingleSpriteResource(spritePath);
		if (spriteComponent != null)
			spriteComponent.setSpriteAsset(sprite.getResourcePath());
	}

	@Override
	public void start() {

	}

	@Override
	public void remove() {
	}

}
