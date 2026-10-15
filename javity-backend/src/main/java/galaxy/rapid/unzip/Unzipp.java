package galaxy.rapid.unzip;

import java.io.File;

import com.badlogic.gdx.files.FileHandle;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;

public class Unzipp {
	public boolean canUnzip() {
		return false;
	}

	public boolean unzipWithPassword(File fileZip, FileHandle destination, String password){
	    try {
	         ZipFile zipFile = new ZipFile(fileZip);
	         if (zipFile.isEncrypted()) {
	            zipFile.setPassword(password);
	         }
	         zipFile.extractAll(destination.file().getAbsolutePath());
	    } catch (ZipException e) {
	        e.printStackTrace();
	    }

		return true;
	}
	public boolean unzip(File fileZip, FileHandle assetFolder) {
		return unzipWithPassword(fileZip, assetFolder, "");
	}
	
	
}
