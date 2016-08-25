package org.javity.engine.utilities;

public enum JNavigation implements Intent{
	INSTANCE;
	
	private Intent nativeIntent;
	
	public void setNativeIntent(Intent nativeIntent){
		this.nativeIntent = nativeIntent;
	}

	@Override
	public void navigate(String gpx) {
		if(nativeIntent != null) nativeIntent.navigate(gpx);
	}
}
