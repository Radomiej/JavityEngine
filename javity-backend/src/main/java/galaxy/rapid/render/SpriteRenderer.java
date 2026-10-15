package galaxy.rapid.render;

import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.Sprite;

import galaxy.rapid.asset.RapidAsset;
import galaxy.rapid.components.PositionComponent;
import galaxy.rapid.components.RenderComponent;
import galaxy.rapid.components.SpriteComponent;
import galaxy.rapid.configuration.RapidConfiguration;

public enum SpriteRenderer implements Renderer {
	INSTANCE;

	public void render(Entity e, Batch batch) {
		ComponentMapper<SpriteComponent> spriteMapper = (ComponentMapper<SpriteComponent>) ComponentMapper.getFor(SpriteComponent.class, e.getWorld());
		ComponentMapper<PositionComponent> positionMapper = (ComponentMapper<PositionComponent>) ComponentMapper.getFor(PositionComponent.class,
				e.getWorld());
		ComponentMapper<RenderComponent> renderMapper = (ComponentMapper<RenderComponent>) ComponentMapper.getFor(RenderComponent.class, e.getWorld());
		
		// BodyComponent body = bodyMapper.get(e);
		PositionComponent position = positionMapper.get(e);
		RenderComponent render = renderMapper.get(e);

		if (position == null) {
			Gdx.app.error("SpriteRenderer", "PositionComponent doesn`t exist!");
			return;
		}

		SpriteComponent spriteComponent = spriteMapper.get(e);
		
		String assetName = spriteComponent.getSpriteAsset();
		Sprite sprite = null;
		if (spriteComponent.isAtlas() || assetName.contains("#")) {
			sprite = RapidAsset.INSTANCE.getAtlasSprite(assetName);
		} else if(spriteComponent.isMemory() || assetName.contains("%")){
			sprite = RapidAsset.INSTANCE.getMemorySprite(assetName);
		}else {
			sprite = RapidAsset.INSTANCE.getSprite(assetName);
		}

		if (sprite == null) {
			Gdx.app.error("SpriteRender", "Couldn`t load sprite: " + assetName);
			return;
		}

		sprite.setScale(position.getScale().x, position.getScale().y);
		

		float posX = position.getPosition().x;
		float posY = position.getPosition().y;

		float sizeX = sprite.getWidth() ;
		float sizeY = sprite.getHeight() ;
		
		if(spriteComponent.isTop()){
			posY += (sizeY / 2) * position.getScale().y ;
		}else if(spriteComponent.isBottom()){
			posY -= (sizeY / 2) * position.getScale().y;
		}
		if(spriteComponent.isLeft()){
			posX -= sizeX / 2 * position.getScale().x;
		}else if(spriteComponent.isRight()){
			posX += sizeX / 2 * position.getScale().x;
		}
		// System.out.println("GR: " +
		// RapidConfiguration.INSTANCE.getGameRatio());
		//
		// System.out.println("position: " + position);
		// System.out.println("posX: " + posX + " posY: " + posY);
		// System.out.println("sizeX: " + sizeX + " sizeY: " + sizeY);
		// System.out.println("originX: " + originX + " originX: " + originX);
//		sprite.setPosition(posX, posY);
		// sprite.setSize(sizeX, sizeY);
		
		sprite.setOrigin(sprite.getWidth() / 2, sprite.getHeight() / 2);
		sprite.setRotation(position.getRotation());
		sprite.setCenter(posX, posY);
		Color old = sprite.getColor();
		sprite.setColor(render.getColor());
		sprite.draw(batch);
		sprite.setColor(old);
	}

}
