package com.stahlnow.noisetracks.ui;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.Bundle;

import com.actionbarsherlock.app.SherlockActivity;
import com.stahlnow.noisetracks.R;
import com.stahlnow.noisetracks.authenticator.AuthenticationService;
import com.stahlnow.noisetracks.authenticator.AuthenticateActivity;


public class Noisetracks extends SherlockActivity {

	static final int LOGIN_REQUEST = 0;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	
		super.onCreate(savedInstanceState);
		
		Intent intent = this.getIntent();
		
		if (AuthenticationService.accountExists(this)) {
			intent = new Intent(Noisetracks.this, Tabs.class);
			startActivity(intent);
			finish();
			return;
		}
		
		intent = new Intent(Noisetracks.this, AuthenticateActivity.class);
		startActivityForResult(intent, LOGIN_REQUEST);	
		
		
    }
   
    @Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_CANCELED) {
			finish();
			return;
		} else if (requestCode == LOGIN_REQUEST) {
			if (resultCode == RESULT_OK) {
				
				// setup sync
				AccountManager am = AccountManager.get(this);
		        Account a[] = am.getAccountsByType(getString(R.string.ACCOUNT_TYPE));
				ContentResolver.setSyncAutomatically(a[0], getString(R.string.AUTHORITY_PROVIDER), true);
				
				// start main view
				Intent intent = this.getIntent();
				intent = new Intent(Noisetracks.this, Tabs.class);
				startActivity(intent);
				finish();
				return;
			}
		}		
	}
    
    @Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}
    
    
}