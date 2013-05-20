package com.noisetracks.android.ui;

import org.scribe.model.Verb;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;

import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import com.noisetracks.android.NoisetracksApplication;
import com.noisetracks.android.R;
import com.noisetracks.android.client.NoisetracksRequest;
import com.noisetracks.android.client.SQLLoaderCallbacks;
import com.noisetracks.android.helper.ProgressWheel;
import com.noisetracks.android.provider.NoisetracksContract.Entries;
import com.noisetracks.android.provider.NoisetracksProvider;
import com.whiterabbit.postman.ServerInteractionHelper;
import com.whiterabbit.postman.ServerInteractionResponseInterface;
import com.whiterabbit.postman.exceptions.SendingCommandException;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.app.SherlockListFragment;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnLastItemVisibleListener;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;
import com.handmark.pulltorefresh.library.PullToRefreshListView;


public class FeedActivity extends SherlockFragmentActivity {
	
	@SuppressWarnings("unused")
	private static final String TAG = "FeedActivity";
	
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

    public static class FeedListFragment extends SherlockListFragment implements ServerInteractionResponseInterface 
    {
    	private static final String TAG = "FeedListFragment";
    	
    	private static final int MAX_NUMBER_OF_ENTRIES_SHOWN = 500;
    	
    	ServerInteractionHelper mServerHelper;
    	
        private EntryAdapter mEntryAdapter;
        private static PullToRefreshListView mPullToRefreshView;
        private TextView mEmpty;
        private View mHeader;
        private View mFooter;
        private TextView mPadding;
        private static ProgressWheel mProgressWheelLoadingOlderEntries;
        private TextView mNoMoreEntries;
        
		@Override
		public void onDestroy() {
			super.onDestroy();
		}
		
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			
			//Log.d(TAG, "onCreateView");
			
		    View root = inflater.inflate(R.layout.feed_activity, container, false);
		    
		    mPullToRefreshView = (PullToRefreshListView) root.findViewById(R.id.entries_list);
		    mEmpty = (TextView)root.findViewById(R.id.entries_empty);
		    mProgressWheelLoadingOlderEntries = (ProgressWheel)root.findViewById(R.id.loading_older_entries_progress_bar);
		    mNoMoreEntries = (TextView)root.findViewById(R.id.no_more_entries_to_load);
		    
		    mServerHelper = ServerInteractionHelper.getInstance(getActivity());
		    
		    return root;
		}
		
		@Override
		public void onResume() {
			//Log.d(TAG, "onResume");
			mServerHelper.registerEventListener(this, getActivity());
			super.onResume();
			
			mProgressWheelLoadingOlderEntries.setVisibility(View.GONE);
		}
		
		@Override
		public void onPause() {
			//Log.d(TAG, "onPause");
			mServerHelper.unregisterEventListener(this, getActivity());
			super.onPause();
		}
		
		@Override
		public void onActivityCreated(Bundle savedInstanceState) {
			
			//Log.d(TAG, "onActivityCreated");
			
		    super.onActivityCreated(savedInstanceState);
		    
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
            
            // Load entries
            Bundle args = new Bundle();
            args.putStringArray(SQLLoaderCallbacks.PROJECTION, NoisetracksProvider.READ_ENTRY_PROJECTION);
            args.putString(SQLLoaderCallbacks.SELECT, SQLLoaderCallbacks.EntriesFeed(true));
            SQLLoaderCallbacks sql = new SQLLoaderCallbacks(getActivity(), this);
            getActivity().getSupportLoaderManager().initLoader(SQLLoaderCallbacks.ENTRIES_SQL_LOADER_FEED, args, sql);
            
            mServerHelper.registerEventListener(this, getActivity());
           
	        // Set a listener to be invoked when the list should be refreshed.
	        mPullToRefreshView.setOnRefreshListener(new OnRefreshListener<ListView>() {
	            @Override
	            public void onRefresh(PullToRefreshBase<ListView> refreshView) {
	                
	            	Bundle params = new Bundle();
	    	        params.putString("format", "json");				// we need json format
	    	        params.putString("order_by", "-created");		// newest first
	    	        params.putString("audiofile__status", "1");		// only get entries with status = Done
	            	
	            	// get only newer entries, if list is not empty
	            	if (!mEntryAdapter.isEmpty()) {
	            		Cursor c = (Cursor) getListAdapter().getItem(0); // get latest entry
		    	        String created = c.getString(c.getColumnIndex(Entries.COLUMN_NAME_CREATED));
		    	        params.putString("created__gt", created);		// only entries newer than first (latest) entry in list
	            	}
	            	
	    	        NoisetracksRequest request = new NoisetracksRequest(Verb.GET, NoisetracksApplication.URI_ENTRIES, params);
	                try {
	                    mServerHelper.sendRestAction(getActivity(), "Feed Entries onRefresh()", request);
	                } catch (SendingCommandException e) {
	                    Log.e(TAG, e.toString());
	                }
	            	
	            }
	        });
	        
	        
	        // If last item is reached AND list is not empty AND less than MAX_NUMBER_OF_ENTRIES_SHOWN entries
	        mPullToRefreshView.setOnLastItemVisibleListener(new OnLastItemVisibleListener() {
				@Override
				public void onLastItemVisible() {
					if (!mEntryAdapter.isEmpty() && mEntryAdapter.getCount() <= MAX_NUMBER_OF_ENTRIES_SHOWN) { 
						Cursor cursor = (Cursor) getListAdapter().getItem(getListAdapter().getCount()-1); // get last entry
		    	        String created = cursor.getString(cursor.getColumnIndex(Entries.COLUMN_NAME_CREATED));
		            	Bundle params = new Bundle();
		    	        params.putString("format", "json");				// we need json format
		    	        params.putString("order_by", "-created");		// newest first
		    	        params.putString("audiofile__status", "1");		// only get entries with status = Done
		    	        params.putString("created__lt", created);		// older entries only
		    	        NoisetracksRequest request = new NoisetracksRequest(Verb.GET, NoisetracksApplication.URI_ENTRIES, params);
		                try {
		                    mServerHelper.sendRestAction(getActivity(), "Feed Entries onLastItemVisible()", request);
		                    mProgressWheelLoadingOlderEntries.stopSpinning();
		                    mProgressWheelLoadingOlderEntries.spin();
			            	mProgressWheelLoadingOlderEntries.setVisibility(View.VISIBLE);
		                } catch (SendingCommandException e) {
		                	mProgressWheelLoadingOlderEntries.stopSpinning();
		                	mProgressWheelLoadingOlderEntries.setVisibility(View.GONE);
		                    Log.e(TAG, e.toString());
		                }
	            	} else if (mEntryAdapter.getCount() >= MAX_NUMBER_OF_ENTRIES_SHOWN) {
	            		Log.v(TAG, "Limit reached.");
	            		mNoMoreEntries.setVisibility(View.VISIBLE);
	            	}
				}
			});
		}
		
		
    	
