package galaxy.rapid.downloader;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;

import galaxy.rapid.downloader.exceptions.CanNotStartDownload;

public class AsynAdvenceDownloader {
	private final int INTERVAL_BETWEEN_UPDATE = 1000;
	private DownloadListener downloadListener;
	private DownloadInfo downloadInfo;
	private URL url;
	private Timer timer = new Timer();

	private volatile boolean complete = false;

	public AsynAdvenceDownloader(URL linkToResource, DownloadListener downloadListener) {
		this.downloadListener = downloadListener;
		this.downloadInfo = new DownloadInfo();
		this.url = linkToResource;
	}

	public void startDownload() throws CanNotStartDownload {
		final File tempFile = createTempFile();
		downloadInfo.setLenght(DownloadUtils.tryGetFileSize(url));
		
		Thread thread = new Thread(new Runnable() {			
			@Override
			public void run() {
				downloadFile(tempFile);		
				downloadListener.downloadComplete(tempFile, downloadInfo);
				complete = true;
			}
		});
		thread.setDaemon(true);
		thread.setName("Downloader Thread");
		thread.start();
		
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				downloadInfo.setInterval();
				downloadListener.downloadProgress(downloadInfo);
			}
		}, INTERVAL_BETWEEN_UPDATE, INTERVAL_BETWEEN_UPDATE);
		
	}

	private void downloadFile(final File downloadedFile){
		BufferedOutputStream out = null;
		BufferedInputStream in = null;
		try {
			in = new BufferedInputStream(url.openStream());
			out = new BufferedOutputStream(new FileOutputStream(downloadedFile));
			byte data[] = new byte[1024];
			int count;			
			
			while ((count = in.read(data, 0, 1024)) != -1) {
				
				out.write(data, 0, count);
				downloadInfo.addDownloadedData(count);				
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}finally {
			timer.cancel();			
			try {
				out.close();
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
		}
	}
	
	private File createTempFile() throws CanNotStartDownload {
		File temp = null;
		try {
			temp = File.createTempFile("testDownload", ".tmp");
		} catch (IOException e1) {
			e1.printStackTrace();
			throw new CanNotStartDownload(e1);
		}
		return temp;
	}

	public boolean isComplete() {
		return complete;
	}

	
	
	
}
