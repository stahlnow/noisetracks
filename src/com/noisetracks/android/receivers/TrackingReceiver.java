package com.noisetracks.android.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.noisetracks.android.service.TrackingService;

public class TrackingReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		context.startService(new Intent(context, TrackingService.class));
	}
}
