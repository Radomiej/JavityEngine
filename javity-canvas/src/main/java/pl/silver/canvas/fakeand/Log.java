package pl.silver.canvas.fakeand;

public class Log {

	public static void w(String tag, String message) {
		System.out.println(tag + " : " + message);
	}

	public static void i(String tag, String message) {
		System.out.println(tag + " : " + message);
	}

	public static void e(String tag, String message) {
		System.err.println(tag + " : " + message);		
	}

	public static void d(String tag, String message) {
		System.out.println(tag + " : " + message);
	}

}
