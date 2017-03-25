package org.javity.engine;

import org.javity.engine.mobile.JGeolocation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;

import galaxy.rapid.input.RapidInput;

public class JInput {
	public static JGeolocation geolocation;
	
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

	public static boolean isTouch() {
		return Gdx.input.isTouched();
	}

	public static Vector2 getMouseDragged() {
		return new Vector2(Gdx.input.getDeltaX(), Gdx.input.getDeltaY());
	}

	public static int getMouseWheelDelta() {
		return RapidInput.getScroll();
	}
}
