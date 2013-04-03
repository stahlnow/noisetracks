/*
 * Copyright (C) 2010 The Android Open Source Project
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.stahlnow.noisetracks.authenticator;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Window;

import com.stahlnow.noisetracks.R;
import com.stahlnow.noisetracks.provider.NoisetracksContract;
import com.stahlnow.noisetracks.utility.AppLog;

/**
 * Activity which displays login screen to the user.
 */
public class AuthenticateActivity extends SherlockFragmentActivity {
    public static final String PARAM_CONFIRMCREDENTIALS = "confirmCredentials";
    public static final String PARAM_PASSWORD = "password";
    public static final String PARAM_USERNAME = "identification";
    public static final String PARAM_AUTHTOKEN_TYPE = "authtokenType";

    private static final String TAG = "AuthenticatorActivity";

    private AccountAuthenticatorResponse mAccountAuthenticatorResponse = null;
    private Bundle mResultBundle = null;
 
    private ProgressDialog progressDialog = null;
    
    private AccountManager mAccountManager;
    private Thread mAuthThread;
    private String mAuthtoken;
    private String mAuthtokenType;
    
    private static final int SIGNUP_REQUEST = 0;

    /**
     * If set we are just checking that the user knows their credentials; this
     * doesn't cause the user's password to be changed on the device.
     */
    private Boolean mConfirmCredentials = false;

    /** for posting authentication attempts back to UI thread */
    private TextView mMessage;
    private String mPassword;
    private EditText mPasswordEdit;

    /** Was the original caller asking for an entirely new account? */
    protected boolean mRequestNewAccount = false;

    private String mUsername;
    private EditText mUsernameEdit;

    
    /**
     * Set the result that is to be sent as the result of the request that caused this
     * Activity to be launched. If result is null or this method is never called then
     * the request will be canceled.
     * @param result this is returned as the result of the AbstractAccountAuthenticator request
     */
    public final void setAccountAuthenticatorResult(Bundle result) {
        mResultBundle = result;
    }
    
    /**
     * Retreives the AccountAuthenticatorResponse from either the intent of the icicle, if the
     * icicle is non-zero.
     * @param savedInstanceState the save instance data of this Activity, may be null
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	
        super.onCreate(savedInstanceState);
        
        // hide action bar
        getWindow().requestFeature((int) Window.FEATURE_ACTION_BAR);
        getSupportActionBar().hide();

        mAccountAuthenticatorResponse = getIntent().getParcelableExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE);

        if (mAccountAuthenticatorResponse != null) {
            mAccountAuthenticatorResponse.onRequestContinued();
        }
        
        mAccountManager = AccountManager.get(this);
        
        final Intent intent = getIntent();
        mUsername = intent.getStringExtra(PARAM_USERNAME);
        mAuthtokenType = intent.getStringExtra(PARAM_AUTHTOKEN_TYPE);
        
        mRequestNewAccount = mUsername == null;
        mConfirmCredentials = intent.getBooleanExtra(PARAM_CONFIRMCREDENTIALS, false);
        
        setContentView(R.layout.login_activity);

        mMessage = (TextView) findViewById(R.id.message);
        mUsernameEdit = (EditText) findViewById(R.id.username_edit);
        mPasswordEdit = (EditText) findViewById(R.id.password_edit);
        // hack to change typeface, typeface argument in xml resource is not working.
        mPasswordEdit.setTypeface(Typeface.SANS_SERIF);
        mPasswordEdit.setTransformationMethod(new PasswordTransformationMethod());

        mUsernameEdit.setText(mUsername);
        mMessage.setText(getMessage());

    }
   
    
    /**
     * Handles onClick event on the sign in button. Sends username/password to
     * the server for authentication.
     * 
     * @param view The sign in button for which this method is invoked
     */
    public void handleLogin(View view) {
        if (mRequestNewAccount) {
            mUsername = mUsernameEdit.getText().toString();
        }
        mPassword = mPasswordEdit.getText().toString();
        if (TextUtils.isEmpty(mUsername) || TextUtils.isEmpty(mPassword)) {
            mMessage.setText(getMessage());
        } else {
            // Start authenticating...
        	new AuthenticateTask(this).execute(mUsername, mPassword);
        }
    }
    
    /**
     * Handles onClick event on the sign up button. Starts Sign up activity. 
     * 
     * @param view The sign up button for which this method is invoked
     */
    public void handleSignup(View view) {
    	Intent intent = this.getIntent();
    	intent = new Intent(AuthenticateActivity.this, SignupActivity.class);
		startActivityForResult(intent, SIGNUP_REQUEST);
    }
    
    @Override
   	public void onActivityResult(int requestCode, int resultCode, Intent data) {
   		if (resultCode == Activity.RESULT_CANCELED) {
   			finish();
   			return;
   		} else if (requestCode == SIGNUP_REQUEST) { // try sign in user
   			if (resultCode == RESULT_OK) {
   				mUsername = data.getExtras().getString("username");
   				mPassword = data.getExtras().getString("password");
   				mUsernameEdit.setText(mUsername);
   				mPasswordEdit.setText(mPassword);
   				new AuthenticateTask(this).execute(mUsername, mPassword);   				
   				return;
   			}
   		}		
   	}


