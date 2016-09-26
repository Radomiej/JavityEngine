package pl.radomiej.map;

import org.geojson.Point;

import com.badlogic.gdx.math.Vector2;

/**
 * Created by Radomiej on 16.09.2016.
 */

/**
 * @author Peter Karich
 */
public class GeoPoint
{
    public double lat = Double.NaN;
    public double lon = Double.NaN;

    public GeoPoint()
    {
    }

    public GeoPoint( double lat, double lon )
    {
        this.lat = lat;
        this.lon = lon;
    }

    public double getLon()
    {
        return lon;
    }

    public double getLat()
    {
        return lat;
    }

    public boolean isValid()
    {
        return !Double.isNaN(lat) && !Double.isNaN(lon);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GeoPoint geoPoint = (GeoPoint) o;

        if (Double.compare(geoPoint.lat, lat) != 0) return false;
        return Double.compare(geoPoint.lon, lon) == 0;

    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(lat);
        result = (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(lon);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public String toString()
    {
        return lat + " " + lon;
    }

    /**
     * Attention: geoJson is LON,LAT
     */
    public Double[] toGeoJson()
    {
        return new Double[]
                {
                        lon, lat
                };
    }

    public Point toGeoJsonPoint(){
    	return new Point(lon, lat);
    }
    
    public Vector2 toVector2(){
    	return new Vector2((float)lon, (float)lat);
    }
    
    public static GeoPoint parse( String str )
    {
        // if the point is in the format of lat,lon we don't need to call geocoding service
        String[] fromStrs = str.split(",");
        if (fromStrs.length == 2)
        {
            try
            {
                double fromLat = Double.parseDouble(fromStrs[0]);
                double fromLon = Double.parseDouble(fromStrs[1]);
                return new GeoPoint(fromLat, fromLon);
            } catch (Exception ex)
            {
            }
        }
        return null;
    }

	public GeoPoint cpy() {
		return new GeoPoint(lat, lon);
	}
}