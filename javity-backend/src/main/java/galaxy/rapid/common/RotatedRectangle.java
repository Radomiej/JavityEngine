package galaxy.rapid.common;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

public class RotatedRectangle {

	private Rectangle collisionRectangle;
	private float rotation;
	private Vector2 origin;

	public RotatedRectangle(Rectangle rec, float rotation) {
		collisionRectangle = rec;
		this.rotation = rotation;
		origin = new Vector2(rec.width / 2, rec.height / 2);
	}	

	public void setPosition(float theXPosition, float theYPosition) {
		collisionRectangle.x = theXPosition;
		collisionRectangle.y = theYPosition;
	}

	public Boolean intersects(Rectangle theRectangle) {
		return intersects(new RotatedRectangle(theRectangle, 0.0f));
	}

	public Boolean intersects(RotatedRectangle theRectangle) {

		List<Vector2> aRectangleAxis = new ArrayList<Vector2>();

		aRectangleAxis.add(UpperRightCorner().add(-UpperLeftCorner().x, -UpperLeftCorner().y));
		aRectangleAxis.add(UpperRightCorner().add(-LowerRightCorner().x, -LowerRightCorner().y));
		aRectangleAxis.add(theRectangle.UpperLeftCorner().add(-theRectangle.LowerLeftCorner().x,
				-theRectangle.LowerLeftCorner().y));
		aRectangleAxis.add(theRectangle.UpperLeftCorner().add(-theRectangle.UpperRightCorner().x,
				-theRectangle.UpperRightCorner().y));

		for (Vector2 aAxis : aRectangleAxis) {
			if (!IsAxisCollision(theRectangle, aAxis)) {
				return false;
			}
		}

		return true;
	}

	private int getMin(List<Integer> list) {
		int temp = Integer.MAX_VALUE;
		for (int i : list) {
			if (i < temp)
				temp = i;
		}
		return temp;
	}

	private int getMax(List<Integer> list) {
		int temp = Integer.MIN_VALUE;
		for (int i : list) {
			if (i > temp)
				temp = i;
		}
		return temp;
	}

	private Boolean IsAxisCollision(RotatedRectangle theRectangle, Vector2 aAxis) {
		// Project the corners of the Rectangle we are checking on to the Axis
		// and
		// get a scalar value of that project we can then use for comparison
		List<Integer> aRectangleAScalars = new ArrayList<Integer>();
		aRectangleAScalars.add(GenerateScalar(theRectangle.UpperLeftCorner(), aAxis));
		aRectangleAScalars.add(GenerateScalar(theRectangle.UpperRightCorner(), aAxis));
		aRectangleAScalars.add(GenerateScalar(theRectangle.LowerLeftCorner(), aAxis));
		aRectangleAScalars.add(GenerateScalar(theRectangle.LowerRightCorner(), aAxis));

		// Project the corners of the current Rectangle on to the Axis and
		// get a scalar value of that project we can then use for comparison
		List<Integer> aRectangleBScalars = new ArrayList<Integer>();
		aRectangleBScalars.add(GenerateScalar(UpperLeftCorner(), aAxis));
		aRectangleBScalars.add(GenerateScalar(UpperRightCorner(), aAxis));
		aRectangleBScalars.add(GenerateScalar(LowerLeftCorner(), aAxis));
		aRectangleBScalars.add(GenerateScalar(LowerRightCorner(), aAxis));

		// Get the Maximum and Minium Scalar values for each of the Rectangles
		int aRectangleAMinimum = getMin(aRectangleAScalars);
		int aRectangleAMaximum = getMax(aRectangleAScalars);
		int aRectangleBMinimum = getMin(aRectangleBScalars);
		int aRectangleBMaximum = getMax(aRectangleBScalars);

		// If we have overlaps between the Rectangles (i.e. Min of B is less
		// than Max of A)
		// then we are detecting a collision between the rectangles on this Axis
		if (aRectangleBMinimum <= aRectangleAMaximum && aRectangleBMaximum >= aRectangleAMaximum) {
			return true;
		} else if (aRectangleAMinimum <= aRectangleBMaximum && aRectangleAMaximum >= aRectangleBMaximum) {
			return true;
		}

		return false;
	}


