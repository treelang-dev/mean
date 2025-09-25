package com.treelang.mean.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.core.app.RemoteInput;

import com.treelang.mean.services.WirelessPairingService;
import com.treelang.mean.utils.NotificationHelper;

public class PairingCodeInputReceiver extends BroadcastReceiver {
    public static final String ACTION_ENTER_PAIRING_CODE = "com.treelang.mean.ACTION_ENTER_PAIRING_CODE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_ENTER_PAIRING_CODE.equals(intent.getAction())) {
            // Get the text input from the notification
            Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
            if (remoteInput != null) {
                String pairingCode = remoteInput.getCharSequence(NotificationHelper.KEY_PAIRING_CODE_INPUT).toString().trim();

                if (pairingCode.length() == 6 && pairingCode.matches("\\d+")) {
                    // Start pairing process with the code
                    Intent serviceIntent = new Intent(context, WirelessPairingService.class);
                    serviceIntent.setAction(WirelessPairingService.ACTION_ATTEMPT_PAIRING);
                    serviceIntent.putExtra("pairing_code", pairingCode);
                    context.startService(serviceIntent);

                    Toast.makeText(context, "Attempting to pair...", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Please enter a valid 6-digit code", Toast.LENGTH_SHORT).show();
                    // Keep the notification with input field visible
                    NotificationHelper.updateNotificationServiceFound(context);
                }
            } else {
                // No text input provided, just keep the notification with input field
                NotificationHelper.updateNotificationServiceFound(context);
            }
        }
    }
}
