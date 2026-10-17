package galaxy.rapid.asset.spine;

import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.esotericsoftware.spine.SkeletonData;

/**
 * Mandatory parameter to be passed to {@link AssetManager#load(String, Class, AssetLoaderParameters)}.
 * This will insure the skeleton data is loaded correctly
 * @author Alvaro Barbeira
 */
public class SkeletonDataLoaderParameter extends AssetLoaderParameters<SkeletonData> {
	// A SkeletonJson must be loaded from an atlas.
	public String atlasName;
	public float scale;

	public SkeletonDataLoaderParameter (String atlasPath, float scale) {
		this.atlasName = atlasPath;
		this.scale = scale;
	}

	public SkeletonDataLoaderParameter (String atlasPath) {
		this(atlasPath, 1);
	}
}