package galaxy.rapid.render;

import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;

import galaxy.rapid.asset.RapidAsset;
import galaxy.rapid.camera.RapidCamera;
import galaxy.rapid.components.PositionComponent;
import galaxy.rapid.components.SpriteComponent;
import galaxy.rapid.components.TextComponent;
import galaxy.rapid.configuration.RapidConfiguration;

public enum TextRenderer implements Renderer {
	INSTANCE;

	private RapidCamera camera = new RapidCamera(new OrthographicCamera(640, 480));
	private RapidCamera mainCamera;
	private Matrix4 oldProjMatrix;
	
	public void prepareCamera(RapidCamera mainCamera){
		this.mainCamera = mainCamera;
		oldProjMatrix = mainCamera.getCombined();
		camera.setViewport(mainCamera.getViewport());
	}
	
	public void render(Entity e, Batch batch) {
		ComponentMapper<TextComponent> textMapper = (ComponentMapper<TextComponent>) ComponentMapper.getFor(TextComponent.class, e.getWorld());
		ComponentMapper<PositionComponent> positionMapper = (ComponentMapper<PositionComponent>) ComponentMapper.getFor(PositionComponent.class,
				e.getWorld());

		// BodyComponent body = bodyMapper.get(e);
		PositionComponent position = positionMapper.get(e);

		if (position == null) {
			Gdx.app.error("SpriteRenderer", "PositionComponent doesn`t exist!");
			return;
		}

		TextComponent  textComponent = textMapper.get(e);
		String textToDraw = textComponent.getText();
		String assetName = textComponent.getBitmapAsset();
		BitmapFont bitmapFont = RapidAsset.INSTANCE.getBitmapFont(assetName);
		

		if (bitmapFont == null) {
			Gdx.app.error("TextRenderer", "Couldn`t load bitmap font: " + assetName);
			return;
		}

		
		float sizeX = bitmapFont.getData().spaceXadvance * textToDraw.length();
		float sizeY = bitmapFont.getLineHeight();
		
		float posX = position.getPosition().x - sizeX / 2 + mainCamera.getPosition().x;
		float posY = position.getPosition().y - sizeY / 2 + mainCamera.getPosition().y;

		camera.setPosition(posX, posY);
		camera.setRotation(mainCamera.getRotation() + position.getRotation());
		Vector2 reverseScale = new Vector2(1 / position.getScale().x, 1 / position.getScale().y);
		camera.setViewport(mainCamera.getViewport().scl(reverseScale));
		camera.update();
		
		batch.setProjectionMatrix(mainCamera.getCombined());
		bitmapFont.setColor(textComponent.getColor());
		bitmapFont.draw(batch, textToDraw, position.getPosition().x - sizeX / 2, position.getPosition().y + sizeY / 4);
		batch.setProjectionMatrix(oldProjMatrix);
		
	}

}
