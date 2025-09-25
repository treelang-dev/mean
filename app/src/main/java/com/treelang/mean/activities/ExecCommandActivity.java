package com.treelang.mean.activities;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.treelang.mean.R;
import com.treelang.mean.utils.AdbHelper;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class ExecCommandActivity extends ComponentActivity {
    // Constants
    private static final int BATCH_UPDATE_DELAY = 50;
    private static final String COMMAND_PREFIX = "$ ";
    private static final String NEWLINE = "\n";

    // UI Components
    private TextInputEditText commandInput;
    private MaterialToolbar materialToolbar;
    private TextView outputText;
    private ExtendedFloatingActionButton extendedFab;
    private View progressIndicator;

    // State management
    private final AtomicBoolean isExecuting = new AtomicBoolean(false);
    private volatile Process currentProcess;

    // Batch update mechanism - using thread-safe collections
    private final Handler batchUpdateHandler = new Handler(Looper.getMainLooper());
    private final ConcurrentLinkedQueue<String> pendingOutputs = new ConcurrentLinkedQueue<>();

    private final Runnable batchUpdateRunnable = () -> {
        if (!pendingOutputs.isEmpty()) {
            StringBuilder batch = new StringBuilder();
            String output;
            while ((output = pendingOutputs.poll()) != null) {
                batch.append(output);
            }
            outputText.append(batch.toString());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exec_command);
        EdgeToEdge.enable(this);

        setupSystemUI();
        initializeViews();
        setupClickListeners();
        showWarning();
    }

    private void setupSystemUI() {
        // for miui
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
    }

    private void initializeViews() {
        materialToolbar = findViewById(R.id.toolbar);
        commandInput = findViewById(R.id.command_input);
        outputText = findViewById(R.id.output_text);
        extendedFab = findViewById(R.id.extended_fab);
        progressIndicator = findViewById(R.id.progress_indicator);
    }

    private void setupClickListeners() {
        extendedFab.setOnClickListener(v -> executeCommand());
        materialToolbar.setNavigationOnClickListener(v -> finish());
    }

    private void showWarning() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Warn")
                .setMessage("Executing instructions can have adverse effects on applications and even devices. If in doubt, stop immediately")
                .setPositiveButton("Confirm", null)
                .setNegativeButton("Cancel", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void executeCommand() {
        String command = getCommandText();
        if (TextUtils.isEmpty(command)) {
            showSnackbar("Please enter a command to execute");
            return;
        }

        if (!tryStartExecution()) {
            showSnackbar("The command is being executed, do not do anything else");
            return;
        }

        appendOutputBatched(COMMAND_PREFIX + command + NEWLINE);
        executeAdbCommand(command);
    }

    private String getCommandText() {
        return commandInput.getText() != null ? commandInput.getText().toString().trim() : "";
    }

    private boolean tryStartExecution() {
        if (isExecuting.compareAndSet(false, true)) {
            setExecutingUI(true);
            return true;
        }
        return false;
    }

    private void executeAdbCommand(String command) {
        AdbHelper.executeShellCommandAsync(command, new AdbHelper.AdbCommandListener() {
            @Override
            public void onCommandOutput(String output) {
                appendOutputBatched(output + NEWLINE);
            }

            @Override
            public void onCommandComplete(int exitCode) {
                runOnUiThread(() -> {
                    flushPendingOutput();
                    appendOutputBatched(NEWLINE + "[exit code:" + exitCode + "]" + NEWLINE);
                    finishExecution();
                });
            }

            @Override
            public void onCommandError(Exception e) {
                runOnUiThread(() -> {
                    flushPendingOutput();
                    appendOutputBatched(NEWLINE + "[error:" + e.getMessage() + "]" + NEWLINE);
                    finishExecution();
                    showSnackbar("Execution error: " + e.getMessage());
                });
            }
        });
    }

    private void appendOutputBatched(String text) {
        pendingOutputs.offer(text);

        // Schedule batch update
        batchUpdateHandler.removeCallbacks(batchUpdateRunnable);
        batchUpdateHandler.postDelayed(batchUpdateRunnable, BATCH_UPDATE_DELAY);
    }

    private void flushPendingOutput() {
        batchUpdateHandler.removeCallbacks(batchUpdateRunnable);
        batchUpdateRunnable.run();
    }

    private void finishExecution() {
        isExecuting.set(false);
        setExecutingUI(false);
    }

    private void setExecutingUI(boolean executing) {
        extendedFab.setEnabled(!executing);
        commandInput.setEnabled(!executing);
        progressIndicator.setVisibility(executing ? View.VISIBLE : View.GONE);

        if (executing) {
            extendedFab.setText("Stop");
            extendedFab.setIconResource(R.drawable.baseline_stop_24);
            extendedFab.setOnClickListener(v -> stopCommand());
        } else {
            extendedFab.setText("Run");
            extendedFab.setIconResource(R.drawable.baseline_play_arrow_24);
            extendedFab.setOnClickListener(v -> executeCommand());
        }
    }

    private void stopCommand() {
        Process process = currentProcess;
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
        finishExecution();
        showSnackbar("The command has been stopped");
    }

    private void showSnackbar(String message) {
        Snackbar.make(outputText, message, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanup();
    }

    private void cleanup() {
        batchUpdateHandler.removeCallbacks(batchUpdateRunnable);
        Process process = currentProcess;
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }
}