		@Override
		public void onListItemClick (ListView l, View v, int position, long id) {
			super.onListItemClick(l, v, position, id);
			
			Log.v(TAG, "selected id: " + id);
			Intent i = new Intent(getActivity().getApplicationContext(), EntryActivity.class);
			i.putExtra(SQLLoaderCallbacks.SELECT, SQLLoaderCallbacks.EntriesFeed(false));
			i.putExtra(EntryActivity.ID, id);
			startActivity(i);
			
			
			/*
			
			Cursor c = (Cursor)getListView().getItemAtPosition(position);
			
			if (c != null) {
				
				if (c.getInt(c.getColumnIndex(Entries.COLUMN_NAME_TYPE)) == Entries.TYPE.LOAD_MORE.ordinal()) { // user clicked on 'load more'
					// remove 'load more' entry
	            	Uri lm = ContentUris.withAppendedId(Entries.CONTENT_ID_URI_BASE, id);
	            	getActivity().getContentResolver().delete(lm, null, null);
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
				
				
					Log.v(TAG, "selected id: " + id);
					Intent i = new Intent(getActivity().getApplicationContext(), EntryActivity.class);
					i.putExtra(SQLLoaderCallbacks.SELECT, SQLLoaderCallbacks.EntriesFeed(false));
					i.putExtra(EntryActivity.ID, id);
					startActivity(i);
				
				//}
			}*/
		}
		
		
		public void onReselectedTab () {
			getListView().setSelection(0); // take me to the top
		}
		
		
		public void onLoadFinished(Cursor data) {
			
			mEntryAdapter.swapCursor(data);
        	
        	if (mEntryAdapter.isEmpty()) {
        		
        		mPadding.setVisibility(View.INVISIBLE);
            	mHeader.setVisibility(View.INVISIBLE);
            	mFooter.setVisibility(View.INVISIBLE);
            	mEmpty.setText("loading");
            	
            	// try getting data from server
            	Bundle params = new Bundle();
    	        params.putString("format", "json");				// we need json format
    	        params.putString("order_by", "-created");		// newest first
    	        params.putString("audiofile__status", "1");		// only get entries with status = Done
                NoisetracksRequest request = new NoisetracksRequest(Verb.GET, NoisetracksApplication.URI_ENTRIES, params);
                try {
                    mServerHelper.sendRestAction(getActivity(), "Feed Entries onLoadFinished()", request);
                } catch (SendingCommandException e) {
                    Log.e(TAG, e.toString());
                }
                
        		
        	} else {
        		
        		mPadding.setVisibility(View.VISIBLE);
            	mHeader.setVisibility(View.VISIBLE);
            	mFooter.setVisibility(View.VISIBLE);
        		mEmpty.setText("");
        	}
		}
		
		public static void hack() {
			mPullToRefreshView.onRefreshComplete();
			mProgressWheelLoadingOlderEntries.stopSpinning();
        	mProgressWheelLoadingOlderEntries.setVisibility(View.GONE);
		}
		
		@Override
		public void onServerResult(String result, String requestId) {
			Log.v(TAG, "onServerResult: " + result + " from " + requestId);
			mPullToRefreshView.onRefreshComplete();
			mProgressWheelLoadingOlderEntries.stopSpinning();
        	mProgressWheelLoadingOlderEntries.setVisibility(View.GONE);
		}

		@Override
		public void onServerError(String result, String requestId) {
			Log.w(TAG, "Server responded: " + result + " from " + requestId);
			
			mPullToRefreshView.onRefreshComplete();
			mProgressWheelLoadingOlderEntries.stopSpinning();
        	mProgressWheelLoadingOlderEntries.setVisibility(View.GONE);
			
			if (mEntryAdapter.isEmpty())
				mEmpty.setText("Could not connect to Noisetracks.");
		}
		
	}
}
