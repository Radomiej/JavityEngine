package org.javity.components;

import java.util.ArrayList;
import java.util.List;

import org.javity.engine.NativeComponent;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;

import galaxy.rapid.components.RenderComponent;
import galaxy.rapid.components.ShapeComponent;

public class LineRenderer extends NativeComponent {
	private transient ShapeComponent shapeComponent;
	private transient RenderComponent renderComponent;
	private List<Vector2> polygons = new ArrayList<Vector2>();
	private Vector2 tempVector = new Vector2();
	private float width = 1;
	private Color color = new Color(Color.WHITE);
	
	
	public LineRenderer() {
	}
	
	public LineRenderer(Vector2... polygons) {
		for(Vector2 point : polygons){
			this.polygons.add(point);
		}
	}
	
	public LineRenderer(List<Vector2> polygons) {
		this.polygons.addAll(polygons);
	}

	@Override
	public void awake() {
		renderComponent = new RenderComponent();
		shapeComponent = new ShapeComponent();
		addNativeComponent(renderComponent);
		addNativeComponent(shapeComponent);
	}

	@Override
	public void update() {
//		System.out.println("2 rotation: " + getTransform().getRotation() + " uuid: " + getGameObject().getObjectId());
		renderComponent.setOrderZ(getTransform().getOrderZ());
		
		if(shapeComponent.getWidth() != width){
			shapeComponent.setWidth(width);
		}
		if(!shapeComponent.getColor().equals(color)){
			shapeComponent.setColor(color);
		}
		
		Vector2 positionObject = getTransform().getPosition();
//		System.out.println("position " + positionObject + " rotation: " + getTransform().getRotation());
		for (int x = 0; x < polygons.size(); x++) {
			Vector2 relativePoint = polygons.get(x);
			tempVector.set(relativePoint);
			relativePoint = tempVector.rotate(getTransform().getRotation());
			Vector2 drawPoint = shapeComponent.getPolygonPoints().get(x);
			drawPoint.set(relativePoint.x + positionObject.x, relativePoint.y + positionObject.y);
		}
	}

	@Override
	public void start() {
		for (int x = 0; x < polygons.size(); x++) {
			Vector2 point = polygons.get(x);
			shapeComponent.getPolygonPoints().add(point.cpy());
		}
	}
	
	
	public void drawLine(Vector2 start, Vector2 end) {
		clearPolygons();
		addPoint(start);
		addPoint(end);
	}

	public void drawCircle(Vector2 center, float radius, int circleParts){
		float angleStep = 360f / circleParts;
		Vector2[] vectores = new Vector2[circleParts + 1];
		Vector2 base = new Vector2(0, radius);
		for(int x = 0; x < vectores.length; x++ ){
			vectores[x] = base.cpy();
			vectores[x].add(center);
			base.rotate(angleStep);			
		}
		
		clearPolygons();
		for(Vector2 vector : vectores){
			addPoint(vector);
		}
	}
	
	public void drawRectangle(Vector2 center, float width, float height){
		float x = center.x - width / 2;
		float y = center.y - height / 2;
		
		clearPolygons();
		addPoint(new Vector2(x, y));
		addPoint(new Vector2(x, y + height));
		addPoint(new Vector2(x + width, y + height));
		addPoint(new Vector2(x + width, y));
		addPoint(new Vector2(x, y));
	}
	
	private void clearPolygons() {
		polygons.clear();
		if(shapeComponent != null) shapeComponent.getPolygonPoints().clear();
	}

	public void addPoint(Vector2 point) {
		polygons.add(point);
		if(shapeComponent != null) shapeComponent.getPolygonPoints().add(point.cpy());
	}
	
	@Override
	public void onEnabled() {
		renderComponent.setRender(true);
	}
	
	@Override
	public void onDisable() {
		renderComponent.setRender(false);
	}

	public float getWidth() {
		return width;
	}

	public void setWidth(float width) {
		this.width = width;
	}

	public Color getColor() {
		return color;
	}

	public void setColor(Color color) {
		this.color.set(color);
	}
}
