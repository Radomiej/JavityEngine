package pl.radomiej.map.demo;

import org.javity.engine.Component;
import org.javity.engine.JCamera;
import org.javity.engine.JComponent;
import org.javity.engine.JResources;

import com.badlogic.gdx.math.Vector2;

public class StaticZoomComponent extends JComponent {
	private float scaleX = 1f, scaleY = 1f;

	public StaticZoomComponent() {
	}
	
	public StaticZoomComponent(Vector2 scale){
		scaleX = scale.x;
		scaleY = scale.y;
	}
	
	@Override
	public void update() {
		float camerZoom = JCamera.getMain().getZoom();
		getTransform().setLocalScale(camerZoom * scaleX, camerZoom * scaleY);
	}
}
