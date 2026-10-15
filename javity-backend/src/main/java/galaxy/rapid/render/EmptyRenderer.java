package galaxy.rapid.render;

import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Rectangle;

import galaxy.rapid.asset.RapidAsset;
import galaxy.rapid.components.PositionComponent;
import galaxy.rapid.configuration.RapidConfiguration;

public enum EmptyRenderer implements Renderer {
	INSTANCE;
	private com.badlogic.gdx.graphics.glutils.ShapeRenderer shapeRenderer;
	private Rectangle emptyBody = new Rectangle(0, 0, 20, 20);

	private EmptyRenderer() {
		shapeRenderer = new com.badlogic.gdx.graphics.glutils.ShapeRenderer();
	}

	public void render(Entity e, Batch batch) {
		ComponentMapper<PositionComponent> positionMapper = (ComponentMapper<PositionComponent>) ComponentMapper.getFor(PositionComponent.class,
				e.getWorld());

		PositionComponent position = positionMapper.get(e);

		if (position == null) {
			Gdx.app.error("EmptyRenderer", "PositionComponent doesn`t exist!");
			return;
		}

		float sizeX = emptyBody.width;
		float sizeY = emptyBody.height;

		float posX = position.getPosition().x;
		float posY = position.getPosition().y;

		shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
		shapeRenderer.begin(ShapeType.Line);
		shapeRenderer.rect(posX, posY, 0, 0, sizeX, sizeY, 1, 1, 0);
		shapeRenderer.end();
	}

}
