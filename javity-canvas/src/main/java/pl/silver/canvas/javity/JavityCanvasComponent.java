package pl.silver.canvas.javity;

import java.util.ArrayList;
import java.util.List;

import org.javity.engine.JCamera;
import org.javity.engine.JComponent;

import com.badlogic.gdx.graphics.OrthographicCamera;

import pl.silver.canvas.libgdx.LibGdxCanvas;

public class JavityCanvasComponent extends JComponent{
	private LibGdxCanvas canvas;
	private List<DrawJob> jobs = new ArrayList<DrawJob>();
	
	@Override
	public void start() {
		canvas = new LibGdxCanvas();
	}
	
	public void addDrawJob(DrawJob drawJob){
		jobs.add(drawJob);
	}
	
	public void postRender(){
		OrthographicCamera camera = canvas.getCamera();
		OrthographicCamera nativeCamera = JCamera.getMain().getNative().getCamera();
		
		camera.position.set(nativeCamera.position);
		camera.combined.set(nativeCamera.combined);
		camera.update();
		
		for(DrawJob job : jobs){
			job.draw(canvas);
		}
	}
}
