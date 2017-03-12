package pl.silver.canvas;

import java.util.concurrent.atomic.AtomicInteger;

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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Float.floatToIntBits(X);
		result = prime * result + Float.floatToIntBits(Y);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Position other = (Position) obj;
		if (Float.floatToIntBits(X) != Float.floatToIntBits(other.X))
			return false;
		if (Float.floatToIntBits(Y) != Float.floatToIntBits(other.Y))
			return false;
		return true;
	}
	
	
}
