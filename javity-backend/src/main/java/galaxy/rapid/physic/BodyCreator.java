package galaxy.rapid.physic;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.World;

public class BodyCreator {
	public static Body getBody(Vector2 position, World physicWorld, BodyType bodyType) {
		// First we create a body definition
		BodyDef bodyDef = new BodyDef();
		// We set our body to dynamic, for something like ground which doesn't
		// move we would set it to StaticBody
		bodyDef.type = bodyType;
		bodyDef.position.set(position);
		// Create our body in the world using our body definition
		Body body = physicWorld.createBody(bodyDef);
		return body;
	}
}
