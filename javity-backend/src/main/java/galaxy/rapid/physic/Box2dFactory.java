package galaxy.rapid.physic;

import com.badlogic.gdx.Gdx;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.CircleShape;

import galaxy.rapid.components.CircleColliderComponent;
import galaxy.rapid.components.PolygonColliderComponent;
import galaxy.rapid.components.PositionComponent;
import galaxy.rapid.components.RectangleColliderComponent;

public class Box2dFactory {
	/*
	 * public static Fixture createCircle(World world, BodyComponent
	 * bodyComponent, PositionComponent positionComponent) {
	 * 
	 * // Set our body's starting position in the world Vector2 position = new
	 * Vector2(positionComponent.getPosition());
	 * 
	 * Body body = BodyCreator.getBody(position, world);
	 * body.setLinearDamping(0.2f); body.setAngularDamping(1); //
	 * body.setFixedRotation(true); // Create a circle shape and set its radius
	 * to 6
	 * 
	 * float radius = (bodyComponent.getSize().x + bodyComponent.getSize().y) /
	 * 2; CircleShape circle = new CircleShape(); circle.setPosition(position);
	 * circle.setRadius(radius);
	 * 
	 * // Create a fixture definition to apply our shape to FixtureDef
	 * fixtureDef = new FixtureDef(); fixtureDef.shape = circle;
	 * fixtureDef.density = 0.3f; fixtureDef.friction = 0.5f;
	 * fixtureDef.restitution = 0.3f; // Make it bounce a little bit
	 * 
	 * // Create our fixture and attach it to the body Fixture fixture =
	 * body.createFixture(fixtureDef);
	 * 
	 * PolygonShape polygon = new PolygonShape();
	 * 
	 * 
	 * Vector2[] vectors = getCake(position, 0, 200, radius + 5);
	 * polygon.set(vectors);
	 * 
	 * // Create a fixture definition to apply our shape to FixtureDef
	 * fixtureDef2 = new FixtureDef(); fixtureDef2.isSensor = true;
	 * fixtureDef2.shape = polygon; fixtureDef2.density = 0.3f;
	 * fixtureDef2.friction = 0.5f; fixtureDef2.restitution = 0.3f; // Make it
	 * bounce a little bit
	 * 
	 * // Create our fixture and attach it to the body Fixture fixture2 =
	 * body.createFixture(fixtureDef2);
	 * 
	 * circle.dispose(); return fixture; }
	 * 
	 * private static Vector2[] getCake(Vector2 position, int startAngle, int
	 * degress, float radius) { float angleStep = degress / 8f; Vector2[]
	 * vectores = new Vector2[8]; // vectores[0] = position.cpy(); //
	 * vectores[degress + 1] = position.cpy(); Vector2 base = new Vector2(0,
	 * radius); base.rotate(startAngle); for(int x = 0; x < 8; x++ ){
	 * vectores[x] = base.cpy(); vectores[x].add(position);
	 * base.rotate(angleStep); }
	 * 
	 * return vectores; }
	 */
	public static Fixture createRectangle(World world, PositionComponent positionComponent,
			RectangleColliderComponent rectangleColliderComponent, Body body) {
		// body.setLinearDamping(0.2f);
		// body.setAngularDamping(1);
		// body.setFixedRotation(true);
		// Create a circle shape and set its radius to 6
		PolygonShape rectangle = new PolygonShape();
		rectangle.setAsBox(rectangleColliderComponent.getWidth() / 2, rectangleColliderComponent.getHeight() / 2,
				new Vector2(rectangleColliderComponent.getOffsetX(), rectangleColliderComponent.getOffsetY()), 0);

		// Create a fixture definition to apply our shape to
		FixtureDef fixtureDef = new FixtureDef();
		fixtureDef.shape = rectangle;
		fixtureDef.density = rectangleColliderComponent.getDensity();
		fixtureDef.friction = rectangleColliderComponent.getFriction();
		fixtureDef.restitution = rectangleColliderComponent.getRestitution(); // Make it bounce a little bit

		// Create our fixture and attach it to the body
		Fixture fixture = body.createFixture(fixtureDef);

		// Remember to dispose of any shapes after you're done with them!
		// BodyDef and FixtureDef don't need disposing, but shapes do.
		rectangle.dispose();
		return fixture;
	}
	/*
	 * public static void createGround(World world) { // Create our body
	 * definition BodyDef groundBodyDef = new BodyDef(); // Set its world
	 * position groundBodyDef.position.set(new Vector2(0, 10));
	 * 
	 * // Create a body from the defintion and add it to the world Body
	 * groundBody = world.createBody(groundBodyDef);
	 * 
	 * // Create a polygon shape PolygonShape groundBox = new PolygonShape(); //
	 * Set the polygon shape as a box which is twice the size of our view //
	 * port and 20 high // (setAsBox takes half-width and half-height as
	 * arguments) groundBox.setAsBox(Gdx.graphics.getWidth(), 10.0f); // Create
	 * a fixture from our polygon shape and add it to our ground body
	 * groundBody.createFixture(groundBox, 0.0f); // Clean up after ourselves
	 * groundBox.dispose(); }
	 * 
	 * public static Fixture createWall(World world, Rectangle wallRec) { //
	 * Create our body definition BodyDef groundBodyDef = new BodyDef(); // Set
	 * its world position groundBodyDef.position.set(new Vector2(wallRec.x +
	 * wallRec.width / 2, wallRec.y + wallRec.height / 2));
	 * 
	 * // Create a body from the defintion and add it to the world Body
	 * groundBody = world.createBody(groundBodyDef);
	 * 
	 * // Create a polygon shape PolygonShape groundBox = new PolygonShape(); //
	 * Bottom groundBox.setAsBox(wallRec.width / 2, wallRec.height / 2); //
	 * Create a fixture from our polygon shape and add it to our ground body
	 * Fixture fixture = groundBody.createFixture(groundBox, 0.0f); // Clean up
	 * after ourselves groundBox.dispose();
	 * 
	 * return fixture; }
	 */

