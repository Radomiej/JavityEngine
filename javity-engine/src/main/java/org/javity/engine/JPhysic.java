package org.javity.engine;

import java.util.ArrayList;
import java.util.List;

import org.javity.engine.physic.RaycastHit;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.QueryCallback;
import com.badlogic.gdx.physics.box2d.RayCastCallback;
import com.badlogic.gdx.physics.box2d.World;

import galaxy.rapid.event.PhysicDebugEnterChangeEvent;
import galaxy.rapid.eventbus.RapidBus;

public class JPhysic {

	private static JPhysic physic;

	static void setPhysic(JPhysic newPhysic) {
		physic = newPhysic;
	}

	private World physicWorld;
	private RapidBus eventBus;

	public JPhysic(World physicWorld, RapidBus eventBus) {
		this.eventBus = eventBus;
		this.physicWorld = physicWorld;
	}

	public static void setGravity(Vector2 gravityVector) {
		physic.physicWorld.setGravity(gravityVector);
	}

	public static void setDebugRender(boolean debugRender) {
		physic.eventBus.post(new PhysicDebugEnterChangeEvent(debugRender));
	}

	public static List<RaycastHit> raycastPoint(final Vector2 pointToTest) {
		final List<RaycastHit> hits = new ArrayList<RaycastHit>();
		Vector2 end = pointToTest.add(new Vector2(1, 1));
		physic.physicWorld.QueryAABB(new QueryCallback() {
			@Override
			public boolean reportFixture(Fixture fixture) {
				if (fixture.testPoint(pointToTest)) {
					hits.add(new RaycastHit(fixture, pointToTest));
				}
				return true;
			}
		}, pointToTest.x < end.x ? pointToTest.x : end.x, pointToTest.y < end.y ? pointToTest.y : end.y,
				pointToTest.x > end.x ? pointToTest.x : end.x, pointToTest.y > end.y ? pointToTest.y : end.y);

		return hits;
	}

	public static List<RaycastHit> raycast(Vector2 start, Vector2 direction) {
		final List<RaycastHit> hits = new ArrayList<RaycastHit>();
		Vector2 end = start.add(direction);
		physic.physicWorld.rayCast(new RayCastCallback() {

			@Override
			public float reportRayFixture(Fixture fixture, Vector2 point, Vector2 normal, float fraction) {
				hits.add(new RaycastHit(fixture, point, normal, fraction));
				return 1;
			}
		}, start, end);
		return hits;
	}
}
