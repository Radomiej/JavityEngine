package galaxy.rapid.downloader;

import java.util.ArrayList;
import java.util.List;

public class DownloadHistory {
	private List<String> downloadUrls;

	public DownloadHistory() {
		downloadUrls = new ArrayList<String>();
	}

	public boolean contains(String searchUrl){
		for(String url : downloadUrls){
			if(url.equalsIgnoreCase(searchUrl)) return true;
		}
		return false;
	}
	public List<String> getDownloadUrls() {
		return downloadUrls;
	}

	public void setDownloadUrls(List<String> downloadUrls) {
		this.downloadUrls = downloadUrls;
	}

}
