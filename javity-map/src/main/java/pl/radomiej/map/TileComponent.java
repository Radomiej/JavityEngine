package pl.radomiej.map;

import java.net.URL;
import java.util.Random;

import org.geojson.GeoJsonObject;
import org.geojson.GeoJsonObjectVisitor;
import org.javity.components.SpriteRenderer;
import org.javity.engine.JComponent;
import org.javity.engine.JResources;
import org.javity.engine.resources.SpriteResource;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;

import pl.radomiej.web.WWW;

public class TileComponent extends JComponent {
	private int zoom;
	private long x, y;
	private WWW www;
	public TileComponent() {
	}

	public TileComponent(long x, long y, int zoom) {
		this.zoom = zoom;
		this.x = x;
		this.y = y;
	}

	@Override
	public void start() {
		int randIndx = new Random().nextInt(3);
		String[] subdomains = {"a", "b", "c"};
		String subdomain = subdomains[randIndx];
		
		String url = "http://" + subdomain + ".tile.openstreetmap.org/" + zoom + "/" + x + "/" + y + ".png";
		www = new WWW(url);
		www.GET();
//		Gdx.app.debug(TileComponent.class.getSimpleName(), "Tile zoom: " + zoom + " x: " + x + " y: " + y);
		
		
	}
	
	private boolean waitToDownload = true;
	@Override
	public void update() {
		if(waitToDownload && !www.isWork()){
			waitToDownload = false;
			Texture texture = www.getTexture();
			
			if(texture == null){
				www.GET();
				return;
			}
			
			String tileName = "tile-" + zoom + "-" + x + "-" + y;
			SpriteResource spriteResource = JResources.addMemorySprite(tileName, texture);
			getGameObject().getComponent(SpriteRenderer.class).setSprite(spriteResource);
		}
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (x ^ (x >>> 32));
		result = prime * result + (int) (y ^ (y >>> 32));
		result = prime * result + zoom;
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TileComponent other = (TileComponent) obj;
		if (x != other.x)
			return false;
		if (y != other.y)
			return false;
		if (zoom != other.zoom)
			return false;
		return true;
	}
}
