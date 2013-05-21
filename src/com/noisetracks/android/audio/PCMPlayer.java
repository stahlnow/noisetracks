package com.noisetracks.android.audio;

import java.io.IOException;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

public class PCMPlayer {
	
	private static final String TAG = "PCMPlayer";
	
	public PCMPlayer() {
		m_lock=new Object();
		m_reportFinishedLock=new Object();
		m_state=STATE_CLOSED;
		m_basePosition=0;
	}
	
	public boolean open(PCMDecoder decoder) throws IOException {
		close();
		int rate=decoder.getRate();
		int channelConfig=decoder.getChannels();
		if (channelConfig==1) {
			channelConfig=AudioFormat.CHANNEL_CONFIGURATION_MONO;
		} else if (channelConfig==2) {
			channelConfig=AudioFormat.CHANNEL_CONFIGURATION_STEREO;
		} else {
			throw new IOException(String.format(
				"Invalid number of channels (%d).",channelConfig
			));
		}
		if (!decoder.isSeekable()) {
			throw new IOException("Not seekable.");
		}
		int audioBufferLength=AudioTrack.getMinBufferSize(
			rate,
			channelConfig,
			AudioFormat.ENCODING_PCM_16BIT);
		int bufferLength=4*audioBufferLength;
		try {
			m_track=new AudioTrack(
				AudioManager.STREAM_MUSIC,
				rate,
				channelConfig,
				AudioFormat.ENCODING_PCM_16BIT,
				bufferLength,
				AudioTrack.MODE_STREAM); // MODE_STREAM
		}
		catch (Exception e) {
			IOException ioe=new IOException("Failed to initialize audio.");
			ioe.initCause(e);
			throw ioe;
		}
		m_decoder=decoder;
		m_state=STATE_STOPPED;
		m_audioBufferLength=audioBufferLength;
		allocateBuffers(bufferLength);
		startThreads();
		return true;
	}
	
	public void close() {
		stop();
		synchronized (m_lock) {
			if (m_state==STATE_CLOSED) {
				return;
			}
			setStateNotify(STATE_CLOSED);
		}
		stopThreads();
		m_decoder.close();
		m_decoder=null;
		m_track.release();
		m_track=null;
		m_basePosition=0;
	}
	
	public boolean isOpened() {
		synchronized (m_lock) {
			return m_state!=STATE_CLOSED;			
		}
	}
	
	public boolean prepare() throws IOException {
		return prepare(null);
	}
	
	public boolean prepare(Synchronizer playSynchronizer) throws IOException {
		synchronized (m_lock) {
			if (m_state!=STATE_STOPPED) {
				return false;
			}
			if (m_state==STATE_READING) {
				return true;
			}
			m_decoder.seekToTime(m_basePosition);
			m_playSynchronizer=playSynchronizer;
			resetBuffers();
			setStateNotify(STATE_READING);
			while (true) {
				if (m_readerBufferLength!=0 || m_state!=STATE_READING) {
					break;
				}
				Simply.waitNoLock(m_lock);
			}
			return true;
		}
	}
	
	public boolean play() throws IOException {
		synchronized (m_lock) {
			if (m_state!=STATE_STOPPED && m_state!=STATE_READING) {
				return false;
			}
			prepare(m_playSynchronizer);
			
			// TEST
//			int looping = m_track.setLoopPoints(0, 1000, -1);
//			Log.d(TAG, "Looping: " + looping);
			
			m_track.play();
			while (m_track.getPlayState()!=AudioTrack.PLAYSTATE_PLAYING) {
				Simply.waitSleep(0);
			}
			setStateNotify(STATE_PLAYING);
			return true;
		}
	}
	
	public boolean stop() {
		synchronized (m_lock) {
			if (m_state!=STATE_READING && m_state!=STATE_PLAYING) {
				return false;
			}
			if (m_state==STATE_PLAYING || m_state==STATE_PLAYING_LAST) {
				m_basePosition=getPosition();
				m_track.stop();
			}
			setStateNotify(STATE_STOPPED);
			return true;
		}
	}
	
