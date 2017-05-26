package org.javity.components;

import org.javity.engine.Component;
import org.javity.engine.JCamera;
import org.javity.engine.JComponent;
import org.javity.engine.JInput;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

import galaxy.rapid.common.RotatedRectangle;

public class TouchableObject extends JComponent {
	private final Rectangle intersectorRectangle = new Rectangle();
	private final Vector2 draggedDelta = new Vector2();
	private float width, height;
	private final Rectangle bounds = new Rectangle();

	private long pressedTime;
	private float draggedDeltaActivate = 12;
	private boolean draggedActive = false;
	
	private float longPressTimeActivateInMs = 1000;
	
	private boolean startPressedOverMy, endPressedOverMy;
	
	public TouchableObject(int width, int height) {
		this.width = width;
		this.height = height;
	}

	public TouchableObject(float width, float height) {
		this.width = width;
		this.height = height;
	}
	
	public TouchableObject() {
	}
	
	@Override
	public void start() {
		refresh();
	}

	public void refresh() {
		if (getGameObject().hasComponent(SpriteRenderer.class)) {
			SpriteRenderer spriteRenderer = getGameObject().getComponent(SpriteRenderer.class);
			width = spriteRenderer.getSpriteWidth();
			height = spriteRenderer.getSpriteHeight();
		}else if(getGameObject().hasComponent(SpriteRenderer.class)){
			TextRenderer textRenderer = getGameObject().getComponent(TextRenderer.class);
			Rectangle textBound = textRenderer.getBounds();
			width = textBound.width;
			height = textBound.height;
		}
	}

	@Override
	public void update() {
		if (JInput.isJustPressed()) {
			pressedTime = System.currentTimeMillis();
			draggedDelta.set(0, 0);
			
			if(isPressedOverMe()){
				startPressedOverMy = true;
			}
			
		} else if (JInput.isTouch()) {
			if(!startPressedOverMy) return;
			
			draggedDelta.add(JInput.getMouseDragged());
			float draggedDistance = draggedDelta.len();
			if(draggedDistance >= draggedDeltaActivate){
				draggedActive = true;
			}
			
			if(draggedActive){
				invokeMouseDraggedStateInAllComponentInThisGameObject();
			}
			
		}else if(JInput.isJustRelased()){
			if(!startPressedOverMy) return;
			
			long clickTime = System.currentTimeMillis() - pressedTime;
			float draggedDistance = draggedDelta.len();
			
			if(isPressedOverMe()){
				endPressedOverMy = true;
			}
			
			if(draggedDistance > 5 || draggedActive) {
				draggedActive = false;
				return;
			}
			
			if(clickTime < longPressTimeActivateInMs){
				invokeClickStateInAllComponentInThisGameObject();
			}else if(clickTime > longPressTimeActivateInMs){
				invokeLongClickStateInAllComponentInThisGameObject();
			}
			
			startPressedOverMy = false;
			endPressedOverMy = false;
		}

	}

	private RotatedRectangle rotatedRectangle = new RotatedRectangle(new Rectangle(), 0);
	
	private boolean isPressedOverMe() {
		Vector2 size = new Vector2(width, height);
		size.scl(getTransform().getScale().x, getTransform().getScale().y);
		
		bounds.setSize(size.x, size.y);
		bounds.setCenter(getTransform().getPosition());
		
		Vector2 screenMousePosition = JInput.getMousePosition();
		Vector2 worldMousePosition = JCamera.getMain().screenToWorldPoint(screenMousePosition);
		Rectangle mouseRectangle = new Rectangle();
		mouseRectangle.setSize(1);
		mouseRectangle.setCenter(worldMousePosition);
		
		rotatedRectangle.setCenterBounds(getTransform().getPosition().x, getTransform().getPosition().y, size.x, size.y);
		
		
		if(rotatedRectangle.intersects(mouseRectangle)){
			return true;
		}
		
		return false;
	}

	private void invokeMouseDraggedStateInAllComponentInThisGameObject() {
		for(Component component : getGameObject().getAllComponents()){
			component.onMouseDragged(JInput.getMouseDragged());
		}
	}

	private void invokeLongClickStateInAllComponentInThisGameObject() {
		//TODO Add support for Long-press
	}

	private void invokeClickStateInAllComponentInThisGameObject() {
		for(Component component : getGameObject().getAllComponents()){
			component.onMouseClicked();
		}
	}

}
