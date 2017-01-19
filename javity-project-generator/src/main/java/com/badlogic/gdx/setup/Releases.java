/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.badlogic.gdx.setup;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import com.github.zafarkhaja.semver.Version;

/**
 * Retrieves javity release information from the javity repository and checks
 * if this tool is compatible with the releases listed.
 */
public class Releases {
	private static final String BASE_JAVITY_VERSION = "0.5.0";
	private static final String BASE_LIBGDX_VERSION = "1.9.3";
	private static final String BASE_ROBOVM_VERSION = "1.12.0";
	private static final String BASE_ANDROID_BUILD_TOOLS_VERSION = "23.0.1";
	private static final String BASE_ANDROID_API_VERSION = "20";
	private static final String BASE_MINIBUS_VERSION = "1.0.0";
	private static final String BASE_MINISCRIPT_VERSION = "1.0.0";
	private static final String BASE_PARCL_VERSION = "1.0.8";
	private static final String BASE_GRADLE_BUTLER_PLUGIN_VERSION = "1.0.1";

	private static final Release BASE_RELEASE = new Release(BASE_JAVITY_VERSION, BASE_LIBGDX_VERSION,
			BASE_ROBOVM_VERSION, BASE_ANDROID_BUILD_TOOLS_VERSION, BASE_ANDROID_API_VERSION, BASE_MINIBUS_VERSION,
			BASE_MINISCRIPT_VERSION, BASE_PARCL_VERSION, BASE_GRADLE_BUTLER_PLUGIN_VERSION);

	private static boolean COMPATIBLE_SETUP_TOOL;
	private static Release[] RELEASES = new Release[1];

	public static void fetchData() {
		Scanner urlScanner = null;
		try {
			urlScanner = new Scanner(
					new URL("https://raw.githubusercontent.com/Radomiej/JavityEngine/master/RELEASES").openStream(), "UTF-8");
			urlScanner.useDelimiter("\\A");
			String[] contents = urlScanner.next().split("\n");

			InputStream stream = Releases.class.getClassLoader().getResourceAsStream("VERSION");
			Version setupToolVersion = Version.valueOf(new Scanner(stream).useDelimiter("\\A").next());
			System.out.println("Project Generator Version: " + setupToolVersion);

			if (setupToolVersion.getPreReleaseVersion().equals("SNAPSHOT")) {
				COMPATIBLE_SETUP_TOOL = true;
			} else {
				Version requiredSetupToolVersion = Version
						.valueOf(contents[0].substring(contents[0].indexOf(':') + 1).trim());
				COMPATIBLE_SETUP_TOOL = setupToolVersion.greaterThanOrEqualTo(requiredSetupToolVersion);
				System.out.println("Required Project Generator Version: " + requiredSetupToolVersion);
			}

			List<Release> activeReleases = new ArrayList<Release>();
			for (int i = 0; i < contents.length - 1; i++) {
				Release release = new Release(contents[i + 1]);
				if (release.isDeprecated()) {
					System.out.println("Ignoring deprecated release: " + release);
					continue;
				}
				System.out.println("Found " + release);
				activeReleases.add(release);
			}
			RELEASES = activeReleases.toArray(RELEASES);
		} catch (Exception e) {
			e.printStackTrace();

			COMPATIBLE_SETUP_TOOL = true;
			RELEASES = new Release[] { BASE_RELEASE };
		} finally {
			if (urlScanner != null) {
				urlScanner.close();
			}
		}
	}

	public static boolean isCompatibleSetupTool() {
		return COMPATIBLE_SETUP_TOOL;
	}

	public static Release[] getReleases() {
		return RELEASES;
	}

	public static Release getLatestRelease() {
		return RELEASES[0];
	}

	public static Release getRelease(String javityVersion) {
		for (Release release : RELEASES) {
			if (release.getJavityVersion().equals(javityVersion)) {
				return release;
			}
		}
		return null;
	}
}
