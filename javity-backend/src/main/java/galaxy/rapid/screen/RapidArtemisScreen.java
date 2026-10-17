package galaxy.rapid.screen;

import com.artemis.WorldConfiguration;
import com.artemis.managers.GroupManager;
import com.artemis.managers.UuidEntityManager;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.World;

import galaxy.rapid.camera.RapidCamera;
import galaxy.rapid.common.EntityEngine;
import galaxy.rapid.eventbus.RapidBus;
import galaxy.rapid.managers.RenderableManager;
import galaxy.rapid.systems.BeforePhysicSystem;
import galaxy.rapid.systems.DeleteEventSystem;
import galaxy.rapid.systems.PhysicColliderSystem;
import galaxy.rapid.systems.PhysicSystem;
import galaxy.rapid.systems.RenderSystem;
import galaxy.rapid.systems.TickEventSystem;

public abstract class RapidArtemisScreen extends RapidScreen {

	public static final float MIN_DELTA = 1 / 15f;

	protected EntityEngine world;
	protected World physicWorld;
	protected InputMultiplexer inputMultiplexer;
	protected RapidCamera camera;
	protected PolygonSpriteBatch spriteBatch;
	protected RapidBus rapidBus;

	public void render(float delta) {
		world.setDelta(MathUtils.clamp(delta, 0, MIN_DELTA));
		world.process();
	}

	public void dispose() {
		world.dispose();
		physicWorld.dispose();
		spriteBatch.dispose();
		rapidBus.unregister(this);
	}

	@Override
	protected void create() {
		
		rapidBus = new RapidBus();
		
		float w = Gdx.graphics.getWidth();
		float h = Gdx.graphics.getHeight();

		OrthographicCamera cameraRaw = new OrthographicCamera(w, w * (h / w));
		cameraRaw.position.set(cameraRaw.viewportWidth / 2f, cameraRaw.viewportHeight / 2f, 0);
		cameraRaw.update();
		camera = new RapidCamera(cameraRaw);
		
		inputMultiplexer = (InputMultiplexer) Gdx.input.getInputProcessor();
		spriteBatch = new PolygonSpriteBatch();

		physicWorld = new World(new Vector2(0, -10), true);
		WorldConfiguration worldConfiguration = new WorldConfiguration();	
		
		worldConfiguration.setSystem(UuidEntityManager.class);
		worldConfiguration.setSystem(GroupManager.class);	
		worldConfiguration.setSystem(RenderableManager.class);	
		worldConfiguration.setSystem(TickEventSystem.class);
		worldConfiguration.setSystem(DeleteEventSystem.class);
		processBeforePhysicWorldConfiguration(worldConfiguration);
		worldConfiguration.setSystem(BeforePhysicSystem.class);
		worldConfiguration.setSystem(PhysicSystem.class);
		worldConfiguration.setSystem(PhysicColliderSystem.class);
		processBeforeRenderWorldConfiguration(worldConfiguration);
		worldConfiguration.setSystem(new RenderSystem());
		worldConfiguration.register(physicWorld);
		worldConfiguration.register(rapidBus);	
		worldConfiguration.register(camera);
		worldConfiguration.register(inputMultiplexer);
		worldConfiguration.register(spriteBatch);
			
		processWorldConfiguration(worldConfiguration);
		
		world = new EntityEngine(worldConfiguration);
		injectWorld(world);
	}	

	protected void processBeforeRenderWorldConfiguration(WorldConfiguration worldConfiguration) {
	}

	protected void processBeforePhysicWorldConfiguration(WorldConfiguration worldConfiguration) {
	}

	protected abstract void processWorldConfiguration(WorldConfiguration worldConfiguration);

	protected abstract void injectWorld(EntityEngine world);

}
