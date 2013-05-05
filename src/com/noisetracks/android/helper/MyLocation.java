package com.noisetracks.android.helper;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

public class MyLocation {
	
	LocationManager mLocationManager;
	LocationResult mLocationResult;
	Location mLocation = null;
	
	public static abstract class LocationResult {
		public abstract void gotLocation(Location location);
	}

	public boolean getLocation(Context context, LocationResult result) {
		
		mLocationResult = result;
		
		if (mLocationManager == null)
			mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

		boolean gps_enabled = false;
		boolean network_enabled = false;
		// exceptions will be thrown if provider is not permitted.
		try {
			gps_enabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
		} catch (Exception ex) {}
		
		try {
			network_enabled = mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
		} catch (Exception ex) {}

		// don't start listeners if no provider is enabled
		if (!gps_enabled && !network_enabled)
			return false;

		if (gps_enabled)
			mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListenerGps);
		if (network_enabled)
			mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListenerNetwork);
		
		return true;
	}

	public void stop() {
		if (mLocationManager != null) {
			mLocationManager.removeUpdates(locationListenerGps);
			mLocationManager.removeUpdates(locationListenerNetwork);
		}
	}

	LocationListener locationListenerGps = new LocationListener() {
		public void onLocationChanged(Location location) {
			gotLocation(location);
		}
		public void onProviderDisabled(String provider) {}
		public void onProviderEnabled(String provider) {}
		public void onStatusChanged(String provider, int status, Bundle extras) {}
	};

	LocationListener locationListenerNetwork = new LocationListener() {
		public void onLocationChanged(Location location) {
			gotLocation(location);
		}
		public void onProviderDisabled(String provider) {}
		public void onProviderEnabled(String provider) {}
		public void onStatusChanged(String provider, int status, Bundle extras) {}
	};
	
	private void gotLocation(Location location) {
		
		if (isBetterLocation(location, mLocation)) {
			mLocation = location;
			mLocationResult.gotLocation(mLocation);
		}
	}
	
	/** Determines whether one Location reading is better than the current Location fix
	  * @param location  The new Location that you want to evaluate
	  * @param currentBestLocation  The current Location fix, to which you want to compare the new one
	  */
	private boolean isBetterLocation(Location location, Location currentBestLocation) {
	    if (currentBestLocation == null) {
	        // A new location is always better than no location
	        return true;
	    }

	    // Check whether the new location fix is newer or older
	    long timeDelta = location.getTime() - currentBestLocation.getTime();
	    boolean isSignificantlyNewer = timeDelta > (1000 * 60 * 2);
	    boolean isSignificantlyOlder = timeDelta < -(1000 * 60 * 2);
	    boolean isNewer = timeDelta > 0;

	    // If it's been more than two minutes since the current location, use the new location
	    // because the user has likely moved
	    if (isSignificantlyNewer) {
	        return true;
	    // If the new location is more than two minutes older, it must be worse
	    } else if (isSignificantlyOlder) {
	        return false;
	    }

	    // Check whether the new location fix is more or less accurate
	    int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
	    boolean isLessAccurate = accuracyDelta > 0;
	    boolean isMoreAccurate = accuracyDelta < 0;
	    boolean isSignificantlyLessAccurate = accuracyDelta > 200;

	    // Check if the old and new location are from the same provider
	    boolean isFromSameProvider = isSameProvider(location.getProvider(), currentBestLocation.getProvider());

	    // Determine location quality using a combination of timeliness and accuracy
	    if (isMoreAccurate) {
	        return true;
	    } else if (isNewer && !isLessAccurate) {
	        return true;
	    } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
	        return true;
	    }
	    return false;
	}

	/** Checks whether two providers are the same */
	private boolean isSameProvider(String provider1, String provider2) {
	    if (provider1 == null) {
	      return provider2 == null;
	    }
	    return provider1.equals(provider2);
	}


	
}
