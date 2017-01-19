package pl.silver.canvas;

public final class Color {
	public static final Color CLEAR = new Color(0, 0, 0, 0);
	public static final Color BLACK = new Color(0, 0, 0, 1);

	public static final Color WHITE = new Color(0xffffffff);
	public static final Color LIGHT_GRAY = new Color(0xbfbfbfff);
	public static final Color GRAY = new Color(0x7f7f7fff);
	public static final Color DARK_GRAY = new Color(0x3f3f3fff);

	public static final Color BLUE = new Color(0, 0, 1, 1);
	public static final Color NAVY = new Color(0, 0, 0.5f, 1);
	public static final Color ROYAL = new Color(0x4169e1ff);
	public static final Color SLATE = new Color(0x708090ff);
	public static final Color SKY = new Color(0x87ceebff);
	public static final Color CYAN = new Color(0, 1, 1, 1);
	public static final Color TEAL = new Color(0, 0.5f, 0.5f, 1);

	public static final Color GREEN = new Color(0x00ff00ff);
	public static final Color CHARTREUSE = new Color(0x7fff00ff);
	public static final Color LIME = new Color(0x32cd32ff);
	public static final Color FOREST = new Color(0x228b22ff);
	public static final Color OLIVE = new Color(0x6b8e23ff);

	public static final Color YELLOW = new Color(0xffff00ff);
	public static final Color GOLD = new Color(0xffd700ff);
	public static final Color GOLDENROD = new Color(0xdaa520ff);
	public static final Color ORANGE = new Color(0xffa500ff);

	public static final Color BROWN = new Color(0x8b4513ff);
	public static final Color TAN = new Color(0xd2b48cff);
	public static final Color FIREBRICK = new Color(0xb22222ff);

	public static final Color RED = new Color(0xff0000ff);
	public static final Color SCARLET = new Color(0xff341cff);
	public static final Color CORAL = new Color(0xff7f50ff);
	public static final Color SALMON = new Color(0xfa8072ff);
	public static final Color PINK = new Color(0xff69b4ff);
	public static final Color MAGENTA = new Color(1, 0, 1, 1);

	public static final Color PURPLE = new Color(0xa020f0ff);
	public static final Color VIOLET = new Color(0xee82eeff);
	public static final Color MAROON = new Color(0xb03060ff);

	public final float R, G, B, A;

	public Color(float r, float g, float b, float a) {
		R = r;
		G = g;
		B = b;
		A = a;
	}

	public Color(int value) {
		R = ((value & 0xff000000) >>> 24) / 255f;
		G = ((value & 0x00ff0000) >>> 16) / 255f;
		B = ((value & 0x0000ff00) >>> 8) / 255f;
		A = ((value & 0x000000ff)) / 255f;
	}

	public int toIntValue() {
		return toIntBits((int)(R * 255), (int)(G * 255), (int)(B * 255), (int)(A * 255));
	}
	
	public static int toIntBits (int r, int g, int b, int a) {
		return (r << 24) | (g << 16) | (b << 8) | a;
	}
}
