package com.treelang.mean.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationManagerCompat;
import com.treelang.mean.services.WirelessPairingService;

public class NotificationActionReceiver extends BroadcastReceiver {
    public static final String ACTION_STOP_SEARCHING = "com.treelang.mean.action.STOP_SEARCHING";
    public static final String ACTION_RETRY_PAIRING = "com.treelang.mean.action.RETRY_PAIRING";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (ACTION_STOP_SEARCHING.equals(action)) {
            // Stop the wireless pairing service and cancel notification
            Intent serviceIntent = new Intent(context, WirelessPairingService.class);
            context.stopService(serviceIntent);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            notificationManager.cancel(NOTIFICATION_ID);
        } else if (ACTION_RETRY_PAIRING.equals(action)) {
            // Restart the wireless pairing monitoring service
            Intent serviceIntent = new Intent(context, WirelessPairingService.class);
            serviceIntent.setAction(WirelessPairingService.ACTION_START_MONITORING);
            context.startService(serviceIntent);
        }
    }
}
