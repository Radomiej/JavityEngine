package org.javity.engine.utilities;

public interface JGeolocation {
	public boolean isEnabledByUser();
	public GeolocationData getLastData();
	public GeolocationStatus getStatus();
	
	public void start();
	public void stop();
}
