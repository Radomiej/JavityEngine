package com.badlogic.gdx.setup;


import java.util.HashMap;

public class DependencyBank {

	//Repositories
	static String mavenLocal = "mavenLocal()";
	static String mavenCentral = "mavenCentral()";
	static String jCenter = "jcenter()";
	static String libGDXSnapshotsUrl = "https://oss.sonatype.org/content/repositories/snapshots/";
	static String libGDXReleaseUrl = "https://oss.sonatype.org/content/repositories/releases/";
	static String javityReleaseUrl = "http://ns364990.ovh.net:5587/";

	//Project plugins
	static String gwtPluginImport = "de.richsource.gradle.plugins:gwt-gradle-plugin:0.6";
	static String androidPluginImport = "com.android.tools.build:gradle:1.5.0";
	static String roboVMPluginImport = "org.robovm:robovm-gradle-plugin:";
	
	//Extension versions
	static String box2DLightsVersion = "1.4";
	static String ashleyVersion = "1.7.0";
	static String aiVersion = "1.9.0";

	HashMap<ProjectDependency, Dependency> gdxDependencies = new HashMap<ProjectDependency, Dependency>();

	public DependencyBank() {
		for (ProjectDependency projectDep : ProjectDependency.values()) {
			Dependency dependency = new Dependency(projectDep.name(),
					projectDep.getGwtInherits(),
					projectDep.getDependencies(ProjectType.CORE),
					projectDep.getDependencies(ProjectType.DESKTOP),
					projectDep.getDependencies(ProjectType.ANDROID),
					projectDep.getDependencies(ProjectType.IOS));
			gdxDependencies.put(projectDep, dependency);
		}
	}

	public Dependency getDependency(ProjectDependency gdx) {
		return gdxDependencies.get(gdx);
	}

	/**
	 * This enum will hold all dependencies available for libgdx, allowing the setup to pick the ones needed by default,
	 * and allow the option to choose extensions as the user wishes.
	 * <p/>
	 * These depedency strings can be later used in a simple gradle plugin to manipulate the users project either after/before
	 * project generation
	 *
	 * @see Dependency for the object that handles sub-module dependencies. If no dependency is found for a sub-module, ie
	 * FreeTypeFont for gwt, an exception is thrown so the user can be notified of incompatability
	 */
	public enum ProjectDependency {
		JAVITY(
			new String[]{"org.javity:javity-engine:$javityVersion"},
			new String[]{"com.badlogicgames.gdx:gdx-backend-lwjgl:$gdxVersion", "com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop", "com.badlogicgames.gdx:gdx-box2d-platform:$gdxVersion:natives-desktop", "com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-desktop"},
			new String[]{"com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi", "com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a", "com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86", "com.badlogicgames.gdx:gdx-box2d-platform:$gdxVersion:natives-armeabi", "com.badlogicgames.gdx:gdx-box2d-platform:$gdxVersion:natives-armeabi-v7a", "com.badlogicgames.gdx:gdx-box2d-platform:$gdxVersion:natives-x86"},
			new String[]{"com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-ios", "com.badlogicgames.gdx:gdx-box2d-platform:$gdxVersion:natives-ios"},
			new String[]{"com.badlogicgames.gdx:gdx-box2d:$gdxVersion:sources", "com.badlogicgames.gdx:gdx-box2d-gwt:$gdxVersion:sources"},
			null,
			
			"Core Library for Javity"
		),
		NET(
				new String[]{"org.javity:javity-net:$javityVersion"},
				new String[]{},
				new String[]{},
				new String[]{},
				new String[]{},
				new String[]{},
				
				"HTTP network for Javity"
		),
		ANDROID (
				new String[]{},
				new String[]{},
				new String[]{"org.javity:javity-android:$javityVersion"},
				new String[]{},
				new String[]{},
				new String[]{},
				
				"Android natives for Javity"
		),
		UI (
				new String[]{"org.javity:javity-xml-ui:$javityVersion"},
				new String[]{},
				new String[]{},
				new String[]{},
				new String[]{},
				new String[]{},
				
				"UI framework for Javity"
		),
		MAP (
				new String[]{"org.javity:javity-map:$javityVersion"},
				new String[]{},
				new String[]{},
				new String[]{},
				new String[]{},
				new String[]{},
				
				"Tiles Map plugin for Javity"
		),		
		TOOLS(
			new String[]{},
			new String[]{"com.badlogicgames.gdx:gdx-tools:$gdxVersion"},
			new String[]{},
			new String[]{},
			new String[]{},
			new String[]{},
			
			"Collection of tools, including 2D/3D particle editors, texture packers, and file processors"
		),	
		BOX2DLIGHTS(
			new String[]{"com.badlogicgames.box2dlights:box2dlights:$box2DLightsVersion"},
			new String[]{},
			new String[]{"com.badlogicgames.box2dlights:box2dlights:$box2DLightsVersion"},
			new String[]{},
			new String[]{"com.badlogicgames.box2dlights:box2dlights:$box2DLightsVersion:sources"},
			new String[]{"Box2DLights"},
			
			"2D Lighting framework that utilises Box2D"
		),
		AI(
			new String[]{"com.badlogicgames.gdx:gdx-ai:$aiVersion"},
			new String[]{},
			new String[]{"com.badlogicgames.gdx:gdx-ai:$aiVersion"},
			new String[]{},
			new String[]{"com.badlogicgames.gdx:gdx-ai:$aiVersion:sources"},
			new String[]{"com.badlogic.gdx.ai"},
			
			"Artificial Intelligence framework"
		);

		private String[] coreDependencies;
		private String[] desktopDependencies;
		private String[] androidDependencies;
		private String[] iosDependencies;
		private String[] gwtDependencies;
		private String[] gwtInherits;
		private String description;

		ProjectDependency(String[] coreDeps, String[] desktopDeps, String[] androidDeps, String[] iosDeps, String[] gwtDeps, String[] gwtInhertis, String description) {
			this.coreDependencies = coreDeps;
			this.desktopDependencies = desktopDeps;
			this.androidDependencies = androidDeps;
			this.iosDependencies = iosDeps;
			this.gwtDependencies = gwtDeps;
			this.gwtInherits = gwtInhertis;
			this.description = description;
		}

		public String[] getDependencies(ProjectType type) {
			switch (type) {
				case CORE:
					return coreDependencies;
				case DESKTOP:
					return desktopDependencies;
				case ANDROID:
					return androidDependencies;
				case IOS:
					return iosDependencies;
				//				case HTML:
				//					return gwtDependencies;
			}
			return null;
		}
		
		public String[] getGwtInherits() {
			return gwtInherits;
		}
		
		public String getDescription() {
			return description;
		}
	}


	public enum ProjectType {
		CORE("core", new String[]{"java"}),
		DESKTOP("desktop", new String[]{"java", "application"}),
		ANDROID("android", new String[]{"android"}),
		IOS("ios(untested)", new String[]{"java", "robovm"});

		private final String name;
		private final String[] plugins;

		ProjectType(String name, String plugins[]) {
			this.name = name;
			this.plugins = plugins;
		}

		public String getName() {
			return name;
		}

		public String[] getPlugins() {
			return plugins;
		}
	}

}
