package com.stahlnow.noisetracks.client;

import java.util.Date;

import android.text.format.DateUtils;

import com.google.android.maps.GeoPoint;
import com.stahlnow.noisetracks.NoisetracksApplication;

public class Entry {
	
	private Date created;
	private Date recorded;
	
	private String file;
	private GeoPoint location;
	
	private String username;
	private String mugshot;
	private String spectrogram;
	
	private String resource_uri;
	
	public Entry() {
		super();
	}

	/**
	    * Entry constructor.
	    *
	    * @param created The date when the entry was created.
	    * @param recorded The date the entry was recorded 
	    * @param file The URL of the stream
	    * @param point The location
	    * @param username The username
	    * @param mugshot The URL of the profile picture
	    * @param spectrogram The URL of the spectrogram image file
	    * @param resource_uri The resource uri for the entry
	    */
	public Entry(Date created, Date recorded, String file, GeoPoint point, String username, String mugshot, String spectrogram, String resource_uri) {
		super();
		this.created = created;
		this.recorded = recorded;
		this.file = file;
		this.location = point;
		
		this.username = username;
		this.mugshot = mugshot;
		this.spectrogram = spectrogram;
		
		this.resource_uri = resource_uri;
	
	}
	
	public Date getCreated() { return this.created; }
	
	public String getCreatedString() {
		return NoisetracksApplication.SDF.format(created); 
	}
	
	public Date getRecorded() { return this.recorded; }
	
	public String getRecordedAgo() {
		return DateUtils.getRelativeTimeSpanString(recorded.getTime(), System.currentTimeMillis(), 0L, DateUtils.FORMAT_ABBREV_ALL).toString();
	}
	
	public String getFile() { return this.file; }
	public GeoPoint getLocation() { return this.location; }
	
	public String getUsername() { return this.username; }
	public String getMugshot() { return this.mugshot; }
	public String getSpectrogram() { return this.spectrogram; }
	
	public String getResourceUri() { return this.resource_uri; }
	
}
