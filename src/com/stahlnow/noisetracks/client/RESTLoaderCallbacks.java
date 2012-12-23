package com.stahlnow.noisetracks.client;

import java.util.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.google.android.maps.GeoPoint;
import com.handmark.pulltorefresh.library.PullToRefreshListView;
import com.stahlnow.noisetracks.provider.NoisetracksContract.Entries;
import com.stahlnow.noisetracks.ui.EntryAdapter;
import com.stahlnow.noisetracks.ui.EntryArrayAdapter;
import com.stahlnow.noisetracks.utility.AppLog;
import com.stahlnow.noisetracks.utility.AppSettings;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
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
    private PullToRefreshListView mPullToRefreshView;
    private TextView mEmpty;
    private TextView mPadding;
    private View mHeader;
    private View mFooter;
    
    // these members are initialized only based on the specific constructer, otherwise they are null
    private EntryAdapter mEntryAdapter = null;
    private EntryArrayAdapter mEntryArrayAdapter = null;
    private ListFragment mListFragment = null;
    
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
		this.mEntryArrayAdapter = null;
		this.mPullToRefreshView = pullToRefreshView;
		this.mEmpty = empty;
		this.mPadding = padding;
		this.mHeader = header;
		this.mFooter = footer;
	}
	
	/**
	 * Constructor with Array Adapter
	 * @param c
	 * @param adapter
	 * @param pullToRefreshView
	 * @param empty
	 * @param padding
	 * @param header
	 * @param footer
	 */
	/*
	public RESTLoaderCallbacks(Context c, ListFragment list, EntryArrayAdapter adapter, PullToRefreshListView pullToRefreshView, TextView empty,
			TextView padding, View header, View footer) {
		super();
		this.mContext = c;
		this.mListFragment = list;
		this.mEntryAdapter = null;
		this.mEntryArrayAdapter = adapter;
		this.mPullToRefreshView = pullToRefreshView;
		this.mEmpty = empty;
		this.mPadding = padding;
		this.mHeader = header;
		this.mFooter = footer;
	}
	*/

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
	            
	            // fill list with entries
	            switch (loader.getId()) {
	            case ENTRIES:
	            	if (mEntryAdapter != null) {
	            		if (!mEntryAdapter.isEmpty()) { // if the sql table is not empty, don't insert anything
	            			break;
	            		}
	            	}
	            	addEntriesFromJSON(json, false, true); // insert on top, remove items
	            	break;
	            case ENTRIES_OLDER:
	            	addEntriesFromJSON(json, true, false); // insert at end, no remove
	            	break;
	            case ENTRIES_NEWER:
	            	addEntriesFromJSON(json, false, false); // insert on top, no remove
	            	break;
	            default:
	            	break;
	            }
	            
	        }
	        else {
	        	Toast.makeText(mContext, "Failed to load data from Server.", Toast.LENGTH_SHORT).show();
	        }
	        
    	}
    	
    	else {
    		Toast.makeText(mContext, "Failed to load data from Server.\nCheck your internet settings.", Toast.LENGTH_SHORT).show();        
    	}
    	
    	// Reset pull refresh view
    	mPullToRefreshView.onRefreshComplete();
    	// Set updated text	    	
    	mPullToRefreshView.setLastUpdatedLabel("Last updated: " + DateUtils.formatDateTime(mContext, System.currentTimeMillis(), DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NUMERIC_DATE | DateUtils.FORMAT_ABBREV_TIME));
	    
    	/*
    	if (mEntryArrayAdapter != null) {
    		
    		mListFragment.setListShown(true);
    		
	    	if (mEntryArrayAdapter.isEmpty()) {
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
    	*/
    	
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
    	/*
    	if (mEntryArrayAdapter != null) {
        	mEntryArrayAdapter.clear(); 
        }
        */
    	if (mEntryAdapter != null) {
    		mEntryAdapter.changeCursor(null);
    	}
    }
    
    private void addEntriesFromJSON(String json, boolean insertAtEnd, boolean removeItems) {
    	
    	try {
            JSONObject entryWrapper = (JSONObject) new JSONTokener(json).nextValue();
            JSONArray entries     	= entryWrapper.getJSONArray("objects");
          
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
            
            //TimeZone zurichTZ = TimeZone.getTimeZone("Europe/Zurich");
            //sdf.setTimeZone(zurichTZ);
            
            /*
            if (mEntryArrayAdapter != null) {    // if we have an array adapter, remove all items
            	if (removeItems)
            		mEntryArrayAdapter.clear(); 
            }
            */
            
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                JSONObject user = entry.getJSONObject("user");
                JSONArray location = entry.getJSONObject("geometry").getJSONArray("coordinates");
                try {
                	
                	/*
                	// if we operate with an array
                	if (mEntryArrayAdapter != null) {
                		
                		if (insertAtEnd) {
                			mEntryArrayAdapter.add(new Entry(
                        			sdf.parse(entry.getString("created").substring(0,23)),
                        			sdf.parse(entry.getString("created").substring(0,23)), // TODO change api to provide 'recorded'
                        			AppSettings.DOMAIN + entry.getJSONObject("audiofile").getString("file"),
                        			new GeoPoint((int)(location.getDouble(1) * 1E6),(int)(location.getDouble(0) * 1E6)),
                        			user.getString("username"),
                        			user.getString("mugshot"),
                        			AppSettings.DOMAIN + entry.getJSONObject("audiofile").getString("spectrogram"),
                        			entry.getString("resource_uri")
                        			));
                		} else {
                		
	                		mEntryArrayAdapter.insert(new Entry(
	                    			sdf.parse(entry.getString("created").substring(0,23)),
	                    			sdf.parse(entry.getString("created").substring(0,23)), // TODO change api to provide 'recorded'
	                    			AppSettings.DOMAIN + entry.getJSONObject("audiofile").getString("file"),
	                    			new GeoPoint((int)(location.getDouble(1) * 1E6),(int)(location.getDouble(0) * 1E6)),
	                    			user.getString("username"),
	                    			user.getString("mugshot"),
	                    			AppSettings.DOMAIN + entry.getJSONObject("audiofile").getString("spectrogram"),
	                    			entry.getString("resource_uri")
	                    			), 0); // insert on top
                		}
                	}
                	*/
                	if (mEntryAdapter != null) {               
	                	ContentValues values = new ContentValues();
	                	values.put(Entries.COLUMN_NAME_FILENAME, AppSettings.DOMAIN + entry.getJSONObject("audiofile").getString("file"));
	                	values.put(Entries.COLUMN_NAME_SPECTROGRAM, AppSettings.DOMAIN + entry.getJSONObject("audiofile").getString("spectrogram"));
	                	values.put(Entries.COLUMN_NAME_LATITUDE, location.getDouble(1));
	                	values.put(Entries.COLUMN_NAME_LONGITUDE, location.getDouble(0));
	                	
	                	values.put(Entries.COLUMN_NAME_CREATED, entry.getString("created").substring(0,23));
	                	
	                	Date recorded = sdf.parse(entry.getString("created").substring(0,23)); // TODO change to recorded
	                	String rec_ago = DateUtils.getRelativeTimeSpanString(recorded.getTime(), System.currentTimeMillis(), 0L, DateUtils.FORMAT_ABBREV_ALL).toString();
	                	values.put(Entries.COLUMN_NAME_RECORDED, rec_ago);
	                	
	                	values.put(Entries.COLUMN_NAME_RESOURCE_URI, entry.getString("resource_uri"));
	                	
						values.put(Entries.COLUMN_NAME_MUGSHOT, user.getString("mugshot"));
						values.put(Entries.COLUMN_NAME_USERNAME, user.getString("username"));
						
						values.put(Entries.COLUMN_NAME_UUID, entry.getString("uuid"));
						
						// add entry to database
						mContext.getContentResolver().insert(Entries.CONTENT_URI, values);
                	}
						
				} catch (ParseException e) {
					AppLog.logString("Error parsing creation date string: " + e.toString());
				}
            }
        }
        catch (JSONException e) {
            AppLog.logString("Failed to parse JSON. " + e.toString());
        }
    	
    }
    

}
