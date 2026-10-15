package galaxy.rapid.systems;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.EntityProcessingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.World;
import com.google.common.eventbus.Subscribe;

import galaxy.rapid.common.UuidHelper;
import galaxy.rapid.components.Box2dComponent;
import galaxy.rapid.components.PositionComponent;
import galaxy.rapid.components.RectangleColliderComponent;
import galaxy.rapid.event.RemoveBox2dComponentEvent;
import galaxy.rapid.event.RemoveColliderComponent;
import galaxy.rapid.eventbus.RapidBus;
import galaxy.rapid.physic.BodyCreator;

public class BeforePhysicSystem extends EntityProcessingSystem {

	private Collection<Box2dComponent> componentsToRemove = new ArrayList<Box2dComponent>();
	private ComponentMapper<Box2dComponent> box2dMapper;
	private ComponentMapper<PositionComponent> positionMapper;

	@Wire
	private World physicWorld;
	@Wire
	private RapidBus rapidBus;

	@SuppressWarnings("unchecked")
	public BeforePhysicSystem() {
		super(Aspect.all(Box2dComponent.class, PositionComponent.class));
	}

	@Override
	protected void initialize() {
		rapidBus.register(this);
	}

	@Override
	protected void begin() {
	}

	@Override
	protected void process(Entity e) {
		Box2dComponent box2d = box2dMapper.get(e);
		PositionComponent position = positionMapper.get(e);

		Body body = box2d.getBody();
		if(!body.getPosition().equals(position.getPosition()) || (body.getAngle() * MathUtils.radiansToDegrees != position.getRotation()))
		{
			body.setTransform(position.getPosition(), position.getRotation() * MathUtils.degreesToRadians);
		}
		position.setPosition(box2d.getBody().getPosition());
		position.setRotation(box2d.getBody().getAngle() * MathUtils.radiansToDegrees);
	}

	@Override
	public void removed(Entity e) {
		Gdx.app.log("PhysicRectangleColliderSystem", "Remove Fixture Event");
	}

	public World getPhysicWorld() {
		return physicWorld;
	}

	@Subscribe()
	public void removeComponentEvent(RemoveBox2dComponentEvent removeComponent) {
		componentsToRemove.add(removeComponent.box2dComponent);
		Gdx.app.log("PhysicSystem", "Remove Body");
	}
}
