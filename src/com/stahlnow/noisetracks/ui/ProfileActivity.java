package com.stahlnow.noisetracks.ui;

import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshListView;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnLastItemVisibleListener;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;
import com.stahlnow.noisetracks.R;
import com.stahlnow.noisetracks.client.RESTLoaderCallbacks;
import com.stahlnow.noisetracks.client.SQLLoaderCallbacks;
import com.stahlnow.noisetracks.helper.ProgressWheel;
import com.stahlnow.noisetracks.provider.NoisetracksProvider;
import com.stahlnow.noisetracks.provider.NoisetracksContract.Entries;
import com.stahlnow.noisetracks.utility.AppLog;
import com.stahlnow.noisetracks.utility.AppSettings;

import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.ListFragment;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ListView;
import android.widget.TextView;

public class ProfileActivity extends FragmentActivity {
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		FragmentManager fm = getSupportFragmentManager();

		// Create the list fragment and add it as our sole content.
		if (fm.findFragmentById(android.R.id.content) == null) {					
			ProfileListFragment list = new ProfileListFragment();
			list.setArguments(getIntent().getExtras());
			fm.beginTransaction().add(android.R.id.content, list).commit();
		}
		

	}	

	public static class ProfileListFragment extends ListFragment {

		//private static ArrayList<Entry> entries = new ArrayList<Entry>(); 
				
		private EntryAdapter mAdapter;
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
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			
		    View root = inflater.inflate(R.layout.profile_activity, container, false);
		    
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
            
            // We have a menu item to show in action bar.
		    setHasOptionsMenu(true);	
            
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
		    
		    // Start out with a progress indicator.
            setListShown(false);
            
            // create array adapter
            //mAdapter = new EntryArrayAdapter(getActivity(), R.layout.entry);
            //setListAdapter(mAdapter);
         
            // Create an empty adapter we will use to display the loaded data.
            mAdapter = new EntryAdapter(
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
                    0, // flags
                    false  // mugshot is not clickable
                    );
            setListAdapter(mAdapter);
            
            // Prepare and init the sql loader for user entries
            Bundle args = new Bundle();
            args.putStringArray(SQLLoaderCallbacks.PROJECTION, NoisetracksProvider.READ_ENTRY_PROJECTION);
            args.putString(SQLLoaderCallbacks.SELECT, SQLLoaderCallbacks.selectEntriesFromUser(getArguments().getString("username"), true));
            SQLLoaderCallbacks sql = new SQLLoaderCallbacks(this.getActivity(), mAdapter, (ListFragment)this, mEmpty, mPadding, mHeader, mFooter);
            getActivity().getSupportLoaderManager().initLoader(1, args, sql);	
            
            // Prepare and init REST loader
            Bundle params = new Bundle();
	        params.putString("format", "json");				// we need json format
	        params.putString("order_by", "-created");		// newest first
	        params.putString("audiofile__status", "1");		// only get entries with status = Done
	        params.putString("user__username", getArguments().getString("username"));	// only entries from specific user
        	Bundle argsEntries = new Bundle();
        	argsEntries.putParcelable(RESTLoaderCallbacks.ARGS_URI, RESTLoaderCallbacks.URI_ENTRIES);
        	argsEntries.putParcelable(RESTLoaderCallbacks.ARGS_PARAMS, params);
        	RESTLoaderCallbacks r = new RESTLoaderCallbacks(getActivity(), mAdapter, mPullToRefreshView, mEmpty, mPadding, mHeader, mFooter);
    		getLoaderManager().initLoader(RESTLoaderCallbacks.ENTRIES, argsEntries, r);
            
            
	        // Set a listener to be invoked when the list should be refreshed.
	        mPullToRefreshView.setOnRefreshListener(new OnRefreshListener<ListView>() {
	            @Override
	            public void onRefresh(PullToRefreshBase<ListView> refreshView) {
	            	AppLog.logString("Profile onRefresh");
	            	// Call api
	            	if (!mAdapter.isEmpty()) { // if list not empty
	            		Cursor cursor = (Cursor) getListAdapter().getItem(0); // get latest entry
		    	        String created = cursor.getString(cursor.getColumnIndex(Entries.COLUMN_NAME_CREATED));
		            	Bundle params = new Bundle();
		    	        params.putString("format", "json");				// we need json format
		    	        params.putString("order_by", "-created");		// newest first
		    	        params.putString("audiofile__status", "1");		// only get entries with status = Done
		    	        params.putString("created__gt", created);		// only entries newer than first (latest) entry in list
		    	        params.putString("user__username", getArguments().getString("username"));	// only entries from specific user
		            	Bundle argsEntriesNewer = new Bundle();
		            	argsEntriesNewer.putParcelable(RESTLoaderCallbacks.ARGS_URI, RESTLoaderCallbacks.URI_ENTRIES);
		            	argsEntriesNewer.putParcelable(RESTLoaderCallbacks.ARGS_PARAMS, params);
		            	// Initialize RESTLoader for refresh view.		    
		            	RESTLoaderCallbacks r = new RESTLoaderCallbacks(getActivity(), mAdapter, mPullToRefreshView, mEmpty, mPadding, mHeader, mFooter);
		            	getActivity().getSupportLoaderManager().restartLoader(RESTLoaderCallbacks.ENTRIES_NEWER, argsEntriesNewer, r);
	            	} else {
	            		Bundle params = new Bundle();
		    	        params.putString("format", "json");				// we need json format
		    	        params.putString("order_by", "-created");		// newest first
		    	        params.putString("audiofile__status", "1");		// only get entries with status = Done
		    	        params.putString("user__username", getArguments().getString("username"));	// only entries from specific user
		            	Bundle argsEntries = new Bundle();
		            	argsEntries.putParcelable(RESTLoaderCallbacks.ARGS_URI, RESTLoaderCallbacks.URI_ENTRIES);
		            	argsEntries.putParcelable(RESTLoaderCallbacks.ARGS_PARAMS, params);
		            	RESTLoaderCallbacks r = new RESTLoaderCallbacks(getActivity(), mAdapter, mPullToRefreshView, mEmpty, mPadding, mHeader, mFooter);
		            	getActivity().getSupportLoaderManager().restartLoader(RESTLoaderCallbacks.ENTRIES, argsEntries, r);
	            	}
	            }
	        });
	        
	        mPullToRefreshView.setOnLastItemVisibleListener(new OnLastItemVisibleListener() {
				@Override
				public void onLastItemVisible() {
					
					if (!mAdapter.isEmpty()) { // if list not empty
						Cursor cursor = (Cursor) getListAdapter().getItem(getListAdapter().getCount()-1); // get last entry
		    	        String created = cursor.getString(cursor.getColumnIndex(Entries.COLUMN_NAME_CREATED));
		            	Bundle params = new Bundle();
		    	        params.putString("format", "json");				// we need json format
		    	        params.putString("order_by", "-created");		// newest first
		    	        params.putString("audiofile__status", "1");		// only get entries with status = Done
		    	        params.putString("created__lt", created);		// older entries only
		    	        params.putString("user__username", getArguments().getString("username"));	// only entries from specific user
		    	        Bundle argsEntriesOlder = new Bundle();
		            	argsEntriesOlder.putParcelable(RESTLoaderCallbacks.ARGS_URI, RESTLoaderCallbacks.URI_ENTRIES);
		            	argsEntriesOlder.putParcelable(RESTLoaderCallbacks.ARGS_PARAMS, params);
		            	RESTLoaderCallbacks r = new RESTLoaderCallbacks(getActivity(), mAdapter, mPullToRefreshView, mEmpty, mPadding, mHeader, mFooter);
		            	getActivity().getSupportLoaderManager().restartLoader(RESTLoaderCallbacks.ENTRIES_OLDER, argsEntriesOlder, r);
	            	}
					
				}
			});
            
        }
		
		@Override
		public void onListItemClick (ListView l, View v, int position, long id) {
			super.onListItemClick(l, v, position, id);
			
			Cursor c = (Cursor)getListView().getItemAtPosition(position);
			
			if (c != null) {
				
				if (c.getString(c.getColumnIndex(Entries.COLUMN_NAME_FILENAME)).contains("load")) { // user clicked on 'load more'
					// remove 'load more' entry
	            	Uri u = ContentUris.withAppendedId(Entries.CONTENT_ID_URI_BASE, c.getInt(c.getColumnIndex(Entries._ID))); //c.getPosition()+1 
	            	getActivity().getContentResolver().delete(u, null, null);
	            	
	            	// load items
					Bundle params = new Bundle(); // no params
					Bundle argsEntries= new Bundle();
	            	argsEntries.putParcelable(
	            			RESTLoaderCallbacks.ARGS_URI,
	            			Uri.parse(AppSettings.DOMAIN + c.getString(c.getColumnIndex(Entries.COLUMN_NAME_RESOURCE_URI)))); // resource uri contains 'next' from last api call
	            	argsEntries.putParcelable(RESTLoaderCallbacks.ARGS_PARAMS, params);
	            	RESTLoaderCallbacks r = new RESTLoaderCallbacks(getActivity(), mAdapter, mPullToRefreshView, mEmpty, mPadding, mHeader, mFooter);
	            	getActivity().getSupportLoaderManager().restartLoader(RESTLoaderCallbacks.ENTRIES_OLDER, argsEntries, r);
	            	
				}
				// start entry activity
				else {
					Intent i = new Intent(getActivity().getApplicationContext(), EntryActivity.class);
					//i.putExtra(SQLLoaderCallbacks.SELECT, SQLLoaderCallbacks.SELECT_ENTRIES_WITHOUT_LOAD_MORE); 	// select entries exclude 'load more' entries
					i.putExtra(SQLLoaderCallbacks.SELECT, SQLLoaderCallbacks.selectEntriesFromUser(getArguments().getString("username"), false));
					i.putExtra("item", position - l.getHeaderViewsCount());
					startActivity(i);
				}
			}
		}
		
		@Override
		public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
			inflater.inflate(R.menu.sub_menu_me, menu);
		}

		@Override
		public boolean onOptionsItemSelected(MenuItem item) {
			switch (item.getItemId()) {
			case R.id.sub_menu_me_add:
				// remove all entries from db
	        	getActivity().getContentResolver().delete(Entries.CONTENT_URI, null, null);
				return true;
			default:
				return super.onOptionsItemSelected(item);
			}
		}
		
		public void setListShown(boolean shown, boolean animate) {
	    				
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
		
	    public void setListShown(boolean shown) {
	        setListShown(shown, true);
	    }
		
	    public void setListShownNoAnimation(boolean shown) {
	        setListShown(shown, false);
	    }
	    

	}

}
