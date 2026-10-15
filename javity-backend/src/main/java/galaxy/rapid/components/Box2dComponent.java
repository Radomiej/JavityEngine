package galaxy.rapid.components;

import com.artemis.Component;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.Fixture;

public class Box2dComponent extends Component {
	private Body body;
	private Fixture fixture;
	private Object userData, fixtureData;
	private BodyType bodyType = BodyType.DynamicBody;
	private boolean removed;

	public Body getBody() {
		return body;
	}

	public void setBody(Body body) {
		this.body = body;
	}

	@Override
	public String toString() {
		String info = "body pos: " + body.getPosition();
		return info;
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((body == null) ? 0 : body.hashCode());
		result = prime * result + ((fixture == null) ? 0 : fixture.hashCode());
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Box2dComponent other = (Box2dComponent) obj;
		if (body == null) {
			if (other.body != null)
				return false;
		} else if (!body.equals(other.body))
			return false;
		if (fixture == null) {
			if (other.fixture != null)
				return false;
		} else if (!fixture.equals(other.fixture))
			return false;
		return true;
	}

	public Object getUserData() {
		return userData;
	}

	public void setUserData(Object userData) {
		this.userData = userData;
	}

	public Object getFixtureData() {
		return fixtureData;
	}

	public void setFixtureData(Object fixtureData) {
		this.fixtureData = fixtureData;
	}

	public Fixture getFixture() {
		return fixture;
	}

	public boolean isKinematic() {
		return bodyType == BodyType.KinematicBody;
	}

	public boolean isDynamic() {
		return bodyType == BodyType.DynamicBody;
	}

	public boolean isStatic() {
		return bodyType == BodyType.StaticBody;
	}

	public BodyType getBodyType() {
		return bodyType;
	}

	public void setBodyType(BodyType bodyType) {
		this.bodyType = bodyType;
	}

	public boolean isRemoved() {
		return removed;
	}

	public void setRemoved(boolean remove) {
		this.removed = remove;
	}

	public void setFixture(Fixture fixture) {
		this.fixture = fixture;
	}

}
