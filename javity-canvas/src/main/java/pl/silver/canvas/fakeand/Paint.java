package pl.silver.canvas.fakeand;

import pl.silver.canvas.Color;
import pl.silver.canvas.fakeand.Paint.Cap;
import pl.silver.canvas.fakeand.Paint.Join;

public class Paint {
	public enum Style {
		FILL, STROKE
	};
	
	public enum Cap {
		BUTT, ROUND, SQUARE
	};
	
	public enum Join {
		MITER, ROUND, BEVEL
	};

	public static final int ANTI_ALIAS_FLAG = 0x00000001;
	public static final int DEV_KERN_TEXT_FLAG = 0x00000100;
	public static final int SUBPIXEL_TEXT_FLAG = 0x00000080;

	private boolean antiAlias;
	private Color color;
	private Style style = Style.STROKE;
	private Join strokeJoin = Join.ROUND;
	private float strokeWidth = 1;
	
	public Paint(Paint fillPaint) {
		throw new UnsupportedOperationException();
	}

	public Paint() {
	}

	public void setFlags(int i) {
		throw new UnsupportedOperationException();
	}

	public void setStyle(Style style) {
		this.style = style;
	}

	public void setTypeface(String default1) {
		throw new UnsupportedOperationException();		
	}

	public void setTypeface(Typeface font) {
		throw new UnsupportedOperationException();			
	}

	public float getTextSize() {
		throw new UnsupportedOperationException();	
	}

	public Shader getShader() {
		throw new UnsupportedOperationException();	
	}

	public float measureText(String text) {
		throw new UnsupportedOperationException();	
	}

	public void getTextBounds(String text, int i, int length, pl.silver.canvas.fakeand.Rect rect) {
		throw new UnsupportedOperationException();	
		
	}

	public void setStrokeWidth(float strokeWidth) {
		this.strokeWidth  = strokeWidth;
	}

	public float getStrokeWidth() {
		return strokeWidth;
	}

	public void setStrokeCap(Cap butt) {
		throw new UnsupportedOperationException();				
	}

	public void setStrokeJoin(Join strokeJoin) {
		this.strokeJoin = strokeJoin;
	}

	public void setStrokeMiter(Float strokeMiterLimit) {
		throw new UnsupportedOperationException();			
	}

	public void setPathEffect(Object object) {
		throw new UnsupportedOperationException();			
	}

	public void setTextSize(float floatValue) {
		throw new UnsupportedOperationException();			
	}

	public void setStrikeThruText(boolean b) {
		throw new UnsupportedOperationException();			
	}

	public void setUnderlineText(boolean b) {
		throw new UnsupportedOperationException();	
		
	}

	public void setColor(Color color) {
		this.color = color;
	}

	public void setColor(int col) {
		setColor(new Color(col));
	}

	public Color getColor() {
		return color;
	}

	public void getTextPath(String text, int i, int length, float x, float y, Path spanPath) {
		throw new UnsupportedOperationException();	
		
	}

	public void setShader(Gradient gr) {
		throw new UnsupportedOperationException();			
	}

	public void setAntiAlias(boolean antiAlias) {
		this.antiAlias = antiAlias;
	}

}
