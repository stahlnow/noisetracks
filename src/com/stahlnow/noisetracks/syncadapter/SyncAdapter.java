package com.stahlnow.noisetracks.syncadapter;

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

import com.stahlnow.noisetracks.NoisetracksApplication;
import com.stahlnow.noisetracks.R;
import com.stahlnow.noisetracks.client.RESTLoader;
import com.stahlnow.noisetracks.client.RESTLoaderCallbacks;
import com.stahlnow.noisetracks.client.SQLLoaderCallbacks;
import com.stahlnow.noisetracks.provider.NoisetracksProvider;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;

import java.io.IOException;
import java.net.URI;
import java.util.Date;

public class SyncAdapter extends AbstractThreadedSyncAdapter {
    
	private static final String TAG = "SyncAdapter";

    private final AccountManager mAccountManager;
    private final Context mContext;

    private Date mLastUpdated;

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContext = context;
        mAccountManager = AccountManager.get(context);
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        
		Log.v(TAG, "Start syncing ...");
		
		try {
			
			String apikey;
			
			// use the account manager to request the credentials
			apikey = mAccountManager.blockingGetAuthToken(
					account, mContext.getString(R.string.AUTHTOKEN_TYPE), true); // true -> notifyAuthFailure

			if (apikey != null)
			{
				DefaultHttpClient httpclient = new DefaultHttpClient();
				HttpResponse response;
				HttpEntity responseEntity;
				StatusLine responseStatus;
				int statusCode;
				
	            // Get entries from REST api.
		    	HttpGet entries = new HttpGet();
		    	entries.setHeader("Authorization", "ApiKey " + account.name + ":" + apikey);
	            Bundle entriesURLParams = new Bundle();
	            entriesURLParams.putString("format", "json");				// we need json format
	            entriesURLParams.putString("order_by", "-created");			// newest first
	            entriesURLParams.putString("audiofile__status", "1");		// only get entries with status = Done
	        	RESTLoader.attachUriWithQuery(entries, NoisetracksApplication.URI_ENTRIES, entriesURLParams);
	        	response = httpclient.execute(entries);
	        	
	        	responseEntity = response.getEntity();
	        	responseStatus= response.getStatusLine();
	        	statusCode = responseStatus != null ? responseStatus.getStatusCode() : 0;
                
                if (statusCode == HttpStatus.SC_OK) {
                	String json = (responseEntity != null ? EntityUtils.toString(responseEntity) : null);
                	if (json != null)
                		RESTLoaderCallbacks.addEntriesFromJSON(json);
                }
                
                
                // Get profile from REST api.
		    	HttpGet profile = new HttpGet();
		    	profile.setHeader("Authorization", "ApiKey " + account.name + ":" + apikey);
	            Bundle profileURLParams = new Bundle();
	            entriesURLParams.putString("format", "json");					// we need json format
	            entriesURLParams.putString("user__username", account.name);		// username
	        	RESTLoader.attachUriWithQuery(profile, NoisetracksApplication.URI_PROFILES, profileURLParams);
	        	response = httpclient.execute(profile);
	        	
	        	responseEntity = response.getEntity();
                responseStatus = response.getStatusLine();
                statusCode     = responseStatus != null ? responseStatus.getStatusCode() : 0;
                
                if (statusCode == HttpStatus.SC_OK) {
                	String json = (responseEntity != null ? EntityUtils.toString(responseEntity) : null);
                	if (json != null)
                		RESTLoaderCallbacks.addProfilesFromJSON(json);
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

