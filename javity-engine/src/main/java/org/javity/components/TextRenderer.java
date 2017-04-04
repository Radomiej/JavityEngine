package org.javity.components;

import org.javity.engine.NativeComponent;
import org.javity.engine.Resource;
import org.javity.engine.resources.SpriteAtlasResource;
import org.javity.engine.resources.SpriteResource;
import org.javity.engine.resources.TextureResource;
import org.jrenner.smartfont.SmartFontGenerator;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Rectangle;

import org.javity.engine.resources.BitmapFontResource;
import org.javity.engine.resources.MemorySpriteResource;
import org.javity.engine.resources.SingleSpriteResource;

import galaxy.rapid.asset.RapidAsset;
import galaxy.rapid.components.RenderComponent;
import galaxy.rapid.components.SpriteComponent;
import galaxy.rapid.components.TextComponent;

public class TextRenderer extends NativeComponent {

	private BitmapFontResource fontResource;
	private transient RenderComponent renderComponent;
	private transient TextComponent textComponent;
	private String text;
	private GlyphLayout layout;
	
	public TextRenderer() {
	}

	public TextRenderer(String fontResourcePath, String text) {
		this(fontResourcePath, text, 0);
	}

	public TextRenderer(String fontResourcePath, String text, int fontSize) {
		if (fontResourcePath.endsWith(".otf") || fontResourcePath.endsWith(".ttf")) {
			if (!RapidAsset.INSTANCE.isBitmapFontLoaded(fontResourcePath)) {
				System.out.println("generate font:");
				FileHandle fontFile = Gdx.files.internal(fontResourcePath);
				SmartFontGenerator sfg = new SmartFontGenerator();

				String fontAssetName = fontFile.nameWithoutExtension() + fontSize;
				BitmapFont font = sfg.createFont(fontFile, fontAssetName, fontSize);
				RapidAsset.INSTANCE.putBitmapFont(fontAssetName, font);
				fontResource = new BitmapFontResource(fontAssetName);
			}
		} else {
			fontResource = new BitmapFontResource(fontResourcePath);
		}
		
		BitmapFont font = RapidAsset.INSTANCE.getBitmapFont(fontResource.getResourcePath());
		layout = new GlyphLayout(font, text);
		this.text = text;
	}

	public TextRenderer(String fontResource) {
		this(fontResource, "");
	}

	@Override
	public void awake() {
		renderComponent = new RenderComponent();
		textComponent = new TextComponent();
		addNativeComponent(renderComponent);
		addNativeComponent(textComponent);

		setBitmapFont(fontResource);
	}

	public void setBitmapFont(BitmapFontResource fontResource2) {
		this.fontResource = fontResource2;
		if (textComponent != null)
			textComponent.setBitmapAsset(fontResource.getResourcePath());
	}

	@Override
	public void start() {
		textComponent.setText(text);
	}

	@Override
	public void update() {
		renderComponent.setOrderZ(getTransform().getOrderZ());
	}

	@Override
	public void remove() {
	}

	public BitmapFontResource getFontResource() {
		return fontResource;
	}

	public void setFontResource(BitmapFontResource fontResource) {
		this.fontResource = fontResource;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
		if (textComponent != null)
			textComponent.setText(text);
	}

	@Override
	public void onEnabled() {
		renderComponent.setRender(true);
	}

	@Override
	public void onDisable() {
		renderComponent.setRender(false);
	}

	public Rectangle getBounds() {
		Rectangle bound = new Rectangle();
		bound.setSize(layout.width, layout.height);
		bound.setCenter(getTransform().getPosition());
		System.out.println("font bound: " + bound);
		return bound;
	}
}
