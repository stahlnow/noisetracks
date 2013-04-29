package com.stahlnow.noisetracks.utility;

import com.stahlnow.noisetracks.NoisetracksApplication;

import android.content.Context;
import android.content.SharedPreferences;

public class AppSettings {
	
	private static final String NOISETRACKS_PREF_NAME = "com.stahlnow.noisetracks";	// Preference file
	private static final String SETTING_SERVICE_STATE = "is_service_running";		// Status of service
	private static final String SETTING_TRACKING_INTERVAL = "tracking_interval";	// Tracking interval in minutes
	private static final String SETTING_USERNAME = "username";						// Username
	private static final String SETTING_APIKEY = "apikey";							// ApiKey
	private static final String SETTING_MUGSHOT = "mugshot";						// URL of user mugshot TODO: set after login
	
	/*
	 *  Getters and setters
	 */
	
	/**
	 * Get the status of the tracking service
	 * @param context
	 * @return True, if the tracking service is running
	 */
	public static boolean getServiceRunning(Context context) {
		SharedPreferences pref = context.getSharedPreferences(NOISETRACKS_PREF_NAME, 0);
		return pref.getBoolean(SETTING_SERVICE_STATE, false);
	}
	
	/**
	 * Set the status for the tracking service
	 * @param context
	 * @param isRunning Set to true, if the service was started or false if the service was stopped.
	 */
	public static void setServiceRunning(Context context, boolean isRunning) {
		SharedPreferences pref = context.getSharedPreferences(NOISETRACKS_PREF_NAME, 0);
		SharedPreferences.Editor editor = pref.edit();
		editor.putBoolean(SETTING_SERVICE_STATE, isRunning);
		editor.commit();
	}
	
	/**
	 * Get tracking interval in minutes
	 * @param context
	 * @return Tracking interval in minutes
	 */
	public static int getTrackingInterval(Context context) {
		SharedPreferences pref = context.getSharedPreferences(NOISETRACKS_PREF_NAME, 0);
		return pref.getInt(SETTING_TRACKING_INTERVAL, NoisetracksApplication.DEFAULT_TRACKING_INTERVAL);
	}
	
	/**
	 * Set tracking interval in minutes
	 * @param context
	 * @param interval Interval in minutes
	 */
	public static void setTrackingInterval(Context context, int interval) {
		SharedPreferences pref = context.getSharedPreferences(NOISETRACKS_PREF_NAME, 0);
		SharedPreferences.Editor editor = pref.edit();
		editor.putInt(SETTING_TRACKING_INTERVAL, interval);
		editor.commit();
	}
	
	/**
	 * Get username of logged in user or "AnonymousUser"
	 * @param context
	 * @return Username of logged in user
	 */
	public static String getUsername(Context context) {
		SharedPreferences pref = context.getSharedPreferences(NOISETRACKS_PREF_NAME, 0);
		return pref.getString(SETTING_USERNAME, "AnonymousUser");
	}
	
	/**
	 * Set user name
	 * @param context
	 * @param username Username of logged in user
	 */
	public static void setUsername(Context context, String username) {
		SharedPreferences pref = context.getSharedPreferences(NOISETRACKS_PREF_NAME, 0);
		SharedPreferences.Editor editor = pref.edit();
		editor.putString(SETTING_USERNAME, username);
		editor.commit();
	}
	
	/**
	 * Get username of logged in user or "AnonymousUser"
	 * @param context
	 * @return Username of logged in user
	 */
	public static String getApiKey(Context context) {
		SharedPreferences pref = context.getSharedPreferences(NOISETRACKS_PREF_NAME, 0);
		return pref.getString(SETTING_APIKEY, "AnonymousUser");
	}
	
	/**
	 * Set user name
	 * @param context
	 * @param username Username of logged in user
	 */
	public static void setApiKey(Context context, String apikey) {
		SharedPreferences pref = context.getSharedPreferences(NOISETRACKS_PREF_NAME, 0);
		SharedPreferences.Editor editor = pref.edit();
		editor.putString(SETTING_APIKEY, apikey);
		editor.commit();
	}
	
	/**
	 * Get mugshot of logged in user or null
	 * @param context
	 * @return Mugshot url of logged in user
	 */
	public static String getMugshot(Context context) {
		SharedPreferences pref = context.getSharedPreferences(NOISETRACKS_PREF_NAME, 0);
		return pref.getString(SETTING_MUGSHOT, null);
	}
	
	/**
	 * Set mugshot url of logged in user
	 * @param context
	 * @param username Mugshot url of logged in user
	 */
	public static void setMugshot(Context context, String mugshot) {
		SharedPreferences pref = context.getSharedPreferences(NOISETRACKS_PREF_NAME, 0);
		SharedPreferences.Editor editor = pref.edit();
		editor.putString(SETTING_MUGSHOT, mugshot);
		editor.commit();
	}
	

}