	private int GenerateScalar(Vector2 theRectangleCorner, Vector2 theAxis) {
		// Using the formula for Vector projection. Take the corner being passed
		// in
		// and project it onto the given Axis
		float aNumerator = (theRectangleCorner.x * theAxis.x) + (theRectangleCorner.y * theAxis.y);
		float aDenominator = (theAxis.x * theAxis.x) + (theAxis.y * theAxis.y);
		float aDivisionResult = aNumerator / aDenominator;
		Vector2 aCornerProjected = new Vector2(aDivisionResult * theAxis.x, aDivisionResult * theAxis.y);

		// Now that we have our projected Vector, calculate a scalar of that
		// projection
		// that can be used to more easily do comparisons
		float aScalar = (theAxis.x * aCornerProjected.x) + (theAxis.y * aCornerProjected.y);

		return (int) aScalar;
	}

	private Vector2 RotatePoint(Vector2 thePoint, Vector2 theOrigin, float theRotation) {
		Vector2 aTranslatedPoint = new Vector2();
		aTranslatedPoint.x = (float) (theOrigin.x + (thePoint.x - theOrigin.x) * MathUtils.cosDeg(theRotation)
				- (thePoint.y - theOrigin.y) * MathUtils.sinDeg(theRotation));
		aTranslatedPoint.y = (float) (theOrigin.y + (thePoint.y - theOrigin.y) * MathUtils.cosDeg(theRotation)
				+ (thePoint.x - theOrigin.x) * MathUtils.sinDeg(theRotation));
		return aTranslatedPoint;
	}

	public Vector2 UpperLeftCorner() {
		Vector2 aUpperLeft = new Vector2(collisionRectangle.x, collisionRectangle.y + collisionRectangle.height);
		float y = origin.y - collisionRectangle.height;
		aUpperLeft = RotatePoint(aUpperLeft.cpy(), new Vector2(aUpperLeft.add(new Vector2(origin.x, y))), rotation);
		return aUpperLeft;
	}

	public Vector2 UpperRightCorner() {
		float y = origin.y - collisionRectangle.height;
		float x = origin.x - collisionRectangle.width;
		Vector2 aUpperRight = new Vector2(collisionRectangle.x + collisionRectangle.width,
				collisionRectangle.y + collisionRectangle.height);
		aUpperRight = RotatePoint(aUpperRight.cpy(), new Vector2(aUpperRight.add(new Vector2(x, y))), rotation);
		return aUpperRight;
	}

	public Vector2 LowerLeftCorner() {
		Vector2 aLowerLeft = new Vector2(collisionRectangle.x, collisionRectangle.y);
		aLowerLeft = RotatePoint(aLowerLeft.cpy(), new Vector2(aLowerLeft.add(new Vector2(origin.x, origin.y))),
				rotation);
		return aLowerLeft;
	}

	public Vector2 LowerRightCorner() {
		float x = origin.x - collisionRectangle.width;
		Vector2 aLowerRight = new Vector2(collisionRectangle.x + collisionRectangle.width, collisionRectangle.y);
		aLowerRight = RotatePoint(aLowerRight.cpy(), new Vector2(aLowerRight.add(new Vector2(x, origin.y))), rotation);
		return aLowerRight;
	}

	public float getX() {
		return collisionRectangle.x;
	}

	public float getY() {
		return collisionRectangle.y;
	}

	public float getWidth() {
		return collisionRectangle.width;
	}

	public float getHeight() {
		return collisionRectangle.height;
	}

	public Rectangle getCollisionRectangle() {
		return collisionRectangle;
	}

	public void setBounds(Rectangle boundRectangle) {
		this.collisionRectangle.set(boundRectangle);
	}
	
	public void setBounds(float x, float y, float width, float height) {
		collisionRectangle.setSize(width, height);
		collisionRectangle.setPosition(x, y);
	}
	
	public void setCenterBounds(float centerX, float centerY, float width, float height) {
		collisionRectangle.setSize(width, height);
		collisionRectangle.setCenter(centerX, centerY);
	}

	public float getRotation() {
		return rotation;
	}

	public void setRotation(float rotation) {
		this.rotation = rotation;
	}

	public Vector2 getOrigin() {
		return origin;
	}

	public void setOrigin(Vector2 origin) {
		this.origin = origin;
	}

}
