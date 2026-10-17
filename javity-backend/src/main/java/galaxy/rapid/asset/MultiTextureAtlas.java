package galaxy.rapid.asset;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.utils.Array;

public class MultiTextureAtlas {
	private String fullName;
	
	private Set<String> atlases = new HashSet<String>();
	private Set<String> assetsInAtlases = new HashSet<String>();
	private AssetManager manager;
	/**
	 * 
	 * @param fullName this is name in format [full-path-in-resources]/[atlas-name].[extansion]
	 */
	public MultiTextureAtlas(String fullName) {
		this.fullName = fullName;
	}
	
	public void findAllAtlas(FileHandleResolver fileHandleResolver, final AssetManager manager){
		this.manager = manager;
		int startIndex = 0;
		while(true){
			final String searchAtlas = fullName + startIndex ;//+ ".atlas";
			FileHandle fileAtlas = fileHandleResolver.resolve(searchAtlas);
			if(fileAtlas.exists()){
				atlases.add(searchAtlas);
				manager.load(searchAtlas, TextureAtlas.class);
				startIndex++;
			}	
			else{
				return;
			}
		}		
	}
	
	public void dispose(){
		for(String atlasName : atlases){
			manager.unload(atlasName);
		}
	}
	
	public Sprite getSprite(String regionName){
		for(String atlasName : atlases){
			TextureAtlas atlas = manager.get(atlasName);
			if(atlas.findRegion(regionName) != null){
				Gdx.app.log("MultiTextureAtlas" , "Znaleziono texture: " + regionName + " w " + atlas.toString());
				return atlas.createSprite(regionName);
			}
		}
		return null;		
	}

	public void signAllAssets(Map<String, Sprite> spriteMap) {
		for(String atlasName : atlases){
			TextureAtlas atlas = manager.get(atlasName);
			Array<AtlasRegion> regions = atlas.getRegions();
			for(AtlasRegion region : regions){
				String spriteName = region.name;
//				System.out.println("Region: " + spriteName);
				assetsInAtlases.add(spriteName);
				spriteMap.put(spriteName, atlas.createSprite(spriteName));
			}
		}
	}
}
