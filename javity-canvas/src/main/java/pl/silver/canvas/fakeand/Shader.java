package pl.silver.canvas.fakeand;

public class Shader {
	private Matrix localMatrix;
	public void getLocalMatrix(Matrix copyMatrix) {
		copyMatrix.affine.set(localMatrix.affine);
	}

	public void setLocalMatrix(Matrix newShaderMatrix) {
		localMatrix.affine.set(newShaderMatrix.affine);
	}

}
