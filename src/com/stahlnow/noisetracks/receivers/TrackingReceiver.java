package com.stahlnow.noisetracks.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.stahlnow.noisetracks.service.TrackingService;

public class TrackingReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		context.startService(new Intent(context, TrackingService.class));
	}
}
