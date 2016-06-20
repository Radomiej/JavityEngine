package org.javity.components;

import org.javity.engine.NativeComponent;
import org.javity.engine.Resource;
import org.javity.engine.resources.SpriteAtlasResource;
import org.javity.engine.resources.SpriteResource;
import org.javity.engine.resources.TextureResource;
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
	
	public TextRenderer() {
	}

	public TextRenderer(String fontResourcePath, String text) {
		fontResource = new BitmapFontResource(fontResourcePath);
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
		if(textComponent != null) textComponent.setText(text);
	}

}
