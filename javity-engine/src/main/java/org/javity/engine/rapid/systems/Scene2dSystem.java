package org.javity.engine.rapid.systems;

import org.javity.engine.rapid.systems.scene2d.PostGuiRenderEvent;
import org.javity.engine.rapid.systems.scene2d.PreGuiRenderEvent;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.EntityProcessingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.scenes.scene2d.Event;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;

import galaxy.rapid.RapidEngine;
import galaxy.rapid.components.ActorComponent;
import galaxy.rapid.components.Box2dComponent;
import galaxy.rapid.components.PositionComponent;
import galaxy.rapid.eventbus.RapidBus;

public class Scene2dSystem extends EntityProcessingSystem {

	private final PreGuiRenderEvent preGuiRenderEvent = new PreGuiRenderEvent();
	private final PostGuiRenderEvent postGuiRenderEvent = new PostGuiRenderEvent();
	
	private Stage stage;
	private ComponentMapper<ActorComponent> actorMapper;
	private ComponentMapper<PositionComponent> positionMapper;
	private boolean handleInput;

	@Wire
	private RapidBus rapidBus;

	public Scene2dSystem() {
		super(Aspect.all(ActorComponent.class));
	}

	@Override
	protected void initialize() {
		stage = new Stage();
		InputMultiplexer inputMultiplexer = (InputMultiplexer) Gdx.input.getInputProcessor();
		inputMultiplexer.addProcessor(stage);
	}

	@Override
	public void inserted(Entity e) {
		ActorComponent actorComponent = actorMapper.get(e);
		stage.addActor(actorComponent.getActor());
	}

	@Override
	public void removed(Entity e) {
		ActorComponent actorComponent = actorMapper.get(e);
		actorComponent.getActor().remove();
	}

	@Override
	protected void begin() {
		rapidBus.post(preGuiRenderEvent);
		// stage.act(getWorld().getDelta());
	}

	@Override
	protected void process(Entity e) {
		ActorComponent actorComponent = actorMapper.get(e);

		if (positionMapper.has(e)) {
			PositionComponent positionComponent = positionMapper.get(e);
			actorComponent.getActor().setPosition(positionComponent.getPosition().x, positionComponent.getPosition().y);
		}
	}

	@Override
	protected void end() {
		stage.draw();
		rapidBus.post(postGuiRenderEvent);
		
		/**
		 * Reset handleInput in this pleace becouse GUI system is the last
		 * logical system that should handle to this params and 
		 */
		handleInput = false;
	}

	/**
	 * Native LibGDX API
	 * 
	 * @return Scene2d Stage Object
	 */
	public Stage getStage() {
		return stage;
	}

	/**
	 * 
	 * @return true if any actor handle input from current frame.
	 */
	public boolean isHandleInput() {
		return handleInput;
	}

	/**
	 * Set handle input for this frame
	 * 
	 * @param b
	 *            input occured on actor this stage
	 */
	public void setHandleInput(boolean b) {
		handleInput = b;
	}

	@Override
	protected void dispose() {
		InputMultiplexer inputMultiplexer = (InputMultiplexer) Gdx.input.getInputProcessor();
		inputMultiplexer.removeProcessor(stage);
	}

}
