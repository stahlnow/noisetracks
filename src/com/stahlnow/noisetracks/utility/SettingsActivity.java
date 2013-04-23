package com.stahlnow.noisetracks.utility;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;
import com.stahlnow.noisetracks.NoisetracksApplication;
import com.stahlnow.noisetracks.R;
import com.stahlnow.noisetracks.helper.Helper;
import com.stahlnow.noisetracks.ui.Tabs;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;

public class SettingsActivity extends SherlockPreferenceActivity {
    @SuppressWarnings("deprecation")
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // load from xml
        addPreferencesFromResource(R.xml.preferences);
        
        // logout
        findPreference("logout").setOnPreferenceClickListener(click);
        
        // cache
        findPreference("cache").setOnPreferenceClickListener(click);
        long dirsize = Helper.dirSize(getCacheDir());
        findPreference("cache").setSummary("Got " + dirsize/1024 + "KB.");
        
        
    }
    
    Preference.OnPreferenceClickListener click = new Preference.OnPreferenceClickListener() {

		@SuppressWarnings("deprecation")
		public boolean onPreferenceClick(Preference preference) {
			Intent i = null;
			if (preference.getKey().equals("logout")) {
				NoisetracksApplication.logout();
				return true;
			}
			
			if (preference.getKey().equals("cache")) {
				NoisetracksApplication.getFileSystemPersistence().clear(); // clear cache
				long dirsize = Helper.dirSize(getCacheDir());
		        findPreference("cache").setSummary("Got " + dirsize/1024 + "KB.");
		        return true;
			}
			
			/*
			if (preference.getKey().equals("tos"))
				i = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.last.fm/legal/terms"));
			if (preference.getKey().equals("privacy"))
				i = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.last.fm/legal/privacy"));
			if (preference.getKey().equals("changes"))
				i = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.last.fm/group/Last.fm+Android/forum/114391/_/589152"));
			*/
			/*
			if (i != null)
				startActivity(i);
			*/
			return false;
		}
	};
	
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			Intent parentActivityIntent = new Intent(this, Tabs.class);
			parentActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(parentActivityIntent);
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
}
