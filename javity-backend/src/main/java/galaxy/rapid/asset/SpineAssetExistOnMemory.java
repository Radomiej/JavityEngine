package galaxy.rapid.asset;

public class SpineAssetExistOnMemory extends RuntimeException
{	
	private static final long serialVersionUID = -7863857744441850421L;

	public SpineAssetExistOnMemory(String skinName) {
		super(skinName);
	}

}
