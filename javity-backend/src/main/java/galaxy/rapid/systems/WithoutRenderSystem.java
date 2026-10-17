package galaxy.rapid.systems;

import com.artemis.Aspect;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.EntityProcessingSystem;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import galaxy.rapid.components.RenderComponent;
import galaxy.rapid.render.EmptyRenderer;

public class WithoutRenderSystem extends EntityProcessingSystem {

	
	@Wire
	private SpriteBatch spriteBatch;	
	
	@SuppressWarnings("unchecked")
	public WithoutRenderSystem() {
		super(Aspect.exclude(RenderComponent.class));
	}
	
	@Override
	protected void initialize() {
	}
	
	@Override
	protected void begin() {
		spriteBatch.begin();
	}
	@Override
	protected void process(Entity e) {		
		EmptyRenderer.INSTANCE.render(e, spriteBatch);
	}
	
	@Override
	protected void end() {
		spriteBatch.end();
	}

}
