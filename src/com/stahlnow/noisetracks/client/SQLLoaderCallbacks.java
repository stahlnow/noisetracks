package com.stahlnow.noisetracks.client;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.View;
import android.widget.TextView;

import com.stahlnow.noisetracks.provider.NoisetracksContract.Entries;
import com.stahlnow.noisetracks.utility.AppLog;

public class SQLLoaderCallbacks implements LoaderCallbacks<Cursor> {
	
	public static final Uri ENTRIES = Entries.CONTENT_URI; // defines content provider uri for entries
	
	// selection args
	public static final String SELECT = "select";
	public static final String SELECT_ENTRIES = "((" + Entries.COLUMN_NAME_FILENAME + " NOTNULL) AND (" + Entries.COLUMN_NAME_FILENAME + " != '' ))";
	
	// projection
	public static final String PROJECTION = "projection";
	
	private Context mContext;
	private SimpleCursorAdapter mAdapter;
	private ListFragment mListFragment;
	private TextView mEmpty;
	private TextView mPadding;
    private View mHeader;
    private View mFooter;
	
	public SQLLoaderCallbacks(Context c, SimpleCursorAdapter adapter, ListFragment fragment, TextView empty,
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
	public static String selectEntriesFromUser(String username) {
		return "((" + Entries.COLUMN_NAME_FILENAME + " NOTNULL) AND (" + Entries.COLUMN_NAME_FILENAME + " != '' ) AND (" + Entries.COLUMN_NAME_USERNAME + " == '" + username + "' ))";
	}
    
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		AppLog.logString("SQLLoaderCallbacks onCreateLoader");
        return new CursorLoader(
        		mContext,						// context
        		ENTRIES,						// content uri
                args.getStringArray(PROJECTION),// projection
                args.getString(SELECT),			// selection criteria
                null,							// selection args
                Entries.DEFAULT_SORT_ORDER);	// sorting
        
    }

    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
    	    	
    	AppLog.logString("SQLLoaderCallbacks onLoadFinished");
    	
    	mAdapter.changeCursor(data); 
    	
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
        
    }

    public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.changeCursor(null);
    }
}
