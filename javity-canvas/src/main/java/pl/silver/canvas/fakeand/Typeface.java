package pl.silver.canvas.fakeand;

public class Typeface {

	// TODO implement initialize
	public static final Typeface DEFAULT = null;
	public static final Typeface SERIF = null;
	public static final Typeface SANS_SERIF = null;
	public static final Typeface MONOSPACE = null;

	public static final int BOLD_ITALIC = 0x00000003;
	public static final int BOLD = 0x00000001;
	public static final int ITALIC = 0x00000002;
	public static final int NORMAL = 0x00000000;

	public static Typeface createFromAsset(AssetManager assetManager, String string) {
		throw new UnsupportedOperationException();
	}

	public static Typeface create(Typeface typeface, int typefaceStyle) {
		throw new UnsupportedOperationException();
	}

}
