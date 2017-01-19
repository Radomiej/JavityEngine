package pl.silver.canvas.fakeand;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxRuntimeException;

import pl.silver.canvas.libgdx.Matrix3Helper;

public class Matrix {
//	private static OrthographicCamera camera = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
//	
//	static{
//		camera.setToOrtho(true);
//	}	
//	Matrix3 matrixGdx;
	
	Affine2 affine;

	public Matrix() {
		affine  = new Affine2();
	}

	public Matrix(Matrix matrix) {
		affine = new Affine2(matrix.affine);
	}

	public void preConcat(Matrix matrix) {
		affine.preMul(matrix.affine);
	}

	public void postConcat(Matrix matrix) {
		affine.mul(matrix.affine);
	}

	public boolean invert(Matrix inverseMatrix) {
		if (affine.det() == 0) {
			return false;
		}

		inverseMatrix.affine.set(affine);
		inverseMatrix.affine.inv();
		return true;
	}

	public void mapPoints(float[] pts) {
		if(pts.length % 2 == 1){
			throw new IndexOutOfBoundsException("map points must be a pair of vertex. Current lenght: " + pts.length);
		}
		
//		camera.projection.set(affine);
//		camera.update();
		
		Vector2 point = new Vector2();
		for(int i = 0; i < pts.length; i += 2){
			point.set(pts[i], pts[i + 1]);
//			point = camera.unproject(point);
			affine.applyTo(point);
			pts[i] = point.x;
			pts[i + 1] = point.y;
		}
		
	}

	public void preTranslate(float x, float y) {
		affine.preTranslate(x, y);
	}

	public void postScale(float rx, float ry) {
		affine.scale(rx, ry);
	}

	public void postRotate(float angle) {
		affine.rotate(angle);
	}

	public void postTranslate(float cx, float cy) {
		affine.translate(cx, cy);
	}

	public void preRotate(float angle) {
		affine.preRotate(angle);
	}

	public void preScale(float scaleX, float scaleY) {
		affine.preScale(scaleX, scaleY);
	}

	public void reset() {
		affine.set(new Affine2());
	}

	public void setValues(float[] values) {
		affine.set(new Matrix3(values));
	}

	public void preRotate(float ang, float cx, float cy) {
		throw new UnsupportedOperationException();
	}

	
	public void preSkew(float shearX, float shearY) {
		affine.shear(shearX, shearY);
	}

}
