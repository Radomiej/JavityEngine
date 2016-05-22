package org.javity.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;

public class JInput {
	public static Vector2 getMousePosition(){
		return new Vector2(Gdx.input.getX(), Gdx.input.getY());
	}

	private static boolean isTouchedOld = false;
	static void saveOldStatus(){
		isTouchedOld = Gdx.input.isTouched();
	}
	
	public static boolean isJustPressed() {
		return Gdx.input.justTouched();
	}

	public static boolean isJustRelased() {
		return !Gdx.input.isTouched() && isTouchedOld;
	}

	public static boolean isClicked() {
		return Gdx.input.isTouched();
	}

	public static Vector2 getMouseDragged() {
		return new Vector2(Gdx.input.getDeltaX(), Gdx.input.getDeltaY());
	}
}
