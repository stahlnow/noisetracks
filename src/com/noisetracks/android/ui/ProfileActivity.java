package com.noisetracks.android.ui;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.MenuItem;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshListView;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnLastItemVisibleListener;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;
import com.noisetracks.android.NoisetracksApplication;
import com.noisetracks.android.R;
import com.noisetracks.android.client.RESTLoaderCallbacks;
import com.noisetracks.android.client.SQLLoaderCallbacks;
import com.noisetracks.android.helper.ProgressWheel;
import com.noisetracks.android.helper.httpimage.HttpImageManager;
import com.noisetracks.android.provider.NoisetracksContract.Profiles;
import com.noisetracks.android.provider.NoisetracksProvider;
import com.noisetracks.android.provider.NoisetracksContract.Entries;
import com.noisetracks.android.utility.AppSettings;
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
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class ProfileActivity extends SherlockFragmentActivity {
	
	@SuppressWarnings("unused")
	private static final String TAG = "ProfileActivity";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		
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

	public static class ProfileListFragment extends SherlockListFragment {
		
		private static final String TAG = "ProfileListFragment";
		
		private RESTLoaderCallbacks r;
		
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
		private EntryAdapter mEntryAdapter;						// cursor adapter for db
		private boolean mListShown;		
        private TextView mEmpty;							// shown if list is empty
        private View mProgressContainer; 					// progress wheel container
        private ProgressWheel mProgressWheel; 				// progress wheel
        private View mListContainer;						// list container
        private View mHeader;								// list header (with rounded corners)
        private View mFooter;								// list footer (with rounded corners)
        private TextView mPadding;							// top padding for list header
		
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
                    
            r = new RESTLoaderCallbacks(getActivity(), this);
		    
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
		    
		    // Start out with a progress indicator.
            setListShown(false);
         
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
            
            // Prepare and initialize the sql loader for user entries
            Bundle argsEntriesSQL = new Bundle();
            argsEntriesSQL.putStringArray(SQLLoaderCallbacks.PROJECTION, NoisetracksProvider.READ_ENTRY_PROJECTION);
            String username = getArguments().getString("username");
            if (AppSettings.getUsername(getActivity()).equals(username)) {  // if it's the users own entries, load also 'recorded' entries
            	argsEntriesSQL.putString(SQLLoaderCallbacks.SELECT, SQLLoaderCallbacks.EntriesUser(true, username));
            } else {
            	argsEntriesSQL.putString(SQLLoaderCallbacks.SELECT, SQLLoaderCallbacks.EntriesUser(false, username));
            }
            
            SQLLoaderCallbacks sqlentries = new SQLLoaderCallbacks(getActivity(), this);
            getActivity().getSupportLoaderManager().initLoader(NoisetracksApplication.ENTRIES_SQL_LOADER_PROFILE, argsEntriesSQL, sqlentries);
            
            
            // Prepare and initialize the sql loader for user profile data
            Bundle argsProfileSQL = new Bundle();
            argsProfileSQL.putStringArray(SQLLoaderCallbacks.PROJECTION, NoisetracksProvider.READ_PROFILE_PROJECTION);
            argsProfileSQL.putString(SQLLoaderCallbacks.SELECT, SQLLoaderCallbacks.selectProfileForUser(getArguments().getString("username")));
            SQLLoaderCallbacks sqlprofile = new SQLLoaderCallbacks(getActivity(), this);
            getActivity().getSupportLoaderManager().initLoader(NoisetracksApplication.PROFILE_SQL_LOADER, argsProfileSQL, sqlprofile);	
            
            
            // Prepare and initialize REST loader for entries
            Bundle params = new Bundle();
	        params.putString("format", "json");				// we need json format
	        params.putString("order_by", "-created");		// newest first
	        params.putString("audiofile__status", "1");		// only get entries with status = Done
	        params.putString("user__username", getArguments().getString("username"));	// only entries from specific user
        	Bundle argsEntriesREST = new Bundle();
        	argsEntriesREST.putParcelable(RESTLoaderCallbacks.ARGS_URI, NoisetracksApplication.URI_ENTRIES);
        	argsEntriesREST.putParcelable(RESTLoaderCallbacks.ARGS_PARAMS, params);
    		getActivity().getSupportLoaderManager().initLoader(NoisetracksApplication.ENTRIES_REST_LOADER, argsEntriesREST, r);
    		
            
	        // Set a listener to be invoked when the list should be refreshed.
	        mPullToRefreshView.setOnRefreshListener(new OnRefreshListener<ListView>() {
	            @Override
	            public void onRefresh(PullToRefreshBase<ListView> refreshView) {
	            	// Prepare and initialize REST loader for profile
		            Bundle paramsProfile = new Bundle();
		            paramsProfile.putString("format", "json");				// we need json format
		            paramsProfile.putString("user__username", getArguments().getString("username"));	// get profile for specific user
		        	Bundle argsProfileREST = new Bundle();
		        	argsProfileREST.putParcelable(RESTLoaderCallbacks.ARGS_URI, NoisetracksApplication.URI_PROFILES);
		        	argsProfileREST.putParcelable(RESTLoaderCallbacks.ARGS_PARAMS, paramsProfile);
		        	getActivity().getSupportLoaderManager().restartLoader(NoisetracksApplication.PROFILE_REST_LOADER, argsProfileREST, r);
	            	
	            	// Call api
	            	if (!mEntryAdapter.isEmpty()) { // if list not empty
	            		Cursor cursor = (Cursor) getListAdapter().getItem(0); // get latest entry
		    	        String created = cursor.getString(cursor.getColumnIndex(Entries.COLUMN_NAME_CREATED));
		            	Bundle params = new Bundle();
		    	        params.putString("format", "json");				// we need json format
		    	        params.putString("order_by", "-created");		// newest first
		    	        params.putString("audiofile__status", "1");		// only get entries with status = Done
		    	        params.putString("created__gt", created);		// only entries newer than first (latest) entry in list
		    	        params.putString("user__username", getArguments().getString("username"));	// only entries from specific user
		            	Bundle argsEntriesNewer = new Bundle();
		            	argsEntriesNewer.putParcelable(RESTLoaderCallbacks.ARGS_URI, NoisetracksApplication.URI_ENTRIES);
		            	argsEntriesNewer.putParcelable(RESTLoaderCallbacks.ARGS_PARAMS, params);
		            	getActivity().getSupportLoaderManager().restartLoader(NoisetracksApplication.ENTRIES_NEWER_REST_LOADER, argsEntriesNewer, r);
	            	} else {
	            		Bundle params = new Bundle();
		    	        params.putString("format", "json");				// we need json format
		    	        params.putString("order_by", "-created");		// newest first
		    	        params.putString("audiofile__status", "1");		// only get entries with status = Done
		    	        params.putString("user__username", getArguments().getString("username"));	// only entries from specific user
		            	Bundle argsEntries = new Bundle();
		            	argsEntries.putParcelable(RESTLoaderCallbacks.ARGS_URI, NoisetracksApplication.URI_ENTRIES);
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
		    	        params.putString("user__username", getArguments().getString("username"));	// only entries from specific user
		    	        Bundle argsEntriesOlder = new Bundle();
		            	argsEntriesOlder.putParcelable(RESTLoaderCallbacks.ARGS_URI, NoisetracksApplication.URI_ENTRIES);
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
	            	//getActivity().getContentResolver().notifyAll();
	            	
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
				
				//c.close();
			}
		}
		
		public void onReselectedTab () {
			getListView().setSelection(0);
		}
		
		
		public void setProfileHeader(Cursor data) {
			try  {
				data.moveToFirst();
				// Set mugshot image
				//mMugshot.setImageResource(R.drawable.default_image); // TODO set default image
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
				
				Log.v(TAG, "Could not load from db, will try loading with REST client...");
				
				// Prepare and initialize REST loader for profile
	            Bundle paramsProfile = new Bundle();
	            paramsProfile.putString("format", "json");				// we need json format
	            paramsProfile.putString("user__username", getArguments().getString("username"));	// get profile for specific user
	        	Bundle argsProfileREST = new Bundle();
	        	argsProfileREST.putParcelable(RESTLoaderCallbacks.ARGS_URI, NoisetracksApplication.URI_PROFILES);
	        	argsProfileREST.putParcelable(RESTLoaderCallbacks.ARGS_PARAMS, paramsProfile);
	        	getActivity().getSupportLoaderManager().restartLoader(NoisetracksApplication.PROFILE_REST_LOADER, argsProfileREST, r);
	    		
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
	    	//mPullToRefreshView.setLastUpdatedLabel("Last updated: " + DateUtils.formatDateTime(getActivity(), System.currentTimeMillis(), DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NUMERIC_DATE | DateUtils.FORMAT_ABBREV_TIME));

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
