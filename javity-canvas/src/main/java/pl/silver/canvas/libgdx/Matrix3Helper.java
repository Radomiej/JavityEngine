package pl.silver.canvas.libgdx;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix3;

public class Matrix3Helper {
	public static final int M00 = 0;
	public static final int M01 = 3;
	public static final int M02 = 6;
	public static final int M10 = 1;
	public static final int M11 = 4;
	public static final int M12 = 7;
	public static final int M20 = 2;
	public static final int M21 = 5;
	public static final int M22 = 8;

	public static Matrix3 preTranslate(float x, float y, Matrix3 matrix) {
		float[] tmp = new float[9];

		float[] val = matrix.val;
		tmp[M00] = 1;
		tmp[M10] = 0;
		tmp[M20] = 0;

		tmp[M01] = 0;
		tmp[M11] = 1;
		tmp[M21] = 0;

		tmp[M02] = x;
		tmp[M12] = y;
		tmp[M22] = 1;
		mul(tmp, val);
		return matrix;
	}

	private static void mul(float[] mata, float[] matb) {
		float v00 = mata[M00] * matb[M00] + mata[M01] * matb[M10] + mata[M02] * matb[M20];
		float v01 = mata[M00] * matb[M01] + mata[M01] * matb[M11] + mata[M02] * matb[M21];
		float v02 = mata[M00] * matb[M02] + mata[M01] * matb[M12] + mata[M02] * matb[M22];

		float v10 = mata[M10] * matb[M00] + mata[M11] * matb[M10] + mata[M12] * matb[M20];
		float v11 = mata[M10] * matb[M01] + mata[M11] * matb[M11] + mata[M12] * matb[M21];
		float v12 = mata[M10] * matb[M02] + mata[M11] * matb[M12] + mata[M12] * matb[M22];

		float v20 = mata[M20] * matb[M00] + mata[M21] * matb[M10] + mata[M22] * matb[M20];
		float v21 = mata[M20] * matb[M01] + mata[M21] * matb[M11] + mata[M22] * matb[M21];
		float v22 = mata[M20] * matb[M02] + mata[M21] * matb[M12] + mata[M22] * matb[M22];

		mata[M00] = v00;
		mata[M10] = v10;
		mata[M20] = v20;
		mata[M01] = v01;
		mata[M11] = v11;
		mata[M21] = v21;
		mata[M02] = v02;
		mata[M12] = v12;
		mata[M22] = v22;
	}

	/**
	 * Premultiplies this matrix with a (counter-clockwise) rotation matrix.
	 * Postmultiplication is also used by OpenGL ES' 1.x
	 * glTranslate/glRotate/glScale.
	 * 
	 * @param degrees
	 *            The angle in degrees
	 * @return This matrix for the purpose of chaining.
	 */
	public static Matrix3 preRotate(float degrees, Matrix3 matrixGdx) {
		return rotateRad(MathUtils.degreesToRadians * degrees, matrixGdx);
	}

	/**
	 * Premultiplies this matrix with a (counter-clockwise) rotation matrix.
	 * 
	 * @param radians
	 *            The angle in radians
	 * @return This matrix for the purpose of chaining.
	 */
	public static Matrix3 rotateRad(float radians, Matrix3 matrixGdx) {
		if (radians == 0)
			return matrixGdx;
		float cos = (float) Math.cos(radians);
		float sin = (float) Math.sin(radians);
		float[] tmp = new float[9];
		float[] val = matrixGdx.val;

		tmp[M00] = cos;
		tmp[M10] = sin;
		tmp[M20] = 0;

		tmp[M01] = -sin;
		tmp[M11] = cos;
		tmp[M21] = 0;

		tmp[M02] = 0;
		tmp[M12] = 0;
		tmp[M22] = 1;
		mul(tmp, val);
		return matrixGdx;
	}

	/**
	 * Postmultiplies this matrix with a scale matrix. Postmultiplication is
	 * also used by OpenGL ES' 1.x glTranslate/glRotate/glScale.
	 * 
	 * @param scaleX
	 *            The scale in the x-axis.
	 * @param scaleY
	 *            The scale in the y-axis.
	 * @return This matrix for the purpose of chaining.
	 */
	public static Matrix3 preScale(float scaleX, float scaleY, Matrix3 matrixGdx) {

		float[] tmp = new float[9];
		float[] val = matrixGdx.val;

		tmp[M00] = scaleX;
		tmp[M10] = 0;
		tmp[M20] = 0;
		tmp[M01] = 0;
		tmp[M11] = scaleY;
		tmp[M21] = 0;
		tmp[M02] = 0;
		tmp[M12] = 0;
		tmp[M22] = 1;
		mul(tmp, val);
		return matrixGdx;
	}

}
