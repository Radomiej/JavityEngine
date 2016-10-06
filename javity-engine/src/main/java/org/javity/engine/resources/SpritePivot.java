package org.javity.engine.resources;

public enum SpritePivot {
	CENTER, LEFT, RIGHT, TOP, BOTTOM, LEFT_TOP, LEFT_BOTTOM, RIGHT_TOP, RIGHT_BOTTOM;

	public boolean isLeft() {
		return this == LEFT || this == LEFT_TOP || this == LEFT_BOTTOM;
	}

	public boolean isRight() {
		return this == RIGHT || this == RIGHT_TOP || this == RIGHT_BOTTOM;
	}

	public boolean isBottom() {
		return this == BOTTOM || this == RIGHT_BOTTOM || this == LEFT_BOTTOM;
	}

	public boolean isTop() {
		return this == TOP || this == RIGHT_TOP || this == LEFT_TOP;
	}
}
