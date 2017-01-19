package pl.silver.canvas.fakeand;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;

import pl.silver.canvas.fakeand.Bitmap.Config;

public class Bitmap {
	public enum Config {
		ARGB_8888
	}

	public void recycle() {
		throw new UnsupportedOperationException();
	}

	private Pixmap pixmap;
	
	public static Bitmap createBitmap(float width, float height, Config argb8888) {
		Bitmap bitmap = new Bitmap();
		bitmap.pixmap = new Pixmap((int)width, (int)height, Format.RGBA8888);
		
		return bitmap;
	}

	public int getWidth() {
		return pixmap.getWidth();
	}

	public int getHeight() {
		return pixmap.getHeight();
	}

	public void getPixels(int[] pixels, int offset, int stride, int x, int y, int width, int height) {
		throw new UnsupportedOperationException();
	}

	public void setPixels(int[] pixels, int offset, int stride, int x, int y, int width, int height) {
		throw new UnsupportedOperationException();
	}

}
