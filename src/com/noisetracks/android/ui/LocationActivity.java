package com.noisetracks.android.ui;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.noisetracks.android.R;
import com.noisetracks.android.client.UploadTask;
import com.noisetracks.android.helper.MyLocation;
import com.noisetracks.android.helper.MyLocation.LocationResult;
import com.noisetracks.android.provider.NoisetracksContract.Entries;

public class LocationActivity extends SherlockFragmentActivity implements LocationListener, OnMapClickListener {

	private static final String TAG = "LocationActivity";
	
	private Context mContext;
	private GoogleMap mMap;
	private Uri mEntry; 											// uri to entry
	private MyLocation mMyLocation = new MyLocation();
	private Marker mMarker;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.location_activity);
		
		mContext = this;
		
		mEntry = getIntent().getParcelableExtra(RecordingActivity.EXTRA_URI);
		
		if (mEntry == null) {
			Log.e(TAG, "Invalid entry.");
			finish();
		}
		
		setUpMapIfNeeded();
		
		// get entry
		Cursor c = this.getContentResolver().query(mEntry, null, null, null, null);
		if (c != null) {
			if (c.moveToFirst()) {
				// Check if a location has been set before
				float lat = c.getFloat(c.getColumnIndex(Entries.COLUMN_NAME_LATITUDE));
				float lng = c.getFloat(c.getColumnIndex(Entries.COLUMN_NAME_LONGITUDE));
				if (lat == 0.0 && lng == 0.0) {
					// Start looking for location fix
					if (!mMyLocation.getLocation(this, locationResult)) {
						Toast.makeText(mContext, "Could not get location", Toast.LENGTH_SHORT).show();
					}
				} else {
					Location l = new Location("");
					l.setLatitude(lat);
					l.setLongitude(lng);
					if (mMap != null) {
						if (mMarker == null) {
							mMarker = mMap.addMarker(new MarkerOptions()
					        .position(new LatLng(lat, lng))
					        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
						}
					}
					onLocationChanged(l); // center map on location and add marker
				}
				c.close();
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		setUpMapIfNeeded();
	}

	@Override
	protected void onPause() {
		super.onPause();
		
		if (mMyLocation != null) {
			mMyLocation.stop();
		}
	}
	
	/**
	 * User clicked 'Post' button
	 * @param view
	 */
	public void post(View view) {
		
		if (mMarker != null) { 
			if (mMarker.getPosition().latitude == 0.0 && mMarker.getPosition().longitude == 0.0) {
				Toast.makeText(mContext, getString(R.string.set_location), Toast.LENGTH_SHORT).show();
			}
			
			else {
				Cursor c = mContext.getContentResolver().query(mEntry, null, null, null, null);
				if (c != null) {
					if (c.moveToFirst()) {
				        ContentValues cv = new ContentValues();
				        cv.put(Entries.COLUMN_NAME_LATITUDE, mMarker.getPosition().latitude);
				        cv.put(Entries.COLUMN_NAME_LONGITUDE, mMarker.getPosition().longitude);
				        cv.put(Entries.COLUMN_NAME_TYPE, Entries.TYPE.UPLOADING.ordinal());
				        mContext.getContentResolver().update(mEntry, cv, null, null);
				        
				        c.close();
					}
				}
				new UploadTask(this).execute(mEntry);
				finish();
			}
		} else {
			Toast.makeText(mContext, getString(R.string.set_location), Toast.LENGTH_SHORT).show();
		}
	}
	
	/**
	 * User clicked 'Back' button
	 * @param view
	 */
	public void back(View view) {
		Intent locationActivity = new Intent(this, RecordingActivity.class);
		locationActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
		locationActivity.putExtra(RecordingActivity.EXTRA_URI, mEntry);
		startActivity(locationActivity);
		finish();
	}
	
	
	private LocationResult locationResult = new LocationResult() {
	    @Override
	    public void gotLocation(Location location) {
			onLocationChanged(location);
	    }
	};

	private void setUpMapIfNeeded() {
		// Do a null check to confirm that we have not already instantiated the map.
		if (mMap == null) {
			// Try to obtain the map from the SupportMapFragment.
			mMap = ((SupportMapFragment) getSupportFragmentManager()
					.findFragmentById(R.id.location_map)).getMap();
			// Check if we were successful in obtaining the map.
			if (mMap != null) {
				setUpMap();
			}
		}
	}

	private void setUpMap() {
		// Enable MyLocation overlay
		mMap.setMyLocationEnabled(true);
		mMap.setOnMapClickListener(this);
	}
	
	@Override
	public void onLocationChanged(Location location) {
		Log.v(TAG, "New location fix at " + location.toString());
		double lat = location.getLatitude();
		double lng = location.getLongitude();
		LatLng l = new LatLng(lat, lng);
		
		CameraUpdate center = CameraUpdateFactory.newLatLng(l);
		mMap.moveCamera(center);
		mMap.animateCamera(CameraUpdateFactory.zoomTo(18.0f));
	}
	
	@Override
    public void onMapClick(LatLng point) {
		
		if (mMarker == null) {
			mMarker = mMap.addMarker(new MarkerOptions()
		       .position(point)
		       .title(Double.toString(point.latitude) + ", " + Double.toString(point.longitude))
		       .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
		} else {
	        mMarker.setPosition(point);
	        mMarker.setTitle(Double.toString(point.latitude) + ", " + Double.toString(point.longitude));
		}
		
        Cursor c = mContext.getContentResolver().query(mEntry, null, null, null, null);
		if (c != null) {
			if (c.moveToFirst()) {
		        ContentValues cv = new ContentValues();
		        cv.put(Entries.COLUMN_NAME_LATITUDE, point.latitude);
		        cv.put(Entries.COLUMN_NAME_LONGITUDE, point.longitude);
		        mContext.getContentResolver().update(mEntry, cv, null, null);
		        c.close();
			}
		}
	}

	@Override
	public void onProviderDisabled(String provider) { }

	@Override
	public void onProviderEnabled(String provider) { }

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) { }

	
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			Intent parentActivityIntent = new Intent(this, Tabs.class);
			parentActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(parentActivityIntent);
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
