package pl.radomiej.map;

import java.util.ArrayList;
import java.util.List;

import org.javity.components.RectangleCollider;
import org.javity.components.Rigidbody;
import org.javity.components.SpriteRenderer;
import org.javity.engine.JCamera;
import org.javity.engine.JComponent;
import org.javity.engine.JGUI;
import org.javity.engine.JGameObject;
import org.javity.engine.JGameObjectImpl;
import org.javity.engine.JInput;
import org.javity.engine.JSceneManager;
import org.javity.engine.JTime;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

public class MapComponent extends JComponent {

	public float zoomSpeed = 1;
	int minZoom = 2;
	int maxZoom = 17;
	int currentZoom;

	private float draggedSpeed = 1;
	private List<LayerComponent> layers = new ArrayList<LayerComponent>();
	private float targetCameraZoom = 1;
	
	@Override
	public void start() {
		currentZoom = minZoom;
		
		JCamera.getMain().setZoom(32000f);

		System.out.println("start Map");
		for (int x = minZoom; x <= maxZoom; x++) {
			int zoomLevel = (maxZoom - minZoom) - (x - minZoom);
			int localScale = (int) Math.pow(2, zoomLevel);

			JGameObject layer = JSceneManager.current.instantiateGameObject(getTransform().getPosition());
			LayerComponent layerComponent = new LayerComponent(x);
			layer.addComponent(layerComponent);
			layer.getTransform().setParent(getGameObject());
			layer.getTransform().setLocalScale(localScale, localScale);
			System.out.println("layers scale: " + layer.getTransform().getScale());

			layers.add(layerComponent);
			// if(x == minZoom){
			// draggedSpeed = (float) Math.pow(2, zoomLevel);
			// layer.getComponent(LayerComponent.class).show();
			// }
		}
		System.out.println("layers create");

		changeZoom();
	}

	@Override
	public void update() {
		if (JInput.getMouseWheelDelta() != 0) {
			int scrollDelta = JInput.getMouseWheelDelta() > 0 ? 1 : -1;
			currentZoom -= scrollDelta;
			if (currentZoom <= minZoom)
				currentZoom = minZoom;
			if (currentZoom > maxZoom )
				currentZoom = maxZoom;

			changeZoom();

		}
		if(JCamera.getMain().getZoom() != targetCameraZoom){
		
			float deltaChange = JCamera.getMain().getZoom() - targetCameraZoom;
			int dir = deltaChange > 0 ? -1 : 1;
			float currendZoomSpeed = zoomSpeed * JTime.getDelta() * (float) Math.pow(2, getZoomLevel());
			if(Math.abs(deltaChange) < currendZoomSpeed){
				currendZoomSpeed = Math.abs(deltaChange);
			}
			currendZoomSpeed *= (float)dir;
						
			float newZoom = JCamera.getMain().getZoom() + currendZoomSpeed;
//			Gdx.app.log(this.getClass().getSimpleName(), "target zoom: " + targetCameraZoom + "zoom new: " + newZoom + " currentZoom: " + JCamera.getMain().getZoom());
			JCamera.getMain().setZoom(newZoom);
		}
	}

	private void changeZoom() {
		int zoomLevel = getZoomLevel();
		draggedSpeed = (float) Math.pow(2, zoomLevel);
		targetCameraZoom =  (int)Math.pow(2, zoomLevel);
		System.out.println("Zoom: " + currentZoom + " zoom level: " + zoomLevel + " target zoom: " + targetCameraZoom);

		currentLayer().show(JCamera.getMain().getPosition());
	}

	private int getZoomLevel(){
		return (maxZoom - minZoom) - (currentZoom - minZoom);
	}
	public LayerComponent currentLayer() {
		return layers.get(currentZoom - minZoom);
	}

	@Override
	public void onMouseDragged(Vector2 draggedDelta) {
		if(JGUI.INSTANCE.isStageHandleInput()) return;
		 System.out.println("dragged: " + draggedSpeed);
		Vector2 position = JCamera.getMain().getPosition();
		position.add(-draggedDelta.x * draggedSpeed, draggedDelta.y * draggedSpeed);
		JCamera.getMain().setPosition(position);
	}

}
