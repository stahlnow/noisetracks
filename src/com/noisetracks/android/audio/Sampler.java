package com.noisetracks.android.audio;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import sa.dsp.Compressor;
import sa.dsp.Gate;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;
import com.noisetracks.android.NoisetracksApplication;
import com.noisetracks.android.ui.RecordingActivity;

public class Sampler {
	
	private static final String TAG = "Sampler";
	
	public static final int SAMPLERATE = 44100;
	
	private static final int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;
	private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO;
	private static final int CHANNELS = 2;
	private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
	private static final int BIT_DEPTH = 16;
	private static final int BYTES_PER_SAMPLE = BIT_DEPTH / 8;
	private static final String AUDIO_RECORDER_FILE_EXT_WAV = ".wav";
	private static final String AUDIO_RECORDER_FOLDER = "Noisetracks/files";
	private static final String AUDIO_RECORDER_TEMP_FILE = "noisetracks.raw";
	
	/**
	 * 16-bit sample buffer
	 */
	private static short[] buffer;
	
	/** 
	 * Reference to user interface
	 */
	private RecordingActivity mRecordingActivity = null;
	
	/**
	 * Size of the recording buffer
	 */
	private int mBufferSize;
	
	/**
	 * Number of shorts (samples) that were read from the input
	 */
	private int mSamplesRead;
	
	/**
	 * The full path + filename of the recorded .wav file
	 */
	private String mFilename = null;
	
	private AudioRecord mAudioRecord;
	private Thread mRecordingThread;
	private boolean mIsRecording = false;
	
	private SamplerListener mSamplerListener;	
	
	public Sampler() {
		this(null);
	}
	
	public Sampler(RecordingActivity activity) {
		mRecordingActivity = activity;
		mSamplesRead = 0;
					
		mBufferSize = AudioRecord.getMinBufferSize(SAMPLERATE, CHANNEL_CONFIG, AUDIO_FORMAT);
		
		if (mBufferSize < 0) {
			Log.e(TAG, "Error getting buffer size. State: " + mBufferSize + ".");
		} else {
			Log.v(TAG, "Buffer size of 16-Bit samples is " + mBufferSize + ".");
		}
		
		mAudioRecord = new AudioRecord(
				AUDIO_SOURCE,
				SAMPLERATE,
				CHANNEL_CONFIG,
				AUDIO_FORMAT,
				mBufferSize * BYTES_PER_SAMPLE
		);
		
		buffer = new short[mBufferSize];

	}

	/**
	 * Collects audio data and sends it back to the recording activity
	 */
	public void startSampling() {
		
		try {
			mAudioRecord.startRecording();
		} catch (IllegalStateException e) {
			if (mSamplerListener != null)
				mSamplerListener.onError(e.toString() + ". State " + mAudioRecord.getState() + ".");
			return;
		}
		
		if (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
			if (mSamplerListener != null)
				mSamplerListener.onError("Could not initialize microphone. State " + mAudioRecord.getState() + ".");
			return;
		} else {
			Log.v(TAG, "State initialized");
		}
		
		mIsRecording = true;
		
		mRecordingThread = new Thread(new Runnable() {
		
			public void run() {
				
				OutputStream os = null;
				try {
					os = new BufferedOutputStream(new FileOutputStream(getTempFilename()));
				} catch (FileNotFoundException e) {
					Log.e(TAG, "File not found.");
				}
				
				Compressor compressor = new Compressor();
		        compressor.setThresh(-10.0d);
		        compressor.setRatio(-4.0d);
		        compressor.initRuntime();

		        Gate gate = new Gate();
		        gate.setThresh(-36.0d);
		        gate.initRuntime();
		        
		        if (null != os) {
		        
					while (mIsRecording) {
						
						mSamplesRead = mAudioRecord.read(Sampler.buffer, 0, mBufferSize);  // Read samples from device
						
						if (mSamplesRead > 0 && AudioRecord.ERROR_INVALID_OPERATION != mSamplesRead) {
							
							// Process audio
							gate.process(mSamplesRead, Sampler.buffer);
			                compressor.process(mSamplesRead, Sampler.buffer);
			                
			                // Write .wav compatible byte stream
							try {
								byte bytebuffer[] = short2byte(Sampler.buffer);
								os.write(bytebuffer, 0, mBufferSize * BYTES_PER_SAMPLE);
							} catch (IOException e) {
								Log.e(TAG, "Could not write to file.");
							}

							
							// Send buffer to RecordingActivity for visualization
							if (mRecordingActivity != null)
								mRecordingActivity.setBuffer(Sampler.buffer);
						} else {
							mAudioRecord.stop();
							mIsRecording = false;
							deleteTempFile();
							if (mSamplerListener != null)
								mSamplerListener.onError("Could not record, maybe microphone is in use.");
						}
					}		
					
					try {
						os.close();
					} catch (IOException e) {
						Log.e(TAG, e.toString());
					}
		        }
			}
		}, "Sampler Recording Thread");;
		
		mRecordingThread.start();
	}
	
