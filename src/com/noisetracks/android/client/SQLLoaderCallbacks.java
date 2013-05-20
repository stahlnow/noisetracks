package com.noisetracks.android.client;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;

import com.noisetracks.android.provider.NoisetracksContract;
import com.noisetracks.android.provider.NoisetracksContract.Entries;
import com.noisetracks.android.provider.NoisetracksContract.Profiles;
import com.noisetracks.android.ui.FeedActivity.FeedListFragment;
import com.noisetracks.android.ui.ProfileActivity.ProfileListFragment;

public class SQLLoaderCallbacks implements LoaderCallbacks<Cursor> {
	
	@SuppressWarnings("unused")
	private static final String TAG = "SQLLoaderCallbacks";
	
	// loader ids
	public static final int ENTRIES_SQL_LOADER_FEED = 0;
	public static final int ENTRIES_SQL_LOADER_PROFILE = 1;
	public static final int PROFILE_SQL_LOADER = 2;
	
	// projection
	public static final String PROJECTION = "projection";
		
	// selection args
	public static final String SELECT = "select"; 
	
	// limit query param
	public static final String LIMIT = "limit";
	
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
		
		// if username is empty, load entries from any user, ignore recorded parameter
		if (username.equals("")) {
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
	
	private Context mContext;
	private Fragment mFragment;
	
	public SQLLoaderCallbacks(Context c, Fragment f) {
		super();
		mContext = c;
		mFragment = f;
	}
    
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		//Log.d(TAG, "onCreateLoader");
		switch(id) {
		case ENTRIES_SQL_LOADER_FEED:
		case ENTRIES_SQL_LOADER_PROFILE:
			Uri uri = Entries.CONTENT_URI;
			if (args.getInt(LIMIT) != 0)
				uri = uri.buildUpon().appendQueryParameter(NoisetracksContract.QUERY_PARAMETER_LIMIT, Integer.toString(args.getInt(LIMIT))).build();
			return new CursorLoader(
	        		mContext,						// context
	        		uri,							// content uri
	                args.getStringArray(PROJECTION),// projection
	                args.getString(SELECT),			// selection criteria
	                null,							// selection args
	                Entries.DEFAULT_SORT_ORDER);	// sorting
		case PROFILE_SQL_LOADER:
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
    	//Log.d(TAG, "onLoadFinished: " + loader.getId());
    	switch(loader.getId()) {
    	case ENTRIES_SQL_LOADER_FEED: 	// entries for feed
    		FeedListFragment f = (FeedListFragment)mFragment;
    		f.onLoadFinished(data);
    		break;
    	case ENTRIES_SQL_LOADER_PROFILE: // entries for profile
    		ProfileListFragment p = (ProfileListFragment)mFragment;
    		p.onLoadFinishedEntries(data);
    		break;
    	case PROFILE_SQL_LOADER: // single profile
    		ProfileListFragment p2 = (ProfileListFragment)mFragment;
    		p2.onLoadFinishedProfiles(data);
    		break;
    	default:
    		break;
    	}
        
    }

    public void onLoaderReset(Loader<Cursor> loader) {
    	
    }
}
