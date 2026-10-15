package galaxy.rapid.render;

import com.artemis.Entity;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;

public interface Renderer {

	public void render(Entity e, Batch batch);

}
