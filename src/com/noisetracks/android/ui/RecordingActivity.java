package com.noisetracks.android.ui;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.location.Location;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaPlayer.OnSeekCompleteListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;

import com.noisetracks.android.NoisetracksApplication;
import com.noisetracks.android.R;
import com.noisetracks.android.audio.Sampler;
import com.noisetracks.android.audio.WaveForm;
import com.noisetracks.android.audio.Sampler.SamplerListener;
import com.noisetracks.android.helper.Helper;
import com.noisetracks.android.helper.MyLocation;
import com.noisetracks.android.helper.MyLocation.LocationResult;
import com.noisetracks.android.provider.NoisetracksContract.Entries;
import com.noisetracks.android.utility.AppSettings;

public class RecordingActivity extends SherlockActivity implements
	OnPreparedListener, OnErrorListener, OnCompletionListener, OnSeekCompleteListener {
	
	public static final String EXTRA_URI = "uri";
	
	private static final String TAG = "RecordingActivity";

	private Context mContext;
	
	private MyLocation mMyLocation = new MyLocation();
	
	private WaveForm mWaveForm;

	private Sampler mSampler;
	private SeekBar mSeekBarTop;
	private SeekBar mSeekBarBottom;
	private Button mBtnNext;
	private Button mBtnDelete;
	private ImageButton mBtnPlay;
	
	private MediaPlayer mPlayer;
	private final Handler mStopRecHandler = new Handler();
	private final Handler mPositionHandler = new Handler();
	
	private Uri mEntry;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mContext = this;
		
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		setContentView(R.layout.recording_activity);
		
		mWaveForm = (WaveForm)findViewById(R.id.waveform);
		mSeekBarTop = (SeekBar)findViewById(R.id.rec_seekbar_top);
		mSeekBarBottom = (SeekBar)findViewById(R.id.rec_seekbar_bottom);
		mSeekBarTop.setMax(NoisetracksApplication.MAX_RECORDING_DURATION_SECONDS * Sampler.SAMPLERATE);
		mSeekBarBottom.setMax(NoisetracksApplication.MAX_RECORDING_DURATION_SECONDS * Sampler.SAMPLERATE);
		
		mBtnPlay = (ImageButton)findViewById(R.id.rec_play);
		mBtnDelete = (Button)findViewById(R.id.rec_delete);
		mBtnNext = (Button)findViewById(R.id.rec_next);
		
		mEntry = getIntent().getParcelableExtra(EXTRA_URI);
	}
	
	@Override
	public void onStart() {
		super.onStart();
		
		if (mEntry == null)
			startRecording();
		else
			showEntry();
	}
	
	/**
	 * Button click handler for 'play', plays back the recorded file
	 * @param view
	 */
	public void play(View view) {
		
		if (mPlayer.isPlaying()) {
			mPositionHandler.removeCallbacks(updatePositionRunnable);
			mPlayer.pause();
			mBtnPlay.setImageResource(R.drawable.av_play);
		} else {
			mPlayer.start();
			mBtnPlay.setImageResource(R.drawable.av_pause);
			updatePosition();
		}
		
	}
	
	/**
	 * Button click handler for 'delete', deletes entry (db provider takes care of wavfile)
	 * @param view
	 */
	public void delete(View view) {
		if (mEntry != null) {
			getContentResolver().delete(mEntry, null, null);
	    	finish();
		}
	}
	
	/**
	 * Button click handler for 'next', starts the location view.
	 * @param view
	 */
	public void next(View view) {
		Intent locationActivity = new Intent(this, LocationActivity.class);
		locationActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
		locationActivity.putExtra(EXTRA_URI, mEntry);
		startActivity(locationActivity);
		finish();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		if (mWaveForm != null) {
			if (mWaveForm.getThread() != null)
				mWaveForm.getThread().setRunning(false);
		}
		
		if (mPlayer != null) {
			if (mPlayer.isPlaying()) {
				mPlayer.stop();
			}
			mPlayer.release();
			mPlayer = null;
		}
		
		if (mMyLocation != null) {
			mMyLocation.cancelTimer();
		}
		
		finish();
	}

	@Override
	protected void onResume() {
		super.onResume();
	}
	
	/**
	 * Receives the buffer from the sampler
	 * 
	 * @param buffer
	 */
	public void setBuffer(short[] buffer) {
		
		if (mWaveForm.getThread() != null) {
			mWaveForm.getThread().setBuffer(buffer);
			mSeekBarTop.setProgress(mSeekBarTop.getProgress() + (int)(buffer.length / 2.0f));
			mSeekBarBottom.setProgress(mSeekBarBottom.getProgress() + (int)(buffer.length / 2.0f));	
		}
	}
	
	private LocationResult locationResult = new LocationResult() {
	    @Override
	    public void gotLocation(Location location) {
	    	Cursor c = mContext.getContentResolver().query(mEntry, null, null, null, null);
			if (c != null) {
				if (c.moveToFirst()) {
			    	Log.v(TAG, "New location fix at " + location.toString());
			        ContentValues cv = new ContentValues();
			        cv.put(Entries.COLUMN_NAME_LATITUDE, location.getLatitude());
			        cv.put(Entries.COLUMN_NAME_LONGITUDE, location.getLongitude());
			        int r = mContext.getContentResolver().update(mEntry, cv, null, null);
			        if (r != 0) {
			        	Log.v(TAG, "Entry updated with location");
			        }
			        
			        c.close();
				}
			}
	    }
	};
	
	private void showEntry() {
		// get entry
		Cursor c = this.getContentResolver().query(mEntry, null, null, null, null);
		if (c != null) {
			if (c.moveToFirst()) {
				// Check if a location has been set before
				float lat = c.getFloat(c.getColumnIndex(Entries.COLUMN_NAME_LATITUDE));
				float lng = c.getFloat(c.getColumnIndex(Entries.COLUMN_NAME_LONGITUDE));
				if (lat == 0.0 && lng == 0.0) {
					// Start looking for location fix
					mMyLocation.getLocation(this, locationResult);
				}
				
				// Load wav and show waveform
				String filename = c.getString(c.getColumnIndex(Entries.COLUMN_NAME_FILENAME));
				int size = 0;
				FileInputStream in = null;
				
				try {
					in = new FileInputStream(filename);
				} catch (FileNotFoundException e) {
					Log.e(TAG, "File not found.");
				}
				
				try {
					size = (int)in.getChannel().size()-44;
				} catch (IOException e) {
					Log.e(TAG, "Could not get file size.");
				}
					
				byte[] bytes = new byte[size];
				try {
					in.skip(44);
					in.read(bytes, 0, size);
				} catch (IOException e) {
					Log.e(TAG, "Could not read from buffer");
				}
				
				ShortBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
				
				Log.v(TAG, "byte buffer is: " + bytes.length);
				Log.v(TAG, "buffer length is: " + buffer.capacity());
				
				
				if (mWaveForm.getThread() != null) {
					mWaveForm.getThread().setRunning(false);					
				} else {
					Log.e(TAG, "mWaveForm DrawThread is null!");
				}
				
				FileWaveForm fwf = new FileWaveForm(this, buffer);
				fwf.setLayoutParams(new ViewGroup.LayoutParams(
		        		ViewGroup.LayoutParams.MATCH_PARENT,
		        		(int)(180 * getResources().getDisplayMetrics().density)));
				ViewGroup parent = (ViewGroup) mWaveForm.getParent();
				int i = parent.indexOfChild(mWaveForm);
				parent.removeView(mWaveForm);
				parent.addView(fwf, i);
				
				// Create media player
				mPlayer = new MediaPlayer();
				mPlayer.setOnPreparedListener(this);
				mPlayer.setOnCompletionListener(this);
				mPlayer.setOnErrorListener(this);
		
				mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		
				// Set media url
				try {
					mPlayer.setDataSource(filename);
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
				// Prepare buffer and start playback
				mPlayer.prepareAsync();
				
			}
			c.close();
		}
	}

	private void startRecording() {
		
		mBtnPlay.setBackgroundColor(getResources().getColor(R.color.light_grey));
		mBtnPlay.setEnabled(false);
		mBtnDelete.setEnabled(false);
		mBtnNext.setEnabled(false);
		
		try {
			if (mSampler == null) {
				mSampler = new Sampler(this);
				mSampler.setErrorListener(new SamplerListener() {
					@Override
					public void onError(String what) {
						Log.e(TAG, "Recording stopped with errors: " + what);
						mStopRecHandler.removeCallbacks(stopRecording);
						mWaveForm.getThread().setRunning(false);
						//mBtnDelete.setVisibility(View.INVISIBLE);	// TODO add 'retry' button					
					}
				});
				
			}
			if (mSampler != null) {
				mSampler.startSampling();		// Start recording
				mStopRecHandler.postDelayed(	// Schedule handler to stop recording
						stopRecording,
						NoisetracksApplication.MAX_RECORDING_DURATION_SECONDS * 1000);
			}
		} catch (NullPointerException e) {
			Log.e(TAG, "NullPointer: " + e.getMessage());
		}	
	}	
	
	private Runnable stopRecording = new Runnable() {
		@Override
		public void run() {
			mSampler.stopRecording();
			mWaveForm.getThread().setRunning(false);
			
			// add entry to database
			ContentValues values = new ContentValues();
			values.put(Entries.COLUMN_NAME_FILENAME, mSampler.getFilename());
			values.put(Entries.COLUMN_NAME_USERNAME, AppSettings.getUsername(mContext));
			values.put(Entries.COLUMN_NAME_TYPE, Entries.TYPE.RECORDED.ordinal());
			mEntry = mContext.getContentResolver().insert(Entries.CONTENT_URI, values);
			showEntry();
			
		}
	};
	
	/* Media player stuff */
	
	@Override
	public void onPrepared(MediaPlayer player) {
		
		mBtnPlay.setBackgroundColor(getResources().getColor(R.color.indicator));
		mBtnPlay.setEnabled(true);
		mBtnDelete.setEnabled(true);
		mBtnNext.setEnabled(true);
		
		if (!player.isPlaying()) {
			player.start();
			mBtnPlay.setImageResource(R.drawable.av_pause);
			try {
				mSeekBarTop.setMax(player.getDuration());
				mSeekBarBottom.setMax(player.getDuration());
				updatePosition();
			} catch (NullPointerException e) {

			}
		}
	}

	private void updatePosition() {
		mPositionHandler.removeCallbacks(updatePositionRunnable);
		try {
			mSeekBarTop.setProgress(mPlayer.getCurrentPosition());
			mSeekBarBottom.setProgress(mPlayer.getCurrentPosition());
		} catch (NullPointerException e) {
		}
		mPositionHandler.postDelayed(updatePositionRunnable, 1);
	}

	private final Runnable updatePositionRunnable = new Runnable() {
		public void run() {
			updatePosition();
		}
	};

	@Override
	public void onCompletion(MediaPlayer mp) {
		mBtnPlay.setImageResource(R.drawable.av_play);
		mPositionHandler.removeCallbacks(updatePositionRunnable);
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		return false;
	}

	@Override
	public void onSeekComplete(MediaPlayer mp) {
		if (!mPlayer.isPlaying()) {
			mPlayer.start();
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
		}
		return super.onOptionsItemSelected(item);
	}
}




