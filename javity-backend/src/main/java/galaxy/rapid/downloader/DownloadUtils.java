package galaxy.rapid.downloader;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadUtils {
	public static double getMb(double bytes) {
		return getKb(bytes) / 1024;
	}

	public static int tryGetFileSize(URL url) {
		HttpURLConnection conn = null;
		try {
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("HEAD");
			conn.getInputStream();
			return conn.getContentLength();
		} catch (IOException e) {
			return -1;
		} finally {
			conn.disconnect();
		}
	}

	public static double getKb(double bytes) {
		return bytes / 1024;
	}
}
