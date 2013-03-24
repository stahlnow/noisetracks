package com.stahlnow.noisetracks.ui;

import java.lang.reflect.Field;
import java.text.ParseException;
import java.util.Date;

import com.stahlnow.noisetracks.NoisetracksApplication;
import com.stahlnow.noisetracks.R;
import com.stahlnow.noisetracks.client.SQLLoaderCallbacks;
import com.stahlnow.noisetracks.helper.FixedSpeedScroller;
import com.stahlnow.noisetracks.helper.httpimage.HttpImageManager;
import com.stahlnow.noisetracks.provider.NoisetracksProvider;
import com.stahlnow.noisetracks.provider.NoisetracksContract.Entries;
import com.stahlnow.noisetracks.utility.AppLog;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.Html;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;
import com.handmark.pulltorefresh.extras.viewpager.PullToRefreshViewPager;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;

public class EntryActivity extends SherlockFragmentActivity implements OnRefreshListener<ViewPager> { 

	private PullToRefreshViewPager mPullToRefreshViewPager;
	private ViewPager mPager;
	private PagerAdapter mAdapter;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// enable 'up' navigation in action bar
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		
		setContentView(R.layout.entry_activity);

		mPullToRefreshViewPager = (PullToRefreshViewPager) findViewById(R.id.entry_activity_pull_refresh_view_pager);
		mPullToRefreshViewPager.setOnRefreshListener(this);

		mPager = mPullToRefreshViewPager.getRefreshableView();

		String select = getIntent().getExtras().getString(SQLLoaderCallbacks.SELECT);
		
		Cursor cursor = getContentResolver().query(
				Entries.CONTENT_URI,
				NoisetracksProvider.READ_ENTRY_PROJECTION,
				select,
				null,
				Entries.DEFAULT_SORT_ORDER
		);
		
		mAdapter = new EntryPagerAdapter<EntryDetailFragment>(getSupportFragmentManager(), EntryDetailFragment.class, NoisetracksProvider.READ_ENTRY_PROJECTION, cursor);
		mPager.setAdapter(mAdapter);
		mPager.setCurrentItem(getIntent().getExtras().getInt("item"), false); // select item	
	
