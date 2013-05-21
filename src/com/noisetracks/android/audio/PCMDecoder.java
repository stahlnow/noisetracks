package com.noisetracks.android.audio;

import java.io.Closeable;
import java.io.IOException;

public interface PCMDecoder extends Closeable {

	public void close();
	public boolean isOpened();
	
	public boolean isSeekable();
	public int getRate();
	public int getChannels();
	
	public void seekToTime(int time) throws IOException;
	public int read(byte[] buffer,int offset,int length) throws IOException;
}
