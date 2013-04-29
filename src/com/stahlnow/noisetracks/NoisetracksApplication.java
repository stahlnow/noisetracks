package com.stahlnow.noisetracks;


import java.text.SimpleDateFormat;
import java.util.Locale;

import com.stahlnow.noisetracks.authenticator.AuthenticationService;
import com.stahlnow.noisetracks.helper.httpimage.FileSystemPersistence;
import com.stahlnow.noisetracks.helper.httpimage.HttpImageManager;
import com.stahlnow.noisetracks.provider.NoisetracksProvider;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.util.Log;

public class NoisetracksApplication extends Application {

	private static final String TAG = "NoisetracksApplication"; 
	
	public static final String HOST = "192.168.1.217";
	public static final int HTTP_PORT = 8000;
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
	public static final int DEFAULT_TRACKING_INTERVAL = 1;	 // TODO change to reasonable value
	
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
		// remove account from device
		AuthenticationService.removeAccount(getInstance().getApplicationContext());
		
		Log.v(TAG, "Cleaning up...");
		// delete database
		getInstance().getApplicationContext().deleteDatabase(NoisetracksProvider.DATABASE_NAME);
		
		// delete cache directory
		if (mFileSystemPersistence != null)
			mFileSystemPersistence.clear();
		
		// collect garbage, exit
		System.gc();
		System.exit(0);
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
