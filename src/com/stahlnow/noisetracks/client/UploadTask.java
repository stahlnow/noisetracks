package com.stahlnow.noisetracks.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import com.stahlnow.noisetracks.NoisetracksApplication;
import com.stahlnow.noisetracks.R;
import com.stahlnow.noisetracks.provider.NoisetracksContract.Entries;
import com.stahlnow.noisetracks.ui.ProfileActivity;

public class UploadTask extends AsyncTask<Uri, Void, Boolean> {
	
	private static final String TAG = "UploadTask";

	private final Handler mHandler = new Handler();
	private Context mContext;
	
	private Uri mUri; 			// local entry uri
	private String mUsername;	// account username
	private String mApiKey;		// account api key
	
	private String mFilename;
	private float mLat;
	private float mLng;
	private String mRecorded;
	private String mAudioUuid;		// audio uuid received from server after upload of file
	
	public UploadTask(Context context) {
        this.mContext = context;
    }
	
	@Override
	protected Boolean doInBackground(Uri... params) {
		
		Boolean result = false;
		mUri = params[0];
		
		AccountManager am = AccountManager.get(mContext);
        Account a[] = am.getAccountsByType(mContext.getString(R.string.ACCOUNT_TYPE));
        if (a[0] != null) {
        	try {
				mApiKey = am.blockingGetAuthToken(a[0], mContext.getString(R.string.AUTHTOKEN_TYPE), true);
				mUsername = a[0].name;				
			} catch (OperationCanceledException e) {
				Log.e(TAG, e.toString()); return false;
			} catch (AuthenticatorException e) {
				Log.e(TAG, e.toString()); return false;
			} catch (IOException e) {
				Log.e(TAG, e.toString()); return false;
			}
        }
		
		// get entry
		Cursor c = mContext.getContentResolver().query(mUri, null, null, null, null);
		if (c != null) {
			if (c.moveToFirst()) {
				mFilename = c.getString(c.getColumnIndex(Entries.COLUMN_NAME_FILENAME));
				mLat = c.getFloat(c.getColumnIndex(Entries.COLUMN_NAME_LATITUDE));
				mLng = c.getFloat(c.getColumnIndex(Entries.COLUMN_NAME_LONGITUDE));
				mRecorded = c.getString(c.getColumnIndex(Entries.COLUMN_NAME_RECORDED));
				
				if (uploadAudioFile()) {
					
					Log.v(TAG, "Uploaded audio file.");
					
					if (postEntry()) {
						Log.v(TAG, "Posted entry.");
						result = true;
					}
					
				}
				
			}
		}
		c.close();
		
		sendResult(result);
		return result;
		
	}
	
	@Override
	protected void onPreExecute () {
		super.onPreExecute();
	}
	
	
	@Override
    protected void onPostExecute(Boolean result) {
		
    }
	
	private Boolean uploadAudioFile() {
		
    	DefaultHttpClient httpclient = new DefaultHttpClient();
    	HttpPost httppost = new HttpPost(NoisetracksApplication.URI_UPLOAD.toString());
    	
    	// set header (see http://django-tastypie.readthedocs.org/en/latest/authentication_authorization.html#apikeyauthentication)
		httppost.setHeader("Authorization", "ApiKey " + mUsername + ":" + mApiKey);
    	
        MultipartEntity entity = new MultipartEntity();
        File file = new File(mFilename);
        FileBody wavfile = new FileBody(file);
        entity.addPart("wavfile", wavfile);
        httppost.setEntity(entity);
    	
    	try {
			HttpResponse response = httpclient.execute(httppost);
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_CREATED) {
				InputStream is = response.getEntity().getContent();
				BufferedReader rd = new BufferedReader(new InputStreamReader(is));
				String uuid = rd.readLine();	// uuid is first and only line in response
				if (uuid != null) {
					mAudioUuid = uuid;
					rd.close();
					return true;				// success
				} else {
					Log.e(TAG, "Response is emtpy.");
				}
			} else {
				Log.e(TAG, "Unexpected response code: " + response.getStatusLine().getStatusCode());
			}
		} catch (ClientProtocolException e) {
			Log.e(TAG, e.toString());
		} catch (IOException e) {
			Log.e(TAG, e.toString());
		}
    	
    	return false;
    	
	}
	
	
	private Boolean postEntry() {
		
		// create json object for POST
		JSONObject json = new JSONObject();     
	    try {
	    	JSONObject coordinates = new JSONObject();
	    	JSONArray points = new JSONArray();
	    	points.put(mLng); points.put(mLat); // django PointField is [Longitude, Latitude]
	    	coordinates.put("coordinates", points);
	    	coordinates.put("type", "Point");
	    	json.put("location", coordinates);
		    json.put("recorded", mRecorded);
		    json.put("audio", mAudioUuid);
		} catch (JSONException e) {
			Log.e(TAG, "Could not create JSON. " + e.toString());
			return false;
		}  
	    
    	DefaultHttpClient httpclient = new DefaultHttpClient();
    	HttpPost httppost = new HttpPost(NoisetracksApplication.URI_ENTRIES.toString());
    	
    	// set headers (see http://django-tastypie.readthedocs.org/en/latest/authentication_authorization.html#apikeyauthentication)
		httppost.setHeader("Authorization", "ApiKey " + mUsername + ":" + mApiKey);
		httppost.setHeader("Content-Type", "application/json");
		
		try {
			StringEntity j = new StringEntity(json.toString());
	    	j.setContentEncoding("UTF-8");
	    	j.setContentType("application/json");
	    	httppost.setEntity(j);
	    	
			HttpResponse response = httpclient.execute(httppost); // post
			
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_CREATED) {
				return true; // success
			} else {
				Log.e(TAG, "Unexpected response code: " + response.getStatusLine().getStatusCode());
			}
		} catch (ClientProtocolException e) {
			Log.e(TAG, e.toString());
		} catch (IOException e) {
			Log.e(TAG, e.toString());
		}
		
		return false;
	}
	
	
    private void sendResult(final Boolean result) {
        if (mHandler == null || mContext == null) {
            return;
        }
        mHandler.post(new Runnable() {
            public void run() {
                ProfileActivity.onUploadResult(result, mUri);
            }
        });
    } 
       

}


