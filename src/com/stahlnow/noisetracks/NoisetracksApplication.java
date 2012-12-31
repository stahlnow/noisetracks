package com.stahlnow.noisetracks;


import java.text.SimpleDateFormat;

import com.stahlnow.noisetracks.helper.httpimage.FileSystemPersistence;
import com.stahlnow.noisetracks.helper.httpimage.HttpImageManager;

import edu.mit.mobile.android.imagecache.ImageCache;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.support.v4.util.LruCache;

public class NoisetracksApplication extends Application {

	public static final String LOG_TAG = "Noisetracks";
	public static final String BASEDIR = "/sdcard/Noisetracks";
	
	public static SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
	
	private static NoisetracksApplication instance = null;
	

	// keep references to our global resources
	private static LruCache<String, Bitmap> mBitmapCache = null;
	private static ImageCache mImageCache = null;
	private static HttpImageManager mHttpImageManager;

	@Override
	public void onCreate() {
		super.onCreate();
		// provide an instance for our static accessors
		instance = this;
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
			mHttpImageManager = new HttpImageManager(
					HttpImageManager.createDefaultMemoryCache(), 
					new FileSystemPersistence(BASEDIR));
		}
		return mHttpImageManager;
	}
	
	public static ImageCache getImageCache() {
		if (mImageCache == null) {
			checkInstance();
			//mImageCache = ImageCache.getInstance(instance);
			mImageCache = new ImageCache(instance, CompressFormat.PNG, 100);
		}
		return mImageCache;
	}
	
	public static LruCache<String, Bitmap> getBitmapCache() {
		if (mBitmapCache == null) {
			checkInstance();
			// Pick cache size based on memory class of device			
	        final ActivityManager am = (ActivityManager) instance.getSystemService(Context.ACTIVITY_SERVICE);
	        final int memoryClassBytes = am.getMemoryClass() * 1024 * 1024;
	        mBitmapCache = new LruCache<String, Bitmap>(memoryClassBytes / 2);
		}
		return mBitmapCache;
	}

	private static void checkInstance() {
		if (instance == null)
			throw new IllegalStateException("Application not created yet!");
	}

	

}
