package com.stahlnow.noisetracks.client;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.stahlnow.noisetracks.NoisetracksApplication;
import com.stahlnow.noisetracks.authenticator.SignupActivity.SignupFragment;
import com.stahlnow.noisetracks.provider.NoisetracksContract.Entries;
import com.stahlnow.noisetracks.provider.NoisetracksContract.Profiles;
import com.stahlnow.noisetracks.ui.EntryActivity.EntryDetailFragment;
import com.stahlnow.noisetracks.ui.FeedActivity.FeedListFragment;
import com.stahlnow.noisetracks.ui.ProfileActivity.ProfileListFragment;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.util.Log;
import android.widget.Toast;

public final class RESTLoaderCallbacks implements LoaderCallbacks<RESTLoader.RESTResponse> {
	
	private static final String TAG = "RESTLoaderCallbacks";
	
	public static final String ARGS_URI    = "com.stahlnow.noisetracks.ARGS_URI";
	public static final String ARGS_PARAMS = "com.stahlnow.noisetracks.ARGS_PARAMS";
    
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
	    	
	    	switch(id) {
	    	case NoisetracksApplication.SIGNUP_REST_LOADER:
	    	case NoisetracksApplication.VOTE_LOADER:
	    		return new RESTLoader(mContext, RESTLoader.HTTPVerb.POST, action, params);
			default:
	    		return new RESTLoader(mContext, RESTLoader.HTTPVerb.GET, action, params);
	    	}	
	    }
	    return null;
    }

    @Override
    public void onLoadFinished(Loader<RESTLoader.RESTResponse> loader, RESTLoader.RESTResponse data) {
    	
    	if (data != null) {
    		
	        int    code = data.getCode();
	        String json = data.getData();
	        
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
	        	case NoisetracksApplication.VOTE_LOADER:
	        		EntryDetailFragment df = (EntryDetailFragment)mFragment;
	        		df.onVoteComplete(json);
	        		break;
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
	        } else if (code == 500) {
	        	Toast.makeText(mContext, "Server responded with error. Try again later.", Toast.LENGTH_SHORT).show();
	        } else {
	        	Toast.makeText(mContext, "Could not connect to Noisetracks.", Toast.LENGTH_SHORT).show();
	        }
    	} else {
    		Toast.makeText(mContext, "Could not connect to Noisetracks.", Toast.LENGTH_SHORT).show();        
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
    
    public static void addEntriesFromJSON(String json) {
    	
    	try {
            JSONObject entryWrapper = (JSONObject) new JSONTokener(json).nextValue();
            JSONObject meta 		= entryWrapper.getJSONObject("meta");
            JSONArray entries     	= entryWrapper.getJSONArray("objects");
            
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                JSONObject user = entry.getJSONObject("user");
                JSONArray location = entry.getJSONObject("location").getJSONArray("coordinates");
                
				             
				ContentValues values = new ContentValues();
				values.put(Entries.COLUMN_NAME_FILENAME, NoisetracksApplication.DOMAIN + entry.getJSONObject("audiofile").getString("file"));
				values.put(Entries.COLUMN_NAME_SPECTROGRAM, NoisetracksApplication.DOMAIN + entry.getJSONObject("audiofile").getString(Entries.COLUMN_NAME_SPECTROGRAM));
				values.put(Entries.COLUMN_NAME_LATITUDE, location.getDouble(1));
				values.put(Entries.COLUMN_NAME_LONGITUDE, location.getDouble(0));
				values.put(Entries.COLUMN_NAME_CREATED, entry.getString(Entries.COLUMN_NAME_CREATED).substring(0,19));					
				values.put(Entries.COLUMN_NAME_RECORDED, entry.getString(Entries.COLUMN_NAME_RECORDED).substring(0,19));
				values.put(Entries.COLUMN_NAME_RESOURCE_URI, entry.getString(Entries.COLUMN_NAME_RESOURCE_URI));
				values.put(Entries.COLUMN_NAME_MUGSHOT, user.getString(Entries.COLUMN_NAME_MUGSHOT));
				values.put(Entries.COLUMN_NAME_USERNAME, user.getString(Entries.COLUMN_NAME_USERNAME));
				values.put(Entries.COLUMN_NAME_UUID, entry.getString(Entries.COLUMN_NAME_UUID));
				values.put(Entries.COLUMN_NAME_SCORE, entry.getInt(Entries.COLUMN_NAME_SCORE));
				values.put(Entries.COLUMN_NAME_VOTE, entry.getInt(Entries.COLUMN_NAME_VOTE));
				values.put(Entries.COLUMN_NAME_TYPE, Entries.TYPE.DOWNLOADED.ordinal()); // set type to DOWNLOADED
				
				// add entry to database
				NoisetracksApplication.getInstance().getContentResolver().insert(Entries.CONTENT_URI, values);
				
            }
            
            /*
            // add 'load more' special entry
            if (meta.getString("next") != "null") {
            	ContentValues values = new ContentValues();
            	String created = entries.getJSONObject(entries.length()-1).getString("created").substring(0,19);
            	// add 1 second to 'created' time
            	Integer sec = Integer.getInteger(Character.valueOf(created.charAt(18)).toString());
            	sec++;
            	StringBuilder _created_ = new StringBuilder(created);
            	_created_.setCharAt(18, Integer.toString(sec).toCharArray()[0]); // replace second with new value
            	values.put(Entries.COLUMN_NAME_CREATED, _created_.toString()); // set created to last entry + one second
            	values.put(Entries.COLUMN_NAME_RESOURCE_URI, meta.getString("next")); // special: resource uri is value of 'next'
            	values.put(Entries.COLUMN_NAME_TYPE, Entries.TYPE.LOAD_MORE.ordinal()); // Set type to LOAD_MORE
            	mContext.getContentResolver().insert(Entries.CONTENT_URI, values);
            }
            */
            
        }
        catch (JSONException e) {
            Log.w(TAG, "Failed to parse JSON. " + e.toString());
        }
    	
    }
    
    public static void addProfilesFromJSON(String json) {
    	try {
            JSONObject profileWrapper = (JSONObject) new JSONTokener(json).nextValue();
            JSONObject meta 		= profileWrapper.getJSONObject("meta");
            JSONArray profiles     	= profileWrapper.getJSONArray("objects");
            
            for (int i = 0; i < profiles.length(); i++) {
                JSONObject profile = profiles.getJSONObject(i);
                JSONObject user = profile.getJSONObject("user");
                
				              
				ContentValues values = new ContentValues();
					
				values.put(Profiles.COLUMN_NAME_USERNAME, user.getString(Profiles.COLUMN_NAME_USERNAME));
					
				try {
					values.put(Profiles.COLUMN_NAME_EMAIL, user.getString(Profiles.COLUMN_NAME_EMAIL));
				}
				catch (JSONException e) {
			         // email is only visible to logged in user
			    }
					
				values.put(Profiles.COLUMN_NAME_MUGSHOT, user.getString("mugshot"));
				values.put(Profiles.COLUMN_NAME_BIO, profile.getString(Profiles.COLUMN_NAME_BIO));
				values.put(Profiles.COLUMN_NAME_NAME, profile.getString(Profiles.COLUMN_NAME_NAME));
				values.put(Profiles.COLUMN_NAME_TRACKS, profile.getInt(Profiles.COLUMN_NAME_TRACKS));
				values.put(Profiles.COLUMN_NAME_WEBSITE, profile.getString(Profiles.COLUMN_NAME_WEBSITE));
					
				// add entry to database
				NoisetracksApplication.getInstance().getContentResolver().insert(Profiles.CONTENT_URI, values);
				
            }
        }
        catch (JSONException e) {
            Log.w(TAG, "Failed to parse JSON. " + e.toString());
        }
    }
    

}
