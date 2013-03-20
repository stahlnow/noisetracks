package com.stahlnow.noisetracks;


import java.text.SimpleDateFormat;

import com.stahlnow.noisetracks.authenticator.AuthenticationService;
import com.stahlnow.noisetracks.helper.httpimage.FileSystemPersistence;
import com.stahlnow.noisetracks.helper.httpimage.HttpImageManager;
import com.stahlnow.noisetracks.provider.NoisetracksProvider;
import com.stahlnow.noisetracks.utility.AppLog;

import android.app.Application;

public class NoisetracksApplication extends Application {

	public static final String LOG_TAG = "Noisetracks";
	
	// The global date format used everywhere.
	public static SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
	
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
	
	// Provide an instance for our static accessors
	private static NoisetracksApplication instance = null;
	
	// Keep references to our global resources.
	private static FileSystemPersistence mFileSystemPersistence;
	private static HttpImageManager mHttpImageManager;
	

	@Override
	public void onCreate() {
		super.onCreate();
		
		// create new cache
		mFileSystemPersistence = new FileSystemPersistence(this.getCacheDir().toString());
		
		AppLog.logString("cache dir is: " + getCacheDir().toString());
		
		instance = this;
	}
	
	public static void logout() {
		AppLog.logString("Logging out...");
		
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
	 * Convenient accessor, saves having to call and cast
	 * getApplicationContext()
	 */
	public static NoisetracksApplication getInstance() {
		checkInstance();
		return instance;
	}

	/**
	 * Accessor for some resource that depends on a context
	 */
	public static HttpImageManager getHttpImageManager() {
		if (mHttpImageManager == null) {
			checkInstance();
			// init HttpImageManager manager.
			mHttpImageManager = new HttpImageManager(HttpImageManager.createDefaultMemoryCache(), mFileSystemPersistence);
		}
		return mHttpImageManager;
	}
	
	public static FileSystemPersistence getFileSystemPersistence() {
		return mFileSystemPersistence;
	}

	private static void checkInstance() {
		if (instance == null)
			throw new IllegalStateException("Application not created yet!");
	}

	

}