	public boolean isPlaying() {
		synchronized (m_lock) {
			return 	m_state==STATE_PLAYING ||
					m_state==STATE_PLAYING_LAST;
		}
	}
	
	public boolean setPosition(int position) {
		synchronized (m_lock) {
			if (m_state!=STATE_STOPPED) {
				return false;
			}
			m_basePosition=position;
			return true;
		}
	}
	
	public int getPosition() {
		synchronized (m_lock) {
			int position=0;
			if (m_state==STATE_PLAYING || m_state==STATE_PLAYING_LAST) {
				int rate=m_track.getPlaybackRate();
				int headPosition=m_track.getPlaybackHeadPosition();
				position=(int)(1000l*headPosition/rate);
			}
			return m_basePosition+position;
		}
	}
	
	public boolean setVolume(float volume) {
		synchronized (m_lock) {
			if (m_state==STATE_CLOSED) {
				return false;
			}
			m_track.setStereoVolume(volume,volume);
			return true;
		}
	}
	
	/////////////////////////////////// callback 
	
	public interface Callback {
		public void onFinished(PCMPlayer player,Exception error);
	}
	
	public void setCallback(Callback callback) {
		synchronized (m_lock) {
			m_callback=callback;
		}
	}
	
	///////////////////////////////////////////// implementation
	
	private void readerThreadRun() {
		while (true) {
			reportFinished();
			synchronized (m_lock) {
				if (m_state==STATE_CLOSED) {
					//log("finished");
					break;
				}
				if ((m_readerBufferLength!=0) ||
					(m_state!=STATE_READING && m_state!=STATE_PLAYING))
				{
					//log("buffer is not empty or non-reading state, waiting...");
					Simply.waitNoLock(m_lock);
					continue;
				}
			}
			int read=0;
			Exception error=null;
			try {
				read=m_decoder.read(m_readerBuffer,0,m_readerBuffer.length);
				//log("read: "+read);
			}
			catch (IOException e) {
				error=e;
			}
			synchronized (m_lock) {
				if (m_state!=STATE_READING && m_state!=STATE_PLAYING) {
					//log("state changed, looping");
					continue;
				}
				if (error!=null) {
					finish(error);
					continue;
				}
				if (read==-1 && m_state!=STATE_READING) {
					//log("EOF reached");
					setStateNotify(STATE_PLAYING_LAST);
					continue;
				}
				m_readerBufferLength=read;
				m_lock.notifyAll();
			}
		}
	}
	
	private void playerThreadRun() {
		Synchronizer.Handle shandle=null;
		while (true) {
			reportFinished();
			synchronized (m_lock) {
				if (m_state==STATE_CLOSED) {
					if (shandle!=null) {
						//log("deleting synchronizer handle...");
						shandle.unregister();
						shandle=null;
					}
					//log("finished");
					break;
				}
				if (m_state!=STATE_PLAYING && m_state!=STATE_PLAYING_LAST) {
					//log("non-playing state, waiting...");
					if (shandle!=null) {
						//log("deleting synchronizer handle...");
						shandle.unregister();
						shandle=null;
					}
					Simply.waitNoLock(m_lock);
					continue;
				}
				if (m_playerBufferPosition==m_playerBufferLength) {
					//log("buffer is empty");
					if (m_state==STATE_PLAYING_LAST) {
						//log("reached EOF, finishing");
						finish(null);
						continue;
					}
					if (m_readerBufferLength==0) {
						//log("reader buffer empty, waiting...");
						Simply.waitNoLock(m_lock);
						continue;
					}
					swapBuffers();
					m_lock.notifyAll();
					if (m_playerBufferLength<=0) {
						//log("buffer length is invalid, looping");
						m_playerBufferLength=0;
						continue;
					}
				}
				if (shandle==null && m_playSynchronizer!=null) {
					//log("creating synchronizer handle...");
					shandle=m_playSynchronizer.register();
				}
			}
			if (shandle!=null) {
				//log("synchronizing...");
				if (!shandle.synchronize()) {
					//log("synchronization interrupted...");
					continue;
				}
				//log("synchronized!");
			}
			int written=m_track.write(
				m_playerBuffer,
				m_playerBufferPosition,
				m_playerBufferLength-m_playerBufferPosition);
			
			//log("written: "+written);
			if (written<0) {
				synchronized (m_lock) {
					//log("write error at play state "+m_track.getPlayState());
					if (m_state==STATE_PLAYING || m_state==STATE_PLAYING_LAST) {
						finish(new IOException(
							String.format("Audio failed to play (%d).",written)
						));
					}
				}
			} else {
				m_playerBufferPosition+=written;
			}
		}
	}
	
