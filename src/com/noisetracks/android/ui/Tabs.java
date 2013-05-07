package com.noisetracks.android.ui;

import java.util.ArrayList;

import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.MenuInflater;

import com.noisetracks.android.NoisetracksApplication;
import com.noisetracks.android.R;
import com.noisetracks.android.ui.FeedActivity.FeedListFragment;
import com.noisetracks.android.ui.ProfileActivity.ProfileListFragment;
import com.noisetracks.android.utility.AppSettings;
import com.noisetracks.android.utility.SettingsActivity;

public class Tabs extends SherlockFragmentActivity implements OnTouchListener {

	private static final String TAG = "Tabs";
	
	TabHost mTabHost;
	ViewPager mViewPager;
	TabsAdapter mTabsAdapter;

	@SuppressLint("NewApi")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if (getIntent().getBooleanExtra("EXIT", false)) {
			finish();
			return;
		}
		
		// disable 'up' navigation in action bar for home screen
		getSupportActionBar().setHomeButtonEnabled(false);
		getSupportActionBar().setDisplayHomeAsUpEnabled(false);

		// setup view
		setContentView(R.layout.tabs);
		mTabHost = (TabHost) findViewById(android.R.id.tabhost);
		mTabHost.setup();
		mViewPager = (ViewPager) findViewById(R.id.pager);
		mTabsAdapter = new TabsAdapter(this, mTabHost, mViewPager);
		// add explore tab
		mTabsAdapter.addTab(mTabHost.newTabSpec("explore").setIndicator("Explore"), FeedActivity.FeedListFragment.class, null);
		// add profile tab (Me)
		String username = AccountManager.get(this).getAccountsByType(getString(R.string.ACCOUNT_TYPE))[0].name;
		AppSettings.setUsername(this, username); // TODO: move somewhere else (ie after login)
		Bundle profile_args = new Bundle();
		profile_args.putString("username", username);
		mTabsAdapter.addTab(mTabHost.newTabSpec("profile").setIndicator("Me"), ProfileActivity.ProfileListFragment.class, profile_args);

		// ridiculously complicated method to set tab indicator and tab text colors.
		// set on touch listener
		for (int i = 0; i < mTabHost.getTabWidget().getChildCount(); i++) {
			View v = mTabHost.getTabWidget().getChildAt(i);
			
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
				v.setBackground(getResources().getDrawable(R.drawable.tab_indicator_ab_noise));
			
			TextView tv = (TextView) v.findViewById(android.R.id.title);
			tv.setTextColor(getResources().getColor(R.color.light_grey));
			
			v.setOnTouchListener(this);	

		}

