package com.noisetracks.android.service;

import java.util.Date;
import java.util.UUID;

import com.noisetracks.android.NoisetracksApplication;
import com.noisetracks.android.audio.Sampler;
import com.noisetracks.android.audio.Sampler.SamplerListener;
import com.noisetracks.android.client.UploadTask;
import com.noisetracks.android.helper.MyLocation;
import com.noisetracks.android.helper.MyLocation.LocationResult;
import com.noisetracks.android.provider.NoisetracksContract.Entries;
import com.noisetracks.android.utility.AppSettings;

import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

public class TrackingService extends Service {
	
	private static final String TAG = "NoisetracksService";
	
	private Context mContext;
	private MyLocation mMyLocation = new MyLocation();
	private Sampler mSampler = null;
	private final Handler mStopRecHandler = new Handler();
	private Uri mEntry = null;

	public TrackingService() {
		mContext = this;
	}

	@Override
	public IBinder onBind(Intent arg0) {
		Log.v(TAG, "onBind()");
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
	}
	
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i(TAG, "Started tracking.");
		startTracking();
		return Service.START_STICKY;
	}
	
	private void startTracking() {
		
		
		/*
		 * DISABLED FOR NOW (TODO)
		 */
		stopTrackingService();
		
		
		/*
		// Start looking for location		
		if (!mMyLocation.getLocation(mContext, onLocationResult))
			stopTrackingService();	// if no location provider was found, stop the service
		*/	
	}

	private LocationResult onLocationResult = new LocationResult() {
	    @Override
	    public void gotLocation(final Location location) {	
	    	
	    	mMyLocation.stop(); // stop looking for location
	    	
	    	// Create new entry
			ContentValues values = new ContentValues();
			values.put(Entries.COLUMN_NAME_LATITUDE, location.getLatitude());
			values.put(Entries.COLUMN_NAME_LONGITUDE, location.getLongitude());
			
			if (mEntry == null) {
				values.put(Entries.COLUMN_NAME_USERNAME, AppSettings.getUsername(mContext));
				values.put(Entries.COLUMN_NAME_MUGSHOT, AppSettings.getMugshot(mContext));
				values.put(Entries.COLUMN_NAME_UUID, UUID.randomUUID().toString());
				values.put(Entries.COLUMN_NAME_TYPE, Entries.TYPE.TRACKED.ordinal());
				mEntry = mContext.getContentResolver().insert(Entries.CONTENT_URI, values);
				Log.d(TAG, "Entry created.");
			} else {
				mContext.getContentResolver().update(mEntry, values, null, null);
			}
			
			Log.d(TAG, "Entry updated with location.");
	    	
	    	if (mSampler == null) {
				mSampler = new Sampler();
				mSampler.setErrorListener(new SamplerListener() {
					@Override
					public void onError(String what) {
						Log.e(TAG, "Recording stopped with errors: " + what);
						mStopRecHandler.removeCallbacks(stopRecording);
						mContext.getContentResolver().delete(mEntry, null, null);
						stopTrackingService();
					}
				});
			}
			if (mSampler != null) {
				mSampler.startSampling();		// Start recording
				mStopRecHandler.postDelayed(	// Schedule handler to stop recording
						stopRecording,
						NoisetracksApplication.MAX_RECORDING_DURATION_SECONDS * 1000); 
			}
	    	
	    }
	};
	
	
	private Runnable stopRecording = new Runnable() {
		@Override
		public void run() {
			mSampler.stopRecording();
			// Update entry
			ContentValues values = new ContentValues();
			values.put(Entries.COLUMN_NAME_FILENAME, mSampler.getFilename());
			values.put(Entries.COLUMN_NAME_RECORDED, NoisetracksApplication.SDF.format(new Date()));
			values.put(Entries.COLUMN_NAME_TYPE, Entries.TYPE.UPLOADING.ordinal());
			int r = mContext.getContentResolver().update(mEntry, values, null, null);
			if (r != 0) {
	        	Log.d(TAG, "Entry updated with filename");
	        }
			
			// Try to upload
			new UploadTask(mContext).execute(mEntry);
			
			// Stop 
			stopTrackingService();
		}
	};
	
	private void stopTrackingService() {
		mMyLocation.stop(); // stop looking for location
		stopSelf();
		Log.i(TAG, "Stopped tracking.");
	}
	
}