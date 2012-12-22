package com.stahlnow.noisetracks.ui;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.format.DateUtils;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ListView;
import android.widget.TextView;
import com.google.android.maps.GeoPoint;
import com.stahlnow.noisetracks.R;
import com.stahlnow.noisetracks.client.Entry;
import com.stahlnow.noisetracks.client.RESTLoader;
import com.stahlnow.noisetracks.client.RESTLoaderCallbacks;
import com.stahlnow.noisetracks.client.SQLLoaderCallbacks;
import com.stahlnow.noisetracks.helper.ProgressWheel;
import com.stahlnow.noisetracks.provider.NoisetracksContract.Entries;
import com.stahlnow.noisetracks.provider.NoisetracksProvider;
import com.stahlnow.noisetracks.utility.AppLog;
import com.stahlnow.noisetracks.utility.AppSettings;

import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnLastItemVisibleListener;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;
import com.handmark.pulltorefresh.library.PullToRefreshListView;


public class TabsExplore extends FragmentActivity {
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        FragmentManager fm = getSupportFragmentManager();

        // Create the list fragment and add it as our sole content.
        if (fm.findFragmentById(android.R.id.content) == null) {
        	ExploreListFragment list = new ExploreListFragment();
            fm.beginTransaction().add(android.R.id.content, list).commit();
        }
        
    }

    public static class ExploreListFragment extends ListFragment
    {
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
        
        private MediaPlayer player = null;      
        
		@Override
		public void onDestroy() {
			super.onDestroy();
			
			//handler.removeCallbacks(updatePositionRunnable);
			if (player.isPlaying()) {
				player.stop();
			}
			player.reset();
			player.release();

			player = null;
		}

		
		@Override
		public void onListItemClick (ListView l, View v, int position, long id) {
			super.onListItemClick(l, v, position, id);
			
			/*
			// Constructs a new URI from the incoming URI and the row ID
	        Uri uri = ContentUris.withAppendedId(getActivity().getIntent().getData(), id);
	        AppLog.logString(uri.toString());
	        startActivity(new Intent("noisetracks.intent.view", uri));
			*/
			
			AppLog.logString("pos: " + l.getItemIdAtPosition(position));
			
			Intent i = new Intent(getActivity().getApplicationContext(), EntryActivity.class);
			i.putExtra("item", l.getItemIdAtPosition(position));
			startActivity(i);
			
			/*
			String currentTrack = null;

			try {
				currentTrack = ((Entry)l.getItemAtPosition(position)).getFile();
			}
			catch (NullPointerException e) {	// catch items clicked that are not of type Entry
				//e.printStackTrace(); 
			}
			finally {		
				//AppLog.logString(position + ": " + currentTrack);
				if (currentTrack != null)
					startPlay(currentTrack);
			}
			*/
		}
		
		

		
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			
		    View root = inflater.inflate(R.layout.explore_activity, container, false);
		    
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
		    
		    player = new MediaPlayer();
		    player.setOnCompletionListener(onCompletion);
	        player.setOnErrorListener(onError);
	        
		    
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
                    0); // flags
            setListAdapter(mAdapter);
            
            // Start out with a progress indicator.
            setListShown(false);

            // Prepare the loader.  Either re-connect with an existing one, or start a new one.
            Bundle args = new Bundle();
            args.putStringArray(SQLLoaderCallbacks.PROJECTION, NoisetracksProvider.READ_ENTRY_PROJECTION);
            args.putString(SQLLoaderCallbacks.SELECT, SQLLoaderCallbacks.SELECT_ENTRIES);
            SQLLoaderCallbacks sql = new SQLLoaderCallbacks(this.getActivity(), mAdapter, (ListFragment)this, mEmpty, mPadding, mHeader, mFooter);
            getActivity().getSupportLoaderManager().initLoader(0, args, sql);		    
	        
	        // Set a listener to be invoked when the list should be refreshed.
	        mPullToRefreshView.setOnRefreshListener(new OnRefreshListener<ListView>() {
	            @Override
	            public void onRefresh(PullToRefreshBase<ListView> refreshView) {
	            	
	            	AppLog.logString("Explore onRefresh");
	            	
	            	// Call api
	            	if (!mAdapter.isEmpty()) { // if list not empty
	            		Cursor cursor = (Cursor) getListAdapter().getItem(0); // get latest entry
		    	        String created = cursor.getString(cursor.getColumnIndex(Entries.COLUMN_NAME_CREATED));
		            	Bundle params = new Bundle();
		    	        params.putString("format", "json");				// we need json format
		    	        params.putString("order_by", "created");		// oldest first (reverse order for array insertion)
		    	        params.putString("audiofile__status", "1");		// only get entries with status = Done
		    	        params.putString("created__gt", created);		// only entries newer than first (latest) entry in list
		            	Bundle argsEntriesNewer = new Bundle();
		            	argsEntriesNewer.putParcelable(RESTLoaderCallbacks.ARGS_URI, RESTLoaderCallbacks.URI_ENTRIES);
		            	argsEntriesNewer.putParcelable(RESTLoaderCallbacks.ARGS_PARAMS, params);
		            	RESTLoaderCallbacks r = new RESTLoaderCallbacks(getActivity(), mAdapter, mPullToRefreshView, mEmpty, mPadding, mHeader, mFooter);
		            	getActivity().getSupportLoaderManager().restartLoader(RESTLoaderCallbacks.ENTRIES_NEWER, argsEntriesNewer, r);
	            	} else {
	            		Bundle params = new Bundle();
		    	        params.putString("format", "json");				// we need json format
		    	        params.putString("order_by", "-created");		// newest first
		    	        params.putString("audiofile__status", "1");		// only get entries with status = Done
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
		    	        Bundle argsEntriesOlder = new Bundle();
		            	argsEntriesOlder.putParcelable(RESTLoaderCallbacks.ARGS_URI, RESTLoaderCallbacks.URI_ENTRIES);
		            	argsEntriesOlder.putParcelable(RESTLoaderCallbacks.ARGS_PARAMS, params);
		            	RESTLoaderCallbacks r = new RESTLoaderCallbacks(getActivity(), mAdapter, mPullToRefreshView, mEmpty, mPadding, mHeader, mFooter);
		            	getActivity().getSupportLoaderManager().restartLoader(RESTLoaderCallbacks.ENTRIES_OLDER, argsEntriesOlder, r);
	            	}
					
				}
			});
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
	    
	    
	    private MediaPlayer.OnCompletionListener onCompletion = new MediaPlayer.OnCompletionListener() {
    		
    		@Override
    		public void onCompletion(MediaPlayer mp) {
    			stopPlay();
    		}
    	};
    	
    	private MediaPlayer.OnErrorListener onError = new MediaPlayer.OnErrorListener() {
    		
    		@Override
    		public boolean onError(MediaPlayer mp, int what, int extra) {
    			AppLog.logString(what + " " + extra);
    			// returning false will call the OnCompletionListener
    			return false;
    		}
    	};
	    
	    private void startPlay(String file) {
			
			if (player.isPlaying()) {
				player.stop();
				player.reset();
			}
			
			try {
				player.setAudioStreamType(AudioManager.STREAM_MUSIC);
				player.setDataSource(file);
				player.prepare();
				player.start();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalStateException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			//seekbar.setMax(player.getDuration());
			//playButton.setImageResource(android.R.drawable.ic_media_pause);
			
			//updatePosition();
			
			//isStarted = true;
		}
		
		private void stopPlay() {
			player.stop();
			player.reset();
			
			//playButton.setImageResource(android.R.drawable.ic_media_play);
			//handler.removeCallbacks(updatePositionRunnable);
			//seekbar.setProgress(0);
			
			//isStarted = false;
		}
	}
}
