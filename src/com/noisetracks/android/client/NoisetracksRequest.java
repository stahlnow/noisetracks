package com.noisetracks.android.client;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.util.Log;

import com.noisetracks.android.NoisetracksApplication;
import com.noisetracks.android.provider.NoisetracksContract.Entries;
import com.noisetracks.android.provider.NoisetracksContract.Profiles;
import com.noisetracks.android.utility.AppSettings;
import com.whiterabbit.postman.commands.RequestExecutor;
import com.whiterabbit.postman.commands.RestServerRequest;
import com.whiterabbit.postman.exceptions.PostmanException;
import com.whiterabbit.postman.exceptions.ResultParseException;

import org.apache.http.HttpStatus;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.scribe.exceptions.OAuthException;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Verb;


public class NoisetracksRequest implements RestServerRequest {
	
	private static final String TAG = "NoisetracksRequest";
	
    private Verb 	    	mVerb;
    private Uri          	mAction;
    private Bundle       	mParams;

    public NoisetracksRequest(Verb method, Uri action, Bundle params){
        mVerb = method;
        mAction = action;
        mParams = params;
    }

    @Override
    public String getOAuthSigner() {
        return null;
    }

    @Override
    public String getUrl() {
        return mAction.toString();
    }

    @Override
    public Verb getVerb() {
        return mVerb;
    }
    
    @Override
    public void onHttpResult(Response result, int statusCode, RequestExecutor executor, Context context) throws ResultParseException {
    	
    	String path = mAction.getPath();
    	
    	if (path.matches("^.*/entry/")) {					// entries
    		Log.i(TAG, "Entries");
    		switch (statusCode) {
    		case HttpStatus.SC_OK:
    			addEntriesFromJSON(result.getBody(), context);
                break;
            default:
              	Log.w(TAG, result.getBody());
              	break;
            }	
    	}
    	
    	else if (path.matches("^.*/entry/\\d+/")) {			// single entry
    		Log.i(TAG, "Entry");
    		if (mVerb == Verb.DELETE) {
	    		switch (statusCode) {
	    		case HttpStatus.SC_OK:
	    			Log.d(TAG, mVerb.toString() + "|" + statusCode + "|" + result.getBody());
	    			int d =  context.getContentResolver().delete(
	    					Entries.CONTENT_URI,
	    					Entries.COLUMN_NAME_UUID + " = '" + result.getBody() + "'", null);
	    			if (d > 0) {
	    				Log.v(TAG, "DELETE " + Entries.CONTENT_URI.toString() + " where " + Entries.COLUMN_NAME_UUID.toString() + " = '" + result.getBody() + "'");
	    			} else {
	    				Log.w(TAG, "DELETE failed.");
	    			}
	    			break;
	    		}
    		}
    	}
    	
    	else if (path.matches("^.*/profile/")) {			// profiles
    		Log.i(TAG, "Profiles");
    		switch (statusCode) {
    		case HttpStatus.SC_OK:
    			addProfilesFromJSON(result.getBody(), context);
                break;
            default:
            	Log.w(TAG, mVerb.toString() + "|" + statusCode + "|" + result.getBody());
              	break;
            }	
    	}
    	
    	else if (path.matches("^.*/profile/\\d+/")) {		// single profile
    		Log.i(TAG, "Profile");
    	}
    	
    	else if (path.matches("^.*/signup/")) {
    		Log.i(TAG, "Signup");
    		switch (statusCode) {
    		case HttpStatus.SC_CREATED:
    			Log.i(TAG, "Signup complete.");
                break;
    		case HttpStatus.SC_BAD_REQUEST:
    			Log.w(TAG, "Signup failed.");
    			break;
            default:
            	Log.w(TAG, mVerb.toString() + "|" + statusCode + "|" + result.getBody());
              	break;
            }	
    	}
    	
    	else if (path.matches("^.*/upload/")) {				// upload
    		Log.w(TAG, "Upload is not implmented atm.");
    	}
    	
    	else if (path.matches("^.*/vote/")) {				// vote
    		Log.i(TAG, "Vote");
    		switch (statusCode) {
    		case HttpStatus.SC_CREATED:
    			Log.i(TAG, mVerb.toString() + "|" + statusCode + "|" + result.getBody());
    			
    			String uuid = "";
    			try {
    				JSONObject j = (JSONObject) new JSONTokener(result.getBody()).nextValue();
    				uuid = j.getString("uuid");
    			} catch (JSONException e) {
    				Log.w(TAG, e.toString());
    			}
    	    	
    			Bundle params = new Bundle();
    			params.putString("format", "json");
    			params.putString("uuid", uuid);
    			NoisetracksRequest r = new NoisetracksRequest(Verb.GET, NoisetracksApplication.URI_ENTRIES, params);
    		    try {
					executor.executeRequest(r, context);
				} catch (PostmanException e) {
					Log.w(TAG, e.toString());
				}
                break;
            default:
            	Log.w(TAG, mVerb.toString() + "|" + statusCode + "|" + result.getBody());
              	break;
            }	
    	}
    	
		
    }
    		
   

