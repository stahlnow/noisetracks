package com.noisetracks.android.client;

import java.net.MalformedURLException;
import java.net.URL;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.noisetracks.android.NoisetracksApplication;
import com.noisetracks.android.authenticator.SignupActivity.SignupFragment;
import com.noisetracks.android.provider.NoisetracksContract.Entries;
import com.noisetracks.android.provider.NoisetracksContract.Profiles;
import com.noisetracks.android.ui.EntryActivity;
import com.noisetracks.android.ui.EntryActivity.EntryDetailFragment;
import com.noisetracks.android.ui.FeedActivity.FeedListFragment;
import com.noisetracks.android.ui.ProfileActivity.ProfileListFragment;
import com.noisetracks.android.utility.AppSettings;

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
	
	public static final String ARGS_URI    = "com.noisetracks.android.ARGS_URI";
	public static final String ARGS_PARAMS = "com.noisetracks.android.ARGS_PARAMS";
    
    private Context mContext;
    private Object mCaller;
    
	public RESTLoaderCallbacks(Context c, Object obj) {
		super();
		mContext = c;
		mCaller = obj;
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
	    	case NoisetracksApplication.DELETE_LOADER:
	    		return new RESTLoader(mContext, RESTLoader.HTTPVerb.DELETE, action, params);
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
	        
	        if (code == 200 && !json.equals("")) { // OK
	            switch (loader.getId()) {
	            case NoisetracksApplication.ENTRIES_USER_REST_LOADER:
	            case NoisetracksApplication.ENTRIES_USER_NEWER_REST_LOADER:
	            	NoisetracksApplication.getInstance().getContentResolver().delete(
	        				Entries.CONTENT_URI,
	        				Entries.COLUMN_NAME_TYPE + " = ? AND " + Entries.COLUMN_NAME_USERNAME + " = ?",
	        				new String[] {Integer.toString(Entries.TYPE.DOWNLOADED.ordinal()), AppSettings.getUsername(mContext)});
	            	addEntriesFromJSON(json);	// add entries to db
	            	break;	            	
	            case NoisetracksApplication.ENTRIES_REST_LOADER:
	            case NoisetracksApplication.ENTRIES_NEWER_REST_LOADER:
	            	
	            	NoisetracksApplication.getInstance().getContentResolver().delete(
	        				Entries.CONTENT_URI,
	        				Entries.COLUMN_NAME_TYPE + " = ?",
	        				new String[] {Integer.toString(Entries.TYPE.DOWNLOADED.ordinal())});
	            	addEntriesFromJSON(json);	// add entries to db
	            	break;
	            case NoisetracksApplication.ENTRIES_OLDER_REST_LOADER:
	            	addEntriesFromJSON(json); // add entries, keep existing entries
	            case NoisetracksApplication.PROFILE_REST_LOADER:
	            	addProfilesFromJSON(json);
	            	break;
	            case NoisetracksApplication.DELETE_LOADER:
	        		((EntryActivity)mCaller).onDeleteEntry(json); // json contains only the uuid as string
	        		break;
	            default:
	            	break;
	            }
	        } else if (code == 201) { // CREATED
	        	switch (loader.getId()) {
	        	case NoisetracksApplication.VOTE_LOADER:
	        		((EntryDetailFragment)mCaller).onVoteComplete(json);
	        		break;
	        	case NoisetracksApplication.SIGNUP_REST_LOADER:
	        		((SignupFragment)mCaller).onSignupComplete();
	        		break;
	        	default:
	        		break;
	        	}
	        } else if (code == 204) { // NO CONTENT
	        	switch (loader.getId()) {
	        	
	        	default:
	        		break;
	        	}
	        	
	        } else if (code == 400) { // BAD REQUEST
	        	switch (loader.getId()) {
	        	case NoisetracksApplication.SIGNUP_REST_LOADER:
	        		((SignupFragment)mCaller).onErrorSigningUp(json);
	        	default:
	        		break;
	        	}
	        } else if (code == 500) { // SERVER ERROR
	        	Toast.makeText(mContext, "Server responded with error. Try again later.", Toast.LENGTH_SHORT).show();
	        } else {
	        	Toast.makeText(mContext, "Could not connect to Noisetracks.", Toast.LENGTH_SHORT).show();
	        }
    	} else {
    		Toast.makeText(mContext, "Could not connect to Noisetracks.", Toast.LENGTH_SHORT).show();        
    	}
    	if (mCaller != null) {
	    	if (mCaller.getClass().getSimpleName() == "ProfileListFragment") {
	    		((ProfileListFragment)mCaller).onRefreshComplete();
	    	} else if (mCaller.getClass().getSimpleName() == "FeedListFragment") {
	    		((FeedListFragment)mCaller).onRefreshComplete();
	    	}
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
				// check if mugshot is gravatar (full url) or from server
				boolean gravatar = isValidUrl(user.getString(Entries.COLUMN_NAME_MUGSHOT));
				if (gravatar)
					values.put(Entries.COLUMN_NAME_MUGSHOT, user.getString(Entries.COLUMN_NAME_MUGSHOT));
				else
					values.put(Entries.COLUMN_NAME_MUGSHOT, NoisetracksApplication.DOMAIN + user.getString(Entries.COLUMN_NAME_MUGSHOT));
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
				
				// check if mugshot is gravatar (full url) or from server
				boolean gravatar = isValidUrl(user.getString(Profiles.COLUMN_NAME_MUGSHOT));
				if (gravatar)
					values.put(Profiles.COLUMN_NAME_MUGSHOT, user.getString(Profiles.COLUMN_NAME_MUGSHOT));
				else
					values.put(Profiles.COLUMN_NAME_MUGSHOT, NoisetracksApplication.DOMAIN + user.getString(Profiles.COLUMN_NAME_MUGSHOT));
				
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
    
    private static boolean isValidUrl(String url) {
    	try {
    		URL u = new URL(url);
    	} catch(MalformedURLException e) {
    		return false;
    	}
    	return true;
    }

}
