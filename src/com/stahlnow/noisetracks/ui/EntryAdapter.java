package com.stahlnow.noisetracks.ui;

import java.text.ParseException;
import java.util.Date;

import com.stahlnow.noisetracks.NoisetracksApplication;
import com.stahlnow.noisetracks.R;
import com.stahlnow.noisetracks.helper.ImageHelper;
import com.stahlnow.noisetracks.helper.httpimage.HttpImageManager;
import com.stahlnow.noisetracks.helper.httpimage.HttpImageManager.BitmapFilter;
import com.stahlnow.noisetracks.provider.NoisetracksContract.Entries;
import com.stahlnow.noisetracks.utility.AppLog;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

public class EntryAdapter extends SimpleCursorAdapter implements BitmapFilter { 
	
	private static final String TAG = "EntryAdapter";
	
	private static final int VIEW_TYPE_ENTRY = 0x0;
	private static final int VIEW_TYPE_LOAD_MORE = 0x1;
	private static final int VIEW_TYPE_RECORDING = 0x2;
	private static final int NUM_VIEW_TYPES = 3; // total different views
	
	private Activity mContext;
	private HttpImageManager mHttpImageManager;
	private LayoutInflater mInflater;
	private boolean mMugshotClickable;

	public EntryAdapter(Context context, int layout, Cursor c, String[] from, int[] to, int flags, boolean mugshotClickable) {
		super(context, layout, c, from, to, flags);

		this.mContext = (Activity) context;
		this.mHttpImageManager = NoisetracksApplication.getHttpImageManager();
		this.mHttpImageManager.setBitmapFilter(this);
		this.mInflater = LayoutInflater.from(context);
		
		this.mMugshotClickable = mugshotClickable;
		
	}

	public static class ViewHolder {
		// entry
		public ImageView spectrogram;
		public ImageView mugshot;
		public TextView username;
		public TextView recorded_ago;
		// 'load more' entry
		public TextView load_more;
	}
	
	@Override
	public int getItemViewType(int position) {
		Cursor c = getCursor();
		c.moveToPosition(position);
		
	    if (c.getInt(c.getColumnIndex(Entries.COLUMN_NAME_TYPE)) == Entries.TYPE.DOWNLOADED.ordinal()) {
	    	return VIEW_TYPE_ENTRY;
	    } else if (c.getInt(c.getColumnIndex(Entries.COLUMN_NAME_TYPE)) == Entries.TYPE.LOAD_MORE.ordinal()) {
	    	return VIEW_TYPE_LOAD_MORE;
	    } else if (c.getInt(c.getColumnIndex(Entries.COLUMN_NAME_TYPE)) == Entries.TYPE.RECORDED.ordinal()) {
	    	return VIEW_TYPE_RECORDING;
	    } else {
	    	Log.e(TAG, "Error, unknown entry type.");
	    	return -1;
	    }
	}
	
