package com.treelang.mean.services;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;

import com.treelang.mean.utils.NotificationHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class WirelessPairingService extends Service {
    private static final String TAG = "WirelessPairingService";
    public static final String ACTION_START_MONITORING = "com.treelang.mean.action.START_MONITORING";
    public static final String ACTION_ATTEMPT_PAIRING = "com.treelang.mean.action.ATTEMPT_PAIRING";

    private Handler handler;
    private Runnable monitoringRunnable;
    private boolean isMonitoring = false;
    private boolean isPairingServiceFound = false;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();

            if (ACTION_START_MONITORING.equals(action)) {
                startMonitoring();
            } else if (ACTION_ATTEMPT_PAIRING.equals(action)) {
                String pairingCode = intent.getStringExtra("pairing_code");
                attemptPairing(pairingCode);
            }
        } else {
            // Default action when service is started without specific action
            startMonitoring();
        }

        return START_STICKY;
    }

    private void startMonitoring() {
        if (isMonitoring) return;

        isMonitoring = true;
        isPairingServiceFound = false;

        // Send initial notification
        NotificationHelper.sendPairingNotification(this);

        monitoringRunnable = new Runnable() {
            @Override
            public void run() {
                if (isMonitoring) {
                    checkWirelessDebuggingStatus();
                    handler.postDelayed(this, 2000); // Check every 2 seconds
                }
            }
        };

        handler.post(monitoringRunnable);
        Log.d(TAG, "Started monitoring wireless debugging");
    }

    private void checkWirelessDebuggingStatus() {
        try {
            // Check if wireless debugging is enabled
            boolean wirelessDebuggingEnabled = Settings.Global.getInt(
                getContentResolver(),
                "adb_wifi_enabled",
                0
            ) == 1;

            if (wirelessDebuggingEnabled && !isPairingServiceFound) {
                // Check if pairing mode is active by looking for adb processes
                boolean pairingModeActive = checkPairingModeActive();

                if (pairingModeActive) {
                    isPairingServiceFound = true;
                    NotificationHelper.updateNotificationServiceFound(this);
                    Log.d(TAG, "Pairing service found - wireless debugging pairing is active");
                }
            } else if (!wirelessDebuggingEnabled && isPairingServiceFound) {
                // Wireless debugging was turned off
                isPairingServiceFound = false;
                NotificationHelper.sendPairingNotification(this);
                Log.d(TAG, "Wireless debugging disabled, back to searching");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error checking wireless debugging status", e);
        }
    }

    private boolean checkPairingModeActive() {
        try {
            // Check if the wireless debugging port is listening
            // This is a more reliable indicator than checking processes
            return checkAdbPortListening();

        } catch (Exception e) {
            Log.w(TAG, "Could not check pairing mode", e);
            // Fallback: assume pairing is available after wireless debugging is enabled
            return true;
        }
    }

    private boolean checkAdbPortListening() {
        try {
            // Check if any port in the typical ADB wireless range is listening
            Process process = Runtime.getRuntime().exec("netstat -ln");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                // Look for listening ports in the ADB wireless debugging range
                if (line.contains(":555") || line.contains(":37") || line.contains(":42")) {
                    reader.close();
                    return true;
                }
            }

            reader.close();
            return false;

        } catch (IOException e) {
            Log.w(TAG, "Could not check ADB ports", e);
            // Fallback: assume pairing is available after a delay
            return true;
        }
    }

    private void attemptPairing(String pairingCode) {
        // In a real implementation, this would attempt to pair with ADB
        // For now, we'll simulate the pairing process

        handler.postDelayed(() -> {
            // Simulate pairing attempt
            boolean pairingSuccess = simulatePairingAttempt(pairingCode);

            if (pairingSuccess) {
                NotificationHelper.updateNotificationPairingSuccess(this);
                Log.d(TAG, "Pairing successful");

                // Stop monitoring after successful pairing
                stopMonitoring();

                // Auto-cancel notification after a delay
                handler.postDelayed(() -> {
                    NotificationHelper.cancelPairingNotification(this);
                    stopSelf();
                }, 3000);

            } else {
                NotificationHelper.updateNotificationPairingFailed(this);
                Log.d(TAG, "Pairing failed");
            }
        }, 2000); // Simulate 2-second pairing process
    }

    private boolean simulatePairingAttempt(String pairingCode) {
        // Simulate pairing logic - in real implementation this would:
        // 1. Execute adb pair command with the provided code
        // 2. Check the result of the pairing attempt
        // 3. Return true if successful, false if failed

        // For demo purposes, we'll consider codes starting with "1" as successful
        return pairingCode != null && pairingCode.startsWith("1");
    }

    private void stopMonitoring() {
        isMonitoring = false;
        if (monitoringRunnable != null) {
            handler.removeCallbacks(monitoringRunnable);
        }
        Log.d(TAG, "Stopped monitoring wireless debugging");
    }

    @Override
    public void onDestroy() {
        stopMonitoring();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
