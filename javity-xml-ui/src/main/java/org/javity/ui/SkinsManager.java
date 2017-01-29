package org.javity.ui;

import java.util.HashMap;
import java.util.Map;

import org.javity.ui.smart.SmartSkin;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.SkinLoader;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Skin.TintedDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.SerializationException;
import com.badlogic.gdx.utils.Json.ReadOnlySerializer;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.ReflectionException;

public enum SkinsManager {
	INSTANCE;

	private Map<String, Skin> skinsMap = new HashMap<String, Skin>();
	private AssetManager assetManager = new AssetManager();

	public Skin getDefaultSkin() {
		String defaultName = "lml";
		Skin defaultSkin = skinsMap.get(defaultName);
		if (defaultSkin == null) {
			defaultSkin = new SmartSkin(Gdx.files.internal("gdx-skins-master/lml/skin/skin.json"));
			skinsMap.put(defaultName, defaultSkin);
		}

		return defaultSkin;
	}
}
