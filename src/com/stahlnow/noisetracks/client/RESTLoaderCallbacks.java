package com.stahlnow.noisetracks.client;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.handmark.pulltorefresh.library.PullToRefreshListView;
import com.stahlnow.noisetracks.provider.NoisetracksContract.Entries;
import com.stahlnow.noisetracks.ui.EntryAdapter;
import com.stahlnow.noisetracks.utility.AppLog;
import com.stahlnow.noisetracks.utility.AppSettings;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public final class RESTLoaderCallbacks implements LoaderCallbacks<RESTLoader.RESTResponse> {
	
	public static final int ENTRIES = 100;
	public static final int ENTRIES_NEWER = 200;
	public static final int ENTRIES_OLDER = 300;
	
	public static final String ARGS_URI    = "com.stahlnow.noisetracks.ARGS_URI";
	public static final String ARGS_PARAMS = "com.stahlnow.noisetracks.ARGS_PARAMS";
    
	public static final Uri URI_ENTRIES = Uri.parse(AppSettings.DOMAIN + "/api/v1/entry/");
    
    private Context mContext;
    private EntryAdapter mEntryAdapter = null;
    private PullToRefreshListView mPullToRefreshView;
    private TextView mEmpty;
    private TextView mPadding;
    private View mHeader;
    private View mFooter;
    
    /**
     * Constructor with Simple Cursor Adapter
     * @param c The context
     * @param adapter The EntryAdapter
     * @param pullToRefreshView 
     * @param empty
     * @param padding
     * @param header
     * @param footer
     */
	public RESTLoaderCallbacks(Context c, EntryAdapter adapter, PullToRefreshListView pullToRefreshView, TextView empty,
			TextView padding, View header, View footer) {
		super();
		this.mContext = c;
		this.mEntryAdapter = adapter;
		this.mPullToRefreshView = pullToRefreshView;
		this.mEmpty = empty;
		this.mPadding = padding;
		this.mHeader = header;
		this.mFooter = footer;
	}

	@Override
    public Loader<RESTLoader.RESTResponse> onCreateLoader(int id, Bundle args) {
		
        if (args != null && args.containsKey(ARGS_URI) && args.containsKey(ARGS_PARAMS)) {
        	
            Uri    action = args.getParcelable(ARGS_URI);
            Bundle params = args.getParcelable(ARGS_PARAMS);
            
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
	            case ENTRIES:
	            	if (mEntryAdapter != null) {
	            		if (!mEntryAdapter.isEmpty()) { // if the sql table is not empty, don't insert anything
	            			break;
	            		}
	            	}
	            	addEntriesFromJSON(json);	// fill list with entries
	            	break;
	            case ENTRIES_OLDER:
	            case ENTRIES_NEWER:
	            	addEntriesFromJSON(json);	// fill list with entries
	            	break;
	            default:
	            	break;
	            }
	            
	        } else {
	        	Toast.makeText(mContext, "Failed to load data from Server.", Toast.LENGTH_SHORT).show();
	        }
    	} else {
    		Toast.makeText(mContext, "Failed to load data from Server.\nCheck your internet settings.", Toast.LENGTH_SHORT).show();        
    	}
    	
    	// Reset pull refresh view
    	mPullToRefreshView.onRefreshComplete();
    	// Set updated text	    	
    	mPullToRefreshView.setLastUpdatedLabel("Last updated: " + DateUtils.formatDateTime(mContext, System.currentTimeMillis(), DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NUMERIC_DATE | DateUtils.FORMAT_ABBREV_TIME));

    	if (mEntryAdapter != null) {
	    	if (mEntryAdapter.isEmpty()) {
	    		mPadding.setVisibility(View.INVISIBLE);
	        	mHeader.setVisibility(View.INVISIBLE);
	        	mFooter.setVisibility(View.INVISIBLE);
	    		mEmpty.setText("Pull to refresh.");
	    	} else {
	    		mPadding.setVisibility(View.VISIBLE);
	        	mHeader.setVisibility(View.VISIBLE);
	        	mFooter.setVisibility(View.VISIBLE);
	    		mEmpty.setText("");
	    	}
    	}
    }

    @Override
    public void onLoaderReset(Loader<RESTLoader.RESTResponse> loader) {
    	if (mEntryAdapter != null) {
    		mEntryAdapter.changeCursor(null);
    	}
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
                
				if (mEntryAdapter != null) {               
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
    

}
