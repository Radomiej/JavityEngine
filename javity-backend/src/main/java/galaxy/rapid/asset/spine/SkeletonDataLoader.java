package galaxy.rapid.asset.spine;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.spine.SkeletonBinary;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.SkeletonJson;
import galaxy.rapid.asset.spine.SkeletonDataLoaderParameter;
/**
 * AssetLoader for {@link SkeletonData} instances.
 * Loads an exported Spine's skeleton data.
 * The atlas with the images will be loaded as a dependency. This has to be declared as a {@link SkeletonDataLoaderParameter}
 * in the  {@link AssetManager#load(String, Class, AssetLoaderParameters)} call.
 * Supports both binary and JSON skeleton format files. If the animation file name has a 'skel' extension,
 * it will be loaded as binary. Any other extension will be assumed as JSON.
 * <p>
 * Example: suppose you have 'data/spine/character.atlas', 'data/spine/character.png' and 'data/spine/character.skel'.
 * To load it with an asset manager, just do the following:
 * <pre>
 * assetManager.setLoader(SkeletonData.class, new SkeletonDataLoader(new InternalFileHandleResolver()));
 * SkeletonDataLoaderParameter parameter = new SkeletonDataLoaderParameter("data/spine/character.atlas");
 * assetManager.load("data/spine/character.skel", SkeletonData.class, parameter);
 * </pre>
 * @author Alvaro Barbeira
 */
public class SkeletonDataLoader extends AsynchronousAssetLoader<SkeletonData, SkeletonDataLoaderParameter> {
	SkeletonData skeletonData;

	public SkeletonDataLoader () {
		this(new InternalFileHandleResolver());
	}

	public SkeletonDataLoader (FileHandleResolver resolver) {
		super(resolver);
	}

	@Override
	public void loadAsync (AssetManager manager, String fileName, FileHandle file, SkeletonDataLoaderParameter parameter) {
		skeletonData = null;
		TextureAtlas atlas = manager.get(parameter.atlasName, TextureAtlas.class);

		String extension = file.extension();
		if (extension.toLowerCase().equals("skel")) {
			SkeletonBinary skeletonBinary = new SkeletonBinary(atlas);
			skeletonBinary.setScale(parameter.scale);
			skeletonData = skeletonBinary.readSkeletonData(file);
		} else {
			SkeletonJson skeletonJson = new SkeletonJson(atlas);
			skeletonJson.setScale(parameter.scale);
			skeletonData = skeletonJson.readSkeletonData(file);
		}
	}

	@Override
	public SkeletonData loadSync (AssetManager manager, String fileName, FileHandle file, SkeletonDataLoaderParameter parameter) {
		return skeletonData;
	}

	@Override
	public Array<AssetDescriptor> getDependencies (String fileName, FileHandle file, SkeletonDataLoaderParameter parameter) {
		Array<AssetDescriptor> deps = new Array<AssetDescriptor>();
		deps.add(new AssetDescriptor(parameter.atlasName, TextureAtlas.class));
		return deps;
	}
}