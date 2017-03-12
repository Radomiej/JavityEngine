package pl.silver.canvas.javity;

import java.util.ArrayList;
import java.util.List;

import org.javity.engine.JCamera;
import org.javity.engine.JComponent;

import com.badlogic.gdx.graphics.OrthographicCamera;

import pl.silver.canvas.SilverCanvas;
import pl.silver.canvas.generic.GenericCanvas;
import pl.silver.canvas.generic.MapDBCanvas;

public class JavityCanvasComponent extends JComponent{
	private SilverCanvas canvas;
	
	@Override
	public void awake() {
		long time = System.nanoTime();
		
		canvas = new MapDBCanvas((int) Math.pow(2, 11));
		
		time = System.nanoTime() - time;
		time /= 100000;
		System.out.println("time of execution: " + time);
	}
	
	@Override
	public void update() {
	}
	
	
	public void postRender(){
//		OrthographicCamera camera = canvas.getCamera();
//		OrthographicCamera nativeCamera = JCamera.getMain().getNative().getCamera();
//		
//		camera.position.set(nativeCamera.position);
//		camera.combined.set(nativeCamera.combined);
//		camera.zoom = nativeCamera.zoom;
//		canvas.updateCamera();
		
	}

	public SilverCanvas getCanvas() {
		return canvas;
	}
}
