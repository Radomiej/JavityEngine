package pl.radomiej.map;

import java.net.URL;
import java.util.Random;

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
		Gdx.app.log(TileComponent.class.getSimpleName(), "Tile zoom: " + zoom + " x: " + x + " y: " + y);
		
		
	}
	
	private boolean waitToDownload = true;
	@Override
	public void update() {
		if(waitToDownload && !www.isWork()){
			waitToDownload = false;
			Texture texture = www.getTexture();
			String tileName = "tile-" + zoom + "-" + x + "-" + y;
			SpriteResource spriteResource = JResources.addMemorySprite(tileName, texture);
			getGameObject().getComponent(SpriteRenderer.class).setSprite(spriteResource);
		}
	}
}
