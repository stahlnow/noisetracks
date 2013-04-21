package com.stahlnow.noisetracks.service;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.stahlnow.noisetracks.NoisetracksApplication;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

public class NoisetracksService extends Service implements LocationListener {
	
	private static final int gpsMinTime = 500;
	private static final int gpsMinDistance = 0;
	private static final int TIMER_DELAY = 1000;
	private static final int GEOCODER_MAX_RESULTS = 5;
	
	private LocationManager mLocationManager = null;
	private double mLatitude = 0.0;
	private double mLongitude = 0.0;
	private double mAltitude = 0.0;
	private Timer mMonitoringTimer = null;
	
	// recorder variables
	private static final String AUDIO_RECORDER_FILE_EXT_WAV = ".wav";
	private static final String AUDIO_RECORDER_FOLDER = "Android/data/com.stahlnow.noisetracks.ui/files";
	private static final String AUDIO_RECORDER_TEMP_FILE = "record_temp.raw";
	private static final int RECORDER_SAMPLERATE = 44100;
	private static final int RECORDER_BPP = 16;
	private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO;
	private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
	
	private AudioRecord mAudioRecord = null;
	private int mBufferSize = 0;
	private Thread mRecordingThread = null;
	private boolean mIsRecording = false;
	
	private Timer mRecordingTimer = null;

