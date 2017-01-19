package pl.silver.canvas.fakeand;

import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Polyline;
import com.badlogic.gdx.math.Rectangle;

import pl.silver.canvas.Color;
import pl.silver.canvas.LineDefinition;
import pl.silver.canvas.PointDefinition;
import pl.silver.canvas.Position;
import pl.silver.canvas.PointDefinition.PointType;
import pl.silver.canvas.SilverCanvas;
import pl.silver.canvas.libgdx.LibGdxCanvas;
import pl.silver.canvas.shape.SilverPolyline;

public class Canvas {

	public static final int HAS_ALPHA_LAYER_SAVE_FLAG = 0x00000004;
	public static final int MATRIX_SAVE_FLAG = 0x00000001;

	private Matrix saveMatrix;
	private Rectangle saveClip;

	private Matrix currentMatrix;
	private Rectangle clip;

	private Bitmap bitmap;
	private SilverCanvas libgdxCanvas;
	
	public Canvas() {
		libgdxCanvas = new LibGdxCanvas();
	}
	
	public Canvas(Bitmap bitmap) {
		this();
		this.bitmap = bitmap;
	}

	public void save() {
		saveClip = new Rectangle(clip);
		saveMatrix = new Matrix(currentMatrix);
	}

	public void save(int matrixSaveFlag) {
		throw new UnsupportedOperationException();
	}

	public void restore() {
		clip = new Rectangle(saveClip);
		currentMatrix = new Matrix(saveMatrix);
	}

	public Matrix getMatrix() {
		return currentMatrix;
	}

	public void drawPath(Path path, Paint paint) {
		
		PointDefinition startPoint = new PointDefinition(PointType.DOT, paint.getStrokeWidth() + 1, 0, paint.getColor(), Color.CLEAR);
		LineDefinition lineDefinition = new LineDefinition(startPoint, startPoint, paint.getStrokeWidth(), 0, 0, paint.getColor(), paint.getColor());
		
		for(SilverPolyline polyline : path.getPolylines()){
			System.out.println("draw polyline");
			libgdxCanvas.drawPolyline(polyline.getPositions(), lineDefinition);
		}
		
	}

	public void drawBitmap(Bitmap maskedContent, int i, int j, Paint fillPaint) {
		throw new UnsupportedOperationException();
	}

	public void drawText(String text, float x, float y, Paint fillPaint) {
		throw new UnsupportedOperationException();
	}

	public void drawTextOnPath(String text, Path path, float x, float y, Paint fillPaint) {
		throw new UnsupportedOperationException();
	}

	public void setMatrix(Matrix matrix) {
		this.currentMatrix = matrix;
	}

	public void concat(Matrix matrix) {
		this.currentMatrix.preConcat(matrix);
	}

	public void translate(float x, float y) {
		currentMatrix.preTranslate(x, y);
	}

	public void clipRect(float left, float top, float right, float bottom) {
		throw new UnsupportedOperationException();
	}

	public void drawColor(int col) {
		throw new UnsupportedOperationException();
	}

	public float getWidth() {
		throw new UnsupportedOperationException();
		// return 0;
	}

	public float getHeight() {
		throw new UnsupportedOperationException();
		// return 0;
	}

	public void saveLayerAlpha(RectF bounds, int alpha, int saveFlags) {
		throw new UnsupportedOperationException();
	}

	public void clipPath(Path combinedPath) {
		throw new UnsupportedOperationException();
	}

	public void scale(float width, float height) {
		throw new UnsupportedOperationException();
		
	}

}
