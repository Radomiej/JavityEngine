package org.javity.components;

import org.javity.engine.NativeComponent;
import org.javity.engine.Resource;
import org.javity.engine.resources.SpriteAtlasResource;
import org.javity.engine.resources.SpritePivot;
import org.javity.engine.resources.SpriteResource;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Sprite;

import org.javity.engine.resources.MemorySpriteResource;
import org.javity.engine.resources.SingleSpriteResource;

import galaxy.rapid.asset.RapidAsset;
import galaxy.rapid.components.RenderComponent;
import galaxy.rapid.components.SpriteComponent;

public class SpriteRenderer extends NativeComponent {

	private SpriteResource sprite;
	private transient RenderComponent renderComponent;
	private transient SpriteComponent spriteComponent;
	private SpritePivot pivot = SpritePivot.CENTER;
	private Color color = Color.WHITE;
	
	public SpriteRenderer() {
	}

	public SpriteRenderer(String sprite, SpritePivot pivot) {
		this.setPivot(pivot);
		
		if (sprite.contains("#")) {
			this.sprite = new SpriteAtlasResource(sprite);
		} else if (sprite.contains("%")) {
			this.sprite = new MemorySpriteResource(sprite);
		} else {
			this.sprite = new SingleSpriteResource(sprite);
		}
	}
	
	public SpriteRenderer(String sprite) {
		this(sprite, SpritePivot.CENTER);
	}

	public SpriteRenderer(SpriteResource sprite) {
		this(sprite, SpritePivot.CENTER);
	}
	
	public SpriteRenderer(SpriteResource sprite,  SpritePivot pivot) {
		this.setPivot(pivot);
		this.sprite = sprite;
	}

	public SpriteRenderer(String sprite, Color color) {
		this(sprite);
		this.setColor(color);
	}

	@Override
	public void awake() {
		renderComponent = new RenderComponent();
		spriteComponent = new SpriteComponent();
		prepareSpriteComponent(spriteComponent);
		addNativeComponent(renderComponent);
		addNativeComponent(spriteComponent);

		renderComponent.setColor(getColor());
		setSprite(sprite);
	}

	private void prepareSpriteComponent(SpriteComponent sprite) {
		if(getPivot() == SpritePivot.CENTER) return;
		
		if(getPivot() == SpritePivot.LEFT || getPivot() == SpritePivot.LEFT_BOTTOM || getPivot() == SpritePivot.LEFT_TOP){
			sprite.setLeft(true);
		}else if(getPivot() == SpritePivot.RIGHT || getPivot() == SpritePivot.RIGHT_BOTTOM || getPivot() == SpritePivot.RIGHT_TOP){
			sprite.setRight(true);
		}
		
		if(getPivot() == SpritePivot.TOP || getPivot() == SpritePivot.LEFT_TOP || getPivot() == SpritePivot.RIGHT_TOP){
			sprite.setTop(true);
		}else if(getPivot() == SpritePivot.BOTTOM || getPivot() == SpritePivot.RIGHT_BOTTOM || getPivot() == SpritePivot.LEFT_BOTTOM){
			sprite.setBottom(true);
		}
	}

	@Override
	public void update() {
		renderComponent.setOrderZ(getTransform().getOrderZ());
	}
	
	public SpriteResource getSprite() {
		return sprite;
	}

	public void setSprite(SpriteResource sprite) {
		this.sprite = sprite;
		if (spriteComponent != null) {
			spriteComponent.setAtlas(false);
			spriteComponent.setMemory(false);
			
			spriteComponent.setSpriteAsset(sprite.getResourcePath());
			if (sprite instanceof SpriteAtlasResource) {
				spriteComponent.setAtlas(true);
				RapidAsset.INSTANCE.loadTextureAtlas(sprite.getResourcePath().split("#")[0]);
			}else if(sprite instanceof MemorySpriteResource){
				spriteComponent.setMemory(true);
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

	@Override
	public void onEnabled() {
		renderComponent.setRender(true);
	}
	
	@Override
	public void onDisable() {
		renderComponent.setRender(false);
	}

	public SpritePivot getPivot() {
		return pivot;
	}

	public void setPivot(SpritePivot pivot) {
		this.pivot = pivot;
		if(spriteComponent != null) prepareSpriteComponent(spriteComponent);
	}

	public float getSpriteWidth() {
		Sprite spriteImg = RapidAsset.INSTANCE.getSprite(sprite.getResourcePath());
		return spriteImg.getWidth();
	}

	public float getSpriteHeight() {
		Sprite spriteImg = RapidAsset.INSTANCE.getSprite(sprite.getResourcePath());
		return spriteImg.getHeight();
	}

	public Color getColor() {
		return color;
	}

	public void setColor(Color color) {
		this.color = color;
		if(renderComponent != null) renderComponent.setColor(getColor());
	}
}
