package com.noisetracks.android.audio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import android.os.Process;

public class Vorbis2RawConverter {
	
	public Vorbis2RawConverter() {
		m_pauseEvent=new Object();
		setPriority(Process.THREAD_PRIORITY_DEFAULT);
	}
	
	public void setPriority(int priority) {
		m_threadPriority=priority;
	}

	public void start(File vorbisFile,File rawFile) throws IOException {
		stop();
		try {
			m_vorbis=new VorbisDecoder(vorbisFile);
			m_raw=new FileOutputStream(rawFile);
		}
		catch (IOException e) {
			Simply.close(m_vorbis);
			m_vorbis=null;
			throw e;
		}
		m_rawFile=rawFile;
		m_progress=0;
		m_finishError=null;
		startThread();
	}
	
	public void stop() {
		if (m_thread==null) {
			return;
		}
		stopThread();
		close(true);
	}
	
	public boolean isRunning() {
		return m_running;
	}
	
	public void pause() {
		synchronized (m_pauseEvent) {
			m_paused=true;
		}
	}
	
	public void resume() {
		synchronized (m_pauseEvent) {
			if (m_paused) {
				m_paused=false;
				m_pauseEvent.notify();
			}
		}
	}
	
	public boolean isPaused() {
		synchronized (m_pauseEvent) {
			return m_paused;
		}
	}
		
	
	public int getProgress() {
		return m_progress;
	}

	public boolean isFinished() {
		return !m_running;
	}
	
	public Exception getFinishError() {
		return m_finishError;			
	}
	
	/////////////////////////////////// helpers
	
	public static boolean isConvertedFile(File vorbisFile,File rawFile) {
		if (!vorbisFile.exists() || !rawFile.exists()) {
			return false;
		}
		try {
			VorbisDecoder decoder=new VorbisDecoder();
			decoder.open(vorbisFile);
			int timeLength=decoder.getTimeLength();
			timeLength-=(timeLength % 100);
			int decodedSize=RawDecoder.getFileSize(
				decoder.getTimeLength(),
				decoder.getRate(),
				decoder.getChannels());
			decoder.close();
			return rawFile.length()>=decodedSize;
		}
		catch (IOException e) {
			return false;
		}
	}
	
	///////////////////////////////////////////// implementation
	
	private void startThread() {
		m_thread=new Thread() {
			public void run() {
				threadRun();
			}
		};
		m_running=true;
		m_thread.start();
	}
	
	private void stopThread() {
		m_running=false;
		resume();
		Simply.join(m_thread);
	}
	
	private void threadRun() {
		Simply.setThreadPriority(m_threadPriority);
		Exception error=null;
		try {
			RawDecoder.writeHeader(
				m_raw,
				m_vorbis.getRate(),
				m_vorbis.getChannels());
			int progressTotal=m_vorbis.getTimeLength();
			byte[] buffer=new byte[4*1024];
			while (m_running) {
				int read=m_vorbis.read(buffer,0,buffer.length);
				if (read==-1) {
					break;
				}
				m_raw.write(buffer,0,read);
				{
					int progressCurrent=m_vorbis.getTimePosition();
					m_progress=100*progressCurrent/progressTotal;
				}
				synchronized (m_pauseEvent) {
					if (m_paused) {
						Simply.waitNoLock(m_pauseEvent);
					}
				}
			}
		}
		catch (IOException e) {
			error=e;
		}
		m_finishError=error;
		m_running=false;
		close(error!=null);
	}
	
	private void close(boolean delete) {
		Simply.close(m_vorbis);
		m_vorbis=null;
		if (m_raw!=null) {
			Simply.close(m_raw);
			m_raw=null;
			if (delete) {
				m_rawFile.delete();
			}
		}
	}
	
	/////////////////////////////////// data
	
	private int m_threadPriority;
	private Thread m_thread;
	private volatile boolean m_running;
	
	private VorbisDecoder m_vorbis;
	private FileOutputStream m_raw;
	private File m_rawFile;
	
	private boolean m_paused;
	private Object m_pauseEvent;
	
	private int m_progress;
	private Exception m_finishError;
	
}