    @Override
    public void onHttpError(Response result, int statusCode, RequestExecutor executor, Context context) {
        Log.e(TAG, "Error: " + statusCode);
    }

    @Override
    public void onOAuthExceptionThrown(OAuthException exception) {
        Log.e(TAG, "Error: " + exception.toString());
    }

    @Override  
    public void addParamsToRequest(OAuthRequest request) {
    	
    	// add authorization header
    	Context c = NoisetracksApplication.getInstance();
    	request.addHeader("Authorization", "ApiKey " + AppSettings.getUsername(c) + ":" + AppSettings.getApiKey(c));
    	
    	if (mVerb == Verb.POST) {
    		// send content as json
    		request.addHeader("Content-Type", "application/json");
    		request.addPayload(mParams.getString("json"));
    	} else {
			// set request params
			for (BasicNameValuePair param : paramsToList(mParams)) {
	            request.addQuerystringParameter(param.getName(), param.getValue());
	        }
    	}    
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
    	parcel.writeString(mVerb.toString());
        parcel.writeString(mAction.toString());
        parcel.writeBundle(mParams);
    }


    public static final Creator<NoisetracksRequest> CREATOR
            = new Creator<NoisetracksRequest>() {
        public NoisetracksRequest createFromParcel(Parcel in) {
            return new NoisetracksRequest(in);
        }

        public NoisetracksRequest[] newArray(int size) {
            return new NoisetracksRequest[size];
        }
    };


    public NoisetracksRequest(Parcel in) {
    	mVerb = Verb.valueOf(in.readString());
        mAction = Uri.parse(in.readString());
        mParams = in.readBundle();
    }
 
    
    private static List<BasicNameValuePair> paramsToList(Bundle params) {
        ArrayList<BasicNameValuePair> l = new ArrayList<BasicNameValuePair>(params.size());
        
        for (String key : params.keySet()) {
            Object value = params.get(key);
            if (value != null) l.add(new BasicNameValuePair(key, value.toString()));
        }
        
        return l;
    }
    
 
   
