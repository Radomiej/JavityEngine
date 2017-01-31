package pl.silver.canvas.javity;

import pl.silver.canvas.SilverCanvas;

public interface DrawJob {
	public void draw(SilverCanvas canvas);
	public long getId();
}
