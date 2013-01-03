package com.stahlnow.noisetracks.client;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.View;
import android.widget.TextView;

import com.stahlnow.noisetracks.NoisetracksApplication;
import com.stahlnow.noisetracks.provider.NoisetracksContract.Entries;
import com.stahlnow.noisetracks.provider.NoisetracksContract.Profiles;
import com.stahlnow.noisetracks.ui.EntryAdapter;
import com.stahlnow.noisetracks.ui.ProfileActivity.ProfileListFragment;

public class SQLLoaderCallbacks implements LoaderCallbacks<Cursor> {
	
	//public static final Uri ENTRIES = Entries.CONTENT_URI; // defines content provider uri for entries
	//public static final Uri PROFILES = Profiles.CONTENT_URI; // defines content provider uri for profiles
	
	// selection args
	public static final String SELECT = "select";
	public static final String SELECT_ENTRIES = "((" + Entries.COLUMN_NAME_FILENAME + " NOTNULL) AND (" + Entries.COLUMN_NAME_FILENAME + " != '' ))";
	public static final String SELECT_ENTRIES_WITHOUT_LOAD_MORE = "((" + Entries.COLUMN_NAME_FILENAME + " NOTNULL) AND (" + Entries.COLUMN_NAME_FILENAME + " != 'load' ))";
	
	// projection
	public static final String PROJECTION = "projection";
	
	private Context mContext;
	private EntryAdapter mAdapter;
	private ListFragment mListFragment;
	private TextView mEmpty;
	private TextView mPadding;
    private View mHeader;
    private View mFooter;
	
	public SQLLoaderCallbacks(Context c, EntryAdapter adapter, ListFragment fragment, TextView empty,
			TextView padding, View header, View footer) {
		super();
		this.mContext = c;
		this.mAdapter = adapter;
		this.mListFragment = fragment;
		this.mEmpty = empty;
		this.mPadding = padding;
		this.mHeader = header;
		this.mFooter = footer;
	}
	
	/**
	 * Generates selection argument for query with only user entries
	 * @param username The username 
	 * @return The selection argument
	 */
	public static String selectEntriesFromUser(String username, boolean loadmore) {
		if (loadmore) {
			return "((" + Entries.COLUMN_NAME_FILENAME + " NOTNULL) AND (" + Entries.COLUMN_NAME_FILENAME + " != '' ) AND (" + Entries.COLUMN_NAME_USERNAME + " == '" + username + "' ))";
		} else {
			return "((" + Entries.COLUMN_NAME_FILENAME + " NOTNULL) AND (" + Entries.COLUMN_NAME_FILENAME + " != '' ) AND (" + Entries.COLUMN_NAME_USERNAME + " == '" + username + "' ) AND (" + Entries.COLUMN_NAME_FILENAME + " != 'load' ))";
		}
	}
	
	public static String selectProfileForUser(String username) {
		return "((" + Profiles.COLUMN_NAME_USERNAME + " NOTNULL) AND (" + Profiles.COLUMN_NAME_USERNAME + " == '" + username + "' ))";
	}
    
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		switch(id) {
		case NoisetracksApplication.ENTRIES_SQL_LOADER_FEED:
			return new CursorLoader(
	        		mContext,						// context
	        		Entries.CONTENT_URI,			// content uri
	                args.getStringArray(PROJECTION),// projection
	                args.getString(SELECT),			// selection criteria
	                null,							// selection args
	                Entries.DEFAULT_SORT_ORDER);	// sorting
		case NoisetracksApplication.ENTRIES_SQL_LOADER_PROFILE:
			return new CursorLoader(
	        		mContext,						// context
	        		Entries.CONTENT_URI,			// content uri
	                args.getStringArray(PROJECTION),// projection
	                args.getString(SELECT),			// selection criteria
	                null,							// selection args
	                Entries.DEFAULT_SORT_ORDER);	// sorting
		case NoisetracksApplication.PROFILE_SQL_LOADER:
			return new CursorLoader(
	        		mContext,						// context
	        		Profiles.CONTENT_URI,			// content uri
	                args.getStringArray(PROJECTION),// projection
	                args.getString(SELECT),			// selection criteria
	                null,							// selection args
	                Profiles.DEFAULT_SORT_ORDER);	// sorting
		default:
			return null; // shouldn't happen
		}
			
        
    }

    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
    	
    	switch(loader.getId()) {
    	case NoisetracksApplication.ENTRIES_SQL_LOADER_FEED: 	// entries for feed
    	case NoisetracksApplication.ENTRIES_SQL_LOADER_PROFILE: // entries for profile
    		mAdapter.swapCursor(data);
        	
        	if (mAdapter.isEmpty()) {
        		mPadding.setVisibility(View.INVISIBLE);
            	mHeader.setVisibility(View.INVISIBLE);
            	mFooter.setVisibility(View.INVISIBLE);
        		mEmpty.setText("Pull to refresh");
        	} else {
        		mPadding.setVisibility(View.VISIBLE);
            	mHeader.setVisibility(View.VISIBLE);
            	mFooter.setVisibility(View.VISIBLE);
        		mEmpty.setText("");
        	}
        	
            if (mListFragment.isResumed()) {
            	mListFragment.setListShown(true);
            } else {
            	mListFragment.setListShownNoAnimation(true);
            }
    		break;
    	case NoisetracksApplication.PROFILE_SQL_LOADER: // single profile
    		ProfileListFragment plf = (ProfileListFragment)mListFragment;
    		plf.setProfileHeader(data);
    		break;
    	default:
    		break;
    	}
        
    }

    public void onLoaderReset(Loader<Cursor> loader) {
    	switch(loader.getId()) {
    	case NoisetracksApplication.ENTRIES_SQL_LOADER_FEED: // entries for feed
    	case NoisetracksApplication.ENTRIES_SQL_LOADER_PROFILE: // entries for profile
    		mAdapter.swapCursor(null);
    		break;
    	default:
    		break;
    	}
    }
}