		try {
            Field mScroller;
            mScroller = ViewPager.class.getDeclaredField("mScroller");
            mScroller.setAccessible(true); 
            DecelerateInterpolator sInterpolator = new DecelerateInterpolator();
            FixedSpeedScroller scroller = new FixedSpeedScroller(mPager.getContext(), sInterpolator);
            mScroller.set(mPager, scroller);
        } catch (NoSuchFieldException e) {
        } catch (IllegalArgumentException e) {
        } catch (IllegalAccessException e) {
        }
	}
	
	@Override
	public void onRefresh(PullToRefreshBase<ViewPager> refreshView) {
		new GetDataTask().execute();
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	        case android.R.id.home:
	            // This is called when the Home (Up) button is pressed
	            // in the Action Bar.
	            Intent parentActivityIntent = new Intent(this, Tabs.class);
	            parentActivityIntent.addFlags(
	                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
	                    Intent.FLAG_ACTIVITY_NEW_TASK);
	            startActivity(parentActivityIntent);
	            finish();
	            return true;
	    }
	    return super.onOptionsItemSelected(item);
	}
	
	
	// player control click handlers
	public void play(View view) {
	    AppLog.logString("play");
	}
	
	public void previous(View view) {
	    AppLog.logString("previous");
	    mPager.setCurrentItem(mPager.getCurrentItem()-1, true);
	}
	
	public void next(View view) {
	    AppLog.logString("next");
	    mPager.setCurrentItem(mPager.getCurrentItem()+1, true);
	}
	
	

	private static class EntryPagerAdapter<F extends Fragment> extends FragmentStatePagerAdapter {
		
		private final Class<F> mFragmentClass;
		private final String[] mProjection;
		private Cursor mCursor;
		
		public EntryPagerAdapter(FragmentManager fm, Class<F> fragmentClass, String[] projection, Cursor cursor) {
			super(fm);
			this.mFragmentClass = fragmentClass;
			this.mProjection = projection;
			this.mCursor = cursor;			
		}
		
		@Override
	    public F getItem(int position) {
	        if (mCursor == null) // shouldn't happen
	            return null;
	 
	        mCursor.moveToPosition(position);

	        F frag;
	        try {
	            frag = mFragmentClass.newInstance();
	        } catch (Exception ex) {
	            throw new RuntimeException(ex);
	        }
	        Bundle args = new Bundle();
	        for (int i = 0; i < mProjection.length; ++i) {
	            args.putString(mProjection[i], mCursor.getString(i)); // TODO this gets everything as Strings (even latitude/longitude)
	        }
	        frag.setArguments(args);
	        return frag;
	    }
		
		@Override
		public int getCount() {
			if (mCursor == null)
				return 0;
			else
				return mCursor.getCount();
		}

		public void swapCursor(Cursor c) {
			if (mCursor == c)
				return;

			this.mCursor = c;
			notifyDataSetChanged();
		}

		public Cursor getCursor() {
			return mCursor;
		}

	}

	public static class EntryDetailFragment extends Fragment {
		
		private HttpImageManager mHttpImageManager;
		
		public static EntryDetailFragment newInstance() {
			EntryDetailFragment f = new EntryDetailFragment();
			return f;
		}

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			this.mHttpImageManager = NoisetracksApplication.getHttpImageManager();
		}

		/**
		 * The Fragment's UI
		 */
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			
			View v = inflater.inflate(R.layout.entry_detail, container, false);
			ImageView mugshot = (ImageView) v.findViewById(R.id.entry_mugshot);
			TextView username = (TextView) v.findViewById(R.id.entry_username);
			TextView recorded_ago = (TextView) v.findViewById(R.id.entry_recorded_ago);
			ImageView spectrogram = (ImageView) v.findViewById(R.id.entry_spectrogram);
			SeekBar seekbar = (SeekBar) v.findViewById(R.id.entry_seekbar);

			//mugshot.setImageResource(R.drawable.default_image);
			String mug = getArguments().getString("mugshot");
			if (mug != null) {
				Uri mugshotUri = Uri.parse(mug);
				if (mugshotUri != null){
					Bitmap bitmap = mHttpImageManager.loadImage(new HttpImageManager.LoadRequest(mugshotUri, mugshot));
					if (bitmap != null) {
						mugshot.setImageBitmap(bitmap);
				    }
				}
			}
			
			//spectrogram.setImageResource(R.drawable.default_image);
			String spect = getArguments().getString("spectrogram");
			if (spect != null) {
				Uri specUri = Uri.parse(spect);
				if (specUri != null){
					Bitmap bitmap = mHttpImageManager.loadImage(new HttpImageManager.LoadRequest(specUri, spectrogram));
					if (bitmap != null) {
						spectrogram.setImageBitmap(bitmap);
					}
					
				}
			}
			
			username.setText(getArguments().getString("username"));
			
			String recorded = getArguments().getString("recorded");
			if (recorded != null) {
				try {
					Date d = NoisetracksApplication.SDF.parse(recorded);
					String time = DateUtils.formatDateTime(getActivity(), d.getTime(), DateUtils.FORMAT_SHOW_TIME|DateUtils.FORMAT_12HOUR|DateUtils.FORMAT_CAP_AMPM);
					String date = DateUtils.formatDateTime(getActivity(), d.getTime(), DateUtils.FORMAT_SHOW_DATE|DateUtils.FORMAT_ABBREV_ALL);
					recorded_ago.setText(time + Html.fromHtml("&nbsp;\u00B7&nbsp;") + date);
				} catch (ParseException e) {			
					AppLog.logString("Failed to parse recorded date: " + e.toString());
				}
			}
			
			return v;
		}

		@Override
		public void onActivityCreated(Bundle savedInstanceState) {
			super.onActivityCreated(savedInstanceState);
		}

	}

	
	private class GetDataTask extends AsyncTask<Void, Void, Void> {

		@Override
		protected Void doInBackground(Void... params) {
			// Simulates a background job.
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			mPullToRefreshViewPager.onRefreshComplete();
			super.onPostExecute(result);
		}
	}
	
}
