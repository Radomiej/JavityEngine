package pl.radomiej.map;

public class Slippytest {
	public static void main(String[] args) {
		int zoom = 17;
		double lat = 52.757649d;
		double lon = 15.262154;
		System.out.println("http://tile.openstreetmap.org/" + getTileNumber(lat, lon, zoom) + ".png");
		getTileNumber2(lat, lon, zoom);
	}

	public static String getTileNumber(final double lat, final double lon, final int zoom) {
		int xtile = (int) Math.floor((lon + 180) / 360 * (1 << zoom));
		int ytile = (int) Math
				.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2
						* (1 << zoom));
		if (xtile < 0)
			xtile = 0;
		if (xtile >= (1 << zoom))
			xtile = ((1 << zoom) - 1);
		if (ytile < 0)
			ytile = 0;
		if (ytile >= (1 << zoom))
			ytile = ((1 << zoom) - 1);
		return ("" + zoom + "/" + xtile + "/" + ytile);
	}

	static private GeoPoint getTileNumber2(final double lat, final double lon, final int zoom) {

		double xtile = (lon + 180) / 360d * (double) (1 << zoom);

		double ytile = (1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2
				* (1 << zoom);
		if (xtile < 0)
			xtile = 0;
		if (xtile >= (1 << zoom))
			xtile = ((1 << zoom) - 1);
		if (ytile < 0)
			ytile = 0;
		if (ytile >= (1 << zoom))
			ytile = ((1 << zoom) - 1);
		
		System.out.println("http://tile.openstreetmap.org/" + ("" + zoom + "/" + xtile + "/" + ytile) + ".png");
		return new GeoPoint(ytile, xtile);
	}
}
