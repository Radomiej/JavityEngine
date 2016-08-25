package pl.radomiej.javity.intent;


import org.javity.engine.utilities.Intent;

import android.app.Activity;
import android.content.Context;

public class AndroidIntent implements OsmAndHelper.OnOsmandMissingListener, Intent{
	private Context context;
	private Activity activity;
	private OsmAndHelper osmand;
	
	public AndroidIntent(Activity activity) {
		this.activity = activity;
		osmand = new OsmAndHelper(activity, 1001, this);
	}
	
	@Override
	public void navigate(String gpx){
		osmand.navigateRawGpx(true, gpx);
	}
	

	@Override
	public void osmandMissing() {
		
	}
}