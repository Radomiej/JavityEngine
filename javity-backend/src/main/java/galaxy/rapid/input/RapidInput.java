package galaxy.rapid.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;

import galaxy.rapid.configuration.RapidConfiguration;

public class RapidInput extends com.badlogic.gdx.InputAdapter{
	
	static public int getX(){
		int realX = Gdx.input.getX();
		float scale = Gdx.graphics.getWidth() / 1024;
		return (int) (realX * scale);
	}
	
	static public int getY(){
		int realY = Gdx.graphics.getHeight() - Gdx.input.getY();
		return realY;
	}

	private static int scrollValue = 0;
	
	static public int getScroll(){
		return scrollValue;
	}
	
	@Override
	public boolean keyDown(int keycode) {
//		System.out.print("Key Code: " + keycode);
		return false;
	}

	@Override
	public boolean keyUp(int keycode) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean keyTyped(char character) {
//		System.out.println(" Key Typed: " + character);
		return false;
	}

	@Override
	public boolean touchDown(int screenX, int screenY, int pointer, int button) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean touchUp(int screenX, int screenY, int pointer, int button) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean touchDragged(int screenX, int screenY, int pointer) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean mouseMoved(int screenX, int screenY) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean scrolled(float amountX, float amountY) {
		scrollValue += (int) Math.signum(amountY);
		return false;
	}

	public void endRender() {
		scrollValue =  0;
	}
}
