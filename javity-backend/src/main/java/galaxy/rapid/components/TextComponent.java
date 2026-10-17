package galaxy.rapid.components;

import com.artemis.Component;
import com.badlogic.gdx.graphics.Color;

public class TextComponent extends Component {

	private String bitmapAsset;
	private String text;
	private Color color = Color.BLACK;
	
	public void setBitmapAsset(String resourcePath) {
		this.bitmapAsset = resourcePath;
	}

	public String getBitmapAsset() {
		return bitmapAsset;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public Color getColor() {
		return color;
	}

	public void setColor(Color color) {
		this.color = color;
	}

}