	public void stopRecording() {
		
		mIsRecording = false;
		
		if (null != mAudioRecord){
			mAudioRecord.stop();
			mAudioRecord.release();
			mAudioRecord = null;
		}
		
		createWavFile(getTempFilename(), getFilename()); 	// create .wav file
		deleteTempFile();									// delete .raw file
		
	}
	
	public interface SamplerListener {
		public void onError(String what);
	}

	public void setErrorListener(SamplerListener listener) {
		mSamplerListener = listener;
	}
	

	private byte[] short2byte(short[] buffer) {
		int size = buffer.length;
		byte[] bytes = new byte[size * 2];

		for (int i = 0; i < size; i++) {
			bytes[i * 2] = (byte) (buffer[i] & 0x00ff);
			bytes[(i * 2) + 1] = (byte) ((buffer[i] & 0xff00) >> 8);
		}
		return bytes;

	}

	public short[] getBuffer() {
		return buffer;
	}
	
	private void deleteTempFile() {
		File file = new File(getTempFilename());	
		file.delete();
	}
	
	private void setFilename() {
		String filepath = Environment.getExternalStorageDirectory().getPath();
		File file = new File(filepath, AUDIO_RECORDER_FOLDER);
		
		if (!file.exists()) {
			file.mkdirs();
		}
		
		String filename = NoisetracksApplication.SDF.format(new Date());
		filename = filename.replace(":", "-");
		
		mFilename = file.getAbsolutePath() + "/" + filename + AUDIO_RECORDER_FILE_EXT_WAV;
	}
	
	public String getFilename() {
		if (mFilename == null)
			setFilename();
		return mFilename;
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
	
	
	private void createWavFile(String inFilename, String outFilename){
		FileInputStream in = null;
		FileOutputStream out = null;
		long totalAudioLen = 0;
		long totalDataLen = 0;
		long longSampleRate = SAMPLERATE;
		long byteRate = SAMPLERATE * CHANNELS * BYTES_PER_SAMPLE;	// Fs * Nc * Bytes per Sample
		
		byte[] data = new byte[mBufferSize * BYTES_PER_SAMPLE];
                
		try {
			in = new FileInputStream(inFilename);
			out = new FileOutputStream(outFilename);
			totalAudioLen = in.getChannel().size();
			totalDataLen = totalAudioLen + 36; // Chunk size: 4 + 24 + 8 + Bytes per Sample * Nc * Sr + (0 or 1)
			
			Log.v(TAG, "Data size: " + totalDataLen);
			
			WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
					longSampleRate, CHANNELS, byteRate);
			
			
			while(in.read(data) != -1) {
				out.write(data);
			}
			
			Log.v(TAG, "File size: " + out.getChannel().size());
			
			in.close();
			out.close();
		} catch (FileNotFoundException e) {
			Log.e(TAG, e.toString());
		} catch (IOException e) {
			Log.e(TAG, e.toString());
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
		header[20] = 1;  // format = 1 (WAVE_FORMAT_PCM)
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
		header[34] = BIT_DEPTH;  // bits per sample
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
