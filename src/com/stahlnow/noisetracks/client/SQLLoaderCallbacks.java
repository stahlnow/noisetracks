package com.stahlnow.noisetracks.client;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.View;
import android.widget.TextView;

import com.stahlnow.noisetracks.provider.NoisetracksContract.Entries;
import com.stahlnow.noisetracks.ui.EntryAdapter;

public class SQLLoaderCallbacks implements LoaderCallbacks<Cursor> {
	
	public static final Uri ENTRIES = Entries.CONTENT_URI; // defines content provider uri for entries
	
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
    
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(
        		mContext,						// context
        		ENTRIES,						// content uri
                args.getStringArray(PROJECTION),// projection
                args.getString(SELECT),			// selection criteria
                null,							// selection args
                Entries.DEFAULT_SORT_ORDER);	// sorting
        
    }

    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
    	
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
        
    }

    public void onLoaderReset(Loader<Cursor> loader) {
    	mAdapter.swapCursor(null);
    }
}
