package galaxy.rapid.downloader;

import java.io.File;

public interface DownloadListener {

	public void downloadProgress(DownloadInfo downloadInfo);

	public void downloadComplete(File tempFile, DownloadInfo downloadInfo);

}
