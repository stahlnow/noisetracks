package com.stahlnow.noisetracks.ui;

import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import android.support.v4.app.FragmentManager;
import android.support.v4.app.ListFragment;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ListView;
import android.widget.TextView;

import com.stahlnow.noisetracks.NoisetracksApplication;
import com.stahlnow.noisetracks.R;
import com.stahlnow.noisetracks.client.RESTLoaderCallbacks;
import com.stahlnow.noisetracks.client.SQLLoaderCallbacks;
import com.stahlnow.noisetracks.helper.ProgressWheel;
import com.stahlnow.noisetracks.provider.NoisetracksContract.Entries;
import com.stahlnow.noisetracks.provider.NoisetracksProvider;
import com.stahlnow.noisetracks.utility.AppSettings;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnLastItemVisibleListener;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;
import com.handmark.pulltorefresh.library.PullToRefreshListView;


public class FeedActivity extends SherlockFragmentActivity {
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        FragmentManager fm = getSupportFragmentManager();

        // Create the list fragment and add it as our sole content.
        if (fm.findFragmentById(android.R.id.content) == null) {
        	FeedListFragment list = new FeedListFragment();
            fm.beginTransaction().add(android.R.id.content, list).commit();
        }
        
    }

    public static class FeedListFragment extends ListFragment
    {
    	private RESTLoaderCallbacks r;
    	
        private EntryAdapter mEntryAdapter;
        private PullToRefreshListView mPullToRefreshView;
        private boolean mListShown;
        private TextView mEmpty;
        private View mProgressContainer;
        private ProgressWheel mProgressWheel;
        private View mListContainer;
        private View mHeader;
        private View mFooter;
        private TextView mPadding;
        
		@Override
		public void onDestroy() {
			super.onDestroy();
		}
		
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			
		    View root = inflater.inflate(R.layout.feed_activity, container, false);
		    
		    mProgressContainer = root.findViewById(R.id.entries_progressContainer);
		    mProgressWheel = (ProgressWheel) root.findViewById(R.id.pw_spinner);
		    mProgressWheel.spin();
		    
		    mListContainer =  root.findViewById(R.id.entries_listContainer);
		    mPullToRefreshView = (PullToRefreshListView) root.findViewById(R.id.entries_list);
		    mEmpty = (TextView)root.findViewById(R.id.entries_empty);
		    
		    mListShown = true;
		    
		    return root;
		}
		
		@Override
		public void onActivityCreated(Bundle savedInstanceState) {
		    super.onActivityCreated(savedInstanceState);
		    
		    r = new RESTLoaderCallbacks(getActivity(), this);
		    
		    // add header padding
		    mPadding = new TextView(this.getActivity());
		    mPadding.setHeight(15);
		    mPadding.setVisibility(View.INVISIBLE);
		    this.getListView().addHeaderView(mPadding);
		    // add rounded corners header
		    mHeader = getLayoutInflater(savedInstanceState).inflate(R.layout.list_header, null, false);
		    mHeader.setVisibility(View.INVISIBLE);
		    this.getListView().addHeaderView(mHeader);
		    // add rounded corners footer
		    mFooter = getLayoutInflater(savedInstanceState).inflate(R.layout.list_footer, null, false);
		    mFooter.setVisibility(View.INVISIBLE);
		    this.getListView().addFooterView(mFooter);
		    // add footer padding
		    this.getListView().addFooterView(mPadding);
		    
		    
		    // Create an empty adapter we will use to display the loaded data.
            mEntryAdapter = new EntryAdapter(
            		getActivity(), // context
                    R.layout.entry, // layout
                    null, // cursor
                    new String[] {								// from
            			Entries.COLUMN_NAME_MUGSHOT,
            			Entries.COLUMN_NAME_USERNAME,
            			Entries.COLUMN_NAME_RECORDED,
            			Entries.COLUMN_NAME_SPECTROGRAM }, 
                    new int[] {									// to
            			R.id.entry_mugshot,
            			R.id.entry_username,
            			R.id.entry_recorded_ago,
            			R.id.entry_spectrogram },            	
                    0,	// flags
                    true // mugshot is clickable
                    ); 
            setListAdapter(mEntryAdapter);
            
            // Start out with a progress indicator.
            setListShown(false);

            // Prepare the loader.  Either re-connect with an existing one, or start a new one.
            Bundle args = new Bundle();
            args.putStringArray(SQLLoaderCallbacks.PROJECTION, NoisetracksProvider.READ_ENTRY_PROJECTION);
            args.putString(SQLLoaderCallbacks.SELECT, SQLLoaderCallbacks.EntriesFeed(true));
            SQLLoaderCallbacks sql = new SQLLoaderCallbacks(getActivity(), this);
            getActivity().getSupportLoaderManager().initLoader(NoisetracksApplication.ENTRIES_SQL_LOADER_FEED, args, sql);
            
            // Prepare and initialize REST loader
            Bundle params = new Bundle();
	        params.putString("format", "json");				// we need json format
	        params.putString("order_by", "-created");		// newest first
	        params.putString("audiofile__status", "1");		// only get entries with status = Done
        	Bundle argsEntries = new Bundle();
        	argsEntries.putParcelable(RESTLoaderCallbacks.ARGS_URI, RESTLoaderCallbacks.URI_ENTRIES);
        	argsEntries.putParcelable(RESTLoaderCallbacks.ARGS_PARAMS, params);
        	getActivity().getSupportLoaderManager().initLoader(NoisetracksApplication.ENTRIES_REST_LOADER, argsEntries, r);

	        
	        // Set a listener to be invoked when the list should be refreshed.
	        mPullToRefreshView.setOnRefreshListener(new OnRefreshListener<ListView>() {
	            @Override
	            public void onRefresh(PullToRefreshBase<ListView> refreshView) {
	            	
	            	// Call api
	            	if (!mEntryAdapter.isEmpty()) { // if list not empty
	            		Cursor cursor = (Cursor) getListAdapter().getItem(0); // get latest entry
		    	        String created = cursor.getString(cursor.getColumnIndex(Entries.COLUMN_NAME_CREATED));
		            	Bundle params = new Bundle();
		    	        params.putString("format", "json");				// we need json format
		    	        params.putString("order_by", "-created");		// newest first
		    	        params.putString("audiofile__status", "1");		// only get entries with status = Done
		    	        params.putString("created__gt", created);		// only entries newer than first (latest) entry in list
		            	Bundle argsEntriesNewer = new Bundle();
		            	argsEntriesNewer.putParcelable(RESTLoaderCallbacks.ARGS_URI, RESTLoaderCallbacks.URI_ENTRIES);
		            	argsEntriesNewer.putParcelable(RESTLoaderCallbacks.ARGS_PARAMS, params);
		            	getActivity().getSupportLoaderManager().restartLoader(NoisetracksApplication.ENTRIES_NEWER_REST_LOADER, argsEntriesNewer, r);
	            	} else {
	            		Bundle params = new Bundle();
		    	        params.putString("format", "json");				// we need json format
		    	        params.putString("order_by", "-created");		// newest first
		    	        params.putString("audiofile__status", "1");		// only get entries with status = Done
		            	Bundle argsEntries = new Bundle();
		            	argsEntries.putParcelable(RESTLoaderCallbacks.ARGS_URI, RESTLoaderCallbacks.URI_ENTRIES);
		            	argsEntries.putParcelable(RESTLoaderCallbacks.ARGS_PARAMS, params);
		            	getActivity().getSupportLoaderManager().restartLoader(NoisetracksApplication.ENTRIES_REST_LOADER, argsEntries, r);
	            	}
	            }
	        });
	        
	        
	        mPullToRefreshView.setOnLastItemVisibleListener(new OnLastItemVisibleListener() {
				@Override
				public void onLastItemVisible() {
					
					if (!mEntryAdapter.isEmpty()) { // if list not empty
						Cursor cursor = (Cursor) getListAdapter().getItem(getListAdapter().getCount()-1); // get last entry
		    	        String created = cursor.getString(cursor.getColumnIndex(Entries.COLUMN_NAME_CREATED));
		            	Bundle params = new Bundle();
		    	        params.putString("format", "json");				// we need json format
		    	        params.putString("order_by", "-created");		// newest first
		    	        params.putString("audiofile__status", "1");		// only get entries with status = Done
		    	        params.putString("created__lt", created);		// older entries only
		    	        Bundle argsEntriesOlder = new Bundle();
		            	argsEntriesOlder.putParcelable(RESTLoaderCallbacks.ARGS_URI, RESTLoaderCallbacks.URI_ENTRIES);
		            	argsEntriesOlder.putParcelable(RESTLoaderCallbacks.ARGS_PARAMS, params);
		            	getActivity().getSupportLoaderManager().restartLoader(NoisetracksApplication.ENTRIES_OLDER_REST_LOADER, argsEntriesOlder, r);
	            	}
					
				}
			});
			
		}
    	
		@Override
		public void onListItemClick (ListView l, View v, int position, long id) {
			super.onListItemClick(l, v, position, id);
			
			Cursor c = (Cursor)getListView().getItemAtPosition(position);
			
			if (c != null) {
				
				if (c.getInt(c.getColumnIndex(Entries.COLUMN_NAME_TYPE)) == Entries.TYPE.LOAD_MORE.ordinal()) { // user clicked on 'load more'
					// remove 'load more' entry
	            	Uri lm = ContentUris.withAppendedId(Entries.CONTENT_ID_URI_BASE, id);
	            	getActivity().getContentResolver().delete(lm, null, null);
	            	getActivity().getContentResolver().notifyAll();
	            	
	            	// load items
					Bundle params = new Bundle(); // no params
					Bundle argsEntries= new Bundle();
	            	argsEntries.putParcelable(
	            			RESTLoaderCallbacks.ARGS_URI,
	            			Uri.parse(NoisetracksApplication.DOMAIN + c.getString(c.getColumnIndex(Entries.COLUMN_NAME_RESOURCE_URI)))); // resource uri contains 'next' from last api call
	            	argsEntries.putParcelable(RESTLoaderCallbacks.ARGS_PARAMS, params);
	            	getActivity().getSupportLoaderManager().restartLoader(NoisetracksApplication.ENTRIES_OLDER_REST_LOADER, argsEntries, r);
	            	
				}
				// start entry activity
				else {
					Intent i = new Intent(getActivity().getApplicationContext(), EntryActivity.class);
					i.putExtra(SQLLoaderCallbacks.SELECT, SQLLoaderCallbacks.EntriesFeed(false));
					i.putExtra(EntryActivity.ID, id);
					startActivity(i);
				}
			}
		}
		
		
		public void onLoadFinished(Cursor data) {
			mEntryAdapter.swapCursor(data);
        	
        	if (mEntryAdapter.isEmpty()) {
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
        	
            if (isResumed()) {
            	setListShown(true);
            } else {
            	setListShownNoAnimation(true);
            }
		}
		
		public void onRefreshComplete() {
			// Reset pull refresh view
	    	mPullToRefreshView.onRefreshComplete();
	    	// Set updated text	    	
	    	mPullToRefreshView.setLastUpdatedLabel("Last updated: " + DateUtils.formatDateTime(getActivity(), System.currentTimeMillis(), DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NUMERIC_DATE | DateUtils.FORMAT_ABBREV_TIME));

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
    	
    	   
	    public void setListShown(boolean shown, boolean animate){
	    	
	        if (mListShown == shown) {
	            return;
	        }
	        mListShown = shown;
	        if (shown) {
	        	
	        	mProgressWheel.stopSpinning(); // stop spinning
	            
	        	if (animate) {
	            	try {
	            		
	            		mProgressContainer.startAnimation(AnimationUtils.loadAnimation(
		                        getActivity(), android.R.anim.fade_out));
		                mListContainer.startAnimation(AnimationUtils.loadAnimation(
		                        getActivity(), android.R.anim.fade_in));
	            	}
	            	catch (java.lang.NullPointerException e) {
	            		// prevent from crashing, when orientation has changed
	            	}
	                
	            }
	            mProgressContainer.setVisibility(View.GONE);
	            mListContainer.setVisibility(View.VISIBLE);
	        } else {
	            if (animate) {
	            	try {
	                mProgressContainer.startAnimation(AnimationUtils.loadAnimation(
	                        getActivity(), android.R.anim.fade_in));
	                mListContainer.startAnimation(AnimationUtils.loadAnimation(
	                        getActivity(), android.R.anim.fade_out));
	            	}
	            	catch (java.lang.NullPointerException e) {
	            		// prevent from crashing, when orientation has changed
	            	}
	            }
	            mProgressContainer.setVisibility(View.VISIBLE);
	            mListContainer.setVisibility(View.INVISIBLE);
	        }
	    }
	    public void setListShown(boolean shown){
	        setListShown(shown, true);
	    }
	    public void setListShownNoAnimation(boolean shown) {
	        setListShown(shown, false);
	    }
	}
}