	@Override
	public int getViewTypeCount() {
	    return NUM_VIEW_TYPES;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		final ViewHolder holder;
		final String spectrogram;
		final String mugshot;
		final String recorded;
		final String user; 
		
		getCursor().moveToPosition(position);
	
		int type = getItemViewType(position);
        
		switch (type) {
        
        case VIEW_TYPE_ENTRY:
        	if (convertView == null) {
        		convertView = mInflater.inflate(R.layout.entry, null);
                holder = new ViewHolder();
                holder.spectrogram = (ImageView) convertView.findViewById(R.id.entry_spectrogram);
    			holder.mugshot = (ImageView) convertView.findViewById(R.id.entry_mugshot);
    			holder.username = (TextView) convertView.findViewById(R.id.entry_username);
    			holder.recorded_ago = (TextView) convertView.findViewById(R.id.entry_recorded_ago);
    			convertView.setTag(holder);
        	} else {
        		holder = (ViewHolder) convertView.getTag();
        	}
            
			//holder.mugshot.setImageResource(R.drawable.default_image);
			mugshot = getCursor().getString(getCursor().getColumnIndex(Entries.COLUMN_NAME_MUGSHOT));
			if (mugshot != null) {
				Uri mugshotUri = Uri.parse(mugshot);
				if (mugshotUri != null){
					Bitmap bitmap = mHttpImageManager.loadImage(new HttpImageManager.LoadRequest(mugshotUri, holder.mugshot));
					if (bitmap != null) {
						holder.mugshot.setImageBitmap(bitmap);
				    }
				}
			}
			
			//holder.spectrogram.setImageResource(R.drawable.default_image);
			spectrogram = getCursor().getString(getCursor().getColumnIndex(Entries.COLUMN_NAME_SPECTROGRAM));
			if (spectrogram != null) {
				Uri specUri = Uri.parse(spectrogram);
				if (specUri != null){
					Bitmap bitmap = mHttpImageManager.loadImage(new HttpImageManager.LoadRequest(specUri, holder.spectrogram));
					if (bitmap != null) {
						holder.spectrogram.setImageBitmap(bitmap);
				    }
				}
			}
			
			holder.username.setText(getCursor().getString(getCursor().getColumnIndex(Entries.COLUMN_NAME_USERNAME)));
			
			recorded = getCursor().getString(getCursor().getColumnIndex(Entries.COLUMN_NAME_RECORDED));
			if (recorded != null) {
				try {
					Date d = NoisetracksApplication.SDF.parse(recorded);
					String rec_ago = DateUtils.getRelativeTimeSpanString(d.getTime(), System.currentTimeMillis(), 0L, DateUtils.FORMAT_ABBREV_ALL).toString();
					holder.recorded_ago.setText(rec_ago);
				} catch (ParseException e) {			
					AppLog.logString("Failed to parse recorded date: " + e.toString());
				}
			}
			
			user = holder.username.getText().toString();
			if (mMugshotClickable) {			
				holder.mugshot.setOnClickListener(new OnClickListener() {
					
					@Override
					public void onClick(View v) {
						// mugshot was clicked, open profile
						Intent i = new Intent(mContext, ProfileActivity.class);
						i.putExtra("username", user);
						mContext.startActivity(i);
					}
				});
			}
            break;
        case VIEW_TYPE_LOAD_MORE:
        	if (convertView == null) {
	        	convertView = mInflater.inflate(R.layout.entry_load_more, null);
	        	holder = new ViewHolder();
				holder.load_more = (TextView) convertView.findViewById(R.id.load_more);
				convertView.setTag(holder);
        	} else {
    			holder = (ViewHolder) convertView.getTag();
    		} 
        	break;
        case VIEW_TYPE_RECORDING:
        	if (convertView == null) {
	        	convertView = mInflater.inflate(R.layout.entry_upload, null);
	        	holder = new ViewHolder();
    			holder.username = (TextView) convertView.findViewById(R.id.entry_username);
    			holder.recorded_ago = (TextView) convertView.findViewById(R.id.entry_recorded_ago);
				convertView.setTag(holder);
        	} else {
    			holder = (ViewHolder) convertView.getTag();
    		}
        	
			holder.username.setText(getCursor().getString(getCursor().getColumnIndex(Entries.COLUMN_NAME_USERNAME)));
			
			recorded = getCursor().getString(getCursor().getColumnIndex(Entries.COLUMN_NAME_RECORDED));
			if (recorded != null) {
				try {
					Date d = NoisetracksApplication.SDF.parse(recorded);
					String rec_ago = DateUtils.getRelativeTimeSpanString(d.getTime(), System.currentTimeMillis(), 0L, DateUtils.FORMAT_ABBREV_ALL).toString();
					holder.recorded_ago.setText(rec_ago);
				} catch (ParseException e) {			
					AppLog.logString("Failed to parse recorded date: " + e.toString());
				}
			}
        	break;
        default:
        	break;
        }
		
		return convertView;		
	}

	@Override
	public Bitmap filter(Bitmap in) {
		return ImageHelper.transformBitmap(in, 5, false);
	}
	
	/*
	 * @Override public void bindView (View view, Context context, Cursor
	 * cursor) { // nothing to do here }
	 * 
	 * 
	 * @Override public View newView (Context context, Cursor cursor, ViewGroup
	 * parent) { return mInflater.inflate(R.layout.entry, null); }
	 */

}
