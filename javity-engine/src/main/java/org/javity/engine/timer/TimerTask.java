package org.javity.engine.timer;

public class TimerTask {
	private Task task;
	private float timeToInvoke;
	
	/**
	 * @return the task
	 */
	public Task getTask() {
		return task;
	}
	/**
	 * @param task the task to set
	 */
	public void setTask(Task task) {
		this.task = task;
	}
	/**
	 * @return the timeToInvoke
	 */
	public float getTimeToInvoke() {
		return timeToInvoke;
	}
	/**
	 * @param timeToInvoke the timeToInvoke to set
	 */
	public void setTimeToInvoke(float timeToInvoke) {
		this.timeToInvoke = timeToInvoke;
	}
}
