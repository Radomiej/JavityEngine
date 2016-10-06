package pl.radomiej.web;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.net.ServerSocket;
import com.badlogic.gdx.net.ServerSocketHints;
import com.badlogic.gdx.net.Socket;
import com.badlogic.gdx.net.SocketHints;

public class JavityNet implements Net {

	private Net nativeNet = Gdx.net;
	private JavityNetJavaImpl javityNetImpl = new JavityNetJavaImpl();
	
	public JavityNet() {
		
	}
	
	public void sendHttpRequest(HttpRequest httpRequest, HttpResponseListener httpResponseListener) {
		javityNetImpl.sendHttpRequest(httpRequest, httpResponseListener);		
	}

	public void cancelHttpRequest(HttpRequest httpRequest) {
		javityNetImpl.cancelHttpRequest(httpRequest);
	}

	public ServerSocket newServerSocket(Protocol protocol, String hostname, int port, ServerSocketHints hints) {
		return nativeNet.newServerSocket(protocol, port, hints);
	}

	public ServerSocket newServerSocket(Protocol protocol, int port, ServerSocketHints hints) {
		return nativeNet.newServerSocket(protocol, port, hints);
	}

	public Socket newClientSocket(Protocol protocol, String host, int port, SocketHints hints) {
		return nativeNet.newClientSocket(protocol, host, port, hints);
	}

	public boolean openURI(String URI) {
		return nativeNet.openURI(URI);
	}

	
}