	private void addEntriesFromJSON(String json, Context context) {

		try {

			JSONObject entryWrapper = (JSONObject) new JSONTokener(json)
					.nextValue();
			JSONObject meta = entryWrapper.getJSONObject("meta");
			JSONArray entries = entryWrapper.getJSONArray("objects");

			for (int i = 0; i < entries.length(); i++) {
				JSONObject entry = entries.getJSONObject(i);
				JSONObject user = entry.getJSONObject("user");
				JSONArray location = entry.getJSONObject("location")
						.getJSONArray("coordinates");

				ContentValues values = new ContentValues();
				values.put(
						Entries.COLUMN_NAME_FILENAME,
						NoisetracksApplication.DOMAIN
								+ entry.getJSONObject("audiofile").getString(
										"file"));
				values.put(
						Entries.COLUMN_NAME_SPECTROGRAM,
						NoisetracksApplication.DOMAIN
								+ entry.getJSONObject("audiofile").getString(
										Entries.COLUMN_NAME_SPECTROGRAM));
				values.put(Entries.COLUMN_NAME_LATITUDE, location.getDouble(1));
				values.put(Entries.COLUMN_NAME_LONGITUDE, location.getDouble(0));
				values.put(
						Entries.COLUMN_NAME_CREATED,
						entry.getString(Entries.COLUMN_NAME_CREATED).substring(
								0, 19));
				values.put(Entries.COLUMN_NAME_RECORDED,
						entry.getString(Entries.COLUMN_NAME_RECORDED)
								.substring(0, 19));
				values.put(Entries.COLUMN_NAME_RESOURCE_URI,
						entry.getString(Entries.COLUMN_NAME_RESOURCE_URI));
				// check if mugshot is gravatar (full url) or from server
				boolean gravatar = isValidUrl(user
						.getString(Entries.COLUMN_NAME_MUGSHOT));
				if (gravatar)
					values.put(Entries.COLUMN_NAME_MUGSHOT,
							user.getString(Entries.COLUMN_NAME_MUGSHOT));
				else
					values.put(
							Entries.COLUMN_NAME_MUGSHOT,
							NoisetracksApplication.DOMAIN
									+ user.getString(Entries.COLUMN_NAME_MUGSHOT));
				values.put(Entries.COLUMN_NAME_USERNAME,
						user.getString(Entries.COLUMN_NAME_USERNAME));
				values.put(Entries.COLUMN_NAME_UUID,
						entry.getString(Entries.COLUMN_NAME_UUID));
				values.put(Entries.COLUMN_NAME_SCORE,
						entry.getInt(Entries.COLUMN_NAME_SCORE));
				values.put(Entries.COLUMN_NAME_VOTE,
						entry.getInt(Entries.COLUMN_NAME_VOTE));
				values.put(Entries.COLUMN_NAME_TYPE,
						Entries.TYPE.DOWNLOADED.ordinal()); // set type to
															// DOWNLOADED

				// add entry to database
				context.getContentResolver().insert(Entries.CONTENT_URI, values);

			}

			/*
			 * // add 'load more' special entry if (meta.getString("next") !=
			 * "null") { ContentValues values = new ContentValues(); String
			 * created =
			 * entries.getJSONObject(entries.length()-1).getString("created"
			 * ).substring(0,19); // add 1 second to 'created' time Integer sec
			 * =
			 * Integer.getInteger(Character.valueOf(created.charAt(18)).toString
			 * ()); sec++; StringBuilder _created_ = new StringBuilder(created);
			 * _created_.setCharAt(18, Integer.toString(sec).toCharArray()[0]);
			 * // replace second with new value
			 * values.put(Entries.COLUMN_NAME_CREATED, _created_.toString()); //
			 * set created to last entry + one second
			 * values.put(Entries.COLUMN_NAME_RESOURCE_URI,
			 * meta.getString("next")); // special: resource uri is value of
			 * 'next' values.put(Entries.COLUMN_NAME_TYPE,
			 * Entries.TYPE.LOAD_MORE.ordinal()); // Set type to LOAD_MORE
			 * NoisetracksApplication
			 * .getInstance().getContentResolver().insert(Entries.CONTENT_URI,
			 * values); }
			 */

		} catch (JSONException e) {
			Log.w(TAG, "Failed to parse JSON. " + e.toString());
		}

	}

	private void addProfilesFromJSON(String json, Context context) {
		try {
			JSONObject profileWrapper = (JSONObject) new JSONTokener(json)
					.nextValue();
			JSONObject meta = profileWrapper.getJSONObject("meta");
			JSONArray profiles = profileWrapper.getJSONArray("objects");

			for (int i = 0; i < profiles.length(); i++) {
				JSONObject profile = profiles.getJSONObject(i);
				JSONObject user = profile.getJSONObject("user");

				ContentValues values = new ContentValues();

				values.put(Profiles.COLUMN_NAME_USERNAME,
						user.getString(Profiles.COLUMN_NAME_USERNAME));

				try {
					values.put(Profiles.COLUMN_NAME_EMAIL,
							user.getString(Profiles.COLUMN_NAME_EMAIL));
				} catch (JSONException e) {
					// email is only visible to logged in user
				}

				// check if mugshot is gravatar (full url) or from server
				boolean gravatar = isValidUrl(user
						.getString(Profiles.COLUMN_NAME_MUGSHOT));
				if (gravatar)
					values.put(Profiles.COLUMN_NAME_MUGSHOT,
							user.getString(Profiles.COLUMN_NAME_MUGSHOT));
				else
					values.put(
							Profiles.COLUMN_NAME_MUGSHOT,
							NoisetracksApplication.DOMAIN
									+ user.getString(Profiles.COLUMN_NAME_MUGSHOT));

				values.put(Profiles.COLUMN_NAME_BIO,
						profile.getString(Profiles.COLUMN_NAME_BIO));
				values.put(Profiles.COLUMN_NAME_NAME,
						profile.getString(Profiles.COLUMN_NAME_NAME));
				values.put(Profiles.COLUMN_NAME_TRACKS,
						profile.getInt(Profiles.COLUMN_NAME_TRACKS));
				values.put(Profiles.COLUMN_NAME_WEBSITE,
						profile.getString(Profiles.COLUMN_NAME_WEBSITE));

				// add entry to database
				context.getContentResolver().insert(Profiles.CONTENT_URI, values);

			}
		} catch (JSONException e) {
			Log.w(TAG, "Failed to parse JSON. " + e.toString());
		}
	}

	private boolean isValidUrl(String url) {
		try {
			@SuppressWarnings("unused")
			URL u = new URL(url);
		} catch (MalformedURLException e) {
			return false;
		}
		return true;
	}

}

