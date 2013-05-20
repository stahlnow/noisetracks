package com.noisetracks.android.ui;

import org.scribe.model.Verb;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.MenuItem;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshListView;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnLastItemVisibleListener;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;
import com.noisetracks.android.NoisetracksApplication;
import com.noisetracks.android.R;
import com.noisetracks.android.client.NoisetracksRequest;
import com.noisetracks.android.client.SQLLoaderCallbacks;
import com.noisetracks.android.helper.ProgressWheel;
import com.noisetracks.android.helper.httpimage.HttpImageManager;
import com.noisetracks.android.provider.NoisetracksContract.Profiles;
import com.noisetracks.android.provider.NoisetracksProvider;
import com.noisetracks.android.provider.NoisetracksContract.Entries;
import com.noisetracks.android.ui.FeedActivity.FeedListFragment;
import com.noisetracks.android.utility.AppSettings;
import com.whiterabbit.postman.ServerInteractionHelper;
import com.whiterabbit.postman.ServerInteractionResponseInterface;
import com.whiterabbit.postman.exceptions.SendingCommandException;

import android.support.v4.app.FragmentManager;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class ProfileActivity extends SherlockFragmentActivity {
	
	@SuppressWarnings("unused")
	private static final String TAG = "ProfileActivity";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		ActionBar actionBar = getSupportActionBar();
	    actionBar.setDisplayHomeAsUpEnabled(true);
		
		FragmentManager fm = getSupportFragmentManager();

		// Create the list fragment and add it as our sole content.
		if (fm.findFragmentById(android.R.id.content) == null) {					
			ProfileListFragment list = new ProfileListFragment();
			list.setArguments(getIntent().getExtras());
			fm.beginTransaction().add(android.R.id.content, list).commit();
		}
		

	}	
	
	public static void onUploadResult(Boolean result, Uri uri) {	
		Context ctx = (Context)NoisetracksApplication.getInstance();
		if (result) {
			// Entry has been posted and we delete temporary entry.
			ctx.getContentResolver().delete(uri, null, null); 
			// Request sync
			Bundle extras = new Bundle();
			extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true); // sync immediately
			extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);	// allow sync if global sync is disabled
			AccountManager am = AccountManager.get(ctx);
	        Account a = am.getAccountsByType(ctx.getString(R.string.ACCOUNT_TYPE))[0];
			ContentResolver.requestSync(a, ctx.getString(R.string.AUTHORITY_PROVIDER), extras);
			
		} else {
			// Failed to post file, set type to 'recorded' for retry
			Cursor c = ctx.getContentResolver().query(uri, null, null, null, null);
			if (c != null) {
				if (c.moveToFirst()) {
			        ContentValues cv = new ContentValues();
			        cv.put(Entries.COLUMN_NAME_TYPE, Entries.TYPE.RECORDED.ordinal());
			        ctx.getContentResolver().update(uri, cv, null, null);
			        c.close();
				}
			}
		}
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			Intent parentActivityIntent = new Intent(this, Tabs.class);
			parentActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(parentActivityIntent);
			finish();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	public static class ProfileListFragment extends SherlockListFragment implements ServerInteractionResponseInterface {
		
		private static final String TAG = "ProfileListFragment";
		
		private static final int MAX_NUMBER_OF_ENTRIES_SHOWN = 500;
		
		ServerInteractionHelper mServerHelper;
		
		/**
		 * Header Views
		 */
		private View mProfileHeader;						// profile header view
		private ImageView mMugshot;							// mugshot
		private TextView mProfileHeaderText;				// username, tracks, ...
		
		/**
		 * List Views
		 */
		private PullToRefreshListView mPullToRefreshView; 	// pull to refresh view
		private EntryAdapter mEntryAdapter;					// cursor adapter for db
        private TextView mEmpty;							// shown if list is empty
        private View mHeader;								// list header (with rounded corners)
        private View mFooter;								// list footer (with rounded corners)
        private TextView mPadding;							// top padding for list header
        private ProgressWheel mProgressWheelLoadingOlderEntries;
        private TextView mNoMoreEntries;
		
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			
		    View root = inflater.inflate(R.layout.profile_activity, container, false);
		    
		    mPullToRefreshView = (PullToRefreshListView) root.findViewById(R.id.entries_list);
		    mEmpty = (TextView)root.findViewById(R.id.entries_empty);
		    mProgressWheelLoadingOlderEntries = (ProgressWheel)root.findViewById(R.id.loading_older_entries_progress_bar);
		    mNoMoreEntries = (TextView)root.findViewById(R.id.no_more_entries_to_load);
		    
		    mServerHelper = ServerInteractionHelper.getInstance(getActivity());
		    
		    return root;
		}

		@Override
		public void onResume() {
			mServerHelper.registerEventListener(this, getActivity());
			super.onResume();
			
			mProgressWheelLoadingOlderEntries.setVisibility(View.GONE);
		}
		
		@Override
		public void onPause() {
			mServerHelper.unregisterEventListener(this, getActivity());
			super.onPause();
		}
		
		@Override
		public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
		    
            // add profile header
		    mProfileHeader = getLayoutInflater(savedInstanceState).inflate(R.layout.profile_header, null, false);
		    this.getListView().addHeaderView(mProfileHeader);
		    mMugshot = (ImageView)mProfileHeader.findViewById(R.id.profile_mugshot);
		    mProfileHeaderText = (TextView)mProfileHeader.findViewById(R.id.profile_header_text);
		    
            // add list header and padding
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
                    0, // flags
                    false  // mugshot is not clickable
                    );
            setListAdapter(mEntryAdapter);
           
            String username = getArguments().getString("username");
            
            // Prepare and initialize the sql loader for entries
            Bundle argsEntriesSQL = new Bundle();
            argsEntriesSQL.putStringArray(SQLLoaderCallbacks.PROJECTION, NoisetracksProvider.READ_ENTRY_PROJECTION);
           
            if (AppSettings.getUsername(getActivity()).equals(username)) {  // if it's the users own entries, load also 'recorded' entries
            	argsEntriesSQL.putString(SQLLoaderCallbacks.SELECT, SQLLoaderCallbacks.EntriesUser(true, username));
            } else {
            	argsEntriesSQL.putString(SQLLoaderCallbacks.SELECT, SQLLoaderCallbacks.EntriesUser(false, username));
            }
            
            SQLLoaderCallbacks sqlentries = new SQLLoaderCallbacks(getActivity(), this);
            getActivity().getSupportLoaderManager().initLoader(SQLLoaderCallbacks.ENTRIES_SQL_LOADER_PROFILE, argsEntriesSQL, sqlentries);
            
            // Prepare and initialize the sql loader for user profile
            Bundle argsProfileSQL = new Bundle();
            argsProfileSQL.putStringArray(SQLLoaderCallbacks.PROJECTION, NoisetracksProvider.READ_PROFILE_PROJECTION);
            argsProfileSQL.putString(SQLLoaderCallbacks.SELECT, SQLLoaderCallbacks.selectProfileForUser(getArguments().getString("username")));
            SQLLoaderCallbacks sqlprofile = new SQLLoaderCallbacks(getActivity(), this);
            getActivity().getSupportLoaderManager().initLoader(SQLLoaderCallbacks.PROFILE_SQL_LOADER, argsProfileSQL, sqlprofile);	
            
            mServerHelper.registerEventListener(this, getActivity());
            
            // If it's not the user's own profile, get the current profile from server
            if (!AppSettings.getUsername(getActivity()).equals(username)) {
	            Bundle params = new Bundle();
	            params.putString("format", "json");											// we need json format
	            params.putString("user__username", getArguments().getString("username"));	// get profile for specific user
	            NoisetracksRequest request = new NoisetracksRequest(Verb.GET, NoisetracksApplication.URI_PROFILES, params);
                try {
                    mServerHelper.sendRestAction(getActivity(), "ProfileActivity onActivityCreated()", request);
                } catch (SendingCommandException se) {
                    Log.e(TAG, se.toString());
                }
            }
            
        	
	        // Set a listener to be invoked when the list should be refreshed.
	        mPullToRefreshView.setOnRefreshListener(new OnRefreshListener<ListView>() {
	            @Override
	            public void onRefresh(PullToRefreshBase<ListView> refreshView) {
	            	
	            	// get profile
		            Bundle paramsProfile = new Bundle();
		            paramsProfile.putString("format", "json");				// we need json format
		            paramsProfile.putString("user__username", getArguments().getString("username"));	// get profile for specific user
		            NoisetracksRequest profile = new NoisetracksRequest(Verb.GET, NoisetracksApplication.URI_PROFILES, paramsProfile);
	                try {
	                    mServerHelper.sendRestAction(getActivity(), "ProfileActivity onRefresh() Profiles", profile);
	                } catch (SendingCommandException e) {
	                    Log.e(TAG, e.toString());
	                }
	            	
	            	// get user entries
	                Bundle params = new Bundle();
	    	        params.putString("format", "json");				// we need json format
	    	        params.putString("order_by", "-created");		// newest first
	    	        params.putString("audiofile__status", "1");		// only get entries with status = Done
	    	        params.putString("user__username", getArguments().getString("username"));	// only entries from specific user
	            	
	            	if (!mEntryAdapter.isEmpty()) { // if list not empty
	            		Cursor cursor = (Cursor) getListAdapter().getItem(0); // get latest entry
		    	        String created = cursor.getString(cursor.getColumnIndex(Entries.COLUMN_NAME_CREATED));
		    	        params.putString("created__gt", created);		// only entries newer than first (latest) entry in list
	            	}
	            	
	            	NoisetracksRequest entries = new NoisetracksRequest(Verb.GET, NoisetracksApplication.URI_ENTRIES, params);
	                try {
	                    mServerHelper.sendRestAction(getActivity(), "ProfileActivity onRefresh() Entries", entries);
	                } catch (SendingCommandException e) {
	                    Log.e(TAG, e.toString());
	                }
	            	
	            }
	            
	        });
	        
	        
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
		    	        params.putString("user__username", getArguments().getString("username"));	// only entries from specific user
		    	        NoisetracksRequest entries = new NoisetracksRequest(Verb.GET, NoisetracksApplication.URI_ENTRIES, params);
		                try {
		                    mServerHelper.sendRestAction(getActivity(), "ProfileActivity onLastItemVisible()", entries);
		                    mProgressWheelLoadingOlderEntries.stopSpinning();
		                    mProgressWheelLoadingOlderEntries.spin();
			            	mProgressWheelLoadingOlderEntries.setVisibility(View.VISIBLE);
		                } catch (SendingCommandException e) {
		                    Log.e(TAG, e.toString());
		                    mProgressWheelLoadingOlderEntries.stopSpinning();
		                	mProgressWheelLoadingOlderEntries.setVisibility(View.GONE);
		                }
	            	}
					else if (mEntryAdapter.getCount() >= MAX_NUMBER_OF_ENTRIES_SHOWN) {
	            		Log.v(TAG, "Limit reached.");
	            		mNoMoreEntries.setVisibility(View.VISIBLE);
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
					/*	
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
	            	*/
				}
				
				// start entry activity
				else if (c.getInt(c.getColumnIndex(Entries.COLUMN_NAME_TYPE)) == Entries.TYPE.DOWNLOADED.ordinal()) {
					Intent i = new Intent(getActivity().getApplicationContext(), EntryActivity.class);
					// select 'downloaded' entries for user
					i.putExtra(SQLLoaderCallbacks.SELECT, SQLLoaderCallbacks.EntriesUser(false, getArguments().getString("username")));
					i.putExtra(EntryActivity.ID, id);
					startActivity(i);
				
				// start recording activity (to complete upload)
				} else if (c.getInt(c.getColumnIndex(Entries.COLUMN_NAME_TYPE)) == Entries.TYPE.RECORDED.ordinal()) {
					Intent i = new Intent(getActivity().getApplicationContext(), RecordingActivity.class);
					Uri rec = ContentUris.withAppendedId(Entries.CONTENT_ID_URI_BASE, id);
					i.putExtra(RecordingActivity.EXTRA_URI, rec);
					startActivity(i);
				}
				
			}
			
		}
		
		public void onReselectedTab () {
			getListView().setSelection(0); // take me to the top
		}
		
		
		public void onLoadFinishedProfiles(Cursor data) {
			try  {
				data.moveToFirst();
				// Set mugshot image
				String mugshot = data.getString(data.getColumnIndex(Profiles.COLUMN_NAME_MUGSHOT));
				if (mugshot != null) {
					Uri mugshotUri = Uri.parse(mugshot);
					if (mugshotUri != null){
						Bitmap bitmap = NoisetracksApplication.getHttpImageManager().loadImage(new HttpImageManager.LoadRequest(mugshotUri, mMugshot));
						if (bitmap != null) {
							mMugshot.setImageBitmap(bitmap);
					    }
					}
				}
				String username = data.getString(data.getColumnIndex(Profiles.COLUMN_NAME_USERNAME));
				String name = data.getString(data.getColumnIndex(Profiles.COLUMN_NAME_NAME));
				if (name.equals("")) {
					name = username;
				}
				String website = data.getString(data.getColumnIndex(Profiles.COLUMN_NAME_WEBSITE));
				
				mProfileHeaderText.setText(Html.fromHtml(
						"<b>" + name + "</b> @" + username +  "<br />" + 
			            "<b>" + data.getInt(data.getColumnIndex(Profiles.COLUMN_NAME_TRACKS)) + "</b> TRACKS" + "<br />" +
						data.getString(data.getColumnIndex(Profiles.COLUMN_NAME_BIO)) + "<br />" +
						"<a href="+website+">" + website + "</a>"
						));
				mProfileHeaderText.setMovementMethod(LinkMovementMethod.getInstance());
			}
			
			catch (Exception e) {
				// if the own profile was not found, load it form server (this is the case, when the user logged in for the first time)
				if (AppSettings.getUsername(getActivity()).equals(getArguments().getString("username"))) {
					// request profile
		            Bundle params = new Bundle();
		            params.putString("format", "json");				// we need json format
		            params.putString("user__username", getArguments().getString("username"));	// get profile for specific user
		            NoisetracksRequest request = new NoisetracksRequest(Verb.GET, NoisetracksApplication.URI_PROFILES, params);
	                try {
	                    mServerHelper.sendRestAction(getActivity(), "ProfileActivity onLoadFinishedProfiles()", request);
	                } catch (SendingCommandException se) {
	                    Log.e(TAG, se.toString());
	                }
				}
	    		
			}
		}
		
		public void onLoadFinishedEntries(Cursor data) {
			
			mEntryAdapter.swapCursor(data);
        	
        	if (mEntryAdapter.isEmpty()) {
        		mPadding.setVisibility(View.INVISIBLE);
            	mHeader.setVisibility(View.INVISIBLE);
            	mFooter.setVisibility(View.INVISIBLE);
        		
            	// get user entries
                Bundle params = new Bundle();
    	        params.putString("format", "json");				// we need json format
    	        params.putString("order_by", "-created");		// newest first
    	        params.putString("audiofile__status", "1");		// only get entries with status = Done
    	        params.putString("user__username", getArguments().getString("username"));	// only entries from specific user
            	NoisetracksRequest entries = new NoisetracksRequest(Verb.GET, NoisetracksApplication.URI_ENTRIES, params);
                try {
                    mServerHelper.sendRestAction(getActivity(), "ProfileActivity onLoadFinishedEntries()", entries);
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
		
		@Override
		public void onServerResult(String result, String requestId) {
			Log.v(TAG, "onServerResult: " + result + " from " + requestId);
			mPullToRefreshView.onRefreshComplete();
			mProgressWheelLoadingOlderEntries.stopSpinning();
        	mProgressWheelLoadingOlderEntries.setVisibility(View.GONE);
        	
        	FeedListFragment.hack();
		}

		@Override
		public void onServerError(String result, String requestId) {
			Log.w(TAG, "Server responded: " + result + " from " + requestId);
			
			mPullToRefreshView.onRefreshComplete();
			mProgressWheelLoadingOlderEntries.stopSpinning();
        	mProgressWheelLoadingOlderEntries.setVisibility(View.GONE);
        	
        	FeedListFragment.hack();
			
			if (mEntryAdapter.isEmpty())
				mEmpty.setText("Could not connect to Noisetracks.");
		}
		
	}

	

}
