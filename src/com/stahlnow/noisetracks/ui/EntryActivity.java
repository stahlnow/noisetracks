package com.stahlnow.noisetracks.ui;

import java.io.IOException;
import java.lang.reflect.Field;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.stahlnow.noisetracks.NoisetracksApplication;
import com.stahlnow.noisetracks.R;
import com.stahlnow.noisetracks.client.SQLLoaderCallbacks;
import com.stahlnow.noisetracks.helper.FixedSpeedScroller;
import com.stahlnow.noisetracks.helper.httpimage.HttpImageManager;
import com.stahlnow.noisetracks.provider.NoisetracksProvider;
import com.stahlnow.noisetracks.provider.NoisetracksContract.Entries;
import com.stahlnow.noisetracks.ui.EntryActivity.EntryDetailFragment;
import com.stahlnow.noisetracks.utility.AppLog;

import android.support.v4.content.LocalBroadcastManager;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaPlayer.OnSeekCompleteListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.text.Html;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;
import com.handmark.pulltorefresh.extras.viewpager.PullToRefreshViewPager;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;

public class EntryActivity extends SherlockFragmentActivity implements
	OnRefreshListener<ViewPager>, OnPageChangeListener,
	OnPreparedListener, OnErrorListener, OnCompletionListener, OnSeekCompleteListener { 

	private static final int UPDATE_FREQUENCY = 1;
	private final Handler handler = new Handler();
	
	private static MediaPlayer player;
	
	private Cursor mCursor = null;
	private PullToRefreshViewPager mPullToRefreshViewPager;
	private ViewPager mPager;
	private EntryPagerAdapter mAdapter;
	private ImageButton playButton = null;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		getSupportActionBar().setDisplayHomeAsUpEnabled(true); // enable 'up' navigation in action bar
		
		setContentView(R.layout.entry_activity);
		
		playButton = (ImageButton)findViewById(R.id.entry_play_pause);

		mPullToRefreshViewPager = (PullToRefreshViewPager) findViewById(R.id.entry_activity_pull_refresh_view_pager);
		mPullToRefreshViewPager.setOnRefreshListener(this);
		mPager = mPullToRefreshViewPager.getRefreshableView();
		
		String select = getIntent().getExtras().getString(SQLLoaderCallbacks.SELECT);
		
		mCursor = getContentResolver().query(
				Entries.CONTENT_URI,
				NoisetracksProvider.READ_ENTRY_PROJECTION,
				select,
				null,
				Entries.DEFAULT_SORT_ORDER
		);
		
		mAdapter = new EntryPagerAdapter(getSupportFragmentManager(), NoisetracksProvider.READ_ENTRY_PROJECTION, mCursor);
		mPager.setAdapter(mAdapter);
		mPager.setCurrentItem(getIntent().getExtras().getInt("item"), false); 	// select item
		mCursor.moveToPosition(getIntent().getExtras().getInt("item"));			// move cursor in position
		
		// Set custom scroller for slow item scroll animation
		try {
			Field mScroller;
			mScroller = ViewPager.class.getDeclaredField("mScroller");
			mScroller.setAccessible(true);
			DecelerateInterpolator sInterpolator = new DecelerateInterpolator();
			FixedSpeedScroller scroller = new FixedSpeedScroller(mPager.getContext(), sInterpolator);
			mScroller.set(mPager, scroller);
		} catch (NoSuchFieldException e) {
		} catch (IllegalArgumentException e) {
		} catch (IllegalAccessException e) {
		}	
		
		// create media player
		player = new MediaPlayer();
		player.setOnPreparedListener(this);
        player.setOnCompletionListener(this);
        player.setOnErrorListener(this);       
		
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        
        
        // set media url
		try {
			player.setDataSource(mCursor.getString(mCursor.getColumnIndex(Entries.COLUMN_NAME_FILENAME)));
		} catch (IllegalArgumentException e) {
			Toast.makeText(this, R.string.player_error + "Stream not found.", Toast.LENGTH_SHORT).show();
		} catch (SecurityException e) {
			Toast.makeText(this, R.string.player_error + "Stream not found.", Toast.LENGTH_SHORT).show();
		} catch (IllegalStateException e) {
			Toast.makeText(this, R.string.player_error + "Stream not found.", Toast.LENGTH_SHORT).show();
		} catch (IOException e) {
			Toast.makeText(this, R.string.player_error + "Stream not found.", Toast.LENGTH_SHORT).show();
		}
		
		// prepare buffer and start playback
		player.prepareAsync();
		
		mPager.setOnPageChangeListener(this);
        
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();

		// Stop the seekbar handler from sending updates to UI
		handler.removeCallbacks(updatePositionRunnable);
				
		if (player.isPlaying()) {
			player.stop();
		}
		player.reset();
		player.release();
		

		player = null;
	}
	
	/*
	 * Button click handler for 'play'
	 */
	public void play(View view) {
	    if (player.isPlaying()) {
	    	handler.removeCallbacks(updatePositionRunnable);
			player.pause();
			playButton.setImageResource(R.drawable.av_play);
		} else {
			player.start();
			playButton.setImageResource(R.drawable.av_pause);
			updatePosition();
		}
	}
	
	/*
	 * Button click handler for 'previous'
	 */
	public void previous(View view) {
	    mPager.setCurrentItem(mPager.getCurrentItem()-1, true);
	}
	
	/*
	 * Button click handler for 'next'
	 */
	public void next(View view) {
	    mPager.setCurrentItem(mPager.getCurrentItem()+1, true);
	}	
		
	@Override
	public void onPageScrollStateChanged(int arg0) {	
	}

	@Override
	public void onPageScrolled(int arg0, float arg1, int arg2) {
	}

	@Override
	public void onPageSelected(int page) {
		
		mCursor.moveToPosition(page);
		
		if (player.isPlaying()) {
			player.stop();
		}
		player.reset();
		
		try {
			player.setDataSource(mCursor.getString(mCursor.getColumnIndex(Entries.COLUMN_NAME_FILENAME)));
		} catch (IllegalArgumentException e) {
			Toast.makeText(this, R.string.player_error + "Stream not found.", Toast.LENGTH_SHORT).show();
		} catch (SecurityException e) {
			Toast.makeText(this, R.string.player_error + "Stream not found.", Toast.LENGTH_SHORT).show();
		} catch (IllegalStateException e) {
			Toast.makeText(this, R.string.player_error + "Stream not found.", Toast.LENGTH_SHORT).show();
		} catch (IOException e) {
			Toast.makeText(this, R.string.player_error + "Stream not found.", Toast.LENGTH_SHORT).show();		}
		
		
		try {
			EntryDetailFragment f = mAdapter.getFragment(mPager.getCurrentItem());
			f.seekBar.setProgress(0);
		} catch (NullPointerException e) {}
		
		// buffer and start media playback
		player.prepareAsync();
		
	}
	
	@Override
	public void onPrepared(MediaPlayer player) {
		if (!player.isPlaying()) {
			player.start();
			playButton.setImageResource(R.drawable.av_pause);
			
			try {
				EntryDetailFragment f = mAdapter.getFragment(mPager.getCurrentItem());
				f.seekBar.setMax(player.getDuration());
				updatePosition();
			} catch (NullPointerException e) {
				
			}
		}
	}
	
	private void updatePosition(){
		handler.removeCallbacks(updatePositionRunnable);
		try {
			EntryDetailFragment f = mAdapter.getFragment(mPager.getCurrentItem());
			f.seekBar.setProgress(player.getCurrentPosition());
		} catch (NullPointerException e) {}
		AppLog.logString("pos: " + player.getCurrentPosition());
		
		handler.postDelayed(updatePositionRunnable, UPDATE_FREQUENCY);
	}
	
	private final Runnable updatePositionRunnable = new Runnable() {
		public void run() {
			updatePosition();
		}
	};
	
	@Override
	public void onRefresh(PullToRefreshBase<ViewPager> refreshView) {
		new GetDataTask().execute();
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	        case android.R.id.home:
	            // This is called when the Home (Up) button is pressed
	            // in the Action Bar.
	            Intent parentActivityIntent = new Intent(this, Tabs.class);
	            parentActivityIntent.addFlags(
	                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
	                    Intent.FLAG_ACTIVITY_NEW_TASK);
	            startActivity(parentActivityIntent);
	            finish();
	            return true;
	    }
	    return super.onOptionsItemSelected(item);
	}
	

	@Override
	public void onCompletion(MediaPlayer mp) {
		playButton.setImageResource(R.drawable.av_play);
		handler.removeCallbacks(updatePositionRunnable);
		// select next item
		mPager.setCurrentItem(mPager.getCurrentItem()+1, true);
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		return false;
	}
	
	@Override
	public void onSeekComplete(MediaPlayer mp) {
		if (!player.isPlaying()) {
			player.start();
		}
	}
	
	private static class EntryPagerAdapter extends FragmentStatePagerAdapter {
		@SuppressLint("UseSparseArrays")
		private Map<Integer, EntryDetailFragment> mPageReferenceMap = new HashMap<Integer, EntryDetailFragment>();
		private final String[] mProjection;
		private Cursor mCursor;
		
		public EntryPagerAdapter(FragmentManager fm, String[] projection, Cursor cursor) {
			super(fm);
			this.mProjection = projection;
			this.mCursor = cursor;			
		}
		
		@Override
	    public Fragment getItem(int position) {
	        if (mCursor == null) // shouldn't happen
	            return null;
	 
	        mCursor.moveToPosition(position);
	        
	        EntryDetailFragment f;
	        
	        try {
	        	f = EntryDetailFragment.newInstance();
	        	mPageReferenceMap.put(position, f);			// put it in a map, so we have a reference!
	        } catch (Exception ex) {
	            throw new RuntimeException(ex);
	        }
	        Bundle args = new Bundle();
	        for (int i = 0; i < mProjection.length; ++i) {
	            args.putString(mProjection[i], mCursor.getString(i)); // TODO: this gets everything as Strings (even latitude/longitude)
	        }
	        f.setArguments(args);
	        
	        return f;
	    }
		
		@Override
		public int getCount() {
			if (mCursor == null)
				return 0;
			else
				return mCursor.getCount();
		}
		
		@Override
		public void destroyItem(ViewGroup container, int position, Object object) {
			super.destroyItem(container, position, object);
			mPageReferenceMap.remove(Integer.valueOf(position));
		}
		
		public EntryDetailFragment getFragment(int key) {
			return mPageReferenceMap.get(key);
		}

		public void swapCursor(Cursor c) {
			if (mCursor == c)
				return;

			this.mCursor = c;
			notifyDataSetChanged();
		}

		public Cursor getCursor() {
			return mCursor;
		}

	}

	public static class EntryDetailFragment extends Fragment  {
		private SeekBar seekBar;
		private HttpImageManager mHttpImageManager;
		
		Intent intent;
		
		public static EntryDetailFragment newInstance() {
			EntryDetailFragment f = new EntryDetailFragment();
			return f;
		}

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			this.mHttpImageManager = NoisetracksApplication.getHttpImageManager();
		}

		/**
		 * The Fragment's UI
		 */
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			
			View v = inflater.inflate(R.layout.entry_detail, container, false);
			ImageView mugshot = (ImageView) v.findViewById(R.id.entry_mugshot);
			TextView username = (TextView) v.findViewById(R.id.entry_username);
			TextView recorded_ago = (TextView) v.findViewById(R.id.entry_recorded_ago);
			ImageView spectrogram = (ImageView) v.findViewById(R.id.entry_spectrogram);
			
			seekBar = (SeekBar)v.findViewById(R.id.seekbar);
			
			
			seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                	if (fromUser) {
                		player.seekTo(sb.getProgress()); // update player position
           		 	}
                }
            });

			//mugshot.setImageResource(R.drawable.default_image);
			String mug = getArguments().getString("mugshot");
			if (mug != null) {
				Uri mugshotUri = Uri.parse(mug);
				if (mugshotUri != null){
					Bitmap bitmap = mHttpImageManager.loadImage(new HttpImageManager.LoadRequest(mugshotUri, mugshot));
					if (bitmap != null) {
						mugshot.setImageBitmap(bitmap);
				    }
				}
			}
			
			//spectrogram.setImageResource(R.drawable.default_image);
			String spect = getArguments().getString("spectrogram");
			if (spect != null) {
				Uri specUri = Uri.parse(spect);
				if (specUri != null){
					Bitmap bitmap = mHttpImageManager.loadImage(new HttpImageManager.LoadRequest(specUri, spectrogram));
					if (bitmap != null) {
						spectrogram.setImageBitmap(bitmap);
					}
					
				}
			}
			
			username.setText(getArguments().getString("username"));
			
			String recorded = getArguments().getString("recorded");
			if (recorded != null) {
				try {
					Date d = NoisetracksApplication.SDF.parse(recorded);
					String time = DateUtils.formatDateTime(getActivity(), d.getTime(), DateUtils.FORMAT_SHOW_TIME|DateUtils.FORMAT_12HOUR|DateUtils.FORMAT_CAP_AMPM);
					String date = DateUtils.formatDateTime(getActivity(), d.getTime(), DateUtils.FORMAT_SHOW_DATE|DateUtils.FORMAT_ABBREV_ALL);
					recorded_ago.setText(time + Html.fromHtml("&nbsp;\u00B7&nbsp;") + date);
				} catch (ParseException e) {			
					AppLog.logString("Failed to parse recorded date: " + e.toString());
				}
			}
			
			return v;
		}

		@Override
		public void onActivityCreated(Bundle savedInstanceState) {
			super.onActivityCreated(savedInstanceState);
		}
		
		@Override
		public void onDestroy() {
			super.onDestroy();
			
		}
	}

	
	private class GetDataTask extends AsyncTask<Void, Void, Void> {

		@Override
		protected Void doInBackground(Void... params) {
			// Simulates a background job.
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			mPullToRefreshViewPager.onRefreshComplete();
			super.onPostExecute(result);
		}
	}


	
	
	
}
