package com.stahlnow.noisetracks.ui;

import java.util.ArrayList;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import com.stahlnow.noisetracks.R;
import com.stahlnow.noisetracks.authenticator.AuthenticationService;
import com.stahlnow.noisetracks.provider.NoisetracksContract.Entries;
import com.stahlnow.noisetracks.utility.AppSettings;

public class Tabs extends FragmentActivity {
	
	TabHost mTabHost;
    ViewPager  mViewPager;
    TabsAdapter mTabsAdapter;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // register filter for 'account changed'.
        registerReceiver(receiver, new IntentFilter("android.accounts.LOGIN_ACCOUNTS_CHANGED"));
        
        // set default settings
     	AppSettings.setLoggingInterval(this, 1); // each minute
        
        // setup view
        setContentView(R.layout.main);
        mTabHost = (TabHost)findViewById(android.R.id.tabhost);
        mTabHost.setup();
        mViewPager = (ViewPager)findViewById(R.id.pager);
        mTabsAdapter = new TabsAdapter(this, mTabHost, mViewPager);      
        // add explore tab
        mTabsAdapter.addTab(mTabHost.newTabSpec("explore").setIndicator("Explore"), TabsExplore.ExploreListFragment.class, null);
        // add profile tab (Me)
        Bundle profile_args = new Bundle();
        profile_args.putString("username", "stahl");
        mTabsAdapter.addTab(mTabHost.newTabSpec("profile").setIndicator("Me"), ProfileActivity.ProfileListFragment.class, profile_args);
        
        for (int i = 0;i < mTabHost.getTabWidget().getChildCount(); i++) 
        {
        	mTabHost.getTabWidget().getChildAt(i).setBackground(getResources().getDrawable(R.drawable.tab_indicator));
        	TextView tv = (TextView) mTabHost.getTabWidget().getChildAt(i).findViewById(android.R.id.title);
            tv.setTextColor(getResources().getColor(R.color.light_grey));
        }
        
        if (savedInstanceState != null) {
            mTabHost.setCurrentTabByTag(savedInstanceState.getString("tab"));
        }
    }
    
    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
        	if (!AuthenticationService.accountExists(context)) {
        		// TODO on remove account: eventually remove data on disk, cleanup db etc.
        		finish();
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
    
	/*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	
    	MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
    	return super.onCreateOptionsMenu(menu);    	
    	
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
    	if (AppSettings.getServiceRunning(this))
    		menu.findItem(R.id.menu_toggle_tracking).setTitle(R.string.menu_stop_tracking);
    	else
    		menu.findItem(R.id.menu_toggle_tracking).setTitle(R.string.menu_start_tracking);
    	
    	return super.onPrepareOptionsMenu(menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_toggle_tracking:
            	toggleTracking(AppSettings.getServiceRunning(this), AppSettings.getLoggingInterval(this));
            	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            		invalidateOptionsMenu();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
    private void toggleTracking(boolean isStart, float interval){
    	AlarmManager manager = (AlarmManager)getSystemService(Service.ALARM_SERVICE);
    	PendingIntent loggerIntent = PendingIntent.getBroadcast(this, 0, new Intent(this, RecordingReceiver.class), 0);
    	
    	if (isStart) {    		
    		manager.cancel(loggerIntent);
    		AppSettings.setServiceRunning(this, false);
    		AppLog.logString("Service Stopped.");
    	} else {
    		
    		long duration = (int)(interval * 60.0f * 1000.0f); // calculate duration in milliseconds
    		
    		manager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
    				SystemClock.elapsedRealtime(), duration, loggerIntent);
    		
    		AppSettings.setServiceRunning(this, true);
    		
    		AppLog.logString("Service Started with interval " + interval * 60 + " seconds.");
    	}
    }
    */
    
    
    /**
     * This is a helper class that implements the management of tabs and all
     * details of connecting a ViewPager with associated TabHost.  It relies on a
     * trick.  Normally a tab host has a simple API for supplying a View or
     * Intent that each tab will show.  This is not sufficient for switching
     * between pages.  So instead we make the content part of the tab host
     * 0dp high (it is not shown) and the TabsAdapter supplies its own dummy
     * view to show as the tab content.  It listens to changes in tabs, and takes
     * care of switch to the correct paged in the ViewPager whenever the selected
     * tab changes.
     */
    public static class TabsAdapter extends FragmentPagerAdapter
            implements TabHost.OnTabChangeListener, ViewPager.OnPageChangeListener {
        private final Context mContext;
        private final TabHost mTabHost;
        private final ViewPager mViewPager;
        private final ArrayList<TabInfo> mTabs = new ArrayList<TabInfo>();

        static final class TabInfo {
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

        public TabsAdapter(FragmentActivity activity, TabHost tabHost, ViewPager pager) {
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
            return Fragment.instantiate(mContext, info.clss.getName(), info.args);
        }

        @Override
        public void onTabChanged(String tabId) {
            int position = mTabHost.getCurrentTab();
            mViewPager.setCurrentItem(position);
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
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
