package com.stahlnow.noisetracks.ui;

import java.io.IOException;
import java.lang.reflect.Field;
import java.text.ParseException;
import java.util.Date;
import org.json.JSONException;
import org.json.JSONObject;
import com.stahlnow.noisetracks.NoisetracksApplication;
import com.stahlnow.noisetracks.R;
import com.stahlnow.noisetracks.client.RESTLoaderCallbacks;
import com.stahlnow.noisetracks.client.SQLLoaderCallbacks;
import com.stahlnow.noisetracks.helper.FixedSpeedScroller;
import com.stahlnow.noisetracks.helper.httpimage.HttpImageManager;
import com.stahlnow.noisetracks.provider.NoisetracksProvider;
import com.stahlnow.noisetracks.provider.NoisetracksContract.Entries;
import com.stahlnow.noisetracks.utility.AppSettings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
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
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.text.Html;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
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

	private static final String TAG = "EntryActivity";
	public static final String ID = "id";
	private static final int UPDATE_FREQUENCY = 1;
	
	private final Handler handler = new Handler();
	private static MediaPlayer player;
	private String mSelect;
	private PullToRefreshViewPager mPullToRefreshViewPager;
	private ViewPager mPager;
	private EntryPagerAdapter mAdapter;
	private ImageButton mPlayBtn = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		setContentView(R.layout.entry_activity);

		mPlayBtn = (ImageButton) findViewById(R.id.entry_play_pause);

		mPullToRefreshViewPager = (PullToRefreshViewPager) findViewById(R.id.entry_activity_pull_refresh_view_pager);
		mPullToRefreshViewPager.setOnRefreshListener(this);
		mPager = mPullToRefreshViewPager.getRefreshableView();

		mSelect = getIntent().getExtras().getString(SQLLoaderCallbacks.SELECT);
		
		Cursor c = getContentResolver().query(
				Entries.CONTENT_URI,
				NoisetracksProvider.READ_ENTRY_PROJECTION,
				mSelect,
				null,
				Entries.DEFAULT_SORT_ORDER);
		
		// Move cursor to right position and set view pager position
		long id = getIntent().getLongExtra(ID, -1);						// sql _id of the selected entry
		int position = -1;												// position in view pager used later
		
		if (c != null) {
			c.moveToPosition(-1);
			while (c.moveToNext()) {
				position++;
				if (c.getLong(c.getColumnIndex(Entries._ID)) == id) {
					// we got the right one ... cursor is at correct position and 'position' can be used for pager
					break;
				}
			}
		} else {
			Log.e(TAG, "Cursor is invalid.");
			finish();
		}

		// Set adapter
		mAdapter = new EntryPagerAdapter(this, getSupportFragmentManager(), c) {
			@Override
			public Fragment getItem(Context context, Cursor cursor) {
				
				EntryDetailFragment f;
				try {
					f = EntryDetailFragment.newInstance(cursor);
				} catch (Exception ex) {
					throw new RuntimeException(ex);
				}
				return f;				
			}
		};
		mPager.setAdapter(mAdapter);

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

		// Create media player
		player = new MediaPlayer();
		player.setOnPreparedListener(this);
		player.setOnCompletionListener(this);
		player.setOnErrorListener(this);
		player.setAudioStreamType(AudioManager.STREAM_MUSIC);
		
		// Listen to page changes and select current page based on position calculated above
		mPager.setOnPageChangeListener(this);
		mPager.setCurrentItem(position, false);
		onPageSelected(mPager.getCurrentItem());
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		// Stop the seekbar handler from sending updates to UI
		handler.removeCallbacks(updatePositionRunnable);

		if (player != null) {
			if (player.isPlaying()) {
				player.stop();
			}
			player.reset();
			player.release();
	
			player = null;
		}
	}

	/*
	 * Button click handler for 'play'
	 */
	public void play(View view) {
		try {
			if (player.isPlaying()) {
				handler.removeCallbacks(updatePositionRunnable);
				player.pause();
				mPlayBtn.setImageResource(R.drawable.av_play);
			} else {
				player.start();
				mPlayBtn.setImageResource(R.drawable.av_pause);
				updatePosition();
			}
		} catch (NullPointerException e) {
			// oops, player is null..
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
	public void onPageScrollStateChanged(int state) {}

	@Override
	public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

	@Override
	public void onPageSelected(int position) {

		Log.v(TAG, "onPageSelected " + position);
		
		mAdapter.getCursor().moveToPosition(position);

		if (player.isPlaying()) {
			player.stop();
		}
		player.reset();

		try {
			player.setDataSource(mAdapter.getCursor().getString(mAdapter.getCursor().getColumnIndex(Entries.COLUMN_NAME_FILENAME)));
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
			EntryDetailFragment f = (EntryDetailFragment)mAdapter.getFragmentAtPosition(mPager.getCurrentItem());
			f.mSeekBar.setProgress(0);
		} catch (NullPointerException e) {
			Log.w(TAG, "onPageSelected: could not get fragment: " + e.toString());
		}

		// buffer and start media playback
		player.prepareAsync();

	}

	@Override
	public void onPrepared(MediaPlayer player) {
		if (!player.isPlaying()) {
			player.start();
			mPlayBtn.setImageResource(R.drawable.av_pause);

			try {
				EntryDetailFragment f = (EntryDetailFragment)mAdapter.getFragmentAtPosition(mPager.getCurrentItem());
				f.mSeekBar.setMax(player.getDuration());
				updatePosition();
			} catch (NullPointerException e) {
				Log.w(TAG, "Could not get fragment: " + e.toString());
			}
		}
	}

	private void updatePosition() {
		handler.removeCallbacks(updatePositionRunnable);
		try {
			EntryDetailFragment f = (EntryDetailFragment)mAdapter.getFragmentAtPosition(mPager.getCurrentItem());
			f.mSeekBar.setProgress(player.getCurrentPosition());
		} catch (NullPointerException e) {
			Log.w(TAG, "Could not get fragment: " + e.toString());
		}

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
			Intent parentActivityIntent = new Intent(this, Tabs.class);
			parentActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(parentActivityIntent);
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		mPlayBtn.setImageResource(R.drawable.av_play);
		try {
			EntryDetailFragment f = (EntryDetailFragment)mAdapter.getFragmentAtPosition(mPager.getCurrentItem());
			f.mSeekBar.setProgress(player.getDuration());
		} catch (NullPointerException e) {
			Log.w(TAG, "Could not get fragment: " + e.toString());
		}
		handler.removeCallbacks(updatePositionRunnable);
		// auto move to next item TODO: add as an option in preferences
		//mPager.setCurrentItem(mPager.getCurrentItem() + 1, true);
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
	
	public void deleteEntry(String resourceUri, String uuid) {
		// delete entry from local db
		getContentResolver().delete(Entries.CONTENT_URI, Entries.COLUMN_NAME_UUID + " = '" + uuid + "'", null);
		
		// requery
		Cursor newCursor = getContentResolver().query(
				Entries.CONTENT_URI,
				NoisetracksProvider.READ_ENTRY_PROJECTION,
				mSelect,
				null,
				Entries.DEFAULT_SORT_ORDER);
		
		// update cursor
		mAdapter.changeCursor(newCursor);

		// delete entry from server
		RESTLoaderCallbacks r = new RESTLoaderCallbacks(this, this);
    	Bundle args = new Bundle();
    	args.putParcelable(RESTLoaderCallbacks.ARGS_URI, Uri.parse(NoisetracksApplication.DOMAIN + resourceUri));
    	args.putParcelable(RESTLoaderCallbacks.ARGS_PARAMS, new Bundle());
    	getSupportLoaderManager().restartLoader(NoisetracksApplication.DELETE_LOADER, args, r);	
		
	}

	public void onDeleteEntry(String uuid) {
		if (mAdapter.getCount() > 0)
			onPageSelected(mPager.getCurrentItem()); 	// reset player
		else 
			finish();									// no items left, finish..
	}

	
	/**
	 *  EntryDetailFragment defines the view of EntryActivity
	 */
	public static class EntryDetailFragment extends Fragment implements OnCheckedChangeListener {
		private SeekBar mSeekBar;
		private TextView mTVScore;
		private HttpImageManager mHttpImageManager;
		private RESTLoaderCallbacks mRESTLoaderCallbackVote;

		private ToggleButton mToggleButtonVoteUp = null;
		private ToggleButton mToggleButtonVoteDown = null;
		private Boolean mCurrentLikes = null;
		
		private int mScoreDiff = 0;
		
		// user variables
		@SuppressWarnings("unused")
		private String mFilname;
		private String mResourceUri;
		private String mMugshot;
		private String mSpectrogram;
		private String mUsername;
		private String mUuid;
		private String mRecorded;
		@SuppressWarnings("unused")
		private String mCreated;
		
		@SuppressWarnings("unused")
		private Float mLatitude;
		@SuppressWarnings("unused")
		private Float mLongitude;
		
		private int mVote;
		private int mScore;
		
		@SuppressWarnings("unused")
		private int mType;
		
		
		public static EntryDetailFragment newInstance(Cursor cursor) {
			
			EntryDetailFragment f = new EntryDetailFragment();
			
			Bundle args = new Bundle();
			args.putString(Entries.COLUMN_NAME_FILENAME, cursor.getString(cursor.getColumnIndex(Entries.COLUMN_NAME_FILENAME)));
			args.putString(Entries.COLUMN_NAME_RESOURCE_URI, cursor.getString(cursor.getColumnIndex(Entries.COLUMN_NAME_RESOURCE_URI)));
			args.putString(Entries.COLUMN_NAME_MUGSHOT, cursor.getString(cursor.getColumnIndex(Entries.COLUMN_NAME_MUGSHOT)));
			args.putString(Entries.COLUMN_NAME_SPECTROGRAM, cursor.getString(cursor.getColumnIndex(Entries.COLUMN_NAME_SPECTROGRAM)));
			args.putString(Entries.COLUMN_NAME_USERNAME, cursor.getString(cursor.getColumnIndex(Entries.COLUMN_NAME_USERNAME)));
			args.putString(Entries.COLUMN_NAME_UUID, cursor.getString(cursor.getColumnIndex(Entries.COLUMN_NAME_UUID)));
			args.putString(Entries.COLUMN_NAME_RECORDED, cursor.getString(cursor.getColumnIndex(Entries.COLUMN_NAME_RECORDED)));
			args.putString(Entries.COLUMN_NAME_CREATED, cursor.getString(cursor.getColumnIndex(Entries.COLUMN_NAME_CREATED)));
			args.putFloat(Entries.COLUMN_NAME_LATITUDE, cursor.getFloat(cursor.getColumnIndex(Entries.COLUMN_NAME_LATITUDE)));
			args.putFloat(Entries.COLUMN_NAME_LONGITUDE, cursor.getFloat(cursor.getColumnIndex(Entries.COLUMN_NAME_LONGITUDE)));
			args.putInt(Entries.COLUMN_NAME_VOTE, cursor.getInt(cursor.getColumnIndex(Entries.COLUMN_NAME_VOTE)));
			args.putInt(Entries.COLUMN_NAME_SCORE, cursor.getInt(cursor.getColumnIndex(Entries.COLUMN_NAME_SCORE)));
			args.putInt(Entries.COLUMN_NAME_TYPE, cursor.getInt(cursor.getColumnIndex(Entries.COLUMN_NAME_TYPE)));
			
			f.setArguments(args);
			return f;
		}

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			this.mHttpImageManager = NoisetracksApplication.getHttpImageManager();
			this.mRESTLoaderCallbackVote = new RESTLoaderCallbacks(getActivity(), this);
			
			
			mFilname = getArguments() != null ? getArguments().getString(Entries.COLUMN_NAME_FILENAME) : null;
			mResourceUri = getArguments() != null ? getArguments().getString(Entries.COLUMN_NAME_RESOURCE_URI) : null;
			mMugshot = getArguments() != null ? getArguments().getString(Entries.COLUMN_NAME_MUGSHOT) : null;
			mSpectrogram = getArguments() != null ? getArguments().getString(Entries.COLUMN_NAME_SPECTROGRAM) : null;
			mUsername = getArguments() != null ? getArguments().getString(Entries.COLUMN_NAME_USERNAME) : null;
			mUuid = getArguments() != null ? getArguments().getString(Entries.COLUMN_NAME_UUID) : null;
			mRecorded = getArguments() != null ? getArguments().getString(Entries.COLUMN_NAME_RECORDED) : null;
			mCreated = getArguments() != null ? getArguments().getString(Entries.COLUMN_NAME_CREATED) : null;
			 
			mLatitude = getArguments() != null ? getArguments().getFloat(Entries.COLUMN_NAME_LATITUDE) : 0.0f;
			mLongitude = getArguments() != null ? getArguments().getFloat(Entries.COLUMN_NAME_LONGITUDE) : 0.0f;
			 
			mVote = getArguments() != null ? getArguments().getInt(Entries.COLUMN_NAME_VOTE) : null;
			mScore = getArguments() != null ? getArguments().getInt(Entries.COLUMN_NAME_SCORE) : null;
			mType = getArguments() != null ? getArguments().getInt(Entries.COLUMN_NAME_TYPE) : null;
			 
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
			ImageButton trash = (ImageButton) v.findViewById(R.id.entry_delete);
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
				public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
					if (fromUser) {
						player.seekTo(sb.getProgress()); // update player position
					}
				}
			});

			mTVScore.setText("Score: " + mScore);

			mToggleButtonVoteUp.setOnCheckedChangeListener(null);
			mToggleButtonVoteDown.setOnCheckedChangeListener(null);

			// Set initial states of the vote buttons based on user's past
			// actions
			if (mVote == 0) {
				// User is currently neutral
				mCurrentLikes = null;
				mToggleButtonVoteUp.setChecked(false);
				mToggleButtonVoteDown.setChecked(false);
			} else if (mVote == 1) {
				// User currently likes it
				mCurrentLikes = true;
				mToggleButtonVoteUp.setChecked(true);
				mToggleButtonVoteDown.setChecked(false);
			} else if (mVote == -1) {
				// User currently dislikes it
				mCurrentLikes = false;
				mToggleButtonVoteUp.setChecked(false);
				mToggleButtonVoteDown.setChecked(true);
			}
			
			mToggleButtonVoteUp.setOnCheckedChangeListener(this);
			mToggleButtonVoteDown.setOnCheckedChangeListener(this);

			// mugshot.setImageResource(R.drawable.default_image);
			if (mMugshot != null) {
				Uri mugshotUri = Uri.parse(mMugshot);
				if (mugshotUri != null) {
					Bitmap bitmap = mHttpImageManager
							.loadImage(new HttpImageManager.LoadRequest(
									mugshotUri, mugshot));
					if (bitmap != null) {
						mugshot.setImageBitmap(bitmap);
					}
				}
			}
			
			mugshot.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					// mugshot was clicked, open profile
					Intent i = new Intent(getActivity(), ProfileActivity.class);
					i.putExtra("username", mUsername);
					startActivity(i);
				}
			});

			// spectrogram.setImageResource(R.drawable.default_image);
			if (mSpectrogram != null) {
				Uri specUri = Uri.parse(mSpectrogram);
				if (specUri != null) {
					Bitmap bitmap = mHttpImageManager
							.loadImage(new HttpImageManager.LoadRequest(
									specUri, spectrogram));
					if (bitmap != null) {
						spectrogram.setImageBitmap(bitmap);
					}

				}
			}

			String info = "<b>" + mUsername + "</b>";
			username.setText(Html.fromHtml(info));

			if (mRecorded != null) {
				try {
					Date d = NoisetracksApplication.SDF.parse(mRecorded);
					String time = DateUtils.formatDateTime(getActivity(), d.getTime(), DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_12HOUR | DateUtils.FORMAT_CAP_AMPM);
					String date = DateUtils.formatDateTime(getActivity(), d.getTime(), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_ALL);
					recordedAgo.setText(time + Html.fromHtml("&nbsp;\u00B7&nbsp;") + date);
				} catch (ParseException e) {
					Log.e(TAG, "Failed to parse recorded date: " + e.toString());
				}
			}
			
			
			if (mUsername.equals(AppSettings.getUsername(getActivity()))) {
				trash.setVisibility(View.VISIBLE);
				trash.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						AlertDialog.Builder del = new AlertDialog.Builder(getActivity());
						del.setTitle("Delete");
						del.setMessage("Do you want to delete this Noise?");
						del.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								((EntryActivity)getActivity()).deleteEntry(mResourceUri, mUuid);
							}
						});								
						del.setNegativeButton("No", new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								// no action needed
							}
						});
						del.show();
					}
				});
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
		}
		

		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			
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
		
		private void vote(int vote) {
			JSONObject json = new JSONObject();
			try {
				json.put(Entries.COLUMN_NAME_UUID, mUuid);
				json.put(Entries.COLUMN_NAME_VOTE, vote);
			} catch (JSONException e) {
				Log.e(TAG, e.toString());
			}

			Bundle params = new Bundle();
			params.putString("json", json.toString());
			Bundle args = new Bundle();
			args.putParcelable(RESTLoaderCallbacks.ARGS_URI, NoisetracksApplication.URI_VOTE);
			args.putParcelable(RESTLoaderCallbacks.ARGS_PARAMS, params);

			getActivity().getSupportLoaderManager().restartLoader(NoisetracksApplication.VOTE_LOADER, args, mRESTLoaderCallbackVote);
		}

	}

	private class GetDataTask extends AsyncTask<Void, Void, Void> {

		@Override
		protected Void doInBackground(Void... params) {
			// Simulates a background job.
			try {
				Thread.sleep(500);
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