	public static Fixture createCircle(World physicWorld, PositionComponent position,
			CircleColliderComponent circleCollider, Body body) {
		CircleShape circle = new CircleShape();
		circle.setPosition(new Vector2(circleCollider.getOffsetX(), circleCollider.getOffsetY()));
		circle.setRadius(circleCollider.getRadius());

		// Create a fixture definition to apply our shape to
		FixtureDef fixtureDef = new FixtureDef();
		fixtureDef.shape = circle;
		fixtureDef.density = circleCollider.getDensity();
		fixtureDef.friction = circleCollider.getFriction();
		fixtureDef.restitution = circleCollider.getRestitution(); // Make it bounce a little bit

		// Create our fixture and attach it to the body
		Fixture fixture = body.createFixture(fixtureDef);

		// Remember to dispose of any shapes after you're done with them!
		// BodyDef and FixtureDef don't need disposing, but shapes do.
		circle.dispose();
		return fixture;
	}

	public static Fixture createPolygon(World physicWorld, PositionComponent position,
			PolygonColliderComponent polygonCollider, Body body) {
		PolygonShape polygon = new PolygonShape();
		polygon.set(polygonCollider.getVertex().toArray());

		// Create a fixture definition to apply our shape to
		FixtureDef fixtureDef = new FixtureDef();
		fixtureDef.shape = polygon;
		fixtureDef.density = polygonCollider.getDensity();
		fixtureDef.friction = polygonCollider.getFriction();
		fixtureDef.restitution = polygonCollider.getRestitution(); // Make it bounce a little bit
		
		// Create our fixture and attach it to the body
		Fixture fixture = body.createFixture(fixtureDef);

		// Remember to dispose of any shapes after you're done with them!
		// BodyDef and FixtureDef don't need disposing, but shapes do.
		polygon.dispose();
		return fixture;
	}
}
