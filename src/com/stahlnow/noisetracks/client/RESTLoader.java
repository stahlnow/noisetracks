package com.stahlnow.noisetracks.client;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import com.stahlnow.noisetracks.NoisetracksApplication;
import com.stahlnow.noisetracks.R;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.AsyncTaskLoader;
import android.util.Log;

public class RESTLoader extends AsyncTaskLoader<RESTLoader.RESTResponse> {
    
	private static final String TAG = "RESTLoader";
    
    // We use this delta to determine if our cached data is 
    // old or not. The value we have here is 10 minutes;
    private static final long STALE_DELTA = 0;
    
    public enum HTTPVerb {
        GET,
        POST,
        PUT,
        DELETE
    }
    
    public static class RESTResponse {
        private String mData;
        private int    mCode;
        
        public RESTResponse() {
        }
        
        public RESTResponse(String data, int code) {
            mData = data;
            mCode = code;
        }
        
        public String getData() {
            return mData;
        }
        
        public int getCode() {
            return mCode;
        }
    }
    
    private Context		 	mContext;
    private HTTPVerb     	mVerb;
    private Uri          	mAction;
    private Bundle       	mParams;
    private RESTResponse 	mRestResponse;
    private long 			mLastLoad;
    
    public RESTLoader(Context context) {
        super(context);
    }
    
    public RESTLoader(Context context, HTTPVerb verb, Uri action) {
        super(context);
        
        //this.setUpdateThrottle(2000);
        
        mContext = context;
        mVerb    = verb;
        mAction  = action;
    }
    
    public RESTLoader(Context context, HTTPVerb verb, Uri action, Bundle params) {
        super(context);
        
        mContext = context;
        mVerb    = verb;
        mAction  = action;
        mParams  = params;
    }
    
    @Override
    public RESTResponse loadInBackground() {
        try {
            // At the very least we always need an action.
            if (mAction == null) {
                Log.e(TAG, "You did not define an action. REST call canceled.");
                return new RESTResponse(); // We send an empty response back. The LoaderCallbacks<RESTResponse>
                                           // implementation will always need to check the RESTResponse
                                           // and handle error cases like this.
            }
            
            // Here we define our base request object which we will
            // send to our REST service via HttpClient.
            HttpRequestBase request = null;
            
            // Let's build our request based on the HTTP verb we were
            // given.
            switch (mVerb) {
                case GET: {
                    request = new HttpGet();
                    attachUriWithQuery(request, mAction, mParams);
                }
                break;
                
                case DELETE: {
                    request = new HttpDelete();
                    attachUriWithQuery(request, mAction, mParams);
                }
                break;
                
                case POST: {
                    request = new HttpPost();
                    request.setURI(new URI(mAction.toString()));
                    
                    HttpPost postRequest = (HttpPost) request;
                    
                    if (mParams != null) {
                    	// POST json
                    	postRequest.setHeader("Content-Type", "application/json");
                    	StringEntity stringEntity = new StringEntity(mParams.getString("json"));
                    	stringEntity.setContentEncoding("UTF-8");
                    	stringEntity.setContentType("application/json");
                    	postRequest.setEntity(stringEntity);
                    }
                }
                break;
                
                case PUT: {
                    request = new HttpPut();
                    request.setURI(new URI(mAction.toString()));
                    
                    // Attach form entity if necessary.
                    HttpPut putRequest = (HttpPut) request;
                    
                    if (mParams != null) {
                        UrlEncodedFormEntity formEntity = new UrlEncodedFormEntity(paramsToList(mParams));
                        putRequest.setEntity(formEntity);
                    }
                }
                break;
            }
            
            if (request != null) {
                HttpClient client = new DefaultHttpClient();
                
                // Add Authorization to header (not set for sign up action)
                if (mAction != NoisetracksApplication.URI_SIGNUP) {
	                AccountManager am = AccountManager.get(mContext);
	                Account a[] = am.getAccountsByType(mContext.getString(R.string.ACCOUNT_TYPE));
	                if (a[0] != null) {
	                	try {
							String apikey = am.blockingGetAuthToken(a[0], mContext.getString(R.string.AUTHTOKEN_TYPE), true);
							// set header: http://django-tastypie.readthedocs.org/en/latest/authentication_authorization.html#apikeyauthentication
							request.setHeader("Authorization", "ApiKey " + a[0].name + ":" + apikey);
						} catch (OperationCanceledException e) {
							Log.e(TAG, e.toString());
						} catch (AuthenticatorException e) {
							Log.e(TAG, e.toString());
						}
	                }
                }
                
                
                // Let's send some useful debug information so we can monitor things
                // in LogCat.
                //AppLog.logString("Executing request: "+ verbToString(mVerb) +": "+ mAction.toString());
                
                // Finally, we send our request using HTTP. This is the synchronous
                // long operation that we need to run on this Loader's thread.
                HttpResponse response = client.execute(request);
                
                HttpEntity responseEntity = response.getEntity();
                StatusLine responseStatus = response.getStatusLine();
                int        statusCode     = responseStatus != null ? responseStatus.getStatusCode() : 0;
                
                // Here we create our response and send it back to the LoaderCallbacks<RESTResponse> implementation.
                RESTResponse restResponse = new RESTResponse(responseEntity != null ? EntityUtils.toString(responseEntity) : null, statusCode);
                return restResponse;
            }
            
            // Request was null if we get here, so let's just send our empty RESTResponse like usual.
            return new RESTResponse();
        }
        catch (URISyntaxException e) {
            Log.e(TAG, "URI syntax was incorrect. "+ verbToString(mVerb) +": "+ mAction.toString(), e);
            return new RESTResponse();
        }
        catch (UnsupportedEncodingException e) {
            Log.e(TAG, "A UrlEncodedFormEntity was created with an unsupported encoding.", e);
            return new RESTResponse();
        }
        catch (ClientProtocolException e) {
            Log.e(TAG, "There was a problem when sending the request.", e);
            return new RESTResponse();
        }
        catch (IOException e) {
            Log.e(TAG, "There was a problem when sending the request.", e);
            return new RESTResponse();
        }
    }
    
