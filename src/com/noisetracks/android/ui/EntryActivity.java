package com.noisetracks.android.ui;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.ParseException;
import java.util.Date;
import org.json.JSONException;
import org.json.JSONObject;
import org.scribe.model.Verb;

import com.noisetracks.android.NoisetracksApplication;
import com.noisetracks.android.R;
import com.noisetracks.android.audio.Sampler;
import com.noisetracks.android.audio.VorbisDecoder;
import com.noisetracks.android.client.NoisetracksRequest;
import com.noisetracks.android.client.SQLLoaderCallbacks;
import com.noisetracks.android.helper.FixedSpeedScroller;
import com.noisetracks.android.helper.httpimage.HttpImageManager;
import com.noisetracks.android.provider.NoisetracksProvider;
import com.noisetracks.android.provider.NoisetracksContract.Entries;
import com.noisetracks.android.utility.AppSettings;
import com.whiterabbit.postman.ServerInteractionHelper;
import com.whiterabbit.postman.ServerInteractionResponseInterface;
import com.whiterabbit.postman.exceptions.SendingCommandException;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.AudioTrack.OnPlaybackPositionUpdateListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
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
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;
import com.handmark.pulltorefresh.extras.viewpager.PullToRefreshViewPager;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;

public class EntryActivity extends SherlockFragmentActivity implements
		OnRefreshListener<ViewPager>, OnPageChangeListener, OnPlaybackPositionUpdateListener, OnCheckedChangeListener, OnSeekBarChangeListener {

	public static final String ID = "id";
	
	private static final String TAG = "EntryActivity";
	
	private Cursor mCursor;
	
	private static VorbisDecoder mVorbisDecoder;
	private static AudioTrack mTrack;
	
	/**
	 * Buffer size in bytes
	 * Calculation is: signal_duration_in_seconds * sample_rate * num_channels * 2.
	 * (Convert 16-bit values to bytes by multiplication of 2).
	 */
	private int mBufferSize;
	private static byte[] mBuffer;
	
	private int mPosition = 0;
	
	private static int mLoopStart;
	private static int mLoopEnd;
	private static int mDuration;
	
	private String mSelect;
	private PullToRefreshViewPager mPullToRefreshViewPager;
	private ViewPager mPager;
	private EntryPagerAdapter mAdapter;
	private static ImageButton mPlayBtn = null;
	private static ToggleButton mLoopBtn = null;
	private static TextView mTxtPitch;
	private static SeekBar mSeekBarPitch;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		ActionBar actionBar = getSupportActionBar();
	    actionBar.setDisplayHomeAsUpEnabled(true);
		
		setContentView(R.layout.entry_activity);

		mPlayBtn = (ImageButton) findViewById(R.id.entry_play_pause);
		mLoopBtn = (ToggleButton) findViewById(R.id.entry_loop);
		mLoopBtn.setOnCheckedChangeListener(this);
		
		mTxtPitch = (TextView) findViewById(R.id.txtPitch);
		mSeekBarPitch = (SeekBar) findViewById(R.id.seekbarPitch);
		mSeekBarPitch.setOnSeekBarChangeListener(this);
		
		mPullToRefreshViewPager = (PullToRefreshViewPager) findViewById(R.id.entry_activity_pull_refresh_view_pager);
		mPullToRefreshViewPager.setOnRefreshListener(this);
		mPager = mPullToRefreshViewPager.getRefreshableView();

		mSelect = getIntent().getExtras().getString(SQLLoaderCallbacks.SELECT);
		
		mCursor = getContentResolver().query(
				Entries.CONTENT_URI,
				NoisetracksProvider.READ_ENTRY_PROJECTION,
				mSelect,
				null,
				Entries.DEFAULT_SORT_ORDER);
		
		// Move cursor to right position and set view pager position
		long id = getIntent().getLongExtra(ID, -1);						// sql _id of the selected entry
		int item = -1;												// position in view pager used later
		
		if (mCursor != null) {
			mCursor.moveToPosition(item);
			while (mCursor.moveToNext()) {
				item++;
				if (mCursor.getLong(mCursor.getColumnIndex(Entries._ID)) == id) {
					// we got the right one ... cursor is at correct position and 'position' can be used for pager
					break;
				}
			}
		} else {
			Log.e(TAG, "Cursor is invalid.");
			finish();
		}

		// Set adapter
		mAdapter = new EntryPagerAdapter(this, getSupportFragmentManager(), mCursor) {
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
		
		// Listen to page changes and select current page based on position calculated above
		mPager.setOnPageChangeListener(this);
		mPager.setCurrentItem(item, false);
				
		if (mPager.getCurrentItem() == 0) 
			onPageSelected(mPager.getCurrentItem()); // for some reason, onPageSelected is not called on the first item. We do it here manually.

	}

	@Override
	protected void onPause() {
		Log.d(TAG, "onPause");
		super.onPause();

		if (mTrack != null) {
			if (mTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
				mTrack.pause();
				if (isFinishing()) {
					mTrack.setPlaybackPositionUpdateListener(null);
					mTrack.stop();
					mTrack.release();
				}
			}
		}
		
		if (mCursor!= null && !mCursor.isClosed()) {
			mCursor.close();
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		requery();
	}

	
	/*
	 * Button click handler for 'play'
	 */
	public void play(View view) {
		
		if (mTrack != null) {
	 		if (mTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
				try {
					Log.d(TAG, "pause");
					mTrack.pause(); mPosition = mTrack.getPlaybackHeadPosition();
					mPlayBtn.setImageResource(R.drawable.av_play);
				} catch (IllegalStateException e) {
					Log.e(TAG, "Error on pause(): " + e.toString());
				}
			} else {
				try {
					Log.d(TAG, "play");
					mTrack.setPlaybackHeadPosition(mPosition); mTrack.play();
					mPlayBtn.setImageResource(R.drawable.av_pause);
				} catch (IllegalStateException e) {
					Log.e(TAG, "Error on play(): " + e.toString());
				}
			}
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
	
	/*
	 * Loop clicked
	 */
	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {		
		setLoop(isChecked);
	}
	

	/*
	 * Pitch changed
	 */
	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		if (fromUser) {
			setPitch(progress-100); // note: progress bar value is 0 ... 200, pitch is -100 ... +100
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {}

	@Override
	public void onPageScrollStateChanged(int state) {}

	@Override
	public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

	@Override
	public void onPageSelected(int position) {

		Log.v(TAG, "onPageSelected " + position);
		
		mAdapter.getCursor().moveToPosition(position);

		if (mTrack != null) {
			try {
				Log.d(TAG, "pause");
				mTrack.pause();
				mPlayBtn.setImageResource(R.drawable.av_play);
			} catch (IllegalStateException e) {
				Log.e(TAG, "Error on pause(): " + e.toString());
			}
		}
		
		String base = Environment.getExternalStorageDirectory().getPath() + "/Noisetracks";
		String filepath = mAdapter.getCursor().getString(mAdapter.getCursor().getColumnIndex(Entries.COLUMN_NAME_FILENAME));
		File ogg = new File(base + filepath);
		try {
			mVorbisDecoder = new VorbisDecoder(ogg);
		} catch (IOException e) {
			Log.e(TAG, "Could not open ogg file: " + e.toString());
		}
		
		Log.i(TAG, "Length: " + mVorbisDecoder.getTimeLength() / 1000.0f + " Rate: " + mVorbisDecoder.getRate() + " Channels: " + mVorbisDecoder.getChannels());
		
		mBufferSize = (int)( (int)(Math.ceil(mVorbisDecoder.getTimeLength() / 1000.0f)) * mVorbisDecoder.getRate() * mVorbisDecoder.getChannels() * 2);
		
		mBuffer = new byte[mBufferSize];
		
		
		try {
			mVorbisDecoder.read(mBuffer, 0, mBufferSize);
		} catch (IOException e) {
			Log.e(TAG, "Error reading: " + e.toString());
		}
		
		mTrack = new AudioTrack(
        		AudioManager.STREAM_MUSIC,
        		Sampler.SAMPLERATE,
        		AudioFormat.CHANNEL_OUT_STEREO,
        		AudioFormat.ENCODING_PCM_16BIT,
        		mBufferSize,  
        		AudioTrack.MODE_STATIC);
		
		mTrack.write(mBuffer, 0, mBuffer.length);
		
		mDuration = (int)(Math.floor(mVorbisDecoder.getTimeLength() / 1000.0f * Sampler.SAMPLERATE));
		mTrack.setNotificationMarkerPosition(mDuration-1);
		mTrack.setPositionNotificationPeriod(64);
		mTrack.setPlaybackPositionUpdateListener(this);
		
		
		Log.d(TAG, "play");
		mTrack.play();
		mPlayBtn.setImageResource(R.drawable.av_pause);
		
		Log.i(TAG, "length is: " + mDuration);

	}
	
	public static void setLoop(boolean looping) {
		
		mLoopBtn.setChecked(looping);
		
		if (mLoopEnd == 0) {
			mLoopEnd = mDuration -1;
		}
		
		if (looping) {
			
			mTrack.pause();
			mTrack.setPlaybackHeadPosition(mLoopStart);
			mTrack.setNotificationMarkerPosition(mLoopEnd);
			//mTrack.setPlaybackPositionUpdateListener(this);
			
			mTrack.play();
			mPlayBtn.setImageResource(R.drawable.av_pause);
			
			
		} else {
			mTrack.setPlaybackHeadPosition(0);
			mTrack.setNotificationMarkerPosition(mDuration-1);
			//mTrack.setPlaybackPositionUpdateListener(this);
		}
	}
	
	public static void setPitch(int pitch) {
		
		mTxtPitch.setText((pitch > 0 ? "+" : "") + Integer.toString(pitch) + "%");
		mSeekBarPitch.setProgress(pitch+100);
		
		if (mTrack.setPlaybackRate(
				AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC) + 
				AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC) * pitch / 100) != AudioTrack.SUCCESS) {
			Log.w(TAG, "Could not change pitch.");
		}
	}
	
	@Override
	public void onMarkerReached(AudioTrack track) {
		
		if (track != null) {
			try {
				mTrack.pause();
				if (mLoopBtn.isChecked()) {
					mTrack.setPlaybackHeadPosition(mLoopStart);
					mTrack.setNotificationMarkerPosition(mLoopEnd);
					mTrack.setPlaybackPositionUpdateListener(this);
					mTrack.play();
					mPlayBtn.setImageResource(R.drawable.av_pause);
				} else {
					mPosition = 0;
					mTrack.setPlaybackHeadPosition(mPosition);
					mTrack.setNotificationMarkerPosition(mDuration - 1);
					mTrack.setPlaybackPositionUpdateListener(this);
					mPlayBtn.setImageResource(R.drawable.av_play);
				}
			} catch (IllegalStateException e) {
				Log.e(TAG, "Error onMarkerReached(): " + e.toString());
			}
		}
	}

	@Override
	public void onPeriodicNotification(AudioTrack track) {
		try {
			EntryDetailFragment f = (EntryDetailFragment)mAdapter.getFragmentAtPosition(mPager.getCurrentItem());
			f.mSeekBar.setProgress(track.getPlaybackHeadPosition());
		} catch (NullPointerException e) {
			Log.w(TAG, "Could not get fragment: " + e.toString());
		} catch (IllegalStateException e) {
			Log.w(TAG, "Warning: " + e.toString());
		}
	}
	

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

	
	public void requery() {
		// requery
		Cursor newCursor = getContentResolver().query(
				Entries.CONTENT_URI,
				NoisetracksProvider.READ_ENTRY_PROJECTION,
				mSelect,
				null,
				Entries.DEFAULT_SORT_ORDER);
		
		// update cursor
		mAdapter.changeCursor(newCursor);
	}
	
	public void onDeleteEntry() {
		Log.v(TAG, "onDeleteEntry");
		requery();
		if (mAdapter.getCount() > 0)
			onPageSelected(mPager.getCurrentItem()); 	// reset player
		else 
			finish();									// no items left, finish..
	}

	
	/**
	 *  EntryDetailFragment defines the view of EntryActivity
	 */
	public static class EntryDetailFragment extends Fragment implements OnCheckedChangeListener, ServerInteractionResponseInterface, OnSeekBarChangeListener {
		ServerInteractionHelper mServerHelper;
		
		private SeekBar mSeekBar;
		private SeekBar mSeekBarLoopStart;
		private SeekBar mSeekBarLoopEnd;
		
		private boolean mIsLooping;
		private int mPitch;
		
		private TextView mTVScore;
		private HttpImageManager mHttpImageManager;

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
			args.putInt(Entries.COLUMN_NAME_LOOP, cursor.getInt(cursor.getColumnIndex(Entries.COLUMN_NAME_LOOP)));
			args.putInt(Entries.COLUMN_NAME_LOOP_START, cursor.getInt(cursor.getColumnIndex(Entries.COLUMN_NAME_LOOP_START)));
			args.putInt(Entries.COLUMN_NAME_LOOP_END, cursor.getInt(cursor.getColumnIndex(Entries.COLUMN_NAME_LOOP_END)));
			args.putInt(Entries.COLUMN_NAME_PITCH, cursor.getInt(cursor.getColumnIndex(Entries.COLUMN_NAME_PITCH)));
			
			f.setArguments(args);
			return f;
		}

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			this.mHttpImageManager = NoisetracksApplication.getHttpImageManager();
			
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
			
			mIsLooping = getArguments() != null ? getArguments().getInt(Entries.COLUMN_NAME_LOOP) == 1 : false;
			
			mLoopStart = getArguments() != null ? getArguments().getInt(Entries.COLUMN_NAME_LOOP_START) : 0;
			mLoopEnd = getArguments() != null ? getArguments().getInt(Entries.COLUMN_NAME_LOOP_END) : 0;
			
			
			mPitch = getArguments() != null ? getArguments().getInt(Entries.COLUMN_NAME_PITCH) : 0;
			
		}

		/**
		 * The Fragment's UI
		 */
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			Log.v(TAG, "Fragment onCreateView");
			mServerHelper = ServerInteractionHelper.getInstance(getActivity());

			View v = inflater.inflate(R.layout.entry_detail, container, false);
			ImageView mugshot = (ImageView) v.findViewById(R.id.entry_mugshot);
			TextView username = (TextView) v.findViewById(R.id.entry_username);
			TextView recordedAgo = (TextView) v.findViewById(R.id.entry_recorded_ago);
			ImageView spectrogram = (ImageView) v.findViewById(R.id.entry_spectrogram);
			ImageButton trash = (ImageButton) v.findViewById(R.id.entry_delete);
			mTVScore = (TextView) v.findViewById(R.id.score);
			mToggleButtonVoteUp = (ToggleButton) v.findViewById(R.id.vote_up);
			mToggleButtonVoteDown = (ToggleButton) v.findViewById(R.id.vote_down);

			mSeekBar = (SeekBar) v.findViewById(R.id.seekbar);
			mSeekBar.setOnSeekBarChangeListener(this);
			mSeekBar.setMax(mDuration);
			
			mSeekBarLoopStart = (SeekBar) v.findViewById(R.id.seekbarLoopStart);
			mSeekBarLoopStart.setOnSeekBarChangeListener(this);
			mSeekBarLoopStart.setMax(mDuration - 2);
			mSeekBarLoopStart.setProgress(mLoopStart);
			
			mSeekBarLoopEnd = (SeekBar) v.findViewById(R.id.seekbarLoopEnd);
			mSeekBarLoopEnd.setOnSeekBarChangeListener(this);
			mSeekBarLoopEnd.setMax(mDuration - 1);
			mSeekBarLoopEnd.setProgress(mLoopEnd);
			
			setLoop(mIsLooping); // TODO test if OK
			
			setPitch(mPitch);

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
					String time = DateUtils.formatDateTime(getActivity(), d.getTime(), DateUtils.FORMAT_SHOW_TIME);
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
								// delete entry from server
								NoisetracksRequest r = new NoisetracksRequest(
										Verb.DELETE,
										Uri.parse(NoisetracksApplication.DOMAIN + mResourceUri),
										new Bundle());
				                try {
				                    mServerHelper.sendRestAction(getActivity(), "DELETE", r);
				                } catch (SendingCommandException e) {
				                    Log.e(TAG, e.toString());
				                }
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
			 mServerHelper.registerEventListener(this, getActivity());
		}

		@Override
		public void onDestroy() {
			super.onDestroy();
		}
		
		@Override
		public void onResume() {
			mServerHelper.registerEventListener(this, getActivity());
			super.onResume();
		}
		
		@Override
		public void onPause() {
			mServerHelper.unregisterEventListener(this, getActivity());
			super.onPause();
		}
		
		@Override
		public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			if (fromUser) {
				try {
					if (seekBar.getId() == R.id.seekbar) {
						mTrack.pause();
						mTrack.setPlaybackHeadPosition(progress);
						mTrack.play();
						mPlayBtn.setImageResource(R.drawable.av_pause);
					} else if (seekBar.getId() == R.id.seekbarLoopStart) {
						mLoopStart = progress;
						setLoop(true);
					} else if (seekBar.getId() == R.id.seekbarLoopEnd) {
						mLoopEnd = progress;
						setLoop(true);
					} 
				} catch (IllegalStateException e) {
					Log.e(TAG, "Error onProgressChanged(): " + e.toString());
				}
			}
		}

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) { }

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) { }
		
		@Override
		public void onServerResult(String result, String requestId) {
			
			if (requestId.equals("VOTE")) {
			
				Log.d(TAG, "Update vote.");
				
				
			} else if (requestId.equals("DELETE")) {
				
				((EntryActivity)getActivity()).onDeleteEntry();
			}
		}

		@Override
		public void onServerError(String result, String requestId) {
			Log.w(TAG, "Could not complete request for " + requestId + ". " + result);
			Toast.makeText(getActivity(), "Error: " + result, Toast.LENGTH_SHORT).show();
		}

		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			
			mScoreDiff = 0;
			
			if (mCurrentLikes != null) {
				if (mCurrentLikes == true && buttonView == mToggleButtonVoteDown) {
					mToggleButtonVoteUp.setOnCheckedChangeListener(null);
					mToggleButtonVoteUp.setChecked(!isChecked); // flip toggle
					mToggleButtonVoteUp.setOnCheckedChangeListener(this);
					mCurrentLikes = false; // dislike
					vote(-1); // vote down
					mScoreDiff = -2;
					
				} else if (mCurrentLikes == true && buttonView == mToggleButtonVoteUp) {
					mCurrentLikes = null; // user is neutral again
					vote(0);
					mScoreDiff = -1;
					
				} else if (mCurrentLikes == false && buttonView == mToggleButtonVoteDown) {
					mCurrentLikes = null; // user is neutral again
					vote(0);
					mScoreDiff = +1;
					
				} else if (mCurrentLikes == false && buttonView == mToggleButtonVoteUp) {
					mToggleButtonVoteDown.setOnCheckedChangeListener(null);
					mToggleButtonVoteDown.setChecked(!isChecked); // flip toggle
					mToggleButtonVoteDown.setOnCheckedChangeListener(this);
					mCurrentLikes = true; // user likes
					vote(1);
					mScoreDiff = +2;
					
				}
			}
			else {
				if (mCurrentLikes == null && buttonView == mToggleButtonVoteDown) {
					mCurrentLikes = false; // user dislikes
					vote(-1);
					mScoreDiff = -1;
					
				} else if (mCurrentLikes == null && buttonView == mToggleButtonVoteUp) {
					mCurrentLikes = true; // user likes
					vote(1);
					mScoreDiff = +1;
					
				}
			}
			
			mScore += mScoreDiff; // add the new score difference
			mTVScore.setText("Score: " + mScore); // update score text view
		}
		
		/**
		 * Posts vote to the server and then gets entry from server with updated values and stores it in the db.
		 * The current cursor is not updated (would cause flashing of view, which is ugly).
		 * This means: The score is reflecting only current user actions and not synced with the db immediately.
		 * @param vote The vote, can be +1, -1 or 0.
		 */
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
			
			NoisetracksRequest r = new NoisetracksRequest(Verb.POST, NoisetracksApplication.URI_VOTE, params);
            try {
                mServerHelper.sendRestAction(getActivity(), "VOTE", r);
            } catch (SendingCommandException e) {
                Log.e(TAG, e.toString());
            }
			
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
