package com.stahlnow.noisetracks.client;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;

import com.stahlnow.noisetracks.NoisetracksApplication;
import com.stahlnow.noisetracks.provider.NoisetracksContract.Entries;
import com.stahlnow.noisetracks.provider.NoisetracksContract.Profiles;
import com.stahlnow.noisetracks.ui.FeedActivity.FeedListFragment;
import com.stahlnow.noisetracks.ui.ProfileActivity.ProfileListFragment;

public class SQLLoaderCallbacks implements LoaderCallbacks<Cursor> {
	
	private static final String TAG = "SQLLoaderCallbacks";
	
	// selection args
	public static final String SELECT = "select"; 
	
	/**
	 * Select all entries
	 * @param loadmore Set to true, if 'load more' entries should be selected
	 * @return String selection for SQL query
	 */
	public static String EntriesFeed(boolean loadmore) {
		return Entries(loadmore, false, "");
	}
	
	/**
	 * Select entries for user
	 * @param loadmore Set to true, if 'load more' entries should be selected
	 * @param recorded Set to true, if 'recorded' entries should be selected
	 * @param username Username for the selection
	 * @return String selection for SQL query
	 */
	public static String EntriesUser(boolean recorded, String username) {
		return Entries(false, recorded, username);
	}
	
	/**
	 * Generates selection argument for query with only user entries
	 * @param loadmore Set to true, if 'load more' entries should be selected
	 * @param recorded Set to true, if 'recorded' entries should be selected
	 * @param username The username 
	 * @return String selection for SQL query
	 */
	private static String Entries(boolean loadmore, boolean recorded, String username) {
		
		// if username is empty, load all entries, ignore recorded parameter
		if (username == "") {
			if (loadmore) {
				return "(" + 
						"(" + Entries.COLUMN_NAME_TYPE + " NOTNULL)" +
						" AND (" +
							Entries.COLUMN_NAME_TYPE + " = '" + Entries.TYPE.DOWNLOADED.ordinal() + "'" +
							" OR " +
							Entries.COLUMN_NAME_TYPE + " = '" + Entries.TYPE.LOAD_MORE.ordinal() + "'" + " )" +
						")";
			} else {
				return "((" + Entries.COLUMN_NAME_TYPE + " NOTNULL)" +
						" AND (" +
							Entries.COLUMN_NAME_TYPE + " = '" + Entries.TYPE.DOWNLOADED.ordinal() + "' ))";
			}
		}
		// load user entries, ignore load more
		else
			if (recorded) {
				return "(" +
						"(" + Entries.COLUMN_NAME_USERNAME + " = '" + username + "' )" +
						"AND (" +
							Entries.COLUMN_NAME_TYPE + " NOTNULL)" +
						"AND (" +
							Entries.COLUMN_NAME_TYPE + " = '" + Entries.TYPE.DOWNLOADED.ordinal() + "'" +
							" OR " +
							Entries.COLUMN_NAME_TYPE + " = '" + Entries.TYPE.RECORDED.ordinal() + "'" +
							" OR " +
							Entries.COLUMN_NAME_TYPE + " = '" + Entries.TYPE.UPLOADING.ordinal() + "'" + " )" +
						")";
			} else {
				return "(" +
						"(" + Entries.COLUMN_NAME_USERNAME + " = '" + username + "' )" +
						"AND (" +
							Entries.COLUMN_NAME_TYPE + " NOTNULL)" +
						"AND (" +
							Entries.COLUMN_NAME_TYPE + " = '" + Entries.TYPE.DOWNLOADED.ordinal() + "'" + " )" +
						")";
			}
	}
	
	public static String selectProfileForUser(String username) {
		return "((" + Profiles.COLUMN_NAME_USERNAME + " NOTNULL) AND (" + Profiles.COLUMN_NAME_USERNAME + " == '" + username + "' ))";
	}
	
	// projection
	public static final String PROJECTION = "projection";
	
	private Context mContext;
	private Fragment mFragment;
	
	public SQLLoaderCallbacks(Context c, Fragment f) {
		super();
		mContext = c;
		mFragment = f;
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
    		FeedListFragment f = (FeedListFragment)mFragment;
    		f.onLoadFinished(data);
    		break;
    	case NoisetracksApplication.ENTRIES_SQL_LOADER_PROFILE: // entries for profile
    		ProfileListFragment p = (ProfileListFragment)mFragment;
    		p.onLoadFinished(data);
    		break;
    	case NoisetracksApplication.PROFILE_SQL_LOADER: // single profile
    		ProfileListFragment p2 = (ProfileListFragment)mFragment;
    		p2.setProfileHeader(data);
    		break;
    	default:
    		break;
    	}
        
    }

    public void onLoaderReset(Loader<Cursor> loader) {
    	
    }
}
