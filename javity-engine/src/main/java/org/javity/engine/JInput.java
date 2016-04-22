package org.javity.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;

public class JInput {
	public static Vector2 getMousePosition(){
		return new Vector2(Gdx.input.getX(), Gdx.input.getY());
	}
}