    @Override
    public void deliverResult(RESTResponse data) {
    	if (isReset()) {
            // An async query came in while the loader is stopped.  We
            // don't need the result.
            if (data != null) {
                onReleaseResources(data);
            }
        }
        // Here we cache our response.
        mRestResponse = data;
        
        if (isStarted()) {
            // If the Loader is currently started, we can immediately
            // deliver its results.
            super.deliverResult(data);
        }
    }
    
    @Override
    protected void onStartLoading() {
    	
        if (mRestResponse != null) {
            // We have a cached result, so we can just return right away.
            super.deliverResult(mRestResponse);
        }
        
        // If our response is null or we have hung onto it for a long time,
        // then we perform a force load.
        if (mRestResponse == null || System.currentTimeMillis() - mLastLoad >= STALE_DELTA) forceLoad();
        mLastLoad = System.currentTimeMillis();
    }
    
    @Override
    protected void onStopLoading() {
    	super.onStopLoading();
    	
        // This prevents the AsyncTask backing this loader from completing if it is currently running.
        cancelLoad();
    }
    
    /**
     * Handles a request to cancel a load.
     */
    @Override public void onCanceled(RESTResponse data) {
        super.onCanceled(data);

        // At this point we can release the resources associated with 'apps' if needed.
        onReleaseResources(data);
    }
    
    @Override
    protected void onReset() {
        super.onReset();
        
        // Stop the Loader if it is currently running.
        onStopLoading();
        
        // Get rid of our cache if it exists.
        mRestResponse = null;
        
        // Reset our stale timer.
        mLastLoad = 0;
    }
    
    /**
     * Helper function to take care of releasing resources associated
     * with an actively loaded data set.
     */
    protected void onReleaseResources(RESTResponse data) {
    	data = null;
    }
    
    /**
     * Helper function to create request. If params are null, only the uri is set.
     * @param request The request
     * @param uri The uri of the request
     * @param params Name/Value pairs for URL params
     */
    public static void attachUriWithQuery(HttpRequestBase request, Uri uri, Bundle params) {
        try {
            if (params == null) {
                // No params were given or they have already been
                // attached to the Uri.
                request.setURI(new URI(uri.toString()));
            }
            else {
                Uri.Builder uriBuilder = uri.buildUpon();
                
                // Loop through our params and append them to the Uri.
                for (BasicNameValuePair param : paramsToList(params)) {
                    uriBuilder.appendQueryParameter(param.getName(), param.getValue());
                }
                
                uri = uriBuilder.build();
                request.setURI(new URI(uri.toString()));
            }
        }
        catch (URISyntaxException e) {
            Log.e(TAG, "URI syntax was incorrect: "+ uri.toString());
        }
    }
    
    private static String verbToString(HTTPVerb verb) {
        switch (verb) {
            case GET:
                return "GET";
                
            case POST:
                return "POST";
                
            case PUT:
                return "PUT";
                
            case DELETE:
                return "DELETE";
        }
        
        return "";
    }
    
    private static List<BasicNameValuePair> paramsToList(Bundle params) {
        ArrayList<BasicNameValuePair> formList = new ArrayList<BasicNameValuePair>(params.size());
        
        for (String key : params.keySet()) {
            Object value = params.get(key);
            
            // We can only put Strings in a form entity, so we call the toString()
            // method to enforce. We also probably don't need to check for null here
            // but we do anyway because Bundle.get() can return null.
            if (value != null) formList.add(new BasicNameValuePair(key, value.toString()));
        }
        
        return formList;
    }
    
}
