package com.stahlnow.noisetracks.utility;

import android.util.Log;

public class AppLog {
	private static final String APP_TAG = "Noisetracks";
	
	public static int logString(String message){
		return Log.i(APP_TAG, message);
	}
}