class FileWaveForm extends SurfaceView implements SurfaceHolder.Callback {

	private static final String TAG = "FileWaveForm";
    private Path mPath;
    private Paint mPaint;
    
    private ShortBuffer mBuffer;
    private short[] mBlock;
    private int mBlockSize;
    
	public FileWaveForm(Context context, ShortBuffer buffer) {
		super(context);
		
		this.mBuffer = buffer;
		
		this.mPath = new Path();
		this.mPaint= new Paint();
		
		mPaint.setAntiAlias(true);
		mPaint.setDither(true);
		mPaint.setStyle(Paint.Style.STROKE);
		mPaint.setStrokeJoin(Paint.Join.ROUND);
		mPaint.setStrokeCap(Paint.Cap.ROUND);
		mPaint.setStrokeWidth(3.0f);
		mPaint.setARGB(255, 100, 100, 100);
		
		getHolder().addCallback(this);
	}
	
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		tryDrawing(holder);
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		tryDrawing(holder);
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {}
	
	private void tryDrawing(SurfaceHolder holder) {
        Log.i(TAG, "Trying to draw...");

        Canvas canvas = holder.lockCanvas();
        if (canvas == null) {
            Log.e(TAG, "Cannot draw onto the canvas as it's null");
        } else {
        	drawWaveForm(canvas);
            holder.unlockCanvasAndPost(canvas);
        }
    }

    private void drawWaveForm(final Canvas canvas) {
        
    	this.mBlockSize = mBuffer.capacity() / canvas.getWidth();
		this.mBlock = new short[mBlockSize];
		
        canvas.drawColor(0xFFEEEEEE);
			
        
		for (int x = 0; x < canvas.getWidth(); x++) {
			
			if (mBlockSize > mBuffer.remaining()) {
				mBlockSize = mBuffer.remaining();
			}
			mBuffer.get(mBlock, 0, mBlockSize); // read block from buffer
			
			int min = 0;
			int max = 0;
			for (int i = 0; i < mBlockSize; i += 2) { // only left channel
				min = Math.min(min, mBlock[i]);
				max = Math.max(max, mBlock[i]);
			}
			
			float y1 = Helper.map2range(min, -32768, 0, 0, canvas.getHeight()/2);
			float y2 = Helper.map2range(max, 0, 32768, canvas.getHeight()/2, canvas.getHeight());
			
			mPath.moveTo(x, y1);
			mPath.lineTo(x, y2);
		}
		
		canvas.drawPath(mPath, mPaint);
		
    }
}
    


