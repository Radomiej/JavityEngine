package galaxy.rapid.configuration;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.SerializationException;

public enum RapidConfiguration {
	INSTANCE;

	private final String CONFIG_ASSET = "rapid.properties";
	private RapidConfig rapidConfig;

	/**
	 * Load application configuration with device memory
	 */
	public void load() {
		loadRapidConfiguration();
	}


	private void loadRapidConfiguration() {
		Json json = new Json();
		FileHandle jsonFile = Gdx.files.internal(CONFIG_ASSET);
		if (jsonFile.exists()) {
			try {
				rapidConfig = json.fromJson(RapidConfig.class, jsonFile);
			} catch (SerializationException e) {
				Gdx.app.error("Config" , "Cannot deserialize RapidCofig, please check this file in: " + CONFIG_ASSET, e);
			}
		}else{
			Gdx.app.error("Config" , "Configuration file not exist in: " + CONFIG_ASSET);
			rapidConfig = new RapidConfig();
		}
	}

	public int getAppVersion() {
		return rapidConfig.appVersion;
	}

	public String getAppName() {
		return rapidConfig.appName;
	}

	public boolean isDebugMode() {
		if(rapidConfig == null) return true;
		return rapidConfig.debugMode;
	}

	public RapidConfig getRapidConfig() {
		return rapidConfig;
	}

	public List<String> getAssetsUrls() {
		return rapidConfig.remoteAssetsUrl;
	}
}
