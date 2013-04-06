package com.stahlnow.noisetracks.utility;

import android.content.Context;
import android.content.SharedPreferences;

public class AppSettings {
	public static final String HOST = "stahlnow";
	public static final int HTTP_PORT = 8000;
	public static final int HTTPS_PORT = 443;
	public static final String DOMAIN = "http://" + HOST + ":" + HTTP_PORT;
	public static final String NOISETRACK_PREF_NAME = "Noisetrack_8879461315648797161";
	public static final String SETTING_SERVICE_STATE = "isServiceRunning";
	public static final String SETTING_LOGGING_INTERVAL = "loggingInterval";
	public static final String SETTING_USER = "username";
	
	public static boolean getServiceRunning(Context context){
		SharedPreferences pref = context.getSharedPreferences(NOISETRACK_PREF_NAME, 0);
		return pref.getBoolean(SETTING_SERVICE_STATE, false);
	}
	
	public static void setServiceRunning(Context context, boolean isRunning){
		SharedPreferences pref = context.getSharedPreferences(NOISETRACK_PREF_NAME, 0);
		SharedPreferences.Editor editor = pref.edit();
		editor.putBoolean(SETTING_SERVICE_STATE, isRunning);
		editor.commit();
	}
	
	public static int getLoggingInterval(Context context){
		SharedPreferences pref = context.getSharedPreferences(NOISETRACK_PREF_NAME, 0);
		return pref.getInt(SETTING_LOGGING_INTERVAL, 5);
	}
	
	public static void setLoggingInterval(Context context, int interval){
		SharedPreferences pref = context.getSharedPreferences(NOISETRACK_PREF_NAME, 0);
		SharedPreferences.Editor editor = pref.edit();
		editor.putInt(SETTING_LOGGING_INTERVAL, interval);
		editor.commit();
	}
	
	public static String getUser(Context context) {
		SharedPreferences pref = context.getSharedPreferences(NOISETRACK_PREF_NAME, 0);
		return pref.getString(SETTING_USER, ""); // TODO
	}
	

}
