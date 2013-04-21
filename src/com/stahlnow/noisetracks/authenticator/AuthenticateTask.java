package com.stahlnow.noisetracks.authenticator;

import java.io.IOException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.widget.LinearLayout.LayoutParams;

import com.stahlnow.noisetracks.NoisetracksApplication;
import com.stahlnow.noisetracks.R;
import com.stahlnow.noisetracks.utility.AppLog;
import com.stahlnow.noisetracks.utility.AppSettings;

/**
 * Provides utility methods for communicating with the server.
 */
public class AuthenticateTask extends AsyncTask<String, Void, Boolean> {

	private final Handler mHandler = new Handler();
	private Context mContext;
	private ProgressDialog mProgress;
	
	private static String mKey = "";
		
	public AuthenticateTask(Context context) {
        this.mContext = context;
        mProgress = new ProgressDialog(context);
    }
	
	@Override
	protected Boolean doInBackground(String... params) {

		Boolean result = authenticate(params[0], params[1]);
		sendResult( result );
		return result;
	}
	
	@Override
	protected void onPreExecute () {
		super.onPreExecute();
		mProgress.getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		mProgress.setMessage(mContext.getText(R.string.ui_activity_authenticating));
		mProgress.setIndeterminate(true);
		mProgress.show();
	}
	
	
	@Override
    protected void onPostExecute(Boolean result) {
		mProgress.dismiss();
    }
	
	
	public static Boolean authenticate(String user, String password) {
		
		DefaultHttpClient httpclient = new DefaultHttpClient();
		
		try {
			// Set Basic Authentication credentials in header
			httpclient.getCredentialsProvider().setCredentials(
					new AuthScope(NoisetracksApplication.HOST, NoisetracksApplication.HTTP_PORT),	//TODO set to 443 = SSL
					new UsernamePasswordCredentials(user, password));

			// Request key from api
			HttpGet httpget = new HttpGet(NoisetracksApplication.DOMAIN + "/api/v1/token/auth/?format=json"); // https = SSL
			HttpResponse response = httpclient.execute(httpget);
			
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
			
                AppLog.logString("Successful authentication: " + response.getStatusLine());
			
                HttpEntity entity = response.getEntity();
                
                
                if (entity != null) {
                	String json = EntityUtils.toString(entity);
                	try {
						JSONObject j = new JSONObject(json);
						mKey = j.getString("key");
                	} catch (JSONException e) {
                		AppLog.logString("Error authenticating. Failed to parse JSON. " + e.toString());
						return false;
					} finally {
						entity.consumeContent();
					}
                }
                return true; // success
			} else {
				AppLog.logString("Error authenticating: " + response.getStatusLine());
                return false;
			}
            
		} catch (final IOException e) {
			AppLog.logString("IOException when getting authtoken" + e.toString());
			return false;
			
		} finally {
			httpclient.getConnectionManager().shutdown();
			AppLog.logString("getAuthtoken completing");
		}
	}

    /**
     * Sends the authentication response from server back to the caller main UI
     * thread through its handler.
     * 
     * @param result The boolean holding authentication result
     * @param key The api key
     */
    private void sendResult(final Boolean result) {
        if (mHandler == null || mContext == null) {
            return;
        }
        mHandler.post(new Runnable() {
            public void run() {
                ((AuthenticateActivity) mContext).onAuthenticationResult(result, mKey);
            }
        });
    }    

}

