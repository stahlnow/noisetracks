package com.stahlnow.noisetracks.ui;

import com.stahlnow.noisetracks.R;
import com.stahlnow.noisetracks.provider.NoisetracksProvider;
import com.stahlnow.noisetracks.provider.NoisetracksContract.Entries;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.handmark.pulltorefresh.extras.viewpager.PullToRefreshViewPager;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;

public class EntryActivity extends FragmentActivity implements OnRefreshListener<ViewPager> { 

	private PullToRefreshViewPager mPullToRefreshViewPager;
	private ViewPager mPager;
	private PagerAdapter mAdapter;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.entry_activity);

		mPullToRefreshViewPager = (PullToRefreshViewPager) findViewById(R.id.entry_activity_pull_refresh_view_pager);
		mPullToRefreshViewPager.setOnRefreshListener(this);

		mPager = mPullToRefreshViewPager.getRefreshableView();

		// set adapter
		Cursor cursor = getContentResolver().query(Entries.CONTENT_URI, NoisetracksProvider.READ_ENTRY_PROJECTION, null, null, Entries.DEFAULT_SORT_ORDER);
		
		mAdapter = new EntryPagerAdapter<EntryDetailFragment>(getSupportFragmentManager(), EntryDetailFragment.class, NoisetracksProvider.READ_ENTRY_PROJECTION, cursor);
		mPager.setAdapter(mAdapter);
		mPager.setCurrentItem((int) getIntent().getExtras().getLong("item") - 1, false); // select item	
	
	}
	
	@Override
	public void onRefresh(PullToRefreshBase<ViewPager> refreshView) {
		new GetDataTask().execute();
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
		
		public static EntryDetailFragment newInstance() {
			EntryDetailFragment f = new EntryDetailFragment();
			return f;
		}

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
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

			username.setText(getArguments().getString("username"));
			recorded_ago.setText(getArguments().getString("recorded"));
			
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