    /**
     * Called when response is received from the server for confirm credentials
     * request. See onAuthenticationResult(). Sets the
     * AccountAuthenticatorResult which is sent back to the caller.
     * 
     * @param the confirmCredentials result.
     */
	protected void finishConfirmCredentials(boolean result, String password) {
		AppLog.logString("finishConfirmCredentials()");
        final Account account = new Account(mUsername, getString(R.string.ACCOUNT_TYPE));
        mAccountManager.setPassword(account, password);
        final Intent intent = new Intent();
        intent.putExtra(AccountManager.KEY_BOOLEAN_RESULT, result);
        setAccountAuthenticatorResult(intent.getExtras());
        setResult(RESULT_OK, intent);
        finish();
    }

    /**
     * 
     * Called when response is received from the server for authentication
     * request. See onAuthenticationResult(). Sets the
     * AccountAuthenticatorResult which is sent back to the caller. Also sets
     * the authToken in AccountManager for this account.
     * 
     * @param the confirmCredentials result.
     */

    protected void finishLogin(String password) {
        AppLog.logString("finishLogin()");
        final Account account = new Account(mUsername, getString(R.string.ACCOUNT_TYPE));

        if (mRequestNewAccount) {
            mAccountManager.addAccountExplicitly(account, password, null);
            // Set noisetracks sync for this account.
            ContentResolver.setSyncAutomatically(account, NoisetracksContract.AUTHORITY, true);
        } else {
            mAccountManager.setPassword(account, password);
        }
        final Intent intent = new Intent();
        mAuthtoken = password;
        intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, mUsername);
        intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, getString(R.string.ACCOUNT_TYPE));
        if (mAuthtokenType != null && mAuthtokenType.equals(getString(R.string.AUTHTOKEN_TYPE))) {
            intent.putExtra(AccountManager.KEY_AUTHTOKEN, mAuthtoken);
        }
        setAccountAuthenticatorResult(intent.getExtras());
        setResult(RESULT_OK, intent);
        finish();
    }

    /**
     * Called when the authentication process completes (see attemptLogin()).
     */
    public void onAuthenticationResult(boolean result, String password) {
        
        if (result) {
        	AppLog.logString("onAuthenticationResult(" + result + ")");
            if (!mConfirmCredentials) {
                finishLogin(password);
            } else {
                finishConfirmCredentials(true, password);
            }
        } else {
            AppLog.logString("onAuthenticationResult: failed to authenticate");
            if (mRequestNewAccount) {
                // "Please enter a valid username/password.
                mMessage.setText(getText(R.string.login_activity_loginfail_text_both));
            } else {
                // "Please enter a valid password." (Used when the
                // account is already in the database but the password
                // doesn't work.)
                mMessage.setText(getText(R.string.login_activity_loginfail_text_pwonly));
            }
        }
    }

    /**
     * Returns the message to be displayed at the top of the login dialog box.
     */
    private CharSequence getMessage() {
        getString(R.string.app_name);
        if (TextUtils.isEmpty(mUsername)) {
            // If no username, then we ask the user to log in using an
            // appropriate service.
            final CharSequence msg = ""; // <edit> removed sign in message
            return msg;
        }
        if (TextUtils.isEmpty(mPassword)) {
            // We have an account but no password
            return getText(R.string.login_activity_loginfail_text_pwmissing);
        }
        return null;
    }
    
    /**
     * Sends the result or a Constants.ERROR_CODE_CANCELED error if a result isn't present.
     */
    public void finish() {
        if (mAccountAuthenticatorResponse != null) {
            // send the result bundle back if set, otherwise send an error.
            if (mResultBundle != null) {
                mAccountAuthenticatorResponse.onResult(mResultBundle);
            } else {
                mAccountAuthenticatorResponse.onError(AccountManager.ERROR_CODE_CANCELED, "canceled");
            }
            mAccountAuthenticatorResponse = null;
        }
        super.finish();
    }
    
    
    /**
     * Hides the progress UI for a lengthy operation.
     */
    
    protected void hideProgress() {
    	progressDialog.hide();
    }

    /**
     * Shows the progress UI for a lengthy operation.
     */
    
    protected void showProgress() {
    	progressDialog = new ProgressDialog(this);
    	progressDialog.getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    	progressDialog.setMessage(getText(R.string.ui_activity_authenticating));
    	progressDialog.setIndeterminate(true);
    	progressDialog.setCancelable(true);
    	progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                Log.i(TAG, "dialog cancel has been invoked");
                if (mAuthThread != null) {
                    mAuthThread.interrupt();
                    finish();
                }
            }
        });
    	progressDialog.show();
    }
    
}
