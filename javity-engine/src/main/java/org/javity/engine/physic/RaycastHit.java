package org.javity.engine.physic;

import org.javity.components.Rigidbody;
import org.javity.components.Transform;
import org.javity.engine.Component;
import org.javity.engine.JGameObject;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Fixture;

public class RaycastHit {

	final public Transform transform;
	final public Component collider;
	final public Rigidbody rigidbody;
	final public Vector2 point, normal;
	final public float fraction;
	
	public RaycastHit(Fixture fixture, Vector2 pointToTest) {
		this(fixture, pointToTest, Vector2.Zero, 0);
	}

	public RaycastHit(Fixture fixture, Vector2 point, Vector2 normal, float fraction) {
		this.collider = (Component) fixture.getUserData();
		JGameObject gameObject = collider.getGameObject();
		this.transform = gameObject.getTransform();
		this.point = point;
		this.normal = normal;
		this.rigidbody = gameObject.getComponent(Rigidbody.class);
		this.fraction = fraction;
	}

}
