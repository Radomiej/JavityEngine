package galaxy.rapid.asset;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.AnimationStateData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;

import galaxy.rapid.asset.spine.SkeletonDataLoader;
import galaxy.rapid.asset.spine.SkeletonDataLoaderParameter;
import galaxy.rapid.configuration.RapidConfiguration;

public enum RapidAsset {
	INSTANCE;

	private volatile AssetManager manager;
	private Map<String, Sprite> spriteMap = new HashMap<String, Sprite>(64);
	private Map<String, Sprite> spriteMemoryMap = new HashMap<String, Sprite>(64);
//	private Map<String, MultiTextureAtlas> atlasMap = new HashMap<String, MultiTextureAtlas>(10);
//	private Map<String, Sprite> atlasesSpriteMap = new HashMap<String, Sprite>(10);
	private Map<String, BitmapFont> fontsMap = new HashMap<String, BitmapFont>(32);
	
	private Set<String> atlases = new HashSet<String>();
	private FileHandleResolver handleResolver;

	private RapidAsset() {
		handleResolver = new RapidFileHandleResolver();
//		if (RapidConfiguration.INSTANCE.isDebugMode()) {
//			handleResolver = new InternalFileHandleResolver();
//		}

		manager = new AssetManager(handleResolver);
		manager.setLoader(SkeletonData.class, new SkeletonDataLoader());
	}

	public void loadSound(String path) {
		String assetName = path;
		manager.load(assetName, Sound.class);
	}
	
	public void loadMusic(String path) {
		manager.load(path, Music.class);
	}

	public Sound getSound(String resourcePath) {
		manager.finishLoadingAsset(resourcePath);
		return manager.get(resourcePath, Sound.class);
	}

	public Music getMusic(String resourcePath) {
		manager.finishLoadingAsset(resourcePath);
		return manager.get(resourcePath, Music.class);
	}

	/**
	 * @param fileName
	 *            path do texture with assets[root] folder
	 */
	public void loadTexture(String fileName) {
		manager.load(fileName, Texture.class);
	}

//	private void loadSprite() {
//		Gdx.app.log("RapidAsset", "Loading all sprites in current avaiable in device memory");
//		for (MultiTextureAtlas multiTextureAtlas : atlasMap.values()) {
//			multiTextureAtlas.signAllAssets(spriteMap);
//		}
//	}

	public void loadTextureAtlas(final String textureAtlasPath) {
//		MultiTextureAtlas multiTextureAtlas = new MultiTextureAtlas(textureAtlasAsset);
//		multiTextureAtlas.findAllAtlas(handleResolver, manager);
		manager.load(textureAtlasPath, TextureAtlas.class);
		manager.finishLoadingAsset(textureAtlasPath);
		TextureAtlas atlas = manager.get(textureAtlasPath);
		Array<AtlasRegion> regions = atlas.getRegions();
		for(AtlasRegion region : regions){
			String spriteName = region.name;
//			System.out.println("Region: " + spriteName);
			spriteMap.put(textureAtlasPath + "#" + spriteName, atlas.createSprite(spriteName));
		}
		
		atlases.add(textureAtlasPath);
	}

	public SpineAssetModel getSpine(String skeletonAssetName, final String skinName) {
		final String jsonFullName = skeletonAssetName;
		manager.finishLoadingAsset(jsonFullName);
		SkeletonData skeletonData = manager.get(jsonFullName, SkeletonData.class);
		
		SpineAssetModel newSpineAssetModel = new SpineAssetModel();
		newSpineAssetModel.setSkeleton(new Skeleton(skeletonData));
		newSpineAssetModel.getSkeleton().setSkin(skinName);

		AnimationStateData animationStateData = new AnimationStateData(skeletonData);
		AnimationState animationState = new AnimationState(animationStateData);
		newSpineAssetModel.setAnimationState(animationState);

		return newSpineAssetModel;
	}