		// try to restore tab
		if (savedInstanceState != null) {
			mTabHost.setCurrentTabByTag(savedInstanceState.getString("tab"));
		}
	}
	
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		boolean consumed = false;
		// use mTabHost.getCurrentTabView to decide if the current tab is touched again
		if (event.getAction() == MotionEvent.ACTION_DOWN && v.equals(mTabHost.getCurrentTabView())) {
			if (mTabHost.getCurrentTabTag().equals("explore")) {
				FeedListFragment f = (FeedListFragment) mTabsAdapter.findFragment(0);
				f.onReselectedTab();
				consumed = true;
			} else if (mTabHost.getCurrentTabTag().equals("profile")) {
				ProfileListFragment f = (ProfileListFragment) mTabsAdapter.findFragment(1);
				f.onReselectedTab();
				consumed = true;
			}
		}
		return consumed;
	}


	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString("tab", mTabHost.getCurrentTabTag());
	}

	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		return super.onCreateOptionsMenu(menu);
	}

	/*
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {		
		menu.findItem(R.id.menu_toggle_tracking).setTitle(AppSettings.getServiceRunning(this)? R.string.menu_stop_tracking : R.string.menu_start_tracking);
		return super.onPrepareOptionsMenu(menu);
	}
	*/
	

	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_record:
			Intent record = new Intent(this, RecordingActivity.class);
			startActivity(record);
			return true;
		/*
		case R.id.menu_toggle_tracking:
			NoisetracksApplication.toggleTracking(AppSettings.getServiceRunning(this), AppSettings.getTrackingInterval(this));
			//invalidateOptionsMenu();
			return true;
		*/
		case R.id.menu_settings:
			startActivity (new Intent(this, SettingsActivity.class));
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * This is a helper class that implements the management of tabs and all
	 * details of connecting a ViewPager with associated TabHost. It relies on a
	 * trick. Normally a tab host has a simple API for supplying a View or
	 * Intent that each tab will show. This is not sufficient for switching
	 * between pages. So instead we make the content part of the tab host 0dp
	 * high (it is not shown) and the TabsAdapter supplies its own dummy view to
	 * show as the tab content. It listens to changes in tabs, and takes care of
	 * switch to the correct paged in the ViewPager whenever the selected tab
	 * changes.
	 */
	public static class TabsAdapter extends FragmentPagerAdapter implements
			TabHost.OnTabChangeListener, ViewPager.OnPageChangeListener {
		private final Context mContext;
		private final TabHost mTabHost;
		private final ViewPager mViewPager;
		private final ArrayList<TabInfo> mTabs = new ArrayList<TabInfo>();

		static final class TabInfo {
			@SuppressWarnings("unused")
			private final String tag;
			private final Class<?> clss;
			private final Bundle args;

			TabInfo(String _tag, Class<?> _class, Bundle _args) {
				tag = _tag;
				clss = _class;
				args = _args;
			}
		}

		static class DummyTabFactory implements TabHost.TabContentFactory {
			private final Context mContext;

			public DummyTabFactory(Context context) {
				mContext = context;
			}

			@Override
			public View createTabContent(String tag) {
				View v = new View(mContext);
				v.setMinimumWidth(0);
				v.setMinimumHeight(0);
				return v;
			}
		}

		public TabsAdapter(FragmentActivity activity, TabHost tabHost,
				ViewPager pager) {
			super(activity.getSupportFragmentManager());
			mContext = activity;
			mTabHost = tabHost;
			mViewPager = pager;
			mTabHost.setOnTabChangedListener(this);
			mViewPager.setAdapter(this);
			mViewPager.setOnPageChangeListener(this);
		}

		public void addTab(TabHost.TabSpec tabSpec, Class<?> clss, Bundle args) {
			tabSpec.setContent(new DummyTabFactory(mContext));
			String tag = tabSpec.getTag();

			TabInfo info = new TabInfo(tag, clss, args);
			mTabs.add(info);
			mTabHost.addTab(tabSpec);
			notifyDataSetChanged();
		}
		
		public Fragment findFragment(int position) {
            String name = "android:switcher:" + mViewPager.getId() + ":" + position;
            FragmentManager fm = ((FragmentActivity) mContext).getSupportFragmentManager();
            Fragment fragment = fm.findFragmentByTag(name);
            if (fragment == null) {
                fragment = getItem(position);
            }
            return fragment;
        }

		@Override
		public int getCount() {
			return mTabs.size();
		}

		@Override
		public Fragment getItem(int position) {
			TabInfo info = mTabs.get(position);
			return Fragment.instantiate(mContext, info.clss.getName(), info.args);
		}

		@Override
		public void onTabChanged(String tabId) {
			int position = mTabHost.getCurrentTab();
			mViewPager.setCurrentItem(position);
		}

		@Override
		public void onPageScrolled(int position, float positionOffset,
				int positionOffsetPixels) {
		}

		@Override
		public void onPageSelected(int position) {
			// Unfortunately when TabHost changes the current tab, it kindly
			// also takes care of putting focus on it when not in touch mode.
			// The jerk.
			// This hack tries to prevent this from pulling focus out of our
			// ViewPager.
			TabWidget widget = mTabHost.getTabWidget();
			int oldFocusability = widget.getDescendantFocusability();
			widget.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
			mTabHost.setCurrentTab(position);
			widget.setDescendantFocusability(oldFocusability);
		}

		@Override
		public void onPageScrollStateChanged(int state) {
		}

		
	}
}
