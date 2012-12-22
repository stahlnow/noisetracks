package com.stahlnow.noisetracks.ui;

import com.stahlnow.noisetracks.NoisetracksApplication;
import com.stahlnow.noisetracks.R;
import com.stahlnow.noisetracks.helper.httpimage.HttpImageManager;
import com.stahlnow.noisetracks.provider.NoisetracksContract.Entries;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

public class EntryAdapter extends SimpleCursorAdapter { 
	
	private Activity mContext;
	private HttpImageManager mHttpImageManager;
	private LayoutInflater mInflater;

	public EntryAdapter(Context context, int layout, Cursor c, String[] from, int[] to, int flags) {
		super(context, layout, c, from, to, flags);

		this.mContext = (Activity) context;
		this.mHttpImageManager = NoisetracksApplication.getHttpImageManager();
		this.mInflater = LayoutInflater.from(context);
		
	}

	public static class ViewHolder {
		public ImageView spectrogram;
		public ImageView mugshot;
		public TextView username;
		public TextView recorded_ago;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		final ViewHolder holder;

		if (convertView == null) {
			mInflater = mContext.getLayoutInflater();
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

		getCursor().moveToPosition(position);

		if (getCursor() != null) {
			
			//holder.mugshot.setImageResource(R.drawable.default_image);
			Uri mugshotUri = Uri.parse(getCursor().getString(getCursor().getColumnIndex(Entries.COLUMN_NAME_MUGSHOT)));
			if (mugshotUri != null){
				Bitmap bitmap = mHttpImageManager.loadImage(new HttpImageManager.LoadRequest(mugshotUri, holder.mugshot));
				if (bitmap != null) {
					holder.mugshot.setImageBitmap(bitmap);
			    }
			}
			
			//holder.spectrogram.setImageResource(R.drawable.default_image);
			Uri specUri = Uri.parse(getCursor().getString(getCursor().getColumnIndex(Entries.COLUMN_NAME_SPECTROGRAM)));
			if (specUri != null){
				Bitmap bitmap = mHttpImageManager.loadImage(new HttpImageManager.LoadRequest(specUri, holder.spectrogram));
				if (bitmap != null) {
					holder.spectrogram.setImageBitmap(bitmap);
			    }
			}
			
			holder.username.setText(getCursor().getString(getCursor().getColumnIndex(Entries.COLUMN_NAME_USERNAME)));
			holder.recorded_ago.setText(getCursor().getString(getCursor().getColumnIndex(Entries.COLUMN_NAME_RECORDED)));
			
			holder.mugshot.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					// mugshot was clicked, open profile
					Intent i = new Intent(mContext, ProfileActivity.class);
					i.putExtra("username", holder.username.getText());
					mContext.startActivity(i);
				}
			});
		}
		
		return convertView;		
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
