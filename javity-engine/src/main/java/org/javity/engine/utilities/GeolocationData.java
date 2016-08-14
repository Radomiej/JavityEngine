package org.javity.engine.utilities;

public class GeolocationData {
	private double latitude, longitude, altitude;
	private long timestamp;
	private float accuracy, bearing, speed;

	/**
	 * @return the latitude
	 */
	public double getLatitude() {
		return latitude;
	}

	/**
	 * @param latitude
	 *            the latitude to set
	 */
	public void setLatitude(double latitude) {
		this.latitude = latitude;
	}

	/**
	 * @return the longitude
	 */
	public double getLongitude() {
		return longitude;
	}

	/**
	 * @param longitude
	 *            the longitude to set
	 */
	public void setLongitude(double longitude) {
		this.longitude = longitude;
	}

	/**
	 * @return the altitude
	 */
	public double getAltitude() {
		return altitude;
	}

	/**
	 * @param altitude
	 *            the altitude to set
	 */
	public void setAltitude(double altitude) {
		this.altitude = altitude;
	}

	/**
	 * @return the timestamp
	 */
	public long getTimestamp() {
		return timestamp;
	}

	/**
	 * @param timestamp
	 *            the timestamp to set
	 */
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "GeolocationData [latitude=" + latitude + ", longitude=" + longitude + ", altitude=" + altitude
				+ ", timestamp=" + timestamp + ", accuracy=" + accuracy + ", bearing=" + bearing + ", speed=" + speed
				+ "]";
	}

	public float getAccuracy() {
		return accuracy;
	}

	public void setAccuracy(float accuracy) {
		this.accuracy = accuracy;
	}

	/**
	 * @return the bearing
	 */
	public float getBearing() {
		return bearing;
	}

	/**
	 * @param bearing
	 *            the bearing to set
	 */
	public void setBearing(float bearing) {
		this.bearing = bearing;
	}

	/**
	 * @return the speed
	 */
	public float getSpeed() {
		return speed;
	}

	/**
	 * @param speed
	 *            the speed to set
	 */
	public void setSpeed(float speed) {
		this.speed = speed;
	}
}
