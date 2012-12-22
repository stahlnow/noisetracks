package com.stahlnow.noisetracks.ui;

import java.util.ArrayList;

import com.stahlnow.noisetracks.NoisetracksApplication;
import com.stahlnow.noisetracks.R;
import com.stahlnow.noisetracks.client.Entry;
import com.stahlnow.noisetracks.helper.DownloadImageTask;
import com.stahlnow.noisetracks.helper.httpimage.HttpImageManager;
import com.stahlnow.noisetracks.provider.NoisetracksContract.Entries;
import com.stahlnow.noisetracks.ui.EntryAdapter.ViewHolder;
import com.stahlnow.noisetracks.utility.AppLog;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class EntryArrayAdapter extends ArrayAdapter<Entry> {
    
	private Activity mContext;
    private int mLayoutResourceId;
    private HttpImageManager mHttpImageManager;
    private LayoutInflater mInflater;
    
    public EntryArrayAdapter(Context context, int layoutResourceId) {
        super(context, layoutResourceId);
        
        this.mContext = (Activity) context;
        this.mLayoutResourceId = layoutResourceId;
        
        this.mHttpImageManager = NoisetracksApplication.getHttpImageManager();
		this.mInflater = LayoutInflater.from(context);
        
    }
 
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        
    	final ViewHolder holder;

		if (convertView == null) {
			mInflater = mContext.getLayoutInflater();
			convertView = mInflater.inflate(mLayoutResourceId, null);

			holder = new ViewHolder();
			holder.spectrogram = (ImageView) convertView.findViewById(R.id.entry_spectrogram);
			holder.mugshot = (ImageView) convertView.findViewById(R.id.entry_mugshot);
			holder.username = (TextView) convertView.findViewById(R.id.entry_username);
			holder.recorded_ago = (TextView) convertView.findViewById(R.id.entry_recorded_ago);

			convertView.setTag(holder);
			
		} else {
			holder = (ViewHolder) convertView.getTag();
		}
        
        final Entry e = getItem(position);
        
        if (e != null) {
        	
        	//holder.mugshot.setImageResource(R.drawable.default_image);
			Uri mugshotUri = Uri.parse(e.getMugshot());
			if (mugshotUri != null){
				Bitmap bitmap = mHttpImageManager.loadImage(new HttpImageManager.LoadRequest(mugshotUri, holder.mugshot));
				if (bitmap != null) {
					holder.mugshot.setImageBitmap(bitmap);
			    }
			}
			
			//holder.spectrogram.setImageResource(R.drawable.default_image);
			Uri specUri = Uri.parse(e.getSpectrogram());
			if (specUri != null){
				Bitmap bitmap = mHttpImageManager.loadImage(new HttpImageManager.LoadRequest(specUri, holder.spectrogram));
				if (bitmap != null) {
					holder.spectrogram.setImageBitmap(bitmap);
			    }
			}
        	
            holder.username.setText(e.getUsername());
            holder.recorded_ago.setText(e.getRecordedAgo());
        }
        
        return convertView;
    }
 
}
