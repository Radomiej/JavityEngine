package galaxy.rapid.render;

import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonRenderer;

import galaxy.rapid.camera.RapidCamera;
import galaxy.rapid.components.PositionComponent;
import galaxy.rapid.components.RenderComponent;
import galaxy.rapid.components.SpineComponent;
import galaxy.rapid.configuration.RapidConfiguration;

public enum SpineRenderer implements Renderer {
	INSTANCE;

	private SkeletonRenderer<PolygonSpriteBatch> renderer;
	private RapidCamera camera = new RapidCamera(new OrthographicCamera(640, 480));
	private RapidCamera mainCamera;
	private Matrix4 oldProjMatrix;
	
	private SpineRenderer() {
		renderer = new SkeletonRenderer<PolygonSpriteBatch>();
		renderer.setPremultipliedAlpha(true);
	}
	
	public void prepareCamera(RapidCamera mainCamera){
		this.mainCamera = mainCamera;
		oldProjMatrix = mainCamera.getCombined();
		camera.setViewport(mainCamera.getViewport());
	}
	
	public void render(Entity e, Batch batch) {
		ComponentMapper<RenderComponent> renderMapper = (ComponentMapper<RenderComponent>) ComponentMapper
				.getFor(RenderComponent.class, e.getWorld());
		ComponentMapper<SpineComponent> spineMapper = (ComponentMapper<SpineComponent>) ComponentMapper
				.getFor(SpineComponent.class, e.getWorld());
		ComponentMapper<PositionComponent> positionMapper = (ComponentMapper<PositionComponent>) ComponentMapper
				.getFor(PositionComponent.class, e.getWorld());

		PositionComponent position = positionMapper.get(e);
		RenderComponent render = renderMapper.get(e);
		SpineComponent spine = spineMapper.get(e);
		AnimationState state = spine.getAnimationState();
		Skeleton skeleton = spine.getSkeleton();

		skeleton.setFlipX(render.isFlipX());
		skeleton.setFlipY(render.isFlipY());

		float posX = position.getPosition().x + spine.getOffset().x;
		float posY = position.getPosition().y + spine.getOffset().y;

		skeleton.setPosition(posX, posY);
		skeleton.getRootBone().setRotation(position.getRotation());
		skeleton.getRootBone().setScale(position.getScale().x, position.getScale().y);
		skeleton.getColor().a = 1;
		state.update(e.getWorld().getDelta());
		state.apply(skeleton);
		skeleton.updateWorldTransform();

		
		int blendDst = batch.getBlendDstFunc();
		int blendSrc = batch.getBlendSrcFunc();
		
		batch.setProjectionMatrix(mainCamera.getCombined());
		renderer.draw((PolygonSpriteBatch) batch, skeleton);

		batch.setBlendFunction(blendSrc, blendDst);
		batch.setProjectionMatrix(oldProjMatrix);
	}

}
