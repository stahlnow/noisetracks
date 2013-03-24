package com.stahlnow.noisetracks.client;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.stahlnow.noisetracks.NoisetracksApplication;
import com.stahlnow.noisetracks.authenticator.SignupActivity.SignupFragment;
import com.stahlnow.noisetracks.provider.NoisetracksContract.Entries;
import com.stahlnow.noisetracks.provider.NoisetracksContract.Profiles;
import com.stahlnow.noisetracks.ui.FeedActivity.FeedListFragment;
import com.stahlnow.noisetracks.ui.ProfileActivity.ProfileListFragment;
import com.stahlnow.noisetracks.utility.AppLog;
import com.stahlnow.noisetracks.utility.AppSettings;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.widget.Toast;

public final class RESTLoaderCallbacks implements LoaderCallbacks<RESTLoader.RESTResponse> {
	
	public static final String ARGS_URI    = "com.stahlnow.noisetracks.ARGS_URI";
	public static final String ARGS_PARAMS = "com.stahlnow.noisetracks.ARGS_PARAMS";
    
	public static final Uri URI_ENTRIES = Uri.parse(AppSettings.DOMAIN + "/api/v1/entry/");
	public static final Uri URI_PROFILES = Uri.parse(AppSettings.DOMAIN + "/api/v1/profile/");
	public static final Uri URI_SIGNUP = Uri.parse(AppSettings.DOMAIN + "/api/v1/signup/");
    
    private Context mContext;
    private Fragment mFragment;
    
    
	public RESTLoaderCallbacks(Context c, Fragment f) {
		super();
		mContext = c;
		mFragment = f;
	}
			

	@Override
    public Loader<RESTLoader.RESTResponse> onCreateLoader(int id, Bundle args) {
		
	    if (args != null && args.containsKey(ARGS_URI) && args.containsKey(ARGS_PARAMS)) {
	    	Uri    action = args.getParcelable(ARGS_URI);
	    	Bundle params = args.getParcelable(ARGS_PARAMS);
	    	
	    	if (id == NoisetracksApplication.SIGNUP_REST_LOADER)
	    		return new RESTLoader(mContext, RESTLoader.HTTPVerb.POST, action, params);
	    	else
	    		return new RESTLoader(mContext, RESTLoader.HTTPVerb.GET, action, params);
	    }
	    return null;
    }

    @Override
    public void onLoadFinished(Loader<RESTLoader.RESTResponse> loader, RESTLoader.RESTResponse data) {
    	
    	if (data != null) {
    		
	        int    code = data.getCode();
	        String json = data.getData();
	        
	        // check to see if we got an HTTP 200 code and have some data.
	        if (code == 200 && !json.equals("")) {
	            
	            switch (loader.getId()) {
	            case NoisetracksApplication.ENTRIES_REST_LOADER:
	            case NoisetracksApplication.ENTRIES_OLDER_REST_LOADER:
	            case NoisetracksApplication.ENTRIES_NEWER_REST_LOADER:
	            	addEntriesFromJSON(json);	// fill list with entries
	            	break;
	            case NoisetracksApplication.PROFILE_REST_LOADER:
	            	addProfilesFromJSON(json);
	            	break;
	            default:
	            	break;
	            }
	        } else if (code == 201) {
	        	switch (loader.getId()) {
	        	case NoisetracksApplication.SIGNUP_REST_LOADER:
	        		SignupFragment sf = (SignupFragment)mFragment;
	        		sf.onSignupComplete();
	        		break;
	        	default:
	        		break;
	        	}
	        	
	        } else if (code == 400) {
	        	switch (loader.getId()) {
	        	case NoisetracksApplication.SIGNUP_REST_LOADER:
	        		SignupFragment sf = (SignupFragment)mFragment;
	        		sf.onErrorSigningUp(json);
	        	default:
	        		break;
	        	}
	        } else {
	        	Toast.makeText(mContext, "Failed to load data from Server.", Toast.LENGTH_SHORT).show();
	        }
    	} else {
    		Toast.makeText(mContext, "Failed to load data from Server.\nCheck your internet settings.", Toast.LENGTH_SHORT).show();        
    	}
    	
    	if (mFragment.getClass().getSimpleName() == "ProfileListFragment") {
    		ProfileListFragment plf = (ProfileListFragment)mFragment;
    		plf.onRefreshComplete();
    	} else if (mFragment.getClass().getSimpleName() == "FeedListFragment") {
    		FeedListFragment plf = (FeedListFragment)mFragment;
    		plf.onRefreshComplete();
    	}
    	
    }

