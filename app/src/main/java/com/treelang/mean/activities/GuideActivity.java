package com.treelang.mean.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.treelang.mean.R;
import com.treelang.mean.utils.NotificationHelper;

public class GuideActivity extends ComponentActivity {

    private boolean isAwaitingPermission = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guide);
        EdgeToEdge.enable(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }

        // Create notification channel on startup
        NotificationHelper.createNotificationChannel(this);

        findViewById(R.id.read_only).setOnClickListener(v -> {
            Intent intent=new Intent(GuideActivity.this, MainActivity.class);
            startActivity(intent);
        });
        findViewById(R.id.pairing).setOnClickListener(v -> askNotificationPermission());
    }

    private void askNotificationPermission() {
        // This is only necessary for API level 33+ (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED) {
                // Permission already granted, send notification and navigate directly
                sendNotificationAndNavigateToNext();
            } else {
                // Permission not granted, navigate to GuidePairingActivity
                navigateToGuidePairing();
            }
        } else {
            // For older versions, check if notifications are enabled
            if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                sendNotificationAndNavigateToNext();
            } else {
                // Notifications not enabled, navigate to GuidePairingActivity
                navigateToGuidePairing();
            }
        }
    }

    private void sendNotificationAndNavigateToNext() {
        // Send notification using the shared helper
        NotificationHelper.sendPairingNotification(this);
        navigateToGuidePairingNext();
    }

    private void navigateToGuidePairingNext() {
        Intent intent = new Intent(GuideActivity.this, GuidePairingNextActivity.class);
        startActivity(intent);
    }

    private void navigateToGuidePairing() {
        Intent intent = new Intent(GuideActivity.this, GuidePairingActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check permission status when returning from settings
        // This helps update the grant status in real time
        if (isAwaitingPermission) {
            checkPermissionAndNavigateIfGranted();
        }
    }

    private void checkPermissionAndNavigateIfGranted() {
        boolean hasPermission;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED;
        } else {
            hasPermission = NotificationManagerCompat.from(this).areNotificationsEnabled();
        }

        // If permission was granted while user was in settings, send notification and navigate automatically
        if (hasPermission) {
            isAwaitingPermission = false;
            sendNotificationAndNavigateToNext();
        }
    }
}