	public void loadSprite(String resourcePath) {
		try {
			manager.load(resourcePath, Texture.class);
			manager.finishLoadingAsset(resourcePath);
			Sprite sprite = new Sprite((Texture) manager.get(resourcePath));
			spriteMap.put(resourcePath, sprite);
//			Gdx.app.debug("AssetManager", "put sprite: " + resourcePath);
		} catch (Exception exd) {
			exd.printStackTrace();
		}
	}

	public Sprite getSprite(String resourcePath) {
//		System.out.println("getSprite: " + spritePath + " instance: "+ spriteMap.get(spritePath));
		if(resourcePath == null || resourcePath.equals("null")){
			Gdx.app.error("AssetManager", "try get null sprite");
			return null;
		}
		
		Sprite loadingSprite = spriteMap.get(resourcePath);
		if (loadingSprite == null) {
			manager.finishLoadingAsset(resourcePath);
			loadingSprite = spriteMap.get(resourcePath);
		}
		
		if (loadingSprite == null) {
			loadingSprite = spriteMemoryMap.get(resourcePath);
		}
		
		if(loadingSprite == null){
			Gdx.app.error("AssetManager", "null sprite: " + resourcePath);
		}
		
		return loadingSprite;
	}

	public Sprite getAtlasSprite(String assetName) {
		Sprite loadingSprite = spriteMap.get(assetName);
		if (loadingSprite == null) {
			loadTextureAtlas(assetName.split("#")[0]);
			loadingSprite = spriteMap.get(loadingSprite);
		}
		return loadingSprite;
	}

	public void loadSpine(String spineAssetsPath) {
		
		final String spineAtlasFullName = spineAssetsPath.replace(".json", ".atlas");
		final String jsonFullName = spineAssetsPath;
		Gdx.app.log("RapidAsset", "spineAtlas: " + spineAtlasFullName);
		Gdx.app.log("RapidAsset", "jsonData: " + jsonFullName);

		SkeletonDataLoaderParameter parameter = new SkeletonDataLoaderParameter(spineAtlasFullName, 1);
		manager.load(jsonFullName, SkeletonData.class, parameter);

	}

	public void unloadSpine(String spineAssetsName) {
		final String jsonFullName = spineAssetsName;
		manager.unload(jsonFullName);
	}

	public void unloadTextureAtlas(String textureAtlasAsset) {
//		MultiTextureAtlas multiTextureAtlas = atlasMap.get(textureAtlasAsset);
//		multiTextureAtlas.dispose();
		TextureAtlas atlas = manager.get(textureAtlasAsset);
		atlas.dispose();
		atlases.remove(textureAtlasAsset);
	}

	public void unloadSound(String sound) {
		String assetName = "sounds/" + sound;
		manager.unload(assetName);
	}
	
	public float getProgress() {
		return manager.getProgress();
	}

	public void dispose() {
		manager.dispose();
	}

	public BitmapFont getBitmapFont(String fontResource) {
		if(fontsMap.containsKey(fontResource)){
			return fontsMap.get(fontResource);
		}
		manager.finishLoadingAsset(fontResource);
		return manager.get(fontResource);
	}
	public boolean isBitmapFontLoaded(String fontResource){
		if(fontsMap.containsKey(fontResource)){
			return true;
		}
		return manager.isLoaded(fontResource);
	}
	
	public void loadBitmapFont(String fontResource) {
		if(isBitmapFontLoaded(fontResource)) return;
		manager.load(fontResource, BitmapFont.class);
	}
	
	public void putBitmapFont(String fontFile, BitmapFont font){
		fontsMap.put(fontFile, font);
	}

	public void addMemorySprite(String spriteName, Sprite sprite) {
		spriteMemoryMap.put(spriteName, sprite);	
	}
	
	public Sprite getMemorySprite(String spriteName){
		return spriteMemoryMap.get(spriteName);
	}
}
