package com.noisetracks.android;


import java.text.SimpleDateFormat;
import java.util.Locale;

import com.noisetracks.android.authenticator.AuthenticationService;
import com.noisetracks.android.helper.httpimage.FileSystemPersistence;
import com.noisetracks.android.helper.httpimage.HttpImageManager;
import com.noisetracks.android.provider.NoisetracksContract;
import com.noisetracks.android.provider.NoisetracksProvider;
import com.noisetracks.android.receivers.TrackingReceiver;
import com.noisetracks.android.ui.Tabs;
import com.noisetracks.android.utility.AppSettings;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;

public class NoisetracksApplication extends Application {

	private static final String TAG = "NoisetracksApplication"; 
	
	public static final String HOST = "noisetracks.com";
	public static final int HTTP_PORT = 80;
	public static final int HTTPS_PORT = 443;
	public static final String DOMAIN = "http://" + HOST + ":" + HTTP_PORT;
	public static final Uri URI_ENTRIES = Uri.parse(DOMAIN + "/api/v1/entry/");
	public static final Uri URI_PROFILES = Uri.parse(DOMAIN + "/api/v1/profile/");
	public static final Uri URI_SIGNUP = Uri.parse(DOMAIN + "/api/v1/signup/");
	public static final Uri URI_VOTE = Uri.parse(DOMAIN + "/api/v1/vote/");
	public static final Uri URI_UPLOAD = Uri.parse(DOMAIN + "/upload/");
	
	/**
	 * Maximum recording duration in seconds
	 */
	public static final int MAX_RECORDING_DURATION_SECONDS = 5; // TODO 10
	
	/**
	 * Default tracking interval in minutes
	 */
	public static final int DEFAULT_TRACKING_INTERVAL = 30;	 // TODO change to reasonable value
	
	/**
	 *  The global SDF (simple date format) used everywhere: "yyyy-MM-dd'T'HH:mm:ss"
	 */
	public static SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.GERMAN);
	
	// The global SQL loader ids.
	public static final int ENTRIES_SQL_LOADER_FEED = 0;
	public static final int ENTRIES_SQL_LOADER_PROFILE = 2;
	public static final int PROFILE_LIST_SQL_LOADER = 3;
	public static final int PROFILE_SQL_LOADER = 3;
	
	// The global REST loader ids.
	public static final int ENTRIES_REST_LOADER = 100;
	public static final int ENTRIES_NEWER_REST_LOADER = 200;
	public static final int ENTRIES_OLDER_REST_LOADER = 300;
	public static final int PROFILE_LIST_REST_LOADER = 400;
	public static final int PROFILE_REST_LOADER = 500;
	public static final int SIGNUP_REST_LOADER = 600;
	public static final int VOTE_LOADER = 700;
	public static final int DELETE_LOADER = 800;
	
	// Provide an instance for static accessories
	private static NoisetracksApplication mInstance = null;
	
	// Keep references to global resources.
	private static FileSystemPersistence mFileSystemPersistence;
	private static HttpImageManager mHttpImageManager;
	

	@Override
	public void onCreate() {
		super.onCreate();
		
		String cacheDir = getCacheDir().toString();
		mFileSystemPersistence = new FileSystemPersistence(cacheDir); // Create new cache
		Log.v(TAG,"Cache dir is: " + cacheDir);
		
		// register filter for 'account changed'.
		registerReceiver(receiver, new IntentFilter("android.accounts.LOGIN_ACCOUNTS_CHANGED"));
		
		mInstance = this;
	}
	
	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (!AuthenticationService.accountExists(context)) {
				Log.v(TAG, "Noisetracks Account has been removed.");
				logout();
			}
		}
	};
	
	public static void logout() {
		Log.v(TAG, "Logging out...");
		// delete cache directory
		if (mFileSystemPersistence != null) {
			mFileSystemPersistence.clear();
		}
		
		// stop tracking
		if (AppSettings.getServiceRunning(getInstance()))
			toggleTracking(true, NoisetracksApplication.DEFAULT_TRACKING_INTERVAL);
		
		// delete database
		getInstance().getApplicationContext().deleteDatabase(NoisetracksProvider.DATABASE_NAME);
				
		// remove account from device
		AuthenticationService.removeAccount(getInstance().getApplicationContext());
	}
	
	public static void toggleTracking(boolean isStart, float interval) {
		
		AlarmManager manager = (AlarmManager)getInstance().getSystemService(Service.ALARM_SERVICE);
		
		PendingIntent tracking = PendingIntent.getBroadcast(
				getInstance(),
				0,
				new Intent(getInstance(), TrackingReceiver.class),
				0);

		if (isStart) {
			manager.cancel(tracking);
			AppSettings.setServiceRunning(getInstance(), false);
			Log.i(TAG, "Tracking service stopped.");
			
		} else {
			// Schedule tracking
			manager.setRepeating(
					AlarmManager.ELAPSED_REALTIME_WAKEUP,
					SystemClock.elapsedRealtime(),
					(long) (interval * 60.0f * 1000.0f), // Tracking interval in milliseconds
					tracking);

			AppSettings.setServiceRunning(getInstance(), true);
			Log.i(TAG, "Tracking service started with interval " + interval * 60.0f + " seconds.");
		}
	}

	/**
	 * Gets the HTTP Image Manager or creates one, if necessary.
	 * @return HttpImageManager instance
	 */
	public static HttpImageManager getHttpImageManager() {
		if (mHttpImageManager == null) {
			checkInstance();
			mHttpImageManager = new HttpImageManager(HttpImageManager.createDefaultMemoryCache(), mFileSystemPersistence);
		}
		return mHttpImageManager;
	}
	
	public static FileSystemPersistence getFileSystemPersistence() {
		checkInstance();
		return mFileSystemPersistence;
	}
	
	
	/**
	 * Convenient accessory, saves having to call and cast
	 * getApplicationContext()
	 */
	public static NoisetracksApplication getInstance() {
		checkInstance();
		return mInstance;
	}
	

	private static void checkInstance() {
		if (mInstance == null)
			throw new IllegalStateException("Application not created yet!");
	}

	

}
