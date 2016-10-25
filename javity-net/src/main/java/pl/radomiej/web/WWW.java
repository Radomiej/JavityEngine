package pl.radomiej.web;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Date;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net.HttpMethods;
import com.badlogic.gdx.Net.HttpRequest;
import com.badlogic.gdx.Net.HttpResponse;
import com.badlogic.gdx.Net.HttpResponseListener;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.net.HttpRequestBuilder;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import com.badlogic.gdx.utils.StreamUtils;

import pl.radomiej.web.downloader.AsynAdvenceDownloader;
import pl.radomiej.web.downloader.DownloadInfo;
import pl.radomiej.web.exceptions.CanNotStartDownload;

public class WWW {

	static {
		Gdx.net = new JavityNet();
		HttpRequestBuilder.json.setOutputType(OutputType.json);
		HttpRequestBuilder.json.setSerializer(Date.class, new Json.Serializer<Date>() {

			public void write(Json json, Date object, Class knownType) {
				json.writeValue(object.getTime());
			}

			public Date read(Json json, JsonValue jsonData, Class type) {
				return new Date(jsonData.asLong());
			}
		});
	}

	private byte[] responseBytes;
	private String urlString;
	private boolean work;

	private String status;
	private int statusCode;

	private WWWResponseListener wwwResponseListener;
	private int timeout = 20000;
	
	public WWW(String url) {
		this.urlString = url;
	}

	public WWW(String url, WWWResponseListener wwwResponseListener) {
		this(url);
		this.wwwResponseListener = wwwResponseListener;
	}

	public void POST() {
		POST(null);
	}

	public void POST(Object form) {
		if (work)
			return;

		work = true;

		HttpRequestBuilder requestBuilder = new HttpRequestBuilder();
		HttpRequest request = requestBuilder.newRequest().url(urlString).timeout(timeout).jsonContent(form).method(HttpMethods.POST)
				.build();

		final WWW my = this;
		Gdx.net.sendHttpRequest(request, new HttpResponseListener() {

			public void handleHttpResponse(final HttpResponse httpResponse) {
				responseBytes = httpResponse.getResult();

//				Gdx.app.log(WWW.class.getSimpleName(), "response bytes: " + responseBytes.length + " response string: "
//						+ httpResponse.getResultAsString() + " Code: " + httpResponse.getStatus().getStatusCode());

				statusCode = httpResponse.getStatus().getStatusCode();
				work = false;
				if (wwwResponseListener != null) {
					Gdx.app.postRunnable(new Runnable() {
						public void run() {
							wwwResponseListener.httpResponseListener(my);
						}
					});
				}
			}

			public void failed(Throwable t) {
				Gdx.app.error(WWW.class.getSimpleName(), "failed: " + t);
				work = false;
			}

			public void cancelled() {
				Gdx.app.error(WWW.class.getSimpleName(), "cancelled");
				work = false;
			}
		});
	}

	public void GET() {
		GET(null);
	}

	public void GET(String query) {
		if (work)
			return;

		work = true;

		HttpRequestBuilder requestBuilder = new HttpRequestBuilder();
		HttpRequest request = requestBuilder.newRequest().url(urlString).timeout(timeout).content(query).method(HttpMethods.GET).build();

		final WWW my = this;
		Gdx.net.sendHttpRequest(request, new HttpResponseListener() {

			public void handleHttpResponse(HttpResponse httpResponse) {
				responseBytes = httpResponse.getResult();
//				Gdx.app.log("WWW", "Response: " + responseBytes.length);
//				Gdx.app.log("WWW", "Status: " + httpResponse.getStatus().getStatusCode());
				work = false;
				if (wwwResponseListener != null) {
					Gdx.app.postRunnable(new Runnable() {
						public void run() {
							wwwResponseListener.httpResponseListener(my);
						}
					});
				}
			}

			public void failed(Throwable t) {
				Gdx.app.error(WWW.class.getSimpleName(), "failed: " + t);
				work = false;
			}

			public void cancelled() {
				Gdx.app.error(WWW.class.getSimpleName(), "cancelled");
				work = false;
			}
		});
	}

	/**
	 * @return the work
	 */
	public boolean isWork() {
		return work;
	}

	/**
	 * @return the status
	 */
	public String getStatus() {
		return status;
	}

	/**
	 * @return the statusCode
	 */
	public int getStatusCode() {
		return statusCode;
	}

	public Texture getTexture() {

//		Gdx.app.log("WWW", "url: " + urlString);
		Pixmap pixmap = null;
		
		try {
			pixmap = new Pixmap(responseBytes, 0, responseBytes.length);
		} catch (Exception e) {
			Gdx.app.error("WWW", "Cannot get texture: " + e.getMessage());
			return null;
		}
		
		final int originalWidth = pixmap.getWidth();
		final int originalHeight = pixmap.getHeight();
		int width = MathUtils.nextPowerOfTwo(pixmap.getWidth());
		int height = MathUtils.nextPowerOfTwo(pixmap.getHeight());

		int deltaX = (int) ((width - originalWidth) / 2f);
		int deltaY = (int) ((height - originalHeight) / 2f);

		final Pixmap potPixmap = new Pixmap(width, height, pixmap.getFormat());
		potPixmap.drawPixmap(pixmap, deltaX, deltaY, 0, 0, pixmap.getWidth(), pixmap.getHeight());
		pixmap.dispose();
		Texture texture = new Texture(potPixmap);
		potPixmap.dispose();
		return texture;
	}

	public byte[] getResponseBytes() {
		return responseBytes;
	}

	public boolean isIncomingResponse(){
		return responseBytes != null && responseBytes.length > 0;
	}
	
	public String getStringResponse() {
		ByteArrayInputStream input = new ByteArrayInputStream(responseBytes);
		try {
			return StreamUtils.copyStreamToString(input, responseBytes.length);
		} catch (IOException e) {
			return "";
		} finally {
			StreamUtils.closeQuietly(input);
		}
	}

	public <T extends Collection> T getJsonArrayObjectResponse(Class<T> collectionClass) {
		if(responseBytes.length == 0) return null;
		return RapidJson.INSTANCE.parseJsonCollection(getStringResponse(), collectionClass);
	}

	public <E extends Collection<T>, T> E getJsonArrayObjectResponse(Class<E> collectionClass, Class<T> elementsClass) {
		if(responseBytes.length == 0) return null;
		return RapidJson.INSTANCE.parseJsonCollection(getStringResponse(), collectionClass, elementsClass);
	}

	public <T> T getJsonObjectResponse(Class<T> componentType) {
		if(responseBytes.length == 0) return null;
		return RapidJson.INSTANCE.parseJsonSingle(getStringResponse(), componentType);
	}

	public int getTimeout() {
		return timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}
}
