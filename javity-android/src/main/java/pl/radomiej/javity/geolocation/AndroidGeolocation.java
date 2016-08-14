package pl.radomiej.javity.geolocation;

import org.javity.engine.utilities.GeolocationData;
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
	
	public AndroidGeolocation(Context context) {
		this.context = context;
		
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
		// TODO Auto-generated method stub

	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub

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
		// TODO Auto-generated method stub

	}

}