	private void finish(Exception error) {
		if (error!=null) {
			//log("Finished with error: "+error.toString());
		} else {
			//log("Finished.");
		}
		m_track.stop();
		m_basePosition=0;
		setStateNotify(STATE_STOPPED);
		if (m_callback!=null) {
			synchronized (m_reportFinishedLock) {
				m_reportFinishedCallback=m_callback;
				m_reportFinishedError=error;
			}
		}
	}
	
	private void reportFinished() {
		Callback callback=null;
		Exception error=null;
		synchronized (m_reportFinishedLock) {
			if (m_reportFinishedCallback!=null) {
				callback=m_reportFinishedCallback;
				error=m_reportFinishedError;
				m_reportFinishedCallback=null;
				m_reportFinishedError=null;
			}
		}
		if (callback!=null) {
			callback.onFinished(this,error);
		}
	}
	
	private void setStateNotify(int state) {
		m_state=state;
		m_lock.notifyAll();
	}
	
	/////////////////////////////////// buffers
	
	private void allocateBuffers(int minLength) {
		if (m_readerBuffer==null || m_readerBuffer.length<minLength) {
			m_readerBuffer=new byte[minLength];
		}
		if (m_playerBuffer==null || m_playerBuffer.length<minLength) {
			m_playerBuffer=new byte[minLength];
		}
		resetBuffers();
	}
	
	private void resetBuffers() {
		m_readerBufferLength=0;
		m_playerBufferPosition=0;
		m_playerBufferLength=0;
	}
		
	
	private void swapBuffers() {
		byte[] buffer=m_readerBuffer;
		m_readerBuffer=m_playerBuffer;
		m_playerBuffer=buffer;
		
		m_playerBufferLength=m_readerBufferLength;
		m_playerBufferPosition=0;
		m_readerBufferLength=0;
	}
	
	/////////////////////////////////// threads
	
	private void startThreads() {
		String name;
		{
			name=getClass().getSimpleName()+"#"+m_instanceCounter;
			m_instanceCounter++;
		}
		m_readerThread=new Thread(name+"/Reader") {
			public void run() {
				readerThreadRun();
			}
		};
		m_playerThread=new Thread(name+"/Player") {
			public void run() {
				playerThreadRun();
			}
		};
		m_readerThread.start();
		m_playerThread.start();
	}
	
	private void stopThreads() {
		Simply.notifyAll(m_lock);
		Simply.join(m_readerThread);
		Simply.join(m_playerThread);
		m_readerThread=null;
		m_playerThread=null;
	}
	
	/////////////////////////////////// misc

	private static void log(String message) {
		Log.e("TOF",Thread.currentThread().getName()+": "+message);
	}
	
	/////////////////////////////////// data
	
	private AudioTrack m_track;
	private PCMDecoder m_decoder;
	private int m_basePosition;
	
	private Callback m_callback;

	private Thread m_readerThread;
	private Thread m_playerThread;
	
	private int m_audioBufferLength;
	
	private byte[] m_readerBuffer;
	private int m_readerBufferLength;
	
	private byte[] m_playerBuffer;
	private int m_playerBufferPosition;
	private int m_playerBufferLength;
	
	private Object m_lock;
	private Synchronizer m_playSynchronizer;
	
	private Object m_reportFinishedLock;
	private Callback m_reportFinishedCallback;
	private Exception m_reportFinishedError;
	
	private volatile int m_state;
	
	private static final int
		STATE_CLOSED		=0,
		STATE_STOPPED		=1,
		STATE_READING		=2,
		STATE_PLAYING		=3,
		STATE_PLAYING_LAST	=4;

	private static int m_instanceCounter=0;
}
