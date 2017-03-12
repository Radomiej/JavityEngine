package org.javity.engine.utilities;

import java.util.List;

import com.badlogic.gdx.math.Vector2;

public class MoveOnPathAlgorithms {
	private float speed = 1;
	private int currentIndex = 0;
	private double afterCurrentIndexPointDistance;
	private List<Vector2> movablePoints;
	private Vector2 currentPostion;

	public MoveOnPathAlgorithms(List<Vector2> movablePoints) {
		this.movablePoints = movablePoints;
		currentPostion = movablePoints.get(0);
	}

	public Vector2 getNextPosition() {
		
		if(currentIndex == movablePoints.size() - 1){
			return movablePoints.get(movablePoints.size() - 1);
		}
		
		fillAmount();		
		
		if(currentIndex == movablePoints.size() - 1){
			return movablePoints.get(movablePoints.size() - 1);
		}
		
		
		currentPostion = getCurrentPositiont();
		return currentPostion;
	}

	private Vector2 getCurrentPositiont() {
		float distanceBetween = movablePoints.get(currentIndex).dst(movablePoints.get(currentIndex + 1));
		double completePath = afterCurrentIndexPointDistance / distanceBetween;
		
		Vector2 current = movablePoints.get(currentIndex);
		Vector2 next = movablePoints.get(currentIndex + 1); 
		float x = current.x;
		x +=  (next.x - current.x) * completePath;
		
		float y = current.y;
		y +=  (next.y - current.y) * completePath;
		return new Vector2(x, y);
	}

	private double fillAmount() {
		double amountDistance = -afterCurrentIndexPointDistance;
		while (true) {
			
			
			double deltaCurrentDistance = movablePoints.get(currentIndex).dst(movablePoints.get(currentIndex + 1));
			if (deltaCurrentDistance >= (speed - amountDistance)) {
				afterCurrentIndexPointDistance =  (speed - amountDistance);
				return amountDistance;
			} else {
				amountDistance += deltaCurrentDistance;
				currentIndex++;
				if(currentIndex == movablePoints.size() - 1){
					return 0;
				}
			}
		}
	}

	public void setSpeed(float d) {
		speed = d;
	}
}