	public NoisetracksService() {
		Log.v(NoisetracksApplication.TAG, "NoisetracksService.NoisetrackService().");
		
		mBufferSize = 16 * AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,RECORDER_CHANNELS,RECORDER_AUDIO_ENCODING);
	}

	@Override
	public IBinder onBind(Intent arg0) {
		Log.v(NoisetracksApplication.TAG, "NoisetracksService.onBind().");
		return null;
	}

	@Override
	public void onCreate() {
		Log.v(NoisetracksApplication.TAG, "NoisetracksService.onCreate().");
		super.onCreate();
	}
	
	
	private static String uploadFile()
    {
    	HttpURLConnection connection = null;
    	DataOutputStream outputStream = null;

    	String pathToOurFile = Environment.getExternalStorageDirectory().getPath();
    	pathToOurFile += "/Noisetracks";
    	pathToOurFile += "/test.wav";
    	Log.v(NoisetracksApplication.TAG, "path is: "+pathToOurFile);
    	String urlServer = "http://192.168.1.111:8000/upload/";
    	String lineEnd = "\r\n";
    	String twoHyphens = "--";
    	String boundary =  "----------303606909l33t";

    	int bytesRead, bytesAvailable, bufferSize;
    	byte[] buffer;
    	int maxBufferSize = 1*1024*1024;

    	try
    	{
    		FileInputStream fileInputStream = new FileInputStream(new File(pathToOurFile) );

	    	URL url = new URL(urlServer);
	    	connection = (HttpURLConnection) url.openConnection();
	
	    	// Allow Inputs & Outputs
	    	connection.setDoInput(true);
	    	connection.setDoOutput(true);
	    	connection.setUseCaches(false);
	
	    	// Enable POST method
	    	connection.setRequestMethod("POST");
	
	    	connection.setRequestProperty("Connection", "Keep-Alive");
	    	connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
	    	
	    	outputStream = new DataOutputStream( connection.getOutputStream() );
	    	outputStream.writeBytes(twoHyphens + boundary + lineEnd);
	    	outputStream.writeBytes("Content-Disposition: form-data; name=\"user_id\"" + lineEnd);
	    	outputStream.writeBytes(lineEnd);
	    	outputStream.writeBytes("909");
	    	outputStream.writeBytes(lineEnd);
	    	outputStream.writeBytes(twoHyphens + boundary + lineEnd);
	    	outputStream.writeBytes("Content-Disposition: form-data; name=\"wavfile\"; filename=\"" + pathToOurFile +"\"" + lineEnd);
	    	outputStream.writeBytes(lineEnd);
	
	    	bytesAvailable = fileInputStream.available();
	    	bufferSize = Math.min(bytesAvailable, maxBufferSize);
	    	buffer = new byte[bufferSize];
	
	    	// Read file
	    	bytesRead = fileInputStream.read(buffer, 0, bufferSize);
	
	    	while (bytesRead > 0)
	    	{
		    	outputStream.write(buffer, 0, bufferSize);
		    	bytesAvailable = fileInputStream.available();
		    	bufferSize = Math.min(bytesAvailable, maxBufferSize);
		    	bytesRead = fileInputStream.read(buffer, 0, bufferSize);
	    	}
	
	    	outputStream.writeBytes(lineEnd);
	    	outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
	
	    	/*
	    	// Responses from the server (code and message)
	    	int serverResponseCode = connection.getResponseCode();
	    	String serverResponseMessage = connection.getResponseMessage();
	    	AppLog.logString("Code: " + serverResponseCode + ": " + serverResponseMessage); //  200 OK
	    	*/
	        
	        fileInputStream.close();
	    	outputStream.flush();
	    	outputStream.close();
	    	
	    	// Get Response	
	        InputStream is = connection.getInputStream();
	        BufferedReader rd = new BufferedReader(new InputStreamReader(is));
	        String line;
	        StringBuffer response = new StringBuffer(); 
	        while((line = rd.readLine()) != null) {
	        	response.append(line);
	        	response.append('\r');
	        }
	        rd.close();
	        Log.v(NoisetracksApplication.TAG, response.toString());
	        return response.toString();
	    	
    	}
    	catch (Exception e)
    	{
    		Log.v(NoisetracksApplication.TAG, "Oooops: " + e.toString());
    		return null;
    	}
    	finally
    	{
    		if (connection != null)
    		{
    			connection.disconnect();
    		}
    	}
    }

	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.v(NoisetracksApplication.TAG, "NoisetracksService.onStartCommand().");
		
		Log.v(NoisetracksApplication.TAG, "Started tracking...");
		
		if (mLocationManager == null)
		{
			mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		}
		
		final Criteria criteria = new Criteria();
		
		criteria.setAccuracy(Criteria.ACCURACY_FINE);
		criteria.setAltitudeRequired(false);
		criteria.setBearingRequired(false);
		criteria.setCostAllowed(true);
		criteria.setPowerRequirement(Criteria.POWER_LOW);
		
		final String bestProvider = mLocationManager.getBestProvider(criteria, true);
		
		if (bestProvider != null && bestProvider.length() > 0)
		{
			mLocationManager.requestLocationUpdates(bestProvider, gpsMinTime, gpsMinDistance, this);
		}
		else
		{
			final List<String> providers = mLocationManager.getProviders(true);
			
			for (final String provider : providers)
			{
				mLocationManager.requestLocationUpdates(provider, gpsMinTime, gpsMinDistance, this);
			}
		}
		
		startMonitoringTimer();
		
		return Service.START_STICKY;
	}

	@Override
	public void onLocationChanged(Location location) {
		Log.v(NoisetracksApplication.TAG, "Got location fix at " + location.toString());
		
		mLatitude = location.getLatitude();
		mLongitude = location.getLongitude();
		mAltitude = location.getAltitude();
	}

	@Override
	public void onProviderDisabled(String provider) {
		Log.v(NoisetracksApplication.TAG, "NoisetracksService.onProviderDisabled().");
	}

	@Override
	public void onProviderEnabled(String provider) {
		Log.v(NoisetracksApplication.TAG, "NoisetracksService.onProviderEnabled().");
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		Log.v(NoisetracksApplication.TAG, "NoisetracksService.onStatusChanged().");
	}
	
	private void stopLoggingService() {
		stopSelf();
	}
	
	private void startMonitoringTimer() {
		
		Log.v(NoisetracksApplication.TAG, "Waiting for location fix...");
		
		mMonitoringTimer = new Timer();
		mMonitoringTimer.scheduleAtFixedRate(
				new TimerTask() {
					@Override
					public void run()
					{						
						if (mLatitude != 0.0 && mLongitude != 0.0)
						{
							Log.v(NoisetracksApplication.TAG, "Start Recording");
							mMonitoringTimer.cancel();
							mMonitoringTimer = null;
							
							mLocationManager.removeUpdates(NoisetracksService.this);
							
							//saveCoordinates(latitude, longitude, altitude, getLocationName(latitude,longitude));
							//startTimedRecording();	// start recording
						}
					}
				}, 
				NoisetracksService.TIMER_DELAY,
				NoisetracksService.TIMER_DELAY);
	}
	
	/************************************************************
	 * Recorder
	 ***********************************************************/
	
	private void startTimedRecording() {
		
		startRecording();						// start recording
		
		// schedule timer to stop recording
		mRecordingTimer = new Timer();
		mRecordingTimer.schedule(
				new TimerTask()
				{
					@Override
					public void run()
					{
						mRecordingTimer.cancel();
						mRecordingTimer = null;
							
						stopRecording();		// stop recording
						stopLoggingService();	// stop service
					}
				}, 
				NoisetracksApplication.MAX_RECORDING_DURATION_SECONDS * 1000);
	}
	
	
	public void startRecording() {
		
		Log.v(NoisetracksApplication.TAG, "Start recording ...");
		
		mAudioRecord = new AudioRecord(
				MediaRecorder.AudioSource.MIC,
				RECORDER_SAMPLERATE,
				RECORDER_CHANNELS,
				RECORDER_AUDIO_ENCODING,
				mBufferSize);
		
		mAudioRecord.startRecording();
		
		mIsRecording = true;
		
		mRecordingThread = new Thread(new Runnable() {
			
			@Override
			public void run() {
				writeAudioDataToFile();
			}
		}, "AudioRecorder Thread");
		
		mRecordingThread.start();
	}
	
	private void writeAudioDataToFile() {
		byte data[] = new byte[mBufferSize];
		String filename = getTempFilename();
		FileOutputStream os = null;
		
		try {
			os = new FileOutputStream(filename);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		int read = 0;
		
		if(null != os) {
			while(mIsRecording) {
				read = mAudioRecord.read(data, 0, mBufferSize);
				
				if(AudioRecord.ERROR_INVALID_OPERATION != read){
					try {
						os.write(data);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			
			try {
				os.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void stopRecording() {
		if(null != mAudioRecord){
			mIsRecording = false;
			
			mAudioRecord.stop();
			mAudioRecord.release();
			
			mAudioRecord = null;
			mRecordingThread = null;
		}
		
		copyWaveFile(getTempFilename(), getFilename());
		deleteTempFile();
	}

	private void deleteTempFile() {
		File file = new File(getTempFilename());
		
		file.delete();
	}
	
	private String getFilename(){
		String filepath = Environment.getExternalStorageDirectory().getPath();
		File file = new File(filepath, AUDIO_RECORDER_FOLDER);
		
		if (!file.exists()) {
			file.mkdirs();
		}
		
		String dateString = NoisetracksApplication.SDF.format(new Date());
		
		return (file.getAbsolutePath() + "/" + dateString + AUDIO_RECORDER_FILE_EXT_WAV);
	}
	
	private String getTempFilename(){
		String filepath = Environment.getExternalStorageDirectory().getPath();
		File file = new File(filepath, AUDIO_RECORDER_FOLDER);
		
		if(!file.exists()){
			file.mkdirs();
		}
		
		File tempFile = new File(filepath, AUDIO_RECORDER_TEMP_FILE);
		
		if(tempFile.exists())
			tempFile.delete();
		
		return (file.getAbsolutePath() + "/" + AUDIO_RECORDER_TEMP_FILE);
	}
	
	
	private void copyWaveFile(String inFilename, String outFilename){
		FileInputStream in = null;
		FileOutputStream out = null;
		long totalAudioLen = 0;
		long totalDataLen = totalAudioLen + 36;
		long longSampleRate = RECORDER_SAMPLERATE;
		int channels = 2;
		long byteRate = RECORDER_BPP * RECORDER_SAMPLERATE * channels/8;
		
		byte[] data = new byte[mBufferSize];
                
		try {
			in = new FileInputStream(inFilename);
			out = new FileOutputStream(outFilename);
			totalAudioLen = in.getChannel().size();
			totalDataLen = totalAudioLen + 36;
			
			Log.v(NoisetracksApplication.TAG, "File size: " + totalDataLen);
			
			WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
					longSampleRate, channels, byteRate);
			
			while(in.read(data) != -1){
				out.write(data);
			}
			
			in.close();
			out.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void WriteWaveFileHeader(
			FileOutputStream out, long totalAudioLen,
			long totalDataLen, long longSampleRate, int channels,
			long byteRate) throws IOException {
		
		byte[] header = new byte[44];
		
		header[0] = 'R';  // RIFF/WAVE header
		header[1] = 'I';
		header[2] = 'F';
		header[3] = 'F';
		header[4] = (byte) (totalDataLen & 0xff);
		header[5] = (byte) ((totalDataLen >> 8) & 0xff);
		header[6] = (byte) ((totalDataLen >> 16) & 0xff);
		header[7] = (byte) ((totalDataLen >> 24) & 0xff);
		header[8] = 'W';
		header[9] = 'A';
		header[10] = 'V';
		header[11] = 'E';
		header[12] = 'f';  // 'fmt ' chunk
		header[13] = 'm';
		header[14] = 't';
		header[15] = ' ';
		header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
		header[17] = 0;
		header[18] = 0;
		header[19] = 0;
		header[20] = 1;  // format = 1
		header[21] = 0;
		header[22] = (byte) channels;
		header[23] = 0;
		header[24] = (byte) (longSampleRate & 0xff);
		header[25] = (byte) ((longSampleRate >> 8) & 0xff);
		header[26] = (byte) ((longSampleRate >> 16) & 0xff);
		header[27] = (byte) ((longSampleRate >> 24) & 0xff);
		header[28] = (byte) (byteRate & 0xff);
		header[29] = (byte) ((byteRate >> 8) & 0xff);
		header[30] = (byte) ((byteRate >> 16) & 0xff);
		header[31] = (byte) ((byteRate >> 24) & 0xff);
		header[32] = (byte) (2 * 16 / 8);  // block align
		header[33] = 0;
		header[34] = RECORDER_BPP;  // bits per sample
		header[35] = 0;
		header[36] = 'd';
		header[37] = 'a';
		header[38] = 't';
		header[39] = 'a';
		header[40] = (byte) (totalAudioLen & 0xff);
		header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
		header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
		header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

		out.write(header, 0, 44);
	}

	
}