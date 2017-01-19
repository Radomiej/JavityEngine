package pl.silver.canvas;

public final class Position {
	public final float X, Y;

	public Position(float x, float y) {
		X = x;
		Y = y;
	}

	@Override
	public String toString() {
		return "Position [X=" + X + ", Y=" + Y + "]";
	}
}
