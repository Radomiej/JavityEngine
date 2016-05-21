package org.javity.engine.rapid.systems;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.systems.EntityProcessingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.scenes.scene2d.Stage;

import galaxy.rapid.RapidEngine;
import galaxy.rapid.components.ActorComponent;
import galaxy.rapid.components.Box2dComponent;
import galaxy.rapid.components.PositionComponent;

public class Scene2dSystem extends EntityProcessingSystem {

	private Stage stage;
	private ComponentMapper<ActorComponent> actorMapper;
	private ComponentMapper<PositionComponent> positionMapper;

	public Scene2dSystem() {
		super(Aspect.all(ActorComponent.class, PositionComponent.class));
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
		stage.act(getWorld().getDelta());
	}

	@Override
	protected void process(Entity e) {
		PositionComponent positionComponent = positionMapper.get(e);
		ActorComponent actorComponent = actorMapper.get(e);
		
		actorComponent.getActor().setPosition(positionComponent.getPosition().x, positionComponent.getPosition().y);
	}

	@Override
	protected void end() {
		stage.draw();
	}

}
