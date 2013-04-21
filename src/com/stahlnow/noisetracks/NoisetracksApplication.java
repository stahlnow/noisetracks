package com.stahlnow.noisetracks;


import java.text.SimpleDateFormat;

import com.stahlnow.noisetracks.authenticator.AuthenticationService;
import com.stahlnow.noisetracks.helper.httpimage.FileSystemPersistence;
import com.stahlnow.noisetracks.helper.httpimage.HttpImageManager;
import com.stahlnow.noisetracks.provider.NoisetracksProvider;
import android.app.Application;
import android.util.Log;

public class NoisetracksApplication extends Application {

	public static final String TAG = "Noisetracks"; // Log tag
	
	public static final String HOST = "192.168.1.217";
	public static final int HTTP_PORT = 8000;
	public static final int HTTPS_PORT = 443;
	public static final String DOMAIN = "http://" + HOST + ":" + HTTP_PORT;
	
	/**
	 *  The global SDF (simple date format) used everywhere: "yyyy-MM-dd'T'HH:mm:ss"
	 */
	public static SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	
	// Maximum recording duration in seconds
	public static final int MAX_RECORDING_DURATION_SECONDS = 5; // TODO 10
	
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
	
	// Provide an instance for static accessories
	private static NoisetracksApplication mInstance = null;
	
	// Keep references to global resources.
	private static FileSystemPersistence mFileSystemPersistence;
	private static HttpImageManager mHttpImageManager;
	

	@Override
	public void onCreate() {
		super.onCreate();
		
		mFileSystemPersistence = new FileSystemPersistence(this.getCacheDir().toString()); // Create new cache
		Log.v(TAG,"Cache dir is: " + getCacheDir().toString());
		
		mInstance = this;
	}
	
	public static void logout() {
		Log.v(TAG, "Logging out...");
		
		// remove account from device
		AuthenticationService.removeAccount(getInstance().getApplicationContext());
		
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
