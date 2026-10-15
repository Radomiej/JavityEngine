package galaxy.rapid.components;

import com.artemis.Component;

public class SpriteComponent extends Component {
	private String spriteAsset;
	private boolean atlas;
	private boolean memory;
	private boolean left, right, top, bottom;

	public SpriteComponent() {
	}

	public SpriteComponent(String string) {
		spriteAsset = string;
	}

	public String getSpriteAsset() {
		return spriteAsset;
	}

	public void setSpriteAsset(String spriteAsset) {
		this.spriteAsset = spriteAsset;
	}

	public boolean isAtlas() {
		return atlas;
	}

	public void setAtlas(boolean isAtlas) {
		this.atlas = isAtlas;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((spriteAsset == null) ? 0 : spriteAsset.hashCode());
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
		SpriteComponent other = (SpriteComponent) obj;
		if (spriteAsset == null) {
			if (other.spriteAsset != null)
				return false;
		} else if (!spriteAsset.equals(other.spriteAsset))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "SpriteComponent [spriteAsset=" + spriteAsset + "]";
	}

	public boolean isMemory() {
		return memory;
	}

	public void setMemory(boolean memory) {
		this.memory = memory;
	}

	/**
	 * @return the left
	 */
	public boolean isLeft() {
		return left;
	}

	/**
	 * @param left
	 *            the left to set
	 */
	public void setLeft(boolean left) {
		this.left = left;
	}

	/**
	 * @return the right
	 */
	public boolean isRight() {
		return right;
	}

	/**
	 * @param right
	 *            the right to set
	 */
	public void setRight(boolean right) {
		this.right = right;
	}

	/**
	 * @return the top
	 */
	public boolean isTop() {
		return top;
	}

	/**
	 * @param top
	 *            the top to set
	 */
	public void setTop(boolean top) {
		this.top = top;
	}

	/**
	 * @return the bottom
	 */
	public boolean isBottom() {
		return bottom;
	}

	/**
	 * @param bottom
	 *            the bottom to set
	 */
	public void setBottom(boolean bottom) {
		this.bottom = bottom;
	}
}
