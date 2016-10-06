package pl.radomiej.javity.geolocation;

import org.javity.engine.JInput;
import org.javity.engine.utilities.JNavigation;

import android.app.Activity;
import pl.radomiej.javity.intent.AndroidIntent;

public enum JAndroid {
	INSTANCE;
	
	public void init(Activity androidApp){
		JInput.geolocation = new AndroidGeolocation(androidApp);
		JNavigation.INSTANCE.setNativeIntent(new AndroidIntent(androidApp));
	}
}