    @Override
    public void onLoaderReset(Loader<RESTLoader.RESTResponse> loader) {
    
    }
    
    private void addEntriesFromJSON(String json) {
    	
    	try {
            JSONObject entryWrapper = (JSONObject) new JSONTokener(json).nextValue();
            JSONObject meta 		= entryWrapper.getJSONObject("meta");
            JSONArray entries     	= entryWrapper.getJSONArray("objects");
            
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                JSONObject user = entry.getJSONObject("user");
                JSONArray location = entry.getJSONObject("location").getJSONArray("coordinates");
                
				             
				ContentValues values = new ContentValues();
				values.put(Entries.COLUMN_NAME_FILENAME, AppSettings.DOMAIN + entry.getJSONObject("audiofile").getString("file"));
				values.put(Entries.COLUMN_NAME_SPECTROGRAM, AppSettings.DOMAIN + entry.getJSONObject("audiofile").getString("spectrogram"));
				values.put(Entries.COLUMN_NAME_LATITUDE, location.getDouble(1));
				values.put(Entries.COLUMN_NAME_LONGITUDE, location.getDouble(0));
				values.put(Entries.COLUMN_NAME_CREATED, entry.getString("created").substring(0,23));					
				values.put(Entries.COLUMN_NAME_RECORDED, entry.getString("recorded").substring(0,23));
				values.put(Entries.COLUMN_NAME_RESOURCE_URI, entry.getString("resource_uri"));
				values.put(Entries.COLUMN_NAME_MUGSHOT, user.getString("mugshot"));
				values.put(Entries.COLUMN_NAME_USERNAME, user.getString("username"));
				values.put(Entries.COLUMN_NAME_UUID, entry.getString("uuid"));
				// add entry to database
				mContext.getContentResolver().insert(Entries.CONTENT_URI, values);
				
            }
            
            // hack: add 'load more' special entry
            if (meta.getString("next") != "null") {
            	ContentValues values = new ContentValues();
            	values.put(Entries.COLUMN_NAME_FILENAME, "load");
            	String created = entries.getJSONObject(entries.length()-1).getString("created").substring(0,23);
            	values.put(Entries.COLUMN_NAME_CREATED, created); // set created to last entry, so the 'load more' entry appears right after the last entry we loaded.
            	values.putNull(Entries.COLUMN_NAME_RECORDED); // set recorded to null
            	values.put(Entries.COLUMN_NAME_RESOURCE_URI, meta.getString("next")); // special: resorce uri is value of 'next'
            	String username = entries.getJSONObject(entries.length()-1).getJSONObject("user").getString("username");
            	values.put(Entries.COLUMN_NAME_USERNAME, username);
            	mContext.getContentResolver().insert(Entries.CONTENT_URI, values);
            }
            
        }
        catch (JSONException e) {
            AppLog.logString("Failed to parse JSON. " + e.toString());
        }
    	
    }
    
    private void addProfilesFromJSON(String json) {
    	try {
            JSONObject profileWrapper = (JSONObject) new JSONTokener(json).nextValue();
            JSONObject meta 		= profileWrapper.getJSONObject("meta");
            JSONArray profiles     	= profileWrapper.getJSONArray("objects");
            
            for (int i = 0; i < profiles.length(); i++) {
                JSONObject profile = profiles.getJSONObject(i);
                JSONObject user = profile.getJSONObject("user");
                
				              
				ContentValues values = new ContentValues();
					
				values.put(Profiles.COLUMN_NAME_USERNAME, user.getString("username"));
					
				try {
					values.put(Profiles.COLUMN_NAME_EMAIL, user.getString("email"));
				}
				catch (JSONException e) {
			         // email is only visible to logged in user
			    }
					
				values.put(Profiles.COLUMN_NAME_MUGSHOT, user.getString("mugshot"));
					
				values.put(Profiles.COLUMN_NAME_BIO, profile.getString("bio"));
				values.put(Profiles.COLUMN_NAME_NAME, profile.getString("name"));
				values.put(Profiles.COLUMN_NAME_TRACKS, profile.getInt("tracks"));
				values.put(Profiles.COLUMN_NAME_WEBSITE, profile.getString("website"));
					
				// add entry to database
				mContext.getContentResolver().insert(Profiles.CONTENT_URI, values);
				
            }
        }
        catch (JSONException e) {
            AppLog.logString("Failed to parse JSON. " + e.toString());
        }
    }
    

}
