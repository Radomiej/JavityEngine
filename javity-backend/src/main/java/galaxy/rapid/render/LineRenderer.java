package galaxy.rapid.render;

import java.util.List;

import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector2;

import galaxy.rapid.components.PositionComponent;
import galaxy.rapid.components.ShapeComponent;

public enum LineRenderer implements Renderer {
	INSTANCE;
	private com.badlogic.gdx.graphics.glutils.ShapeRenderer shapeRenderer;

	private LineRenderer() {
		shapeRenderer = new com.badlogic.gdx.graphics.glutils.ShapeRenderer();
	}

	public void render(Entity e, Batch batch) {
		ComponentMapper<PositionComponent> positionMapper = (ComponentMapper<PositionComponent>) ComponentMapper.getFor(PositionComponent.class, e.getWorld());
		ComponentMapper<ShapeComponent> shapeMapper = (ComponentMapper<ShapeComponent>) ComponentMapper.getFor(ShapeComponent.class, e.getWorld());
		
		PositionComponent position = positionMapper.get(e);
		ShapeComponent shape = shapeMapper.get(e);
		
		if (position == null) {
			Gdx.app.error("ShapeRenderer", "PositionComponent doesn`t exist!");
			return;
		}
		
		batch.end();
		shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
		shapeRenderer.setTransformMatrix(batch.getTransformMatrix());
		shapeRenderer.updateMatrices();
		shapeRenderer.setColor(shape.getColor());
		shapeRenderer.begin(ShapeType.Filled);
		List<Vector2> positions = shape.getPolygonPoints();
		for(int x = 1; x < positions.size(); x++){
			Vector2 endPosition = positions.get(x);
			shapeRenderer.rectLine(positions.get(x - 1),endPosition , shape.getWidth());
			shapeRenderer.circle(endPosition.x, endPosition.y, shape.getWidth() / 2);
		}
		
		shapeRenderer.setColor(Color.WHITE);
		shapeRenderer.end();
		batch.begin();
		
	}

}
