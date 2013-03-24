package com.stahlnow.noisetracks.ui;

import java.util.ArrayList;

import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.MenuInflater;

import com.stahlnow.noisetracks.R;
import com.stahlnow.noisetracks.authenticator.AuthenticationService;
import com.stahlnow.noisetracks.receivers.RecordingReceiver;
import com.stahlnow.noisetracks.utility.AppLog;
import com.stahlnow.noisetracks.utility.AppSettings;
import com.stahlnow.noisetracks.utility.SettingsActivity;

public class Tabs extends SherlockFragmentActivity {

	TabHost mTabHost;
	ViewPager mViewPager;
	TabsAdapter mTabsAdapter;

	@SuppressLint("NewApi")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// register filter for 'account changed'.
		registerReceiver(receiver, new IntentFilter(
				"android.accounts.LOGIN_ACCOUNTS_CHANGED"));

		// set default settings
		AppSettings.setLoggingInterval(this, 1); // each minute
		
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
		Bundle profile_args = new Bundle();
		profile_args.putString("username", username);
		mTabsAdapter.addTab(mTabHost.newTabSpec("profile").setIndicator("Me"), ProfileActivity.ProfileListFragment.class, profile_args);

		// ridiculously complicated method to set tab indicator and tab text colors.
		for (int i = 0; i < mTabHost.getTabWidget().getChildCount(); i++) {
			
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
				mTabHost.getTabWidget()
						.getChildAt(i)
						.setBackground(
								getResources()
										.getDrawable(
												R.drawable.tab_indicator_ab_noise));
			
			TextView tv = (TextView) mTabHost.getTabWidget().getChildAt(i)
					.findViewById(android.R.id.title);
			tv.setTextColor(getResources().getColor(R.color.light_grey));
		}

		// try to restore tab
		if (savedInstanceState != null) {
			mTabHost.setCurrentTabByTag(savedInstanceState.getString("tab"));
		}
	}

	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (!AuthenticationService.accountExists(context)) {
				AppLog.logString("Noisetracks Account has been removed. Cleaning up...");
				System.gc();
				System.exit(0);
			}
		}
	};

	@Override
	protected void onDestroy() {
		unregisterReceiver(receiver);
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

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {		
		//menu.findItem(R.id.menu_toggle_tracking).setIcon(AppSettings.getServiceRunning(this)? R.drawable._record : R.drawable.av_stop);
		menu.findItem(R.id.menu_toggle_tracking).setTitle(AppSettings.getServiceRunning(this)? R.string.menu_stop_tracking : R.string.menu_start_tracking);
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_record:
			// TODO start recording activity
			return true;
		case R.id.menu_toggle_tracking:
			toggleTracking(AppSettings.getServiceRunning(this), AppSettings.getLoggingInterval(this));
			
			// check/uncheck menu item
			//item.setCheckable(true); // this is already set in main_menu.xml but not working somehow.
			//item.setChecked(AppSettings.getServiceRunning(this));
						
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
				invalidateOptionsMenu();
			return true;
		case R.id.menu_settings:
			Intent i = new Intent().setClass(Tabs.this, SettingsActivity.class);
			startActivity(i);
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private void toggleTracking(boolean isStart, float interval) {
		AlarmManager manager = (AlarmManager) getSystemService(Service.ALARM_SERVICE);
		PendingIntent loggerIntent = PendingIntent.getBroadcast(this, 0,
				new Intent(this, RecordingReceiver.class), 0);

		if (isStart) {
			manager.cancel(loggerIntent);
			AppSettings.setServiceRunning(this, false);
			AppLog.logString("Service Stopped.");
		} else {

			long duration = (int) (interval * 60.0f * 1000.0f); // calculate
																// duration in
																// milliseconds

			manager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
					SystemClock.elapsedRealtime(), duration, loggerIntent);

			AppSettings.setServiceRunning(this, true);

			AppLog.logString("Service Started with interval " + interval * 60 + " seconds.");
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

		@Override
		public int getCount() {
			return mTabs.size();
		}

		@Override
		public Fragment getItem(int position) {
			TabInfo info = mTabs.get(position);
			return Fragment.instantiate(mContext, info.clss.getName(),
					info.args);
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
