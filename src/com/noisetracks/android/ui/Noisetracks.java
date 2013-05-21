package com.noisetracks.android.ui;

import java.io.File;
import java.io.IOException;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Process;
import android.util.Log;

import com.actionbarsherlock.app.SherlockActivity;
import com.noisetracks.android.audio.PCMDecoder;
import com.noisetracks.android.audio.PCMPlayer;
import com.noisetracks.android.audio.RawDecoder;
import com.noisetracks.android.audio.Vorbis2RawConverter;
import com.noisetracks.android.audio.VorbisDecoder;
import com.noisetracks.android.authenticator.AuthenticateActivity;
import com.noisetracks.android.authenticator.AuthenticationService;

public class Noisetracks extends SherlockActivity {
	
	@SuppressWarnings("unused")
	private static final String TAG = "Noisetracks";

	static final int LOGIN_REQUEST = 0;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	
		super.onCreate(savedInstanceState);
		
		testPCM();
		
		/*
		Intent intent = this.getIntent();
		
		if (AuthenticationService.accountExists(this)) {
			intent = new Intent(Noisetracks.this, Tabs.class);
			startActivity(intent);
			finish();
			return;
		}
		
		intent = new Intent(Noisetracks.this, AuthenticateActivity.class);
		startActivityForResult(intent, LOGIN_REQUEST);
		*/
    }
   
    @Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_CANCELED) {
			finish();
			return;
		} else if (requestCode == LOGIN_REQUEST) {
			if (resultCode == RESULT_OK) {
				Intent intent = this.getIntent();
				intent = new Intent(Noisetracks.this, Tabs.class);
				startActivity(intent);
				finish();
				return;
			}
		}		
	}
    
    @Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}
    
    
    /**
     * 
     * Testing
     * 
     */
   

	private File ogg;
	private File raw;
    private Handler m_handler=new Handler();
    private Converter m_converter;
	private boolean m_converterStarting;
	private Runnable m_converterPoller=new Runnable() {
		public void run() {
			pollConverter();
		}
	};
	private Runnable m_converterStarter=new Runnable() {
		public void run() {
			m_converterStarting=false;
			startConverter();
		}
	};
    
    private void testPCM() {	
    	
		File dir = new File(Environment.getExternalStorageDirectory().getPath(), "Noisetracks/test");
    	
    	ogg = new File(dir, "test.ogg");
    	raw = new File(dir, "test.ogg.raw");
    	
    	// check if already converted
    	boolean isConverted = Vorbis2RawConverter.isConvertedFile(ogg,raw);
    	
    	if (!isConverted) {
    		Log.i(TAG, "start converting...");
    		m_handler.postDelayed(m_converterStarter, 700);
			m_converterStarting=true;
    	}
    	else {
    		Log.i(TAG, "file is already converted.");
	    	PCMPlayer player = new PCMPlayer();
			try {
				player.open(createDecoder(raw)); // test TODO change to raw again
			} catch (IOException e) {
				Log.e(TAG, "Error opening: " + e.toString());
			}
			try {
				player.prepare();
			} catch (IOException e) {
				Log.e(TAG, "Error preparing: " + e.toString());
			}
			try {
				player.play();
			} catch (IOException e) {
				Log.e(TAG, "Error playing: " + e.toString());
			}
			
    	}
    }
    
    private static PCMDecoder createDecoder(File file) throws IOException {
		String name=file.getName();
		int dot=name.lastIndexOf('.');
		if (dot!=-1) {
			String extension=name.substring(dot+1);
			if (extension.equalsIgnoreCase("ogg")) {
				Log.v(TAG, "got ogg");
				return new VorbisDecoder(file);
			} else if (extension.equalsIgnoreCase("raw")) {
				Log.v(TAG, "got raw");
				return new RawDecoder(file);
			}
		}
		throw new IOException("No decoder for '"+name+"'."); 
	}
	
	private void startConverter() {
		m_converter=new Converter(ogg, raw);
		m_converter.start();
		pollConverter();
	}
    
    
    private void pollConverter() {
		m_converter.check();
		if (m_converter.isFinished()) {
			Exception finishError=m_converter.getFinishError();
			freeConverter();
			if (finishError!=null) {
				//	m_song.getErrorDetails()
				Log.e(TAG, "Failed to decode noise.");
				finish();
			} else {
				Log.i(TAG, "Finished decoding noise.");
			}
			return;
		}
		showConverterProgress(m_converter.getProgress());
		m_handler.postDelayed(m_converterPoller,100);
	}
    
    private void freeConverter() {
		m_converter=null;
		m_handler.removeCallbacks(m_converterPoller);
	}
	
	private void showConverterProgress(int progress) {
		Log.i(TAG, "processing: " + progress);
	}
    
	
	
    
    
    
    
	// ///////////////////////////////// Converter

	private static class Converter {
		
		public Converter(File input, File output) {
			mFileInput = input;
			mFileOutput = output;
		}

		public void start() {
			//SongCache.push(m_song.getID());
			if (!mFileInput.exists()) {
				m_finished = true;
				return;
			}
			startConverter();
		}

		public void stop() {
			Log.v(TAG, "Converter stop()");
			if (m_finished) {
				return;
			}
			if (m_converter != null) {
				m_converter.stop();
				m_converter = null;
			}
			m_finished = false;
			m_finishError = null;
			//SongCache.remove(m_song.getID());
		}

		public void pause() {
			if (m_converter != null) {
				m_converter.pause();
			}
		}

		public void resume() {
			if (m_converter != null) {
				m_converter.resume();
			}
		}

		public void check() {
			if (m_finished) {
				return;
			}
			if (m_converter.isFinished()) {
				m_finishError = m_converter.getFinishError();
				m_converter = null;
				
				if (m_finishError != null) {	
					Log.e(TAG, "Errors converting: " + m_finishError.toString());
					//SongCache.remove(m_song.getID());
				}
				m_finished = true;
			}
		}

		public int getProgress() {
			if (m_finished) {
				return 100;
			}
			if (m_converter == null) {
				return 0;
			}
			else {
				return m_converter.getProgress();
			}
		}

		public boolean isFinished() {
			return m_finished;
		}

		public Exception getFinishError() {
			return m_finishError;
		}

		// /////////////////// implementation

		private void startConverter() {
			
			try {
				m_converter = new Vorbis2RawConverter();
				m_converter.setPriority(Process.THREAD_PRIORITY_DEFAULT);
				m_converter.start(mFileInput, mFileOutput);
				
			} catch (IOException e) {
				m_finished = true;
				m_finishError = e;
			}
		}

		private Vorbis2RawConverter m_converter;

		private File mFileInput;
		private File mFileOutput;
		
		private boolean m_finished;
		private Exception m_finishError;
	}
    
    
    
}