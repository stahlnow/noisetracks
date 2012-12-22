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

import com.stahlnow.noisetracks.NoisetracksApplication;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * Service to handle Account authentication. It instantiates the authenticator
 * and returns its IBinder.
 */
public class AuthenticationService extends Service {
    
    private Authenticator mAuthenticator;

    @Override
    public void onCreate() {
        if (Log.isLoggable(NoisetracksApplication.LOG_TAG, Log.VERBOSE)) {
            Log.v(NoisetracksApplication.LOG_TAG, "Noisetracks Authentication Service started.");
        }
        mAuthenticator = new Authenticator(this);
    }

    @Override
    public void onDestroy() {
        if (Log.isLoggable(NoisetracksApplication.LOG_TAG, Log.VERBOSE)) {
            Log.v(NoisetracksApplication.LOG_TAG, "Noisetracks Authentication Service stopped.");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (Log.isLoggable(NoisetracksApplication.LOG_TAG, Log.VERBOSE)) {
            Log.v(NoisetracksApplication.LOG_TAG, "getBinder()...  returning the AccountAuthenticator binder for intent " + intent);
        }
        return mAuthenticator.getIBinder();
    }
    
    
    public static Boolean accountExists(Context c) {
		return Authenticator.accountExists(c);
	}
	
	public static void removeAccount(Context c) {
		Authenticator.removeAccount(c);
	}
}

