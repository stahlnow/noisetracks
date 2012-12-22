package com.stahlnow.noisetracks.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.stahlnow.noisetracks.service.NoisetracksService;

public class RecordingReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		context.startService(new Intent(context, NoisetracksService.class));
	}
}
