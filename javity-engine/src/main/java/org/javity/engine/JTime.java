package org.javity.engine;

import java.util.ArrayList;
import java.util.List;

import org.javity.engine.timer.Task;
import org.javity.engine.timer.TimerTask;

public enum JTime {
	INSTANCE;
	
	float delta;
	
	private List<TimerTask> timerTasks = new ArrayList<TimerTask>();
	
	public float getDelta(){
		return delta;
	}
	
	public void addTimer(Task task, float timeToDestroy) {
		TimerTask timerTask = new TimerTask();
		timerTask.setTask(task);
		timerTask.setTimeToInvoke(timeToDestroy);
		timerTasks.add(timerTask);
	}
	
	public void tick(){
		for(int x = timerTasks.size() - 1; x >= 0; x--){
			TimerTask timerTask = timerTasks.get(x);
			timerTask.setTimeToInvoke(timerTask.getTimeToInvoke() - delta);
			if(timerTask.getTimeToInvoke() <= 0){
				timerTask.getTask().invoke();
				timerTasks.remove(x);
			}
		}
	}
}
