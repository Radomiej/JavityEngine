package pl.radomiej.javity.intent;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OsmAndHelper {
	private static final String PREFIX = "osmand.api://";

	// Result codes
	// RESULT_OK == -1
	// RESULT_CANCELED == 0
	// RESULT_FIRST_USER == 1
	// from Activity
	public static final int RESULT_CODE_ERROR_UNKNOWN = 1001;
	public static final int RESULT_CODE_ERROR_NOT_IMPLEMENTED = 1002;
	public static final int RESULT_CODE_ERROR_PLUGIN_INACTIVE = 1003;
	public static final int RESULT_CODE_ERROR_GPX_NOT_FOUND = 1004;
	public static final int RESULT_CODE_ERROR_INVALID_PROFILE = 1005;

	// Information
	private static final String GET_INFO = "get_info";

	// Related to recording media
	private static final String RECORD_AUDIO = "record_audio";
	private static final String RECORD_VIDEO = "record_video";
	private static final String RECORD_PHOTO = "record_photo";
	private static final String STOP_AV_REC = "stop_av_rec";

	private static final String ADD_FAVORITE = "add_favorite";
	private static final String ADD_MAP_MARKER = "add_map_marker";

	private static final String SHOW_GPX = "show_gpx";
	private static final String NAVIGATE_GPX = "navigate_gpx";

	private static final String NAVIGATE = "navigate";

	private static final String START_GPX_REC = "start_gpx_rec";
	private static final String STOP_GPX_REC = "stop_gpx_rec";

	// Parameters
	public static final String API_CMD_SUBSCRIBE_VOICE_NOTIFICATIONS = "subscribe_voice_notifications";

	public static final String PARAM_NAME = "name";
	public static final String PARAM_DESC = "desc";
	public static final String PARAM_CATEGORY = "category";
	public static final String PARAM_LAT = "lat";
	public static final String PARAM_LON = "lon";
	public static final String PARAM_COLOR = "color";
	public static final String PARAM_VISIBLE = "visible";

	public static final String PARAM_PATH = "path";
	public static final String PARAM_URI = "uri";
	public static final String PARAM_DATA = "data";
	public static final String PARAM_FORCE = "force";

	public static final String PARAM_START_NAME = "start_name";
	public static final String PARAM_DEST_NAME = "dest_name";
	public static final String PARAM_START_LAT = "start_lat";
	public static final String PARAM_START_LON = "start_lon";
	public static final String PARAM_DEST_LAT = "dest_lat";
	public static final String PARAM_DEST_LON = "dest_lon";
	public static final String PARAM_PROFILE = "profile";

	public static final String PARAM_ETA = "eta";
	public static final String PARAM_TIME_LEFT = "time_left";
	public static final String PARAM_DISTANCE_LEFT = "time_distance_left";

	public static final String PARAM_CLOSE_AFTER_COMMAND = "close_after_command";

	private final int mRequestCode;
	private final Activity mActivity;
	private final OnOsmandMissingListener mOsmandMissingListener;

	public OsmAndHelper(Activity activity, int requestCode, OnOsmandMissingListener listener) {
		this.mRequestCode = requestCode;
		mActivity = activity;
		mOsmandMissingListener = listener;
	}

	/**
	 * Simply requests data about OsmAnd status. Data returned as extras. Each
	 * key value pair as separate entity.
	 */
	public void getInfo() {
		sendRequest(new OsmAndIntentBuilder(GET_INFO));
	}

	/**
	 * Request to start recording audio note for given location. Audio video
	 * notes plugin must be enabled. Otherwise OsmAnd will return
	 * RESULT_CODE_ERROR_PLUGIN_INACTIVE.
	 *
	 * @param lat
	 *            - latitude. Sent as URI parameter.
	 * @param lon
	 *            - longitude. Sent as URI parameter.
	 */
	public void recordAudio(double lat, double lon) {
		// test record audio
		Map<String, String> params = new HashMap<String, String>();
		params.put(PARAM_LAT, String.valueOf(lat));
		params.put(PARAM_LON, String.valueOf(lon));
		sendRequest(new OsmAndIntentBuilder(RECORD_AUDIO).setParams(params));
	}

	/**
	 * Request to start recording video note for given location. Audio video
	 * notes plugin must be enabled. Otherwise OsmAnd will return
	 * RESULT_CODE_ERROR_PLUGIN_INACTIVE.
	 *
	 * @param lat
	 *            - latitude. Sent as URI parameter.
	 * @param lon
	 *            - longitude. Sent as URI parameter.
	 */
	public void recordVideo(double lat, double lon) {
		// test record video
		Map<String, String> params = new HashMap<String, String>();
		params.put(PARAM_LAT, String.valueOf(lat));
		params.put(PARAM_LON, String.valueOf(lon));
		sendRequest(new OsmAndIntentBuilder(RECORD_VIDEO).setParams(params));
	}

	/**
	 * Request to take photo for given location. Audio video notes plugin must
	 * be enabled. Otherwise OsmAnd will return
	 * RESULT_CODE_ERROR_PLUGIN_INACTIVE.
	 *
	 * @param lat
	 *            - latitude. Sent as URI parameter.
	 * @param lon
	 *            - longitude. Sent as URI parameter.
	 */
	public void takePhoto(double lat, double lon) {
		// test record photo
		Map<String, String> params = new HashMap<String, String>();
		params.put(PARAM_LAT, String.valueOf(lat));
		params.put(PARAM_LON, String.valueOf(lon));
		sendRequest(new OsmAndIntentBuilder(RECORD_PHOTO).setParams(params));
	}

	/**
	 * Stop recording audio or video. Audio video notes plugin must be enabled.
	 * Otherwise OsmAnd will return RESULT_CODE_ERROR_PLUGIN_INACTIVE.
	 */
	public void stopAvRec() {
		// test stop recording
		sendRequest(new OsmAndIntentBuilder(STOP_AV_REC));
	}

	/**
	 * Add map marker at given location.
	 *
	 * @param lat
	 *            - latitude. Sent as URI parameter.
	 * @param lon
	 *            - longitude. Sent as URI parameter.
	 * @param name
	 *            - name. Sent as URI parameter.
	 */
	public void addMapMarker(double lat, double lon, String name) {
		// test marker
		Map<String, String> params = new HashMap<String, String>();
		params.put(PARAM_LAT, String.valueOf(lat));
		params.put(PARAM_LON, String.valueOf(lon));
		params.put(PARAM_NAME, name);
		sendRequest(new OsmAndIntentBuilder(ADD_MAP_MARKER).setParams(params));
	}

	// TODO covert color to set

	/**
	 * Add favourite at given location
	 *
	 * @param lat
	 *            - latitude. Sent as URI parameter.
	 * @param lon
	 *            - longitude. Sent as URI parameter.
	 * @param name
	 *            - name of favourite item. Sent as URI parameter.
	 * @param description
	 *            - description of favourite item. Sent as URI parameter.
	 * @param category
	 *            - category of favourite item. Sent as URI parameter. Symbols
	 *            that are not safe for directory name will be removed.
	 * @param color
	 *            - color of favourite item. Can be one of: "red", "orange",
	 *            "yellow", "lightgreen", "green", "lightblue", "blue",
	 *            "purple", "pink", "brown". Sent as URI parameter.
	 * @param visible
	 *            - should favourite item be visible after creation. Sent as URI
	 *            parameter.
	 */
	public void addFavorite(double lat, double lon, String name, String description, String category, String color,
			boolean visible) {
		// test favorite
		Map<String, String> params = new HashMap<String, String>();
		params.put(PARAM_LAT, String.valueOf(lat));
		params.put(PARAM_LON, String.valueOf(lon));
		params.put(PARAM_NAME, name);
		params.put(PARAM_DESC, description);
		params.put(PARAM_CATEGORY, category);
		params.put(PARAM_COLOR, color);
		params.put(PARAM_VISIBLE, String.valueOf(visible));
		sendRequest(new OsmAndIntentBuilder(ADD_FAVORITE).setParams(params));
	}

	/**
	 * Start recording GPX track.
	 *
	 * @param closeAfterCommand
	 *            - true if OsmAnd should be close immediately after executing
	 *            command. Sent as URI parameter.
	 */
	public void startGpxRec(boolean closeAfterCommand) {
		// test start gpx recording
		Map<String, String> params = new HashMap<String, String>();
		params.put(PARAM_CLOSE_AFTER_COMMAND, String.valueOf(closeAfterCommand));
		sendRequest(new OsmAndIntentBuilder(START_GPX_REC).setParams(params));
	}

	/**
	 * Stop recording GPX track.
	 *
	 * @param closeAfterCommand
	 *            - true if OsmAnd should be close immediately after executing
	 *            command. Sent as URI parameter.
	 */
	public void stopGpxRec(boolean closeAfterCommand) {
		// test stop gpx recording
		Map<String, String> params = new HashMap<String, String>();
		params.put(PARAM_CLOSE_AFTER_COMMAND, String.valueOf(closeAfterCommand));
		sendRequest(new OsmAndIntentBuilder(STOP_GPX_REC).setParams(params));
	}

	/**
	 * Show GPX file on map. OsmAnd must have rights to access location. Not
	 * recommended.
	 *
	 * @param file
	 *            - File which represents GPX track. Sent as URI parameter.
	 */
	public void showGpxFile(File file) {
		// test show gpx (path)
		Map<String, String> params = new HashMap<String, String>();
		try {
			params.put(PARAM_PATH, URLEncoder.encode(file.getAbsolutePath(), "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		sendRequest(new OsmAndIntentBuilder(SHOW_GPX).setParams(params));
	}

	/**
	 * Show GPX file on map. In current implementation it is recommended way to
	 * share file if your app supports API 15.
	 *
	 * @param data
	 *            - Raw contents of GPX file. Sent as intent's extra string
	 *            parameter.
	 */
	public void showRawGpx(String data) {
		// test show gpx (data)
		Map<String, String> extraData = new HashMap<String, String>();
		extraData.put(PARAM_DATA, data);
		sendRequest(new OsmAndIntentBuilder(SHOW_GPX).setExtraData(extraData));
	}

	@TargetApi(16)
	/**
	 * Show GPX file on map. Recommended way to share file. In current
	 * implementation it is recommended way to share file if your app supports
	 * API 16 and above.
	 * 
	 * @param gpxUri
	 *            - URI created by FileProvider. Sent as ClipData.
	 */
	public void showGpxUri(Uri gpxUri) {
		// test show gpx (uri)
		Map<String, String> params = new HashMap<String, String>();
		params.put(PARAM_URI, "true");
		sendRequest(new OsmAndIntentBuilder(SHOW_GPX).setParams(params).setGpxUri(gpxUri)
				.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
	}

	/**
	 * Navigate GPX file. OsmAnd must have rights to access location. Not
	 * recommended.
	 *
	 * @param file
	 *            - File which represents GPX track. Sent as URI parameter.
	 * @param force
	 *            - Stop previous navigation if active. Sent as URI parameter.
	 */
	public void navigateGpxFile(boolean force, File file) {
		// test navigate gpx (file)
		Map<String, String> params = new HashMap<String, String>();
		params.put(PARAM_FORCE, String.valueOf(force));
		try {
			params.put(PARAM_PATH, URLEncoder.encode(file.getAbsolutePath(), "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		sendRequest(new OsmAndIntentBuilder(NAVIGATE_GPX).setParams(params));
	}

	/**
	 * Navigate GPX file. In current implementation it is recommended way to
	 * share file if your app supports API 15.
	 *
	 * @param data
	 *            - Raw contents of GPX file. Sent as intent's extra string
	 *            parameter.
	 * @param force
	 *            - Stop previous navigation if active. Sent as URI parameter.
	 */
	public void navigateRawGpx(boolean force, String data) {
		// test navigate gpx (data)
		Map<String, String> params = new HashMap<String, String>();
		params.put(PARAM_FORCE, String.valueOf(force));
		Map<String, String> extraData = new HashMap<String, String>();
		extraData.put(PARAM_DATA, data);
		sendRequest(new OsmAndIntentBuilder(NAVIGATE_GPX).setParams(params).setExtraData(extraData));
	}

	@TargetApi(16)
	/**
	 * Navigate GPX file. Recommended way to share file. In current
	 * implementation it is recommended way to share file if your app supports
	 * API 16 and above.
	 * 
	 * @param gpxUri
	 *            - URI created by FileProvider. Sent as ClipData.
	 * @param force
	 *            - Stop previous navigation if active. Sent as URI parameter.
	 */
	public void navigateGpxUri(boolean force, Uri gpxUri) {
		// test navigate gpx (uri)
		Map<String, String> params = new HashMap<String, String>();
		params.put(PARAM_URI, "true");
		params.put(PARAM_FORCE, String.valueOf(force));
		sendRequest(new OsmAndIntentBuilder(NAVIGATE_GPX).setParams(params).setGpxUri(gpxUri)
				.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
	}

	/**
	 * Navigate from one location to another.
	 *
	 * @param startName
	 *            - Name of starting point. Sent as URI parameter.
	 * @param startLat
	 *            - Start latitude. Sent as URI parameter.
	 * @param startLon
	 *            - Start longitude. Sent as URI parameter.
	 * @param destName
	 *            - Name of destination point. Sent as URI parameter.
	 * @param destLat
	 *            - Destination latitude. Sent as URI parameter.
	 * @param destLon
	 *            - Destination longitude. Sent as URI parameter.
	 * @param profile
	 *            - Map profile can be one of: "default", "car", "bicycle",
	 *            "pedestrian", "aircraft", "boat", "hiking", "motorcycle",
	 *            "truck". Sent as URI parameter.
	 * @param force
	 *            - Stop previous navigation if active. Sent as URI parameter.
	 */
	public void navigate(String startName, double startLat, double startLon, String destName, double destLat,
			double destLon, String profile, boolean force) {
		// test navigate
		Map<String, String> params = new HashMap<String, String>();
		params.put(PARAM_START_LAT, String.valueOf(startLat));
		params.put(PARAM_START_LON, String.valueOf(startLon));
		params.put(PARAM_START_NAME, startName);
		params.put(PARAM_DEST_LAT, String.valueOf(destLat));
		params.put(PARAM_DEST_LON, String.valueOf(destLon));
		params.put(PARAM_DEST_NAME, destName);
		params.put(PARAM_PROFILE, profile);
		params.put(PARAM_FORCE, String.valueOf(force));
		sendRequest(new OsmAndIntentBuilder(NAVIGATE).setParams(params));
	}

	/**
	 * Creates intent and executes request.
	 *
	 * @param intentBuilder
	 *            - contains intent parameters.
	 */
	private void sendRequest(OsmAndIntentBuilder intentBuilder) {
		Uri uri = intentBuilder.getUri();
		Intent intent = new Intent(Intent.ACTION_VIEW, uri);
		intent.addFlags(intentBuilder.getFlags());
		Map<String, String> extraData = intentBuilder.getExtraData();
		if (extraData != null) {
			for (String key : extraData.keySet()) {
				intent.putExtra(key, extraData.get(key));
			}
		}
		if (intentBuilder.getGpxUri() != null) {
			ClipData clipData = ClipData.newRawUri("Gpx", intentBuilder.getGpxUri());
			intent.setClipData(clipData);
		}
		if (isIntentSafe(intent)) {
			mActivity.startActivityForResult(intent, mRequestCode);
		} else {
			mOsmandMissingListener.osmandMissing();
		}
	}

	/**
	 * Convenience method to validate if intent can be handled.
	 *
	 * @param intent
	 *            - intent to be checked
	 * @return true if activity that can handle intent was found. False
	 *         otherwise.
	 */
	public boolean isIntentSafe(Intent intent) {
		PackageManager packageManager = mActivity.getPackageManager();
		List activities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
		return activities.size() > 0;
	}

	public interface OnOsmandMissingListener {
		void osmandMissing();
	}

	private static class OsmAndIntentBuilder {
		final String command;
		Map<String, String> params;
		Map<String, String> extraData;
		int flags;
		Uri gpxUri;

		public OsmAndIntentBuilder(String command) {
			this.command = command;
		}

		public OsmAndIntentBuilder setExtraData(Map<String, String> extraData) {
			this.extraData = extraData;
			return this;
		}

		public OsmAndIntentBuilder setFlags(int flags) {
			this.flags = flags;
			return this;
		}

		public OsmAndIntentBuilder setGpxUri(Uri gpxUri) {
			this.gpxUri = gpxUri;
			return this;
		}

		public OsmAndIntentBuilder setParams(Map<String, String> params) {
			this.params = params;
			return this;
		}

		public Map<String, String> getParams() {
			return params;
		}

		public Uri getUri() {
			return Uri.parse(getUriString(command, params));
		}

		public Map<String, String> getExtraData() {
			return extraData;
		}

		public int getFlags() {
			return flags;
		}

		public Uri getGpxUri() {
			return gpxUri;
		}

		private static String getUriString(String command, Map<String, String> parameters) {
			StringBuilder stringBuilder = new StringBuilder(PREFIX);
			stringBuilder.append(command);
			if (parameters != null && parameters.size() > 0) {
				stringBuilder.append("?");
				for (String key : parameters.keySet()) {
					stringBuilder.append(key).append("=").append(parameters.get(key)).append("&");
				}
				stringBuilder.delete(stringBuilder.length() - 1, stringBuilder.length());
			}
			return stringBuilder.toString();
		}
	}
}
