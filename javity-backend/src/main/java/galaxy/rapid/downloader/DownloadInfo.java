package galaxy.rapid.downloader;

public class DownloadInfo {
	private double lenght;
	private double currentDownloadLenght;
	private double downloadSpeed;
	private double lastIntervalDownloadLenght;

	public DownloadInfo(long bytesToDownload) {
		setLenght(bytesToDownload);
	}

	public DownloadInfo() {
	}

	public double getLenght() {
		return lenght;
	}
	
	void setLenght(double lenght) {
		this.lenght = lenght;
	}

	public double getCurrentDownloadLenght() {
		return currentDownloadLenght;
	}

	void setCurrentDownloadLenght(double currentDownloadLenght) {
		this.currentDownloadLenght = currentDownloadLenght;
	}

	public void addDownloadedData(int count) {
		currentDownloadLenght += count;		
	}

	void setInterval() {
		downloadSpeed = currentDownloadLenght - lastIntervalDownloadLenght;
		lastIntervalDownloadLenght = currentDownloadLenght;	
		
	}
	
	public double getDownloadSpeed() {
		return downloadSpeed;
	}	
}
