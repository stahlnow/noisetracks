package com.noisetracks.android.authenticator;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.scribe.model.Verb;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Window;
import com.noisetracks.android.NoisetracksApplication;
import com.noisetracks.android.R;
import com.noisetracks.android.client.NoisetracksRequest;
import com.whiterabbit.postman.ServerInteractionHelper;
import com.whiterabbit.postman.ServerInteractionResponseInterface;
import com.whiterabbit.postman.exceptions.SendingCommandException;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class SignupActivity extends SherlockFragmentActivity {
	
	private static final String TAG = "SignupActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// hide action bar
        getWindow().requestFeature((int) Window.FEATURE_ACTION_BAR);
        getSupportActionBar().hide();

		FragmentManager fm = getSupportFragmentManager();

		// Create the list fragment and add it as our sole content.
		if (fm.findFragmentById(android.R.id.content) == null) {
			SignupFragment signup = new SignupFragment();
			signup.setArguments(getIntent().getExtras());
			fm.beginTransaction().add(android.R.id.content, signup).commit();
		}

	}

	public static class SignupFragment extends Fragment implements ServerInteractionResponseInterface {
		
		ServerInteractionHelper mServerHelper;

		private TextView mMessage;
		private EditText mUsernameEdit;
		private EditText mEmailEdit;
		private EditText mPasswordEdit;
		private EditText mPassword2Edit;
		private Button mSignup;

		private String mUsername;
		private String mEmail;
		private String mPassword;
		private String mPassword2;
		
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		    View root = inflater.inflate(R.layout.signup_activity, container, false);
		    mMessage = (TextView) root.findViewById(R.id.message);
			mUsernameEdit = (EditText) root.findViewById(R.id.username_edit);
			mEmailEdit = (EditText) root.findViewById(R.id.email_edit);
			mPasswordEdit = (EditText) root.findViewById(R.id.password_edit);
			mPassword2Edit = (EditText) root.findViewById(R.id.password2_edit);
			mSignup = (Button) root.findViewById(R.id.handle_sign_up_button);
			
			mServerHelper = ServerInteractionHelper.getInstance(getActivity());
		    
			return root;
		}
		
		@Override
		public void onResume() {
			mServerHelper.registerEventListener(this, getActivity());
			super.onResume();
		}
		
		@Override
		public void onPause() {
			mServerHelper.unregisterEventListener(this, getActivity());
			super.onPause();
		}

		@Override
		public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
		
			// hack to change typeface, typeface argument in xml resource is not working.
			mPasswordEdit.setTypeface(Typeface.SANS_SERIF);
			mPasswordEdit.setTransformationMethod(new PasswordTransformationMethod());
			mPassword2Edit.setTypeface(Typeface.SANS_SERIF);
			mPassword2Edit.setTransformationMethod(new PasswordTransformationMethod());
			
			mSignup.setOnClickListener(new Button.OnClickListener() {
				public void onClick(View v){
					handleSignup();
				}
			});
			
			mServerHelper.registerEventListener(this, getActivity());
		}

		/**
		 * Handles onClick event on the sign up button. Start sign up task.
		 */
		public void handleSignup() {

			mUsername = mUsernameEdit.getText().toString();
			mEmail = mEmailEdit.getText().toString();
			mPassword = mPasswordEdit.getText().toString();
			mPassword2 = mPassword2Edit.getText().toString();

			// create json object for POST
			JSONObject json = new JSONObject();     
		    try {
		    	json.put("username", mUsername);
			    json.put("email", mEmail);
				json.put("password1", mPassword);
				json.put("password2", mPassword2);
			} catch (JSONException e) {
				Log.e(TAG, e.toString());
			}
		    
		    // post sign up
			Bundle params = new Bundle();
			params.putString("json", json.toString());
			NoisetracksRequest request = new NoisetracksRequest(Verb.POST, NoisetracksApplication.URI_SIGNUP, params);
            try {
                mServerHelper.sendRestAction(getActivity(), NoisetracksApplication.URI_SIGNUP.toString(), request);
            } catch (SendingCommandException e) {
                Log.e(TAG, e.toString());
            }
		}
		
		@Override
		public void onServerResult(String result, String requestId) {
			Log.v(TAG, "onSignupComplete");
			final Intent intent = new Intent();
			intent.putExtra("username", mUsername);
			intent.putExtra("password", mPassword);
			getActivity().setResult(RESULT_OK, intent);
			getActivity().finish();
		}

		@Override
		public void onServerError(String result, String requestId) {
			mMessage.setText("");
			
			JSONObject wrapper;
			try {
				wrapper = (JSONObject) new JSONTokener(result).nextValue();
				JSONObject signup = wrapper.getJSONObject("signup");
				
				try {
					mMessage.setText(signup.getJSONArray("email").getString(0));
				}
				catch (JSONException e) {
				}
				
				try {
					mMessage.setText(mMessage.getText() + "\n" + signup.getJSONArray("username").getString(0));
				}
				catch (JSONException e) {
				}
				
				try {
					mMessage.setText(mMessage.getText() + "\n" + signup.getJSONArray("__all__").getString(0));
				}
				catch (JSONException e) {
				}
				
			} catch (JSONException e) {
				Log.e(TAG, "Failed to parse JSON. " + e.toString());
			}
			
		}
		

	}

}
