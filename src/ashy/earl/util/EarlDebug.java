package ashy.earl.util;

import android.util.Log;

public class EarlDebug {
	private static final String TAG = "EarlDebug";
	public static final boolean DEBUG = true;

	public static void logD(String log) {
		Log.d(TAG, log);
	}

	public static void logE(String log) {
		Log.e(TAG, log);
	}

	public static void logW(String log) {
		Log.w(TAG, log);
	}

	public static void logI(String log) {
		Log.i(TAG, log);
	}
}
