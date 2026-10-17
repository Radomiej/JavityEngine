package galaxy.rapid.asset;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import galaxy.rapid.configuration.RapidConfiguration;

/**
 * This asset downloader download all asset defined in rapid.properties file.
 * 
 * @author Radomiej
 */
public class RapidMultiAssetDownloader {
	private List<String> textUrls;
	private ExecutorService executor = Executors.newSingleThreadExecutor();
	private AtomicInteger loadingCounter = new AtomicInteger(0);
	private int maximumCounter;
	private boolean complete;
	private double speed;
	
	public RapidMultiAssetDownloader() {
		textUrls = RapidConfiguration.INSTANCE.getAssetsUrls();
	}

	public void startDownload() {
		for(final String url : textUrls){	
			incramentCounter();			
			executor.execute(new Runnable() {			
				@Override
				public void run() {
					RapidAssetDownloader downloader = new RapidAssetDownloader(url);
					downloader.startDownload();
					while(!downloader.isComplete()){
						speed = downloader.getSpeed();
						Thread.yield();
					}
					decramentCounter();
				}
			});
		}
	}

	private synchronized void incramentCounter() {
		int current = loadingCounter.incrementAndGet();		
		if(current > maximumCounter) maximumCounter = current;
	}

	private synchronized void decramentCounter(){
		int current = loadingCounter.decrementAndGet();
		if(current > 0){
			return;
		}
		
		complete = true;
	}
	
	public boolean isComplete() {
		if(maximumCounter == 0) return true;
		return complete;
	}
	
	public float getProgress(){
		return 1 - loadingCounter.get() / (float)maximumCounter;
	}

	public int getSpeed() {
		return (int) speed;
	}
}
