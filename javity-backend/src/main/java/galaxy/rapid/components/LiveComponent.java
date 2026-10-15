package galaxy.rapid.components;

import com.artemis.Component;

public class LiveComponent extends Component {
	private int hp;
	private int hpMax;
	private float godModeTime;

	public LiveComponent() {
		this(3);
	}

	public LiveComponent(int hp) {
		this.hp = hpMax = hp;
	}

	public int getHp() {
		return hp;
	}

	public void setHp(int hp) {
		this.hp = hp;
	}

	public int getHpMax() {
		return hpMax;
	}

	public void setHpMax(int hpMax) {
		this.hpMax = hpMax;
	}

	public float getGodModeTime() {
		return godModeTime;
	}

	public void setGodModeTime(float godModeTime) {
		this.godModeTime = godModeTime;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Float.floatToIntBits(godModeTime);
		result = prime * result + hp;
		result = prime * result + hpMax;
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LiveComponent other = (LiveComponent) obj;
		if (Float.floatToIntBits(godModeTime) != Float.floatToIntBits(other.godModeTime))
			return false;
		if (hp != other.hp)
			return false;
		if (hpMax != other.hpMax)
			return false;
		return true;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "LiveComponent [hp=" + hp + ", hpMax=" + hpMax + ", godModeTime=" + godModeTime + "]";
	}
}
