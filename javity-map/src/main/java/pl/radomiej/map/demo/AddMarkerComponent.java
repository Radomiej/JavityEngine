package pl.radomiej.map.demo;

import org.javity.components.SpriteRenderer;
import org.javity.engine.JCamera;
import org.javity.engine.JComponent;
import org.javity.engine.JGameObject;
import org.javity.engine.JInput;

import com.badlogic.gdx.math.Vector2;

import pl.radomiej.map.GeoPoint;
import pl.radomiej.map.MapComponent;
import pl.radomiej.map.Marker;

public class AddMarkerComponent extends JComponent{
	private MapComponent map;
	
	public void start() {
		map = getGameObject().getComponent(MapComponent.class);
	};
	
	@Override
	public void onMouseClicked() {
		
		Vector2 clickWorld = JCamera.getMain().screenToWorldPoint(JInput.getMousePosition());
		
		JGameObject markerWorld = instantiateGameObject(clickWorld);
		markerWorld.addComponent(new SpriteRenderer("badlogic.jpg"));
		markerWorld.addComponent(new StaticZoomComponent());
		markerWorld.getTransform().setZ(99);
		
		
		GeoPoint geo = map.getGeoFromWorldPosition(clickWorld);
		System.out.println("geo marker: " + geo);
		map.addMarker("test-clickWorld", new Marker(geo.lat, geo.lon, "map-marker-icon.png", new Vector2(1, 1)));
	}
}
