package pl.radomiej.javity.geolocation;

import java.util.HashSet;
import java.util.Set;

import org.javity.engine.utilities.GeolocationData;
import org.javity.engine.utilities.GeolocationListener;
import org.javity.engine.utilities.GeolocationStatus;
import org.javity.engine.utilities.JGeolocation;

import com.badlogic.gdx.Gdx;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.GpsStatus.Listener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.provider.Settings;

public class AndroidGeolocation implements JGeolocation, LocationListener {

	private Context context;
	private LocationManager locationManager;
	private GeolocationData lastGeolocationData;
	private Set<GeolocationListener> listeners = new HashSet<>();
	
	public AndroidGeolocation(Context context) {
		this.context = context;
	}

	@Override
	public boolean isEnabledByUser() {
		return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
	}

	@Override
	public GeolocationData getLastData() {
		return lastGeolocationData;
	}

	@Override
	public GeolocationStatus getStatus() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void start() {
		int permissionCheck = context.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION,  android.os.Process.myPid(), android.os.Process.myUid());
		if(permissionCheck == PackageManager.PERMISSION_GRANTED){
			Gdx.app.log("AndroidGeolocation", "ACCESS_FINE_LOCATION granted");
		}else{
			Gdx.app.log("AndroidGeolocation", "ACCESS_FINE_LOCATION not allowed");
//			return;
		}
		
		
		locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
		boolean enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

		// check if enabled and if not send user to the GSP settings
		// Better solution would be to display a dialog and suggesting to
		// go to the settings
		if (!enabled) {
			Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
			context.startActivity(intent);
		}
		findTheBestProvider();

	}

	private void findTheBestProvider()
		{
	//		Criteria criteria = new Criteria();
	//		String provider = locationManager.getBestProvider(criteria, true);
	//		Gdx.app.log("AndroidGeolocation", "The Best Provider: " + provider);
			
			locationManager.removeUpdates(this);
			locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 400, 1, this);
		}

	@Override
	public void stop() {
		locationManager.removeUpdates(this);
	}

	@Override
	public void onLocationChanged(Location location) {
		lastGeolocationData = new GeolocationData();
		lastGeolocationData.setLatitude(location.getLatitude());
		lastGeolocationData.setLongitude(location.getLongitude());
		lastGeolocationData.setAltitude(location.getAltitude());
		lastGeolocationData.setTimestamp(location.getTime());
		lastGeolocationData.setAccuracy(location.getAccuracy());
		lastGeolocationData.setBearing(location.getBearing());
		lastGeolocationData.setSpeed(location.getSpeed());
		
		for(GeolocationListener geolocationListener : listeners){
			geolocationListener.geolocationChanged(lastGeolocationData);
		}
		
		Gdx.app.log("GPS", "Pos update: " + lastGeolocationData);
	}

	@Override
	public void onProviderDisabled(String provider) {
		findTheBestProvider();
	}

	@Override
	public void onProviderEnabled(String provider) {
		findTheBestProvider();
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		System.out.println("onStatusChanged provider: " + provider + " status: " + status + " bundle: " + extras);
	}

	@Override
	public void addGeolocationListener(GeolocationListener geolocationListener) {
		listeners.add(geolocationListener);
	}

	@Override
	public void removeGeolocationListener(GeolocationListener geolocationListener) {
		listeners.remove(geolocationListener);
	}

	@Override
	public void removeAllGeolocationListeners() {
		listeners.clear();
	}

}
