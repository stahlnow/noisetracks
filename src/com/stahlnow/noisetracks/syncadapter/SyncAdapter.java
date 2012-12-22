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

import com.stahlnow.noisetracks.R;
import com.stahlnow.noisetracks.utility.AppLog;

import org.apache.http.ParseException;
import org.apache.http.auth.AuthenticationException;
import org.json.JSONException;

import java.io.IOException;
import java.util.Date;

/**
 * SyncAdapter implementation for syncing sample SyncAdapter contacts to the
 * platform ContactOperations provider.
 */
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
        
		AppLog.logString("onPerformSync: Start ...");

		String apikey = null;
		
		try {
			// use the account manager to request the credentials
			apikey = mAccountManager.blockingGetAuthToken(
					account, mContext.getString(R.string.AUTHTOKEN_TYPE), true); // true -> notifyAuthFailure

			if (apikey != null) {
				AppLog.logString(apikey);
			}
			
			// fetch updates from the sample service over the cloud
			// users = NetworkUtilities.fetchFriendUpdates(account, authtoken,
			// mLastUpdated);

			// update the last synced date.
			mLastUpdated = new Date();
			// update platform contacts.

			// ContactManager.syncContacts(mContext, account.name, users);

			// fetch and update status messages for all the synced users.
			// statuses = NetworkUtilities.fetchFriendStatuses(account,
			// authtoken);
			// ContactManager.insertStatuses(mContext, account.name, statuses);

		} catch (final AuthenticatorException e) {
			syncResult.stats.numParseExceptions++;
			Log.e(TAG, "AuthenticatorException", e);
		} catch (final OperationCanceledException e) {
			Log.e(TAG, "OperationCanceledExcetpion", e);
		} catch (final IOException e) {
			Log.e(TAG, "IOException", e);
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
		/*
		catch (final JSONException e) {
			syncResult.stats.numParseExceptions++;
			Log.e(TAG, "JSONException", e);
		}
		*/
	}
}

