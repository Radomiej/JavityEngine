package pl.radomiej.web.exceptions;

import java.io.IOException;

public class CanNotStartDownload extends Exception {

	public CanNotStartDownload() {		
	}
	
	public CanNotStartDownload(IOException e1) {
		super(e1);
	}

}
