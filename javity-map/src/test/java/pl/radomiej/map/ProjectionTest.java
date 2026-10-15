package pl.radomiej.map;

import com.badlogic.gdx.math.Vector2;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProjectionTest {
    @Test public void geoRoundTripDoesNotMutateWorldPosition() {
        MapComponent map=new MapComponent();
        Vector2 world=map.getWorldFromGeoPosition(52.2297,21.0122).toVector2();
        Vector2 before=world.cpy();
        GeoPoint result=map.getGeoFromWorldPosition(world);
        assertEquals(before,world);
        assertEquals(52.2297,result.getLat(),.0001);
        assertEquals(21.0122,result.getLon(),.0001);
    }

    @Test public void tileCoordinatesHaveCorrectLatitudeLongitudeOrder() {
        MapComponent map=new MapComponent(2,4);
        GeoPoint result=map.getGeoFromTilePosition(8,8);
        assertEquals(0,result.getLat(),.0001);
        assertEquals(0,result.getLon(),.0001);
        result=map.getGeoFromTilePosition(4,8);
        assertEquals(0,result.getLat(),.0001);
        assertEquals(-90,result.getLon(),.0001);
        var point=new LayerComponent(4).getGeoFromTilePosition(4,8);
        assertEquals(-90,point.getCoordinates().getLongitude(),.0001);
        assertEquals(0,point.getCoordinates().getLatitude(),.0001);
    }
}
