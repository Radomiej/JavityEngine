/**
 * Copyright 2015 Thomas Cashman
 */
package com.badlogic.gdx.setup;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Thomas Cashman
 */
public class Release {
	private static final String JAVITY_KEY = "javity";
	private static final String LIBGDX_KEY = "libGDX";
	private static final String MINISCRIPT_KEY = "miniscript";
	private static final String MINIBUS_KEY = "minibus";
	private static final String ROBOVM_KEY = "roboVM";
	private static final String ANDROID_BUILD_TOOLS_KEY = "androidBuildTools";
	private static final String ANDROID_API_KEY = "androidApi";
	private static final String PARCL_KEY = "parcl";
	private static final String GRADLE_BUTLER_PLUGIN_KEY = "gradle-butler-plugin";
	private static final String DEPRECATED_KEY = "deprecated";

	private Map<String, String> versions;
	private boolean deprecated = false;

	public Release(String releaseData) {
		versions = new HashMap<String, String>();
		String[] properties = releaseData.split(",");
		for (String property : properties) {
			String[] mapping = property.split(":");
			String key = mapping[0].trim();
			String value = mapping[1].trim();
			if (key.equals(DEPRECATED_KEY)) {
				deprecated = Boolean.parseBoolean(value);
			} else {
				versions.put(key, value);
			}
		}
	}

	public Release(String javityVersion, String libGDXVersion, 
			String roboVMVersion, String androidBuildToolsVersion, 
			String androidApiVersion, String minibusVersion, String miniscriptVersion, String parclVersion, String gradleButlerPluginVersion) {
		versions = new HashMap<String, String>();
		versions.put(JAVITY_KEY, javityVersion);
		versions.put(LIBGDX_KEY, libGDXVersion);
		versions.put(ROBOVM_KEY, roboVMVersion);
		versions.put(ANDROID_BUILD_TOOLS_KEY, androidBuildToolsVersion);
		versions.put(ANDROID_API_KEY, androidApiVersion);
		versions.put(MINIBUS_KEY, minibusVersion);
		versions.put(MINISCRIPT_KEY, miniscriptVersion);
		versions.put(PARCL_KEY, parclVersion);
		versions.put(GRADLE_BUTLER_PLUGIN_KEY, gradleButlerPluginVersion);
	}

	public String getJavityVersion() {
		return versions.get(JAVITY_KEY);
	}

	public String getLibGDXVersion() {
		return versions.get(LIBGDX_KEY);
	}

	public String getRoboVMVersion() {
		return versions.get(ROBOVM_KEY);
	}

	public String getAndroidBuildToolsVersion() {
		return versions.get(ANDROID_BUILD_TOOLS_KEY);
	}

	public void setAndroidBuildToolsVersion(String androidBuildToolsVersion) {
		versions.put(ANDROID_BUILD_TOOLS_KEY, androidBuildToolsVersion);
	}

	public String getAndroidApiVersion() {
		return versions.get(ANDROID_API_KEY);
	}

	public void setAndroidApiVersion(String androidApiVersion) {
		versions.put(ANDROID_API_KEY, androidApiVersion);
	}

	public String getMinibusVersion() {
		return versions.get(MINIBUS_KEY);
	}
	
	public void setMinibusVersion(String minibusVersion) {
		versions.put(MINIBUS_KEY, minibusVersion);
	}
	
	public String getMiniscriptVersion() {
		return versions.get(MINISCRIPT_KEY);
	}
	
	public void setMiniscriptVersion(String miniscriptVersion) {
		versions.put(MINISCRIPT_KEY, miniscriptVersion);
	}
	
	public String getParclVersion() {
		return versions.get(PARCL_KEY);
	}
	
	public void setParclVersion(String parclVersion) {
		versions.put(PARCL_KEY, parclVersion);
	}
	
	public String getGradleButlerPluginVersion() {
		return versions.get(GRADLE_BUTLER_PLUGIN_KEY);
	}
	
	public void setGradleButlerPluginVersion(String gradleButlerPluginVersion) {
		versions.put(GRADLE_BUTLER_PLUGIN_KEY, gradleButlerPluginVersion);
	}

	public boolean isDeprecated() {
		return deprecated;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder("Release ");
		for (String key : versions.keySet()) {
			builder.append("[" + key + ": " + versions.get(key) + "]");
		}
		builder.append("[deprecated: " + deprecated + "]");
		return builder.toString();
	}
}
