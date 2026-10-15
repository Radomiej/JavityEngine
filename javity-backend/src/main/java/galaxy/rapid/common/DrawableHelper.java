package galaxy.rapid.common;


import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.SpriteDrawable;

import galaxy.rapid.asset.RapidAsset;

public class DrawableHelper {


	public static Drawable getDrawableFromAsset(String regionName) {
		Sprite sprite = RapidAsset.INSTANCE.getSprite(regionName);
		SpriteDrawable drawable = new SpriteDrawable(sprite);
		return drawable;
	}
}
