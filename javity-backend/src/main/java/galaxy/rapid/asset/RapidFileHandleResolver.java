package galaxy.rapid.asset;

import java.io.File;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;

import galaxy.rapid.configuration.RapidConfiguration;

public class RapidFileHandleResolver implements FileHandleResolver {
	public FileHandle resolve(String fileName) {

		try {
			if (Gdx.app.getType().equals(ApplicationType.WebGL)) {
				return getFileHandleForWebGL(fileName);
			} else {
				return getFirstExternalNextInternalAssetFileHandle(fileName);
			}
		} catch (Exception e) {
			Gdx.app.error(RapidFileHandleResolver.class.getSimpleName(), "Brak pliku: " + fileName);
		}
		return null;
	}

	private FileHandle getFirstExternalNextInternalAssetFileHandle(String fileName) {
		FileHandle file = null;

		file = getFromExternal(fileName);
		if (file.exists())
			return file;
		file = Gdx.files.internal(fileName);
		if (file.exists())
			return file;
		file = Gdx.files.local(fileName);
		if (file.exists())
			return file;
		file = Gdx.files.absolute(fileName);
		if (file.exists())
			return file;

		return file;
	}

	private FileHandle getFileHandleForWebGL(String fileName) {
		return Gdx.files.internal(fileName);
	}

	private FileHandle getFromExternal(String fileName) {
		FileHandle file = null;
		file = Gdx.files.external("assets" + File.separator + fileName);
		if (file.exists())
			return file;
		// System.out.println("Brak pliku w: " + file.path());

		file = Gdx.files.external(
				RapidConfiguration.INSTANCE.getAppName() + File.separator + "assets" + File.separator + fileName);
		if (file.exists())
			return file;
		// System.out.println("Brak pliku w: " + file.path());

		return file;
	}
}
