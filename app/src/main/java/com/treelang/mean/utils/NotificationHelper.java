package com.treelang.mean.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.RemoteInput;

import com.treelang.mean.R;
import com.treelang.mean.receivers.NotificationActionReceiver;
import com.treelang.mean.receivers.PairingCodeInputReceiver;

public class NotificationHelper {
    private static final String CHANNEL_ID = "guide_pairing_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final String NOTIFICATION_TITLE = "Mean";

    // Remote input key for pairing code
    public static final String KEY_PAIRING_CODE_INPUT = "key_pairing_code_input";

    // Different notification states
    private static final String NOTIFICATION_TEXT_SEARCHING = "Searching for pairing service...";
    private static final String NOTIFICATION_TEXT_FOUND = "Pairing service found";
    private static final String NOTIFICATION_TEXT_FAILED = "Pairing failed";
    private static final String NOTIFICATION_TEXT_SUCCESS = "Pairing successful";

    private static final String CHANNEL_NAME = "Wireless debugging pairing";
    private static final String CHANNEL_DESCRIPTION = "Allows you to fill in the pairing code through a notification";

    // Different action button texts
    private static final String ACTION_BUTTON_STOP = "Stop searching";
    private static final String ACTION_BUTTON_ENTER_CODE = "Enter pairing code";
    private static final String ACTION_BUTTON_RETRY = "Retry pairing";

    /**
     * Creates the notification channel required for notifications on Android 8.0+
     */
    public static void createNotificationChannel(Context context) {
        // Create the NotificationChannel for API 26+
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription(CHANNEL_DESCRIPTION);

        // Register the channel with the system
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    /**
     * Sends a notification with pairing information
     *
     * @param context The context to send the notification from
     */
    public static void sendPairingNotification(Context context) {
        sendNotification(context, NOTIFICATION_TEXT_SEARCHING, ACTION_BUTTON_STOP, NotificationActionReceiver.ACTION_STOP_SEARCHING, false);
    }

    /**
     * Updates notification when pairing service is found
     */
    public static void updateNotificationServiceFound(Context context) {
        sendNotification(context, NOTIFICATION_TEXT_FOUND, ACTION_BUTTON_ENTER_CODE, PairingCodeInputReceiver.ACTION_ENTER_PAIRING_CODE, true);
    }

    /**
     * Updates notification when pairing fails
     */
    public static void updateNotificationPairingFailed(Context context) {
        sendNotification(context, NOTIFICATION_TEXT_FAILED, ACTION_BUTTON_RETRY, NotificationActionReceiver.ACTION_RETRY_PAIRING, false);
    }

    /**
     * Updates notification when pairing succeeds
     */
    public static void updateNotificationPairingSuccess(Context context) {
        sendNotification(context, NOTIFICATION_TEXT_SUCCESS, null, null, false);
    }

    /**
     * Generic method to send/update notification with different states
     */
    private static void sendNotification(Context context, String text, String actionText, String actionType, boolean enableTextInput) {
        // Check permission before sending notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.baseline_bug_report_24)
                .setContentTitle(NOTIFICATION_TITLE)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        // Add action button if specified
        if (actionText != null && actionType != null) {
            Intent actionIntent = new Intent(context, PairingCodeInputReceiver.class);
            actionIntent.setAction(actionType);
            actionIntent.putExtra(android.app.Notification.EXTRA_NOTIFICATION_ID, NOTIFICATION_ID);

            PendingIntent actionPendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    actionIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );

            NotificationCompat.Action.Builder actionBuilder = new NotificationCompat.Action.Builder(
                    R.drawable.baseline_add_24,
                    actionText,
                    actionPendingIntent
            );

            // Add text input capability for pairing code entry
            if (enableTextInput) {
                RemoteInput remoteInput = new RemoteInput.Builder(KEY_PAIRING_CODE_INPUT)
                        .setLabel("Enter 6-digit pairing code")
                        .build();
                actionBuilder.addRemoteInput(remoteInput);
            }

            builder.addAction(actionBuilder.build());
        }

        // Send the notification
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    /**
     * Cancels the pairing notification
     *
     * @param context The context to cancel the notification from
     */
    public static void cancelPairingNotification(Context context) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.cancel(NOTIFICATION_ID);
    }
}
