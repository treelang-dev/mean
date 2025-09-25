package com.treelang.mean.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.treelang.mean.R;
import com.treelang.mean.utils.NotificationHelper;

public class GuidePairingActivity extends ComponentActivity {

    // Register the permissions callback, which handles the user's response to the
    // system permissions dialog. Save the return value, an instance of
    // ActivityResultLauncher. You can use either a val, as shown in this snippet,
    // or a lateinit var in your onAttach() or onCreate() method.
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // Permission is granted. Navigate directly to the next activity.
                    wasPermissionDenied = false;
                    sendNotificationAndNavigate();
                } else {
                    // Mark that permission was denied so we can check for changes on resume
                    wasPermissionDenied = true;
                    // Explain to the user that the feature is unavailable because the
                    // feature requires a permission that the user has denied. At the
                    // same time, respect the user's decision. Don't link to system
                    // settings in an effort to convince the user to change their
                    // decision.
                    openNotificationSettings();
                }
            });

    private boolean wasPermissionDenied = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guide_pairing);
        EdgeToEdge.enable(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
        NotificationHelper.createNotificationChannel(this);

        MaterialToolbar materialToolbar = findViewById(R.id.toolbar);
        materialToolbar.setNavigationOnClickListener(v -> finish());
        MaterialButton materialButton = findViewById(R.id.materialButton);
        materialButton.setOnClickListener(v -> askNotificationPermission());
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Check if permission was previously denied and user might have granted it in settings
        if (wasPermissionDenied && hasNotificationPermission()) {
            // Permission was granted while app was in background, navigate to next activity
            sendNotificationAndNavigate();
        }
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED;
        } else {
            // For older versions, check if notifications are enabled
            return NotificationManagerCompat.from(this).areNotificationsEnabled();
        }
    }

    private void askNotificationPermission() {
        // This is only necessary for API level 33+ (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (hasNotificationPermission()) {
                // Permission already granted, navigate directly to next activity
                sendNotificationAndNavigate();
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            // For older versions, the permission is granted at install time.
            // However, users can disable notifications in settings.
            if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                sendNotificationAndNavigate();
            } else {
                // Mark that we're waiting for user to enable notifications in settings
                wasPermissionDenied = true;
                // Guide user to settings to enable notifications
                openNotificationSettings();
            }
        }
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(intent);
    }

    private void sendNotificationAndNavigate() {
        // Use the shared notification helper to send notification
        NotificationHelper.sendPairingNotification(this);
        navigateToNextActivity();
    }

    private void navigateToNextActivity() {
        Intent intent = new Intent(GuidePairingActivity.this, GuidePairingNextActivity.class);
        startActivity(intent);
        finish();
    }
}
