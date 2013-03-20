package com.stahlnow.noisetracks.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.stahlnow.noisetracks.authenticator.AuthenticationService;
import com.stahlnow.noisetracks.authenticator.AuthenticateActivity;


public class Noisetracks extends Activity {

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