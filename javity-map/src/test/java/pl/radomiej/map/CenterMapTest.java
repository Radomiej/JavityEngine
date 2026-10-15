package pl.radomiej.map;

import static org.junit.Assert.*;

import org.javity.engine.JCamera;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.badlogic.gdx.math.Vector2;

public class CenterMapTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void test() {
		MapComponent mapComponent = new MapComponent();

		int zoom = mapComponent.maxZoom - mapComponent.minZoom + 0;
		Vector2 tile = new Vector2(); //MapComponent.getTileVector(52.731890, 15.238352, zoom);
		long size = (long) Math.pow(2, zoom);

		// 
		//
		//
//		tile.x -= size / 2f;
//		tile.y -= size / 2f;
//		tile.x *= 256;
//		tile.y *= -256;
//		tile.x += 1 / 2f;
//		tile.y -= 1 / 2f;
//		
//		System.out.println("center on: " + tile + " zoom: " + zoom + " size: " + size);

		
		Vector2 showPosition = tile.cpy();
		showPosition.x -= size / 2f;
		showPosition.y -= size / 2f;
		showPosition.x *= 256;
		showPosition.y *= -256;
		showPosition.x += 256 / 2f;
		showPosition.y -= 256 / 2f;

		System.out.println("showPosition: " + showPosition);
		
		
		Vector2 tilePosition = showPosition.cpy();
		tilePosition.x -= 256 / 2f;
		tilePosition.y += 256 / 2f;
		tilePosition.x /= 256;
		tilePosition.y /= -256;
		tilePosition.x += size / 2f;
		tilePosition.y += size / 2f;

		System.out.println("tilePosition: " + tilePosition);
		
	
		
		
		
		System.out.println("lat: " + tile2lat((int) tilePosition.x, zoom) + " lng: " +  tile2lon((int) tilePosition.y, zoom));
	}

	static double tile2lon(int x, int z) {
		return x / Math.pow(2.0, z) * 360.0 - 180;
	}

	static double tile2lat(int y, int z) {
		double n = Math.PI - (2.0 * Math.PI * y) / Math.pow(2.0, z);
		return Math.toDegrees(Math.atan(Math.sinh(n)));
	}
}
