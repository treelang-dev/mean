package com.treelang.mean.activities;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.treelang.mean.R;
import com.treelang.mean.adapters.ActivityListAdapter;

import java.util.Arrays;
import java.util.List;

public class AppActivitiesActivity extends ComponentActivity {
    private ActivityListAdapter adapter;
    private String packageName;
    private String appName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_activities);
        EdgeToEdge.enable(this);

        // for miui
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }

        packageName = getIntent().getStringExtra("package_name");
        appName = getIntent().getStringExtra("app_name");

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setTitle(appName + " - Activities");

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ActivityListAdapter(getPackageManager());
        recyclerView.setAdapter(adapter);

        adapter.setOnActivityClickListener(this::showActivityOptions);

        loadActivities();
    }

    private void loadActivities() {
        try {
            PackageManager pm = getPackageManager();
            List<ActivityInfo> activities = Arrays.asList(pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES).activities);
            adapter.submitList(activities);
            // 更新toolbar标题，显示活动数量
            MaterialToolbar toolbar = findViewById(R.id.toolbar);
            toolbar.setTitle(appName + " - Activities (" + activities.size() + ")");
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(this, "Can not load activities: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showActivityOptions(ActivityInfo activity) {
        if (!activity.exported) {
            new MaterialAlertDialogBuilder(this).setTitle("Unexported Activities").setMessage("This activity is not exported and cannot be accessed directly\n\n" + "Name: " + activity.name + "\n" + "Label: " + activity.loadLabel(getPackageManager())).setPositiveButton("Confirm", null).show();
            return;
        }

        String[] options = {"Launch", "Create Shortcut"};
        new MaterialAlertDialogBuilder(this).setTitle(activity.loadLabel(getPackageManager())).setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    launchActivity(activity);
                    break;
                case 1:
                    createShortcut(activity);
                    break;
            }
        }).setNegativeButton("Cancel", null).show();
    }

    private void launchActivity(ActivityInfo activity) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(activity.packageName, activity.name));
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to launch Activity:" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }



    private void createShortcut(ActivityInfo activity) {
        ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);

        if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported()) {
            try {
                // Create the intent for when the shortcut is tapped
                Intent shortcutIntent = new Intent();
                shortcutIntent.setComponent(new ComponentName(activity.packageName, activity.name));
                shortcutIntent.setAction(Intent.ACTION_MAIN);
                shortcutIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                // Create icon for the shortcut - try activity icon first, then app icon
                Icon icon;
                try {
                    PackageManager pm = getPackageManager();
                    Drawable drawable = null;

                    // Try to get activity-specific icon first
                    if (activity.getIconResource() != 0) {
                        try {
                            Resources resources = pm.getResourcesForApplication(activity.packageName);
                            drawable = ResourcesCompat.getDrawable(resources, activity.getIconResource(), null);
                        } catch (Exception e) {
                            // Activity icon failed, will try app icon below
                        }
                    }

                    // If no activity icon, use app icon
                    if (drawable == null) {
                        drawable = pm.getApplicationIcon(activity.packageName);
                    }

                    Bitmap bitmap = drawableToBitmap(drawable);
                    icon = Icon.createWithBitmap(bitmap);
                } catch (Exception e) {
                    // Final fallback to launcher icon
                    icon = Icon.createWithResource(this, R.mipmap.ic_launcher);
                }

                // Build the shortcut info
                String shortLabel = activity.loadLabel(getPackageManager()).toString();
                if (shortLabel.isEmpty()) {
                    shortLabel = activity.name.substring(activity.name.lastIndexOf('.') + 1);
                }

                ShortcutInfo pinShortcutInfo = new ShortcutInfo.Builder(this, activity.name)
                        .setShortLabel(shortLabel)
                        .setLongLabel(shortLabel)
                        .setIcon(icon)
                        .setIntent(shortcutIntent)
                        .build();

                // Request to pin the shortcut
                boolean success = shortcutManager.requestPinShortcut(pinShortcutInfo, null);
                if (success) {
                    Toast.makeText(this, "Shortcut creation requested", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Failed to create shortcut", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Error creating shortcut: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "Pinned shortcuts not supported on this device", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        width = width > 0 ? width : 1;
        height = height > 0 ? height : 1;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

}