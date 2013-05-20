package com.noisetracks.android.syncadapter;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.util.Log;
import com.noisetracks.android.NoisetracksApplication;
import com.noisetracks.android.R;
import com.noisetracks.android.client.NoisetracksRequest;
import com.whiterabbit.postman.ServerInteractionHelper;
import com.whiterabbit.postman.exceptions.SendingCommandException;

import org.apache.http.ParseException;
import org.scribe.model.Verb;

import java.io.IOException;

public class SyncAdapter extends AbstractThreadedSyncAdapter {
    
	private static final String TAG = "SyncAdapter";

    private final AccountManager mAccountManager;
    private final Context mContext;
    
    ServerInteractionHelper mServerHelper;

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContext = context;
        mAccountManager = AccountManager.get(context);
        mServerHelper = ServerInteractionHelper.getInstance(context);
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        
		Log.v(TAG, "Sync.");
		
		try {
			
			String username;
			String apikey;
			
			// use the account manager to request the credentials
			apikey = mAccountManager.blockingGetAuthToken(
					account, mContext.getString(R.string.AUTHTOKEN_TYPE), true); // true -> notifyAuthFailure
			
			username = account.name;

			if (apikey != null)
			{
				// get entries
				Bundle params = new Bundle();
    	        params.putString("format", "json");				// we need json format
    	        params.putString("order_by", "-created");		// newest first
    	        params.putString("audiofile__status", "1");		// only get entries with status = Done
            	
    	        NoisetracksRequest request = new NoisetracksRequest(Verb.GET, NoisetracksApplication.URI_ENTRIES, params);
                try {
                    mServerHelper.sendRestAction(mContext, "SyncAdapter Entries", request);
                } catch (SendingCommandException e) {
                    Log.e(TAG, e.toString());
                }
                
                // get profile
	            Bundle paramsProfile = new Bundle();
	            params.putString("format", "json");				// we need json format
	            params.putString("user__username", username);	// get profile for user
	            NoisetracksRequest requestProfile = new NoisetracksRequest(Verb.GET, NoisetracksApplication.URI_PROFILES, paramsProfile);
                try {
                    mServerHelper.sendRestAction(mContext, "SyncAdapter Profile", requestProfile);
                } catch (SendingCommandException se) {
                    Log.e(TAG, se.toString());
                }
			}
				

		} catch (final AuthenticatorException e) {
			syncResult.stats.numParseExceptions++;
			Log.w(TAG, "AuthenticatorException", e);
		} catch (final OperationCanceledException e) {
			Log.w(TAG, "OperationCanceledExcetpion", e);
		} catch (final IOException e) {
			Log.w(TAG, "IOException", e);
			syncResult.stats.numIoExceptions++;
		} 
		/*
		catch (final AuthenticationException e) {
			mAccountManager.invalidateAuthToken(mContext.getString(R.string.ACCOUNT_TYPE), authtoken);
			syncResult.stats.numAuthExceptions++;
			Log.e(TAG, "AuthenticationException", e);

		}*/
		
		catch (final ParseException e) {
			syncResult.stats.numParseExceptions++;
			Log.e(TAG, "ParseException", e);
		}
		
		
	}
}

