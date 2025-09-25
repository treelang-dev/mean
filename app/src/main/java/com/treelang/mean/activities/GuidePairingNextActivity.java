package com.treelang.mean.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.treelang.mean.R;
import com.treelang.mean.services.WirelessPairingService;

public class GuidePairingNextActivity extends ComponentActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guide_pairing_next);
        EdgeToEdge.enable(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
        MaterialToolbar materialToolbar=findViewById(R.id.toolbar);
        materialToolbar.setNavigationOnClickListener(v -> finish());
        findViewById(R.id.developer_options_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent=new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                startActivity(intent);

                // Start the wireless pairing monitoring service
                Intent serviceIntent = new Intent(GuidePairingNextActivity.this, WirelessPairingService.class);
                serviceIntent.setAction(WirelessPairingService.ACTION_START_MONITORING);
                startService(serviceIntent);
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();

        // Check if notification permission is still granted
        if (!hasNotificationPermission()) {
            // Permission was revoked, navigate back to GuidePairingActivity
            navigateBackToGuidePairing();
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

    private void navigateBackToGuidePairing() {
        Intent intent = new Intent(this, GuidePairingActivity.class);
        // Use CLEAR_TOP to return to existing GuidePairingActivity instance
        // This removes GuidePairingNextActivity from the stack and returns to GuidePairingActivity
        // while preserving any activities below GuidePairingActivity
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
}
