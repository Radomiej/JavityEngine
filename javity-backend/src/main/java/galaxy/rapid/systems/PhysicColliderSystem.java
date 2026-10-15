package galaxy.rapid.systems;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.Component;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.EntityProcessingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.World;
import com.google.common.eventbus.Subscribe;

import galaxy.rapid.common.UuidHelper;
import galaxy.rapid.components.Box2dComponent;
import galaxy.rapid.components.CircleColliderComponent;
import galaxy.rapid.components.ColliderComponent;
import galaxy.rapid.components.PolygonColliderComponent;
import galaxy.rapid.components.PositionComponent;
import galaxy.rapid.components.RectangleColliderComponent;
import galaxy.rapid.event.AddColliderComponent;
import galaxy.rapid.event.RemoveColliderComponent;
import galaxy.rapid.eventbus.RapidBus;
import galaxy.rapid.physic.BodyCreator;
import galaxy.rapid.physic.Box2dFactory;
import galaxy.rapid.physic.FixtureCreatorStrategy;

public class PhysicColliderSystem extends EntityProcessingSystem {

	private Map<ColliderComponent, UUID> colliderToEntityMap = new HashMap<ColliderComponent, UUID>();

	private ComponentMapper<Box2dComponent> box2dMapper;
	private ComponentMapper<PositionComponent> positionMapper;
	private ComponentMapper<RectangleColliderComponent> rectangleColliderMapper;
	private ComponentMapper<CircleColliderComponent> circleColliderMapper;
	private ComponentMapper<PolygonColliderComponent> polygonColliderMapper;

	@Wire
	private RapidBus rapidBus;
	private PhysicSystem physicSystem;

	@SuppressWarnings("unchecked")
	public PhysicColliderSystem() {
		super(Aspect.all(Box2dComponent.class, PositionComponent.class).one(CircleColliderComponent.class,
				RectangleColliderComponent.class, PolygonColliderComponent.class));
	}

	@Override
	protected void initialize() {
		rapidBus.register(this);
	}

	@Override
	public void inserted(Entity e) {
		Box2dComponent box2d = box2dMapper.get(e);
		PositionComponent position = positionMapper.get(e);
		Body body = box2d.getBody();
		Fixture fixture = null;
		if(rectangleColliderMapper.has(e)){
			RectangleColliderComponent rectangleCollider = rectangleColliderMapper.get(e);
			fixture = Box2dFactory.createRectangle(physicSystem.getPhysicWorld(), position,
					rectangleCollider, body);
		}
		if(circleColliderMapper.has(e)){
			CircleColliderComponent circleCollider = circleColliderMapper.get(e);
			fixture = Box2dFactory.createCircle(physicSystem.getPhysicWorld(), position,
					circleCollider, body);
		}
		if(polygonColliderMapper.has(e)){
			PolygonColliderComponent polygonCollider = polygonColliderMapper.get(e);
			fixture = Box2dFactory.createPolygon(physicSystem.getPhysicWorld(), position,
					polygonCollider, body);
		}
		fixture.setUserData(box2d.getFixtureData());
		box2d.setFixture(fixture);
		Gdx.app.debug("PhysicColliderSystem", "Added Fixture");
	}

	@Override
	protected void begin() {
	}

	@Override
	protected void process(Entity e) {

	}

	@Override
	public void removed(Entity e) {
		Box2dComponent box2d = box2dMapper.get(e);
		if(box2d.isRemoved()){
			return;
		}
		
		Fixture removeFixture = box2d.getFixture();
		box2d.getBody().destroyFixture(removeFixture);
		Gdx.app.debug("PhysicColliderSystem", "Remove Fixture");
	}

	@Subscribe
	public void addComponentEvent(AddColliderComponent addComponent) {
		UUID uuid = UuidHelper.getUuidFromEntity(addComponent.entity);
		addComponent.entity.edit().add((Component) addComponent.colliderComponent);
		colliderToEntityMap.put(addComponent.colliderComponent, uuid);
	}

	@Subscribe
	public void removeComponentEvent(RemoveColliderComponent removeComponent) {
		UUID uuid = colliderToEntityMap.get(removeComponent.colliderComponent);
		Entity entity = UuidHelper.getEntityFromUuid(uuid, world);
		if (entity == null) {
			Gdx.app.error("UuidHelper.getEntityFromUuid", "Cannot find entity for this uuid: " + uuid);
			return;
		}
		entity.edit().remove(((Component)removeComponent.colliderComponent).getClass());
		colliderToEntityMap.remove(removeComponent.colliderComponent);
	}
}
