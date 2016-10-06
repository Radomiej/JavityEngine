package org.javity.engine.utilities;

public interface JGeolocation {
	public boolean isEnabledByUser();
	public GeolocationData getLastData();
	public GeolocationStatus getStatus();
	public void addGeolocationListener(GeolocationListener geolocationListener);
	public void removeGeolocationListener(GeolocationListener geolocationListener);
	public void removeAllGeolocationListeners();
	public void start();
	public void stop();
}
