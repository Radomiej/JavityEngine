package org.javity.ui.smart;

import org.jrenner.smartfont.SmartFontGenerator;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Skin.TintedDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.SerializationException;
import com.badlogic.gdx.utils.Json.ReadOnlySerializer;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.ReflectionException;

public class SmartSkin extends Skin {
	
	public SmartSkin(FileHandle filePath) {
		super(filePath);
	}

	@Override
	protected Json getJsonLoader (final FileHandle skinFile) {
		final Skin skin = this;

		final Json json = new Json() {
			public <T> T readValue (Class<T> type, Class elementType, JsonValue jsonData) {
				// If the JSON is a string but the type is not, look up the actual value by name.
				if (jsonData.isString() && !ClassReflection.isAssignableFrom(CharSequence.class, type))
					return get(jsonData.asString(), type);
				return super.readValue(type, elementType, jsonData);
			}
		};
		json.setTypeName(null);
		json.setUsePrototypes(false);

		json.setSerializer(Skin.class, new ReadOnlySerializer<Skin>() {
			public Skin read (Json json, JsonValue typeToValueMap, Class ignored) {
				for (JsonValue valueMap = typeToValueMap.child; valueMap != null; valueMap = valueMap.next) {
					try {
						readNamedObjects(json, ClassReflection.forName(valueMap.name()), valueMap);
					} catch (ReflectionException ex) {
						throw new SerializationException(ex);
					}
				}
				return skin;
			}

			private void readNamedObjects (Json json, Class type, JsonValue valueMap) {
				Class addType = type == TintedDrawable.class ? Drawable.class : type;
				for (JsonValue valueEntry = valueMap.child; valueEntry != null; valueEntry = valueEntry.next) {
					Object object = json.readValue(type, valueEntry);
					if (object == null) continue;
					try {
						add(valueEntry.name, object, addType);
						if (addType != Drawable.class && ClassReflection.isAssignableFrom(Drawable.class, addType))
							add(valueEntry.name, object, Drawable.class);
					} catch (Exception ex) {
						throw new SerializationException(
							"Error reading " + ClassReflection.getSimpleName(type) + ": " + valueEntry.name, ex);
					}
				}
			}
		});

		json.setSerializer(BitmapFont.class, new ReadOnlySerializer<BitmapFont>() {
			public BitmapFont read (Json json, JsonValue jsonData, Class type) {
				String path = json.readValue("file", String.class, jsonData);
				int scaledSize = json.readValue("scaledSize", int.class, -1, jsonData);
				int fontSize = json.readValue("fontSize", int.class, 22, jsonData);
				Boolean flip = json.readValue("flip", Boolean.class, false, jsonData);
				Boolean markupEnabled = json.readValue("markupEnabled", Boolean.class, false, jsonData);

				FileHandle fontFile = skinFile.parent().child(path);
				if (!fontFile.exists()) fontFile = Gdx.files.internal(path);
				if (!fontFile.exists()) throw new SerializationException("Font file not found: " + fontFile);

//				System.out.println("font: " + path + " ext: " + fontFile.extension());
				//Support .ttf
				if(fontFile.extension().equals("otf") || fontFile.extension().equals("ttf")){
					Gdx.app.debug(SmartSkin.class.getSimpleName(), "Generate smart font: " + path + " size: " + fontSize);
					SmartFontGenerator sfg = new SmartFontGenerator();
					BitmapFont font = sfg.createFont(fontFile, fontFile.nameWithoutExtension() + fontSize, fontSize);
					font.getData().setScale(2f);
					return font;
				}
				
				// Use a region with the same name as the font, else use a PNG file in the same directory as the FNT file.
				String regionName = fontFile.nameWithoutExtension();
				try {
					BitmapFont font;
					Array<TextureRegion> regions = skin.getRegions(regionName);
					if (regions != null)
						font = new BitmapFont(new BitmapFontData(fontFile, flip), regions, true);
					else {
						TextureRegion region = skin.optional(regionName, TextureRegion.class);
						if (region != null)
							font = new BitmapFont(fontFile, region, flip);
						else {
							FileHandle imageFile = fontFile.parent().child(regionName + ".png");
							if (imageFile.exists())
								font = new BitmapFont(fontFile, imageFile, flip);
							else
								font = new BitmapFont(fontFile, flip);
						}
					}
					font.getData().markupEnabled = markupEnabled;
					// Scaled size is the desired cap height to scale the font to.
					if (scaledSize != -1) font.getData().setScale((scaledSize / font.getCapHeight()));
					return font;
				} catch (RuntimeException ex) {
					throw new SerializationException("Error loading bitmap font: " + fontFile, ex);
				}
			}
		});

		json.setSerializer(Color.class, new ReadOnlySerializer<Color>() {
			public Color read (Json json, JsonValue jsonData, Class type) {
				if (jsonData.isString()) return get(jsonData.asString(), Color.class);
				String hex = json.readValue("hex", String.class, (String)null, jsonData);
				if (hex != null) return Color.valueOf(hex);
				float r = json.readValue("r", float.class, 0f, jsonData);
				float g = json.readValue("g", float.class, 0f, jsonData);
				float b = json.readValue("b", float.class, 0f, jsonData);
				float a = json.readValue("a", float.class, 1f, jsonData);
				return new Color(r, g, b, a);
			}
		});

		json.setSerializer(TintedDrawable.class, new ReadOnlySerializer() {
			public Object read (Json json, JsonValue jsonData, Class type) {
				String name = json.readValue("name", String.class, jsonData);
				Color color = json.readValue("color", Color.class, jsonData);
				Drawable drawable = newDrawable(name, color);
				if (drawable instanceof BaseDrawable) {
					BaseDrawable named = (BaseDrawable)drawable;
					named.setName(jsonData.name + " (" + name + ", " + color + ")");
				}
				return drawable;
			}
		});

		return json;
	}
}
