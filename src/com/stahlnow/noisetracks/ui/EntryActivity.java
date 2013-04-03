package com.stahlnow.noisetracks.ui;

import java.io.IOException;
import java.lang.reflect.Field;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.stahlnow.noisetracks.NoisetracksApplication;
import com.stahlnow.noisetracks.R;
import com.stahlnow.noisetracks.client.RESTLoaderCallbacks;
import com.stahlnow.noisetracks.client.SQLLoaderCallbacks;
import com.stahlnow.noisetracks.helper.FixedSpeedScroller;
import com.stahlnow.noisetracks.helper.httpimage.HttpImageManager;
import com.stahlnow.noisetracks.provider.NoisetracksContract;
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
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;
import com.handmark.pulltorefresh.extras.viewpager.PullToRefreshViewPager;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;

public class EntryActivity extends SherlockFragmentActivity implements
		OnRefreshListener<ViewPager>, OnPageChangeListener, OnPreparedListener,
		OnErrorListener, OnCompletionListener, OnSeekCompleteListener {

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

		getSupportActionBar().setDisplayHomeAsUpEnabled(true); // enable 'up'
																// navigation in
																// action bar

		setContentView(R.layout.entry_activity);

		playButton = (ImageButton) findViewById(R.id.entry_play_pause);

		mPullToRefreshViewPager = (PullToRefreshViewPager) findViewById(R.id.entry_activity_pull_refresh_view_pager);
		mPullToRefreshViewPager.setOnRefreshListener(this);
		mPager = mPullToRefreshViewPager.getRefreshableView();

		String select = getIntent().getExtras().getString(SQLLoaderCallbacks.SELECT);

		mCursor = getContentResolver().query(Entries.CONTENT_URI,
				NoisetracksProvider.READ_ENTRY_PROJECTION, select, null,
				Entries.DEFAULT_SORT_ORDER);

		mAdapter = new EntryPagerAdapter(getSupportFragmentManager(),
				NoisetracksProvider.READ_ENTRY_PROJECTION, mCursor);
		mPager.setAdapter(mAdapter);
		mPager.setCurrentItem(getIntent().getExtras().getInt("item"), false); // select
																				// item
		mCursor.moveToPosition(getIntent().getExtras().getInt("item")); // move
																		// cursor
																		// in
																		// position

		// Set custom scroller for slow item scroll animation
		try {
			Field mScroller;
			mScroller = ViewPager.class.getDeclaredField("mScroller");
			mScroller.setAccessible(true);
			DecelerateInterpolator sInterpolator = new DecelerateInterpolator();
			FixedSpeedScroller scroller = new FixedSpeedScroller(
					mPager.getContext(), sInterpolator);
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
			Toast.makeText(this, R.string.player_error + "Stream not found.",
					Toast.LENGTH_SHORT).show();
		} catch (SecurityException e) {
			Toast.makeText(this, R.string.player_error + "Stream not found.",
					Toast.LENGTH_SHORT).show();
		} catch (IllegalStateException e) {
			Toast.makeText(this, R.string.player_error + "Stream not found.",
					Toast.LENGTH_SHORT).show();
		} catch (IOException e) {
			Toast.makeText(this, R.string.player_error + "Stream not found.",
					Toast.LENGTH_SHORT).show();
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
		mPager.setCurrentItem(mPager.getCurrentItem() - 1, true);
	}

	/*
	 * Button click handler for 'next'
	 */
	public void next(View view) {
		mPager.setCurrentItem(mPager.getCurrentItem() + 1, true);
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
			Toast.makeText(this, R.string.player_error + "Stream not found.",
					Toast.LENGTH_SHORT).show();
		} catch (SecurityException e) {
			Toast.makeText(this, R.string.player_error + "Stream not found.",
					Toast.LENGTH_SHORT).show();
		} catch (IllegalStateException e) {
			Toast.makeText(this, R.string.player_error + "Stream not found.",
					Toast.LENGTH_SHORT).show();
		} catch (IOException e) {
			Toast.makeText(this, R.string.player_error + "Stream not found.",
					Toast.LENGTH_SHORT).show();
		}

		try {
			EntryDetailFragment f = mAdapter.getFragment(mPager
					.getCurrentItem());
			f.mSeekBar.setProgress(0);
		} catch (NullPointerException e) {
		}

		// buffer and start media playback
		player.prepareAsync();

	}

	@Override
	public void onPrepared(MediaPlayer player) {
		if (!player.isPlaying()) {
			player.start();
			playButton.setImageResource(R.drawable.av_pause);

			try {
				EntryDetailFragment f = mAdapter.getFragment(mPager
						.getCurrentItem());
				f.mSeekBar.setMax(player.getDuration());
				updatePosition();
			} catch (NullPointerException e) {

			}
		}
	}

	private void updatePosition() {
		handler.removeCallbacks(updatePositionRunnable);
		try {
			EntryDetailFragment f = mAdapter.getFragment(mPager
					.getCurrentItem());
			f.mSeekBar.setProgress(player.getCurrentPosition());
		} catch (NullPointerException e) {
		}
		//AppLog.logString("pos: " + player.getCurrentPosition());

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
			parentActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
					| Intent.FLAG_ACTIVITY_NEW_TASK);
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
		mPager.setCurrentItem(mPager.getCurrentItem() + 1, true);
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

		public EntryPagerAdapter(FragmentManager fm, String[] projection,
				Cursor cursor) {
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
				mPageReferenceMap.put(position, f); // put it in a map, so we
													// have a reference!
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
			
			Bundle args = new Bundle();
			
			int i = -1;
			i = mCursor.getColumnIndex(Entries.COLUMN_NAME_FILENAME);
			args.putString(mProjection[i], mCursor.getString(i));
			i = mCursor.getColumnIndex(Entries.COLUMN_NAME_MUGSHOT);
			args.putString(mProjection[i], mCursor.getString(i));
			i = mCursor.getColumnIndex(Entries.COLUMN_NAME_RESOURCE_URI);
			args.putString(mProjection[i], mCursor.getString(i));
			i = mCursor.getColumnIndex(Entries.COLUMN_NAME_SPECTROGRAM);
			args.putString(mProjection[i], mCursor.getString(i));
			i = mCursor.getColumnIndex(Entries.COLUMN_NAME_USERNAME);
			args.putString(mProjection[i], mCursor.getString(i));
			i = mCursor.getColumnIndex(Entries.COLUMN_NAME_UUID);
			args.putString(mProjection[i], mCursor.getString(i));
			
			i = mCursor.getColumnIndex(Entries.COLUMN_NAME_LATITUDE);
			args.putFloat(mProjection[i], mCursor.getFloat(i));
			i = mCursor.getColumnIndex(Entries.COLUMN_NAME_LONGITUDE);
			args.putFloat(mProjection[i], mCursor.getFloat(i));
			
			i = mCursor.getColumnIndex(Entries.COLUMN_NAME_VOTE);
			args.putInt(mProjection[i], mCursor.getInt(i));
			
			i = mCursor.getColumnIndex(Entries.COLUMN_NAME_SCORE);
			args.putInt(mProjection[i], mCursor.getInt(i));
			
			i = mCursor.getColumnIndex(Entries.COLUMN_NAME_RECORDED);
			args.putString(mProjection[i], mCursor.getString(i));
			i = mCursor.getColumnIndex(Entries.COLUMN_NAME_CREATED);
			args.putString(mProjection[i], mCursor.getString(i));
			
			i = mCursor.getColumnIndex(Entries.COLUMN_NAME_UPLOADED);
			args.putInt(mProjection[i], mCursor.getInt(i));
			
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

	public static class EntryDetailFragment extends Fragment implements OnCheckedChangeListener {
		private SeekBar mSeekBar;
		private TextView mTVScore;
		private HttpImageManager mHttpImageManager;
		private RESTLoaderCallbacks mRESTLoaderCallback;

		private ToggleButton mToggleButtonVoteUp = null;
		private ToggleButton mToggleButtonVoteDown = null;
		private Boolean mCurrentLikes = null;
		private int mScore = 0;
		private int mScoreDiff = 0;
		
		public static EntryDetailFragment newInstance() {
			EntryDetailFragment f = new EntryDetailFragment();
			return f;
		}

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			this.mHttpImageManager = NoisetracksApplication.getHttpImageManager();
			this.mRESTLoaderCallback = new RESTLoaderCallbacks(getActivity(), this);
		}

		/**
		 * The Fragment's UI
		 */
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {

			View v = inflater.inflate(R.layout.entry_detail, container, false);
			ImageView mugshot = (ImageView) v.findViewById(R.id.entry_mugshot);
			TextView username = (TextView) v.findViewById(R.id.entry_username);
			TextView recordedAgo = (TextView) v
					.findViewById(R.id.entry_recorded_ago);
			ImageView spectrogram = (ImageView) v
					.findViewById(R.id.entry_spectrogram);
			mSeekBar = (SeekBar) v.findViewById(R.id.seekbar);
			mTVScore = (TextView) v.findViewById(R.id.score);
			mToggleButtonVoteUp = (ToggleButton) v
					.findViewById(R.id.vote_up);
			mToggleButtonVoteDown = (ToggleButton) v
					.findViewById(R.id.vote_down);

			mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
				@Override
				public void onStopTrackingTouch(SeekBar seekBar) {
				}

				@Override
				public void onStartTrackingTouch(SeekBar seekBar) {
				}

				@Override
				public void onProgressChanged(SeekBar sb, int progress,
						boolean fromUser) {
					if (fromUser) {
						player.seekTo(sb.getProgress()); // update player
															// position
					}
				}
			});

			mScore = getArguments().getInt(Entries.COLUMN_NAME_SCORE); // get the current score
			mTVScore.setText("Score: " + mScore);

			mToggleButtonVoteUp.setOnCheckedChangeListener(null);
			mToggleButtonVoteDown.setOnCheckedChangeListener(null);

			// Set initial states of the vote buttons based on user's past
			// actions
			int vote = getArguments().getInt(Entries.COLUMN_NAME_VOTE);
			if (vote == 0) {
				// User is currently neutral
				AppLog.logString("User gives a shit: " + vote);
				mCurrentLikes = null;
				mToggleButtonVoteUp.setChecked(false);
				mToggleButtonVoteDown.setChecked(false);
			} else if (vote == 1) {
				AppLog.logString("User likes: " + vote);
				// User currenty likes it
				mCurrentLikes = true;
				mToggleButtonVoteUp.setChecked(true);
				mToggleButtonVoteDown.setChecked(false);
			} else if (vote == -1) {
				// User currently dislikes it
				AppLog.logString("User dislikes: " + vote);
				mCurrentLikes = false;
				mToggleButtonVoteUp.setChecked(false);
				mToggleButtonVoteDown.setChecked(true);
			}
			
			mToggleButtonVoteUp.setOnCheckedChangeListener(this);
			mToggleButtonVoteDown.setOnCheckedChangeListener(this);

			// mugshot.setImageResource(R.drawable.default_image);
			String mug = getArguments().getString(Entries.COLUMN_NAME_MUGSHOT);
			if (mug != null) {
				Uri mugshotUri = Uri.parse(mug);
				if (mugshotUri != null) {
					Bitmap bitmap = mHttpImageManager
							.loadImage(new HttpImageManager.LoadRequest(
									mugshotUri, mugshot));
					if (bitmap != null) {
						mugshot.setImageBitmap(bitmap);
					}
				}
			}

			// spectrogram.setImageResource(R.drawable.default_image);
			String spect = getArguments().getString(Entries.COLUMN_NAME_SPECTROGRAM);
			if (spect != null) {
				Uri specUri = Uri.parse(spect);
				if (specUri != null) {
					Bitmap bitmap = mHttpImageManager
							.loadImage(new HttpImageManager.LoadRequest(
									specUri, spectrogram));
					if (bitmap != null) {
						spectrogram.setImageBitmap(bitmap);
					}

				}
			}

			String info = "<b>" + getArguments().getString(Entries.COLUMN_NAME_USERNAME) + "</b>" + "<br />"
					+ getArguments().getString(Entries.COLUMN_NAME_UUID) + "<br />"
					+ getArguments().getInt(Entries.COLUMN_NAME_VOTE);
			username.setText(Html.fromHtml(info));

			String recorded = getArguments().getString(Entries.COLUMN_NAME_RECORDED);
			if (recorded != null) {
				try {
					Date d = NoisetracksApplication.SDF.parse(recorded);
					String time = DateUtils.formatDateTime(getActivity(), d.getTime(), DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_12HOUR | DateUtils.FORMAT_CAP_AMPM);
					String date = DateUtils.formatDateTime(getActivity(), d.getTime(), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_ALL);
					recordedAgo.setText(time + Html.fromHtml("&nbsp;\u00B7&nbsp;") + date);
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
		
		public void onVoteComplete(String json) {
			/*
			int vote = 0;
			try {
				JSONObject resp = (JSONObject) new JSONTokener(json).nextValue();
				vote = Integer.parseInt(resp.getString("vote"));
			} catch (JSONException e) {
				return;
			}
			*/
			
			mScore += mScoreDiff; // add the new score difference
			
			mTVScore.setText("Score: " + mScore); // update score text view
			
			//mToggleButtonVoteUp.setOnCheckedChangeListener(this); // activate button listeners again
			//mToggleButtonVoteDown.setOnCheckedChangeListener(this);
			
		}

		private void vote(int vote) {
			JSONObject json = new JSONObject();
			try {
				json.put(Entries.COLUMN_NAME_UUID, getArguments().getString(Entries.COLUMN_NAME_UUID));
				json.put(Entries.COLUMN_NAME_VOTE, vote);
			} catch (JSONException e) {
				AppLog.logString(e.toString());
			}

			AppLog.logString(json.toString());

			Bundle params = new Bundle();
			params.putString("json", json.toString());
			Bundle args = new Bundle();
			args.putParcelable(RESTLoaderCallbacks.ARGS_URI, RESTLoaderCallbacks.URI_VOTE);
			args.putParcelable(RESTLoaderCallbacks.ARGS_PARAMS, params);

			getActivity().getSupportLoaderManager().restartLoader(NoisetracksApplication.VOTE_LOADER, args, mRESTLoaderCallback);
		}

		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			
			//mToggleButtonVoteUp.setOnCheckedChangeListener(null);
			//mToggleButtonVoteDown.setOnCheckedChangeListener(null);			
			
			if (mCurrentLikes != null) {
				if (mCurrentLikes == true && buttonView == mToggleButtonVoteDown) {
					mToggleButtonVoteUp.setOnCheckedChangeListener(null);
					mToggleButtonVoteUp.setChecked(!isChecked); // flip toggle
					mToggleButtonVoteUp.setOnCheckedChangeListener(this);
					mCurrentLikes = false; // dislike
					vote(-1); // vote down
					mScoreDiff = -2;
					return;
				}
				if (mCurrentLikes == true && buttonView == mToggleButtonVoteUp) {
					mCurrentLikes = null; // user is neutral again
					vote(0);
					mScoreDiff = -1;
					return;
				}
				if (mCurrentLikes == false && buttonView == mToggleButtonVoteDown) {
					mCurrentLikes = null; // user is neutral again
					vote(0);
					mScoreDiff = +1;
					return;
				}
				if (mCurrentLikes == false && buttonView == mToggleButtonVoteUp) {
					mToggleButtonVoteDown.setOnCheckedChangeListener(null);
					mToggleButtonVoteDown.setChecked(!isChecked); // flip toggle
					mToggleButtonVoteDown.setOnCheckedChangeListener(this);
					mCurrentLikes = true; // user likes
					vote(1);
					mScoreDiff = +2;
					return;
				}
			}
			else {
				if (mCurrentLikes == null && buttonView == mToggleButtonVoteDown) {
					mCurrentLikes = false; // user dislikes
					vote(-1);
					mScoreDiff = -1;
					return;
				}
				if (mCurrentLikes == null && buttonView == mToggleButtonVoteUp) {
					mCurrentLikes = true; // user likes
					vote(1);
					mScoreDiff = +1;
					return;
				}
			}
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
