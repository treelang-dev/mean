package com.treelang.mean.activities;

import static com.treelang.mean.utils.AdbHelper.executorService;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.search.SearchBar;
import com.google.android.material.search.SearchView;
import com.google.android.material.search.SearchView.TransitionState;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.treelang.mean.R;
import com.treelang.mean.adapters.AppListAdapter;
import com.treelang.mean.adapters.OperationsAdapter;
import com.treelang.mean.data.AppInfo;
import com.treelang.mean.data.BackupRecord;
import com.treelang.mean.utils.AdbHelper;
import com.treelang.mean.utils.AppUtils;
import com.treelang.mean.utils.FileUtils;
import com.treelang.mean.viewmodels.MainViewModel;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends ComponentActivity {

    private MainViewModel mainViewModel;
    private AppListAdapter appListAdapter;
    private final List<BackupRecord> backupRecords = new ArrayList<>(); // Backup list

    // Track current sort selection for this session only (not persistent)
    private int currentSortSelection = 0; // Default to first option (Name A-Z)

    private SearchBar searchBar;
    private TabLayout tabLayout;
    private RecyclerView appListRecyclerView;
    private CircularProgressIndicator progressIndicator;
    private SearchView searchView;

    // Controls within SearchView
    private TabLayout searchViewTabLayout;
    private RecyclerView searchViewRecyclerView;
    private AppListAdapter searchViewAppListAdapter;
    private  final Handler uiHandler = new Handler(Looper.getMainLooper());

    private AppInfo currentAppForExtraction;
    private List<AppInfo> currentAppsForBatchExtraction;

    private ViewGroup contextualToolbarContainer;
    private MaterialToolbar contextualToolbar;
    private AppBarLayout appBarLayout;
    private ExtendedFloatingActionButton extendedFloatingActionButton;
    //private int navigationBarHeight = 0;
    private CoordinatorLayout coordinatorLayout;

    // Bottom Sheet components - Change to BottomSheetDialog for modal behavior
    private BottomSheetDialog bottomSheetDialog;
    private BottomSheetDialog sortBottomSheet;
    private ImageView bottomSheetAppIcon;
    private TextView bottomSheetAppName;
    private TextView bottomSheetPackageName;
    private RecyclerView bottomSheetOperationsRecyclerView;
    private OperationsAdapter operationsAdapter;

    // File picker launchers
    private final ActivityResultLauncher<String> openDocumentLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
        if (uri != null) {
            importBackup(uri);
        }
    });

    private final ActivityResultLauncher<String> createDocumentLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
        if (uri != null) {
            if (!backupRecords.isEmpty()) {
                if (FileUtils.exportBackupToFile(this, backupRecords, uri)) {
                    Snackbar.make(coordinatorLayout, "Backup saved to " + uri.getPath(), Snackbar.LENGTH_LONG).show();
                } else {
                    Snackbar.make(coordinatorLayout, "Failed to save backup", Snackbar.LENGTH_LONG).show();
                }
            }
        }
    });

    private final ActivityResultLauncher<String> createApkLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/vnd.android.package-archive"), uri -> {
        if (uri != null && currentAppForExtraction != null) {
            progressIndicator.setVisibility(View.VISIBLE);
            AdbHelper.extractApk(this, currentAppForExtraction.getApkPath(), uri, new AdbHelper.AdbCommandListener() {
                @Override
                public void onCommandOutput(String output) {
                    Log.d("APK Extract", output);
                }

                @Override
                public void onCommandComplete(int exitCode) {
                    runOnUiThread(() -> {
                        progressIndicator.setVisibility(View.GONE);
                        if (exitCode == 0) {
                            Snackbar.make(coordinatorLayout, "APK extracted successfully", Snackbar.LENGTH_LONG).show();
                        } else {
                            Snackbar.make(coordinatorLayout, "Failed to extract APK", Snackbar.LENGTH_LONG).show();
                        }
                        currentAppForExtraction = null; // Clean up
                    });
                }

                @Override
                public void onCommandError(Exception e) {
                    runOnUiThread(() -> {
                        progressIndicator.setVisibility(View.GONE);
                        Snackbar.make(coordinatorLayout, "Error extracting APK: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                        currentAppForExtraction = null; // Clean up
                    });
                }
            });
        }
    });

    private final ActivityResultLauncher<Uri> openDirectoryLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), treeUri -> {
        if (treeUri != null && currentAppsForBatchExtraction != null && !currentAppsForBatchExtraction.isEmpty()) {
            batchExtractApksToUri(currentAppsForBatchExtraction, treeUri);
        }
    });

    private final OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            // Handle back press for modal bottom sheet
            if (bottomSheetDialog != null && bottomSheetDialog.isShowing()) {
                bottomSheetDialog.dismiss();
                return;
            }
            hideContextualToolbarAndClearSelection();
            searchView.hide();
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);  //直接设置布局

        mainViewModel = new ViewModelProvider(this).get(MainViewModel.class);
        EdgeToEdge.enable(this);
        // for miui
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
        coordinatorLayout=findViewById(R.id.root);

        //初始化UI
        appBarLayout = findViewById(R.id.app_bar_layout);
        searchBar = findViewById(R.id.search_bar);
        tabLayout = findViewById(R.id.tab_layout);
        appListRecyclerView = findViewById(R.id.app_list_recycler_view);
        progressIndicator = findViewById(R.id.progress_bar);
        searchView = findViewById(R.id.search_view);
        extendedFloatingActionButton = findViewById(R.id.extended_float_action);

        // contextual
        contextualToolbarContainer = findViewById(R.id.contextual_toolbar_container);
        contextualToolbar = findViewById(R.id.contextual_toolbar);
        //contextualToolbar.setNavigationIcon(R.drawable.baseline_close_24);

        // 初始化SearchView中的控件
        searchViewTabLayout = findViewById(R.id.search_view_tab_layout);
        searchViewRecyclerView = findViewById(R.id.search_view_recycler_view);

        // 设置FAB点击事件
        extendedFloatingActionButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, ExecCommandActivity.class);
            startActivity(intent);
        });

        appListRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0) {
                    // 向下滚动，收起FAB
                    extendedFloatingActionButton.shrink();
                } else if (dy < 0) {
                    // 向上滚动，展开FAB
                    extendedFloatingActionButton.extend();
                }
            }
        });

        setupSearchBarMenu();
        setupSearchBarAndView();
        setupRecyclerView();
        setupTabLayout();

        observeViewModel();
        setupContextualToolbar();
        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);

        ViewCompat.setOnApplyWindowInsetsListener(contextualToolbarContainer, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        boolean needRefresh;
        boolean hasSearchContent = searchBar != null && !searchBar.getText().toString().isEmpty();
        boolean isSearchViewShowing = searchView != null && searchView.isShowing();

        // Batch UI operations to avoid multiple redraws
        if (hasSearchContent || isSearchViewShowing) {
            if (hasSearchContent) {
                searchBar.setText("");
            }

            if (isSearchViewShowing) {
                searchView.hide();
            }

            if (appListAdapter != null) {
                appListAdapter.filterByQuery("");
            }

            needRefresh = true;
        } else {
            needRefresh = false;
        }

        // Load apps in background thread
        executorService.execute(() -> {
            mainViewModel.loadApps();

            if (needRefresh) {
                uiHandler.post(() ->
                        Snackbar.make(findViewById(R.id.root), "应用列表已更新", Snackbar.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void setupSearchBarAndView() {
        // 设置SearchView文本变化监听
        searchView.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().trim().toLowerCase();

                // 实时更新SearchBar的文本，使其与SearchView同步
                searchBar.setText(query);

                // 更新主界面和搜索界面的过滤
                if (appListAdapter != null) {
                    appListAdapter.filterByQuery(query);
                }

                if (searchViewAppListAdapter != null) {
                    searchViewAppListAdapter.filterByQuery(query);
                }
            }
        });

        // 设置SearchView编辑器动作监
        searchView.getEditText().setOnEditorActionListener((v, actionId, event) -> {
            submitSearchQuery(searchView.getText().toString());
            return false;
        });

        // 添加返回按钮处理
        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);
        // 设置SearchView转换状态监听
        searchView.addTransitionListener((searchView, previousState, newState) -> {
            onBackPressedCallback.setEnabled(newState == TransitionState.SHOWN);

            // 当SearchView完全显示时，初始化其中的控件
            if (newState == TransitionState.SHOWN) {
                setupSearchViewContent();
            }
        });

        // 设置SearchBar点击监听器
        searchBar.setOnClickListener(v -> {
            // 点击SearchBar时   如果有查询文本，自动填充到SearchView
            String currentQuery = searchBar.getText().toString().trim();
            if (!currentQuery.isEmpty()) {
                searchView.setText(currentQuery);
            }
            searchView.show();
        });
    }


    private void submitSearchQuery(String query) {
        // 设置搜索栏文本
        searchBar.setText(query);

        // 执行搜索 - 应用过滤
        if (appListAdapter != null) {
            appListAdapter.filterByQuery(query.toLowerCase());
        }

        // 如果SearchView中的适配器     在，也 用相同的过滤
        if (searchViewAppListAdapter != null) {
            searchViewAppListAdapter.filterByQuery(query.toLowerCase());
        }

        // 如果查询为空，就隐藏SearchView并返回主界面
        if (query.isEmpty()) {
            searchView.hide();
        }
    }

    private void setupSearchBarMenu() {
        searchBar.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.action_sort) {
                showSortDialog();
                return true;
            } else if (itemId == R.id.action_backup) {
                showBackupDialog();
                return true;
            } else if (itemId == R.id.action_import) {
                openFilePicker();
                return true;
            } else {
                return false;
            }
        });
    }

    //显示排序对话框
    private void showSortDialog() {
        String[] sortOptions = {"Name (A-Z)", "Name (Z-A)", "Installation Time (Earliest)", "Installation Time (Latest)", "Update Time (Earliest)", "Update Time (Latest)", "Size (Min)", "Size (Max)",};

        // Create bottom sheet dialog
        sortBottomSheet = new BottomSheetDialog(this);
        sortBottomSheet.setContentView(R.layout.bottom_sheet_sort_options);
        
        // Set the peak height to 60% of screen height
        View bottomSheetInternal = sortBottomSheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheetInternal != null) {
            BottomSheetBehavior.from(bottomSheetInternal).setPeekHeight((int) (getScreenHeight(this)*0.6));
        }

        // Set up RecyclerView for sort options
        RecyclerView sortOptionsRecyclerView = sortBottomSheet.findViewById(R.id.sort_options_recycler_view);
        if (sortOptionsRecyclerView != null) {
            sortOptionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));

            // Use instance variable to remember the selection for this session only
            final int[] selectedPosition = {currentSortSelection}; // Use current session selection

            // Create and set adapter with immediate sorting on radio button click
            SortOptionsAdapter adapter = new SortOptionsAdapter(sortOptions, selectedPosition[0], position -> {
                selectedPosition[0] = position; // Update selected position

                // Save the selection for this session only (not persistent)
                currentSortSelection = position;

                // Apply sorting immediately when radio button is clicked
                AppUtils.SortType sortType;
                switch (position) {
                    case 0:
                        sortType = AppUtils.SortType.NAME_ASC;
                        break;
                    case 1:
                        sortType = AppUtils.SortType.NAME_DESC;
                        break;
                    case 2:
                        sortType = AppUtils.SortType.INSTALL_TIME_ASC;
                        break;
                    case 3:
                        sortType = AppUtils.SortType.INSTALL_TIME_DESC;
                        break;
                    case 4:
                        sortType = AppUtils.SortType.UPDATE_TIME_ASC;
                        break;
                    case 5:
                        sortType = AppUtils.SortType.UPDATE_TIME_DESC;
                        break;
                    case 6:
                        sortType = AppUtils.SortType.SIZE_ASC;
                        break;
                    case 7:
                        sortType = AppUtils.SortType.SIZE_DESC;
                        break;
                    default:
                        sortBottomSheet.dismiss();
                        return;
                }

                // Get current displayed app list and sort it
                List<AppInfo> currentApps = mainViewModel.getCurrentShowApps().getValue();
                if (currentApps != null && !currentApps.isEmpty()) {
                    // Ensure we create a new list instance to avoid referencing the original list
                    List<AppInfo> newList = new ArrayList<>(currentApps);
                    AppUtils.sortAppList(newList, sortType);

                    // Update adapter and apply differences
                    appListAdapter.submitList(newList);

                    // Update current list in ViewModel
                    mainViewModel.setCurrentShowApps(newList);

                    // Optimize scrolling to top implementation
                    appListRecyclerView.post(() -> {
                        // Stop any ongoing scrolling first
                        appListRecyclerView.stopScroll();
                        // Use smooth scroll to top
                        appListRecyclerView.smoothScrollToPosition(0);
                        // Ensure view is fully displayed after scroll completion
                        appListRecyclerView.postDelayed(() -> {
                            if (appListRecyclerView.getLayoutManager() instanceof LinearLayoutManager) {
                                ((LinearLayoutManager) appListRecyclerView.getLayoutManager()).scrollToPositionWithOffset(0, 0);
                            }
                        }, 300);
                    });
                }

                // Dismiss the bottom sheet after applying sorting
                sortBottomSheet.dismiss();
            });
            sortOptionsRecyclerView.setAdapter(adapter);
        }

        // Show the bottom sheet
        sortBottomSheet.show();
    }

    // Inner adapter class for sort options
    private static class SortOptionsAdapter extends RecyclerView.Adapter<SortOptionsAdapter.ViewHolder> {
        private final String[] options;
        // Use session-based selection instead of persistent position
        // This allows the selection to reset when the app is restarted
        private int selectedPosition;
        private final OnSortOptionClickListener listener;

        public interface OnSortOptionClickListener {
            void onSortOptionClick(int position);
        }

        public SortOptionsAdapter(String[] options, int selectedPosition, OnSortOptionClickListener listener) {
            this.options = options;
            this.selectedPosition = selectedPosition;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_sort_option, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.sortOptionText.setText(options[position]);
            holder.radioButton.setChecked(position == selectedPosition);

            holder.itemView.setOnClickListener(v -> {
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) return; // Safety check

                int oldSelected = selectedPosition;
                selectedPosition = adapterPosition;
                notifyItemChanged(oldSelected);
                notifyItemChanged(selectedPosition);
                if (listener != null) {
                    listener.onSortOptionClick(selectedPosition);
                }
            });

            holder.radioButton.setOnClickListener(v -> {
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) return; // Safety check

                int oldSelected = selectedPosition;
                selectedPosition = adapterPosition;
                notifyItemChanged(oldSelected);
                notifyItemChanged(selectedPosition);
                if (listener != null) {
                    listener.onSortOptionClick(selectedPosition);
                }
            });
        }

        @Override
        public int getItemCount() {
            return options.length;
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView sortOptionText;
            RadioButton radioButton;

            ViewHolder(View itemView) {
                super(itemView);
                sortOptionText = itemView.findViewById(R.id.text_sort_option);
                radioButton = itemView.findViewById(R.id.radio_sort_option);
            }
        }
    }

    //备份弹窗
    private void showBackupDialog() {
        List<AppInfo> currentData = mainViewModel.getCurrentShowApps().getValue();
        if (currentData == null || currentData.isEmpty()) {
            Toast.makeText(this, "没有要��份的应用", Toast.LENGTH_SHORT).show();
            return;
        }
        backupRecords.clear();
        for (AppInfo appInfo : currentData) {
            backupRecords.add(new BackupRecord(appInfo.getPackageName(), appInfo.getStatus()));
        }
        String time = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault()).format(new Date());

        String fileName = "app_backup_" + time + ".json";
        new MaterialAlertDialogBuilder(this)
                // new AlertDialog.Builder(this)
                .setTitle("备份应用").setMessage("是否备份 " + currentData.size() + " 个应用？ \n文件名��" + fileName).setPositiveButton("确定", (dialog, which) -> exportBackup(fileName)).setNegativeButton("取消", null).show();
    }

    //导出
    private void exportBackup(String fileName) {
        createDocumentLauncher.launch(fileName);
    }

    private void importBackup(Uri uri) {
        try {
            List<BackupRecord> importedRecords = FileUtils.importBackupFromFile(this, uri);

            if (importedRecords != null && !importedRecords.isEmpty()) {
                StringBuilder message = getStringBuilder(importedRecords);

                new MaterialAlertDialogBuilder(this).setTitle("导入备份").setMessage(message.toString()).setPositiveButton("应用更改", (dialog, which) -> applyBackup(importedRecords)).setNegativeButton("取消", null).show();
            } else {
                Toast.makeText(this, "导入失败或文件内容为空", Toast.LENGTH_SHORT).show();
            }
        } catch (RuntimeException e) {
            Log.e("MainActivity", "导入失败", e);
            Toast.makeText(this, "导  失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // Optimize string building with pre-allocated capacity
    @NonNull
    private static StringBuilder getStringBuilder(List<BackupRecord> importedRecords) {
        int size = importedRecords.size();
        int displayCount = Math.min(size, 10);
        StringBuilder message = new StringBuilder(size * 50) // Pre-allocate buffer
                .append("导入的应用状态：\n");

        for (int i = 0; i < displayCount; i++) {
            BackupRecord record = importedRecords.get(i);
            message.append(record.getPackageName())
                    .append(": ")
                    .append(record.getAppStatus())
                    .append('\n');
        }

        if (size > 10) {
            message.append("...及其他 ")
                    .append(size - 10)
                    .append(" 个应用\n");
        }

        message.append("是否要批量操作？");
        return message;
    }

    private void openFilePicker() {
        // 指定只打开 JSON 文件
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        openDocumentLauncher.launch(intent.getType());
    }


    private String getAdbCommand(BackupRecord record) {
        if ("Disabled".equals(record.getAppStatus())) {
            return "pm disable-user " + record.getPackageName();
        } else if ("Uninstalled".equals(record.getAppStatus())) {
            return "pm uninstall " + record.getPackageName();
        }
        return "";
    }

    private void setupRecyclerView() {
        appListAdapter = new AppListAdapter();

        // Enhanced performance optimizations
        // appListRecyclerView.setHasFixedSize(true);
        appListRecyclerView.setItemViewCacheSize(20); // Increased cache size

        // Use shared RecyclerView.RecycledViewPool for better memory efficiency
        RecyclerView.RecycledViewPool sharedPool = new RecyclerView.RecycledViewPool();
        appListRecyclerView.setRecycledViewPool(sharedPool);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false; // Disable for better performance
            }
        };
        appListRecyclerView.setLayoutManager(layoutManager);
        appListRecyclerView.setAdapter(appListAdapter);


        // 点击事件和长按事件
        appListAdapter.setOnAppItemClickListener(this::showAppOperationDialog);
        appListAdapter.setOnAppItemLongClickListener(appInfo -> {
            // 启用选择模式
            AppListAdapter.setSelectionModeEnabled(true);
            // 选中当前项
            appInfo.setSelected(true);
            // 更新UI
            appListAdapter.notifyItemChanged(appListAdapter.getCurrentList().indexOf(appInfo));
            // 触发选择状态改变
            onItemSelectedStateChanged(appInfo);
            return true;
        });
        appListAdapter.setOnItemSelectedStateChangedListener(this::onItemSelectedStateChanged);
    }

    private void showAppDetailDialog(AppInfo appInfo) {
        // Use StringBuilder with estimated capacity to avoid resizing
        StringBuilder detailMsg = new StringBuilder(2048);

        // 基本信息
        detailMsg.append("* Basic Information\n\n");
        detailMsg.append("App name: ").append(appInfo.getAppName()).append("\n");
        detailMsg.append("Package name: ").append(appInfo.getPackageName()).append("\n");
        detailMsg.append("Version: ").append(appInfo.getVersionName()).append(" (").append(appInfo.getVersionCode()).append(")\n");
        detailMsg.append("UID: ").append(appInfo.getUid()).append("\n");
        detailMsg.append("State: ").append(appInfo.getStatus()).append("\n");

        // API级别信息
        detailMsg.append("\n* API Level Information\n\n");
        detailMsg.append("Target API: ").append(appInfo.getTargetSdkVersion()).append("\n");
        detailMsg.append("Min API: ").append(appInfo.getMinSdkVersion()).append("\n");
        detailMsg.append("Compile API: ").append(appInfo.getCompileSdkVersion()).append("\n");

        // 安装包信息
        detailMsg.append("\n* Package Information\n\n");
        detailMsg.append("Package size: ").append(Formatter.formatFileSize(getApplicationContext(), appInfo.getAppSize()));

        // 检查是否是 App Bundle 并显示额外信息
        if (appInfo.getApkPath() != null) {
            File baseApk = new File(appInfo.getApkPath());
            File appDir = baseApk.getParentFile();
            if (appDir != null && appDir.exists()) {
                // 检查目录下所有以 split_ 开头的 APK 文件
                File[] splitFiles = appDir.listFiles((dir, name) -> name.startsWith("split_") && name.endsWith(".apk"));
                if (splitFiles != null && splitFiles.length > 0) {
                    long totalSplitSize = 0;
                    for (File splitFile : splitFiles) {
                        totalSplitSize += splitFile.length();
                    }
                    if (totalSplitSize > 0) {
                        detailMsg.append(" (split +").append(Formatter.formatFileSize(getApplicationContext(), totalSplitSize)).append(")");
                    }
                }
            }
        }
        detailMsg.append("\n");
        detailMsg.append("Package path: ").append(appInfo.getApkPath()).append("\n");

        // 数据路径
        detailMsg.append("\n* Data Directory\n\n");
        detailMsg.append("Data Directory 1: ").append(appInfo.getDataPath()).append("\n");
        detailMsg.append("Data Directory 2: ").append(appInfo.getExternalDataPath()).append("\n");

        // 安装信息
        detailMsg.append("\n* Installation Information\n\n");
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        detailMsg.append("Installation time: ").append(dateFormat.format(new Date(appInfo.getFirstInstallTime()))).append("\n");
        detailMsg.append("Last update time: ").append(dateFormat.format(new Date(appInfo.getLastUpdateTime()))).append("\n");

        // 获取安装源 - 适配API 28
        String installerPackageName = getPackageManager().getInstallerPackageName(appInfo.getPackageName());
        detailMsg.append("Install source: ").append(installerPackageName != null ? installerPackageName : "Unknown").append("\n");

        new MaterialAlertDialogBuilder(this)
                .setTitle(appInfo.getAppName())
                .setIcon(appInfo.getAppIcon())
                .setMessage(detailMsg.toString())
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Share", (dialog, which) -> shareAppInfo(appInfo, detailMsg.toString()))
                .show();
    }

    // 分享应用信息
    private void shareAppInfo(AppInfo appInfo, String details) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, appInfo.getAppName());
        shareIntent.putExtra(Intent.EXTRA_TEXT, details);
        startActivity(Intent.createChooser(shareIntent, "Share"));
    }

    private void showAppOperationDialog(AppInfo appInfo) {
        // Create modal bottom sheet dialog
        if (bottomSheetDialog == null) {
            bottomSheetDialog = new BottomSheetDialog(this);
            //View bottomSheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_app_operations, null);
            bottomSheetDialog.setContentView(R.layout.bottom_sheet_app_operations);
            View bottomSheetInternal = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            BottomSheetBehavior.from(bottomSheetInternal).setPeekHeight((int) (getScreenHeight(this)*0.6)); // Set initial peek height

            //BottomSheetBehavior.from(bottomSheetDialog.findViewById(R.id.drag_handle)).setState(BottomSheetBehavior.STATE_COLLAPSED);
            // Initialize views from the inflated layout
            bottomSheetAppIcon = bottomSheetDialog.findViewById(R.id.app_icon);
            bottomSheetAppName = bottomSheetDialog.findViewById(R.id.app_name);
            bottomSheetPackageName = bottomSheetDialog.findViewById(R.id.package_name);
            bottomSheetOperationsRecyclerView = bottomSheetDialog.findViewById(R.id.operations_recycler_view);

            // Set up RecyclerView
            bottomSheetOperationsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            // Handle dismiss listener
           bottomSheetDialog.setOnDismissListener(dialog ->
                   BottomSheetBehavior.from(bottomSheetInternal).setState(BottomSheetBehavior.STATE_COLLAPSED));
                   onBackPressedCallback.setEnabled(false);
        }

        // Set app information in the bottom sheet
        bottomSheetAppIcon.setImageDrawable(appInfo.getAppIcon());
        bottomSheetAppName.setText(appInfo.getAppName());
        bottomSheetPackageName.setText(appInfo.getPackageName());

        // Create operations list with icons
        List<OperationsAdapter.Operation> operations = createOperationsList(appInfo);

        // Create adapter with click listener
        operationsAdapter = new OperationsAdapter(operations, operation -> {
            // Dismiss bottom sheet first

            //bottomSheetDialog.dismiss();
            // Handle operation click
            switch (operation) {
                case "Launch":
                    launchApp(appInfo);
                    break;
                case "Details":
                    showAppDetailDialog(appInfo);
                    break;
                case "Disable":
                case "Enable":
                    toggleAppState(appInfo);
                    break;
                case "Uninstall":
                case "Uninstall(For current User only)":
                    uninstallApp(appInfo);
                    break;
                case "Install":
                    installApp(appInfo);
                    break;
                case "Extract APK":
                    extractAppApk(appInfo);
                    break;
                case "Activities":
                    showAppActivities(appInfo);
                    break;
                case "More":
                    openAppDetails(appInfo);
                    break;
                default:
                    bottomSheetDialog.dismiss();
            }
            bottomSheetDialog.dismiss();
        });

        // Set adapter to RecyclerView
        bottomSheetOperationsRecyclerView.setAdapter(operationsAdapter);

        // Show modal bottom sheet
        bottomSheetDialog.show();
    }

    private List<OperationsAdapter.Operation> createOperationsList(AppInfo appInfo) {
        List<OperationsAdapter.Operation> operations = new ArrayList<>();

        // Add operations with appropriate icons (using Android system icons)
        operations.add(new OperationsAdapter.Operation("Launch", R.drawable.baseline_launch_24));
        operations.add(new OperationsAdapter.Operation("Details", R.drawable.baseline_details_24));

        // 根据应用状态添加禁用/启用选
        if ("Installed".equals(appInfo.getStatus())) {
            operations.add(new OperationsAdapter.Operation("Disable", R.drawable.baseline_play_disabled_24));
        } else if ("Disabled".equals(appInfo.getStatus())) {
            operations.add(new OperationsAdapter.Operation("Enable", R.drawable.baseline_play_arrow_24));
        }

        // 根据应用状态和类型添加卸载/安装选项
        if ("Installed".equals(appInfo.getStatus()) || "Disabled".equals(appInfo.getStatus())) {
            // 根  应用类型显示不同的卸载选项
            if (appInfo.isSystemApp() || appInfo.isPrivilegedApp() || appInfo.isCoreApp()) {
                operations.add(new OperationsAdapter.Operation("Uninstall(For current User only)",R.drawable.baseline_delete_24));
            } else {
                operations.add(new OperationsAdapter.Operation("Uninstall", R.drawable.baseline_delete_24));
            }
            // 添加提取安装包选项
            operations.add(new OperationsAdapter.Operation("Extract APK", R.drawable.baseline_shopping_bag_24));
        } else if ("Uninstalled".equals(appInfo.getStatus())) {
            operations.add(new OperationsAdapter.Operation("Install", R.drawable.baseline_download_24));
        }

        operations.add(new OperationsAdapter.Operation("Activities",R.drawable.baseline_local_activity_24));
        operations.add(new OperationsAdapter.Operation("More", R.drawable.baseline_more_horiz_24));

        return operations;
    }

    private void showAppActivities(AppInfo appInfo) {
        Intent intent = new Intent(this, AppActivitiesActivity.class);
        intent.putExtra("package_name", appInfo.getPackageName());
        intent.putExtra("app_name", appInfo.getAppName());
        startActivity(intent);
    }

    /**
     * 启动应用
     */
    private void launchApp(AppInfo appInfo) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(appInfo.getPackageName());
        if (launchIntent != null) {
            startActivity(launchIntent);
        } else {
            Snackbar.make(coordinatorLayout,"Unable to launch",Snackbar.LENGTH_SHORT).show();
            //Toast.makeText(this, "Unable to launch", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 打开应用详情页面
     */
    private void openAppDetails(AppInfo appInfo) {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + appInfo.getPackageName()));
        startActivity(intent);
    }

    private void observeViewModel() {
        // 观察加  进度
        mainViewModel.getLoadingProgress().observe(this, progress -> progressIndicator.setProgress(progress));

        // 使用getViewLifecycleOwner而不是this，以防止内存泄漏
        mainViewModel.getCurrentShowApps().observe(this, appInfos -> {
            if (appInfos != null) {
                // 使用DiffUtil更新列表���而不是完全重新提交
                boolean isLoad = appListAdapter.getCurrentList().isEmpty();

                // 确保显示的是当前选中tab页的内容
                int currentTabPosition = mainViewModel.getSelectedTabPosition();
                if (currentTabPosition != tabLayout.getSelectedTabPosition()) {
                    // 如果ViewModel中的tab位置与UI中  不一致，更新UI
                    if (currentTabPosition >= 0 && currentTabPosition < tabLayout.getTabCount()) {
                        tabLayout.selectTab(tabLayout.getTabAt(currentTabPosition));
                    }
                }

                appListAdapter.submitList(appInfos);

                // 如果搜索视图处于显示状态，更新搜索视图中的列表
                if (searchView.isShowing() && searchViewAppListAdapter != null) {
                    String query = searchView.getText().toString().trim().toLowerCase();
                    if (!query.isEmpty()) {
                        // 使用新的FilterStrategy避免���必要的重复过滤
                        // 先提交完整列  ，然后应用过滤
                        searchViewAppListAdapter.submitList(new ArrayList<>(appInfos));
                        searchViewAppListAdapter.filterByQuery(query);
                    } else {
                        searchViewAppListAdapter.submitList(new ArrayList<>(appInfos));
                    }
                }

                // 显示/隐藏进度条和列表
                if (!mainViewModel.isLoading()) {
                    progressIndicator.setVisibility(View.GONE);

                    // 加载完成后更新UI可见性
                    if (isLoad) {
                        appListRecyclerView.setVisibility(View.VISIBLE);
                    }

                    // 如果用户此前正在滚动列表     某个位置，数据更新后保持该位置
                    LinearLayoutManager layoutManager = (LinearLayoutManager) appListRecyclerView.getLayoutManager();
                    if (layoutManager != null && layoutManager.findFirstVisibleItemPosition() > 0) {
                        int position = layoutManager.findFirstVisibleItemPosition();
                        appListRecyclerView.scrollToPosition(position);
                    }
                }
            }
        });
    }

    //在setupTapLayout
    private void setupTabLayout() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();

                // 记录当前选中的标签页位置
                mainViewModel.setSelectedTabPosition(position);

                // 请求更新当前应用列表
                mainViewModel.updateCurrentShowApps(position);

                // 同步SearchView中的TabLayout
                if (searchView.isShowing() && searchViewTabLayout != null && position >= 0 && position < searchViewTabLayout.getTabCount()) {
                    searchViewTabLayout.selectTab(searchViewTabLayout.getTabAt(position));
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // 不需要处理
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // 不需要处理
            }
        });

        // 选择默认的第一个标签页
        if (tabLayout.getTabCount() > 0) {
            tabLayout.selectTab(tabLayout.getTabAt(0));
        }
    }

    //修改loadApp为loadApps
    private void applyBackup(List<BackupRecord> records) {
        int total = records.size();
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);

        // Use controlled concurrency to prevent overwhelming the system
        ExecutorService batchExecutor = Executors.newFixedThreadPool(2);

        for (BackupRecord record : records) {
            if ("Installed".equals(record.getAppStatus())) {
                processedCount.incrementAndGet();
                continue;
            }

            String packageName = record.getPackageName();
            batchExecutor.execute(() -> AdbHelper.executeShellCommandAsync(getAdbCommand(record), new AdbHelper.AdbCommandListener() {
                @Override
                public void onCommandOutput(String output) {
                    Log.d("ADB", output);
                }

                @Override
                public void onCommandComplete(int exitCode) {
                    if (exitCode == 0) {
                        int processed = processedCount.incrementAndGet();

                        // Batch UI updates for better performance
                        if (processed % 3 == 0 || processed == total) {
                            uiHandler.post(() -> {
                                List<AppInfo> appInfos = mainViewModel.getCurrentShowApps().getValue();
                                if (appInfos != null) {
                                    // Find and update efficiently
                                    for (AppInfo appInfo : appInfos) {
                                        if (appInfo.getPackageName().equals(packageName)) {
                                            if ("Disabled".equals(record.getAppStatus())) {
                                                appInfo.setStatus("Disabled");
                                            } else if ("Uninstalled".equals(record.getAppStatus())) {
                                                appInfo.setStatus("Uninstalled");
                                            }
                                            appListAdapter.notifyItemChanged(appInfos.indexOf(appInfo));
                                            break;
                                        }
                                    }
                                }
                            });
                        }
                    } else {
                        failedCount.incrementAndGet();
                    }
                    checkIfAllOperationsComplete(total, processedCount, failedCount);
                }

                @Override
                public void onCommandError(Exception e) {
                    failedCount.incrementAndGet();
                    checkIfAllOperationsComplete(total, processedCount, failedCount);
                }
            }));
        }

        // Shutdown executor when done
        batchExecutor.shutdown();
    }

    private void checkIfAllOperationsComplete(int total, AtomicInteger processed, AtomicInteger failed) {
        if (processed.get() + failed.get() == total) {
            Snackbar.make(coordinatorLayout,"Batch operation: Succeeded" + processed.get() + " , failed " + failed.get() ,Snackbar.LENGTH_LONG).show();


            // 保存当前列表状态
            List<AppInfo> oldList = new ArrayList<>(appListAdapter.getCurrentList());

            // ��新加载应用列表
            mainViewModel.loadApps();

            // 使用 DiffUtil 计算差异并更新
            mainViewModel.getCurrentShowApps().observe(this, newList -> {
                if (newList != null) {
                    DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                        @Override
                        public int getOldListSize() {
                            return oldList.size();
                        }

                        @Override
                        public int getNewListSize() {
                            return newList.size();
                        }

                        @Override
                        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                            return oldList.get(oldItemPosition).getPackageName().equals(newList.get(newItemPosition).getPackageName());
                        }

                        @Override
                        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                            AppInfo oldItem = oldList.get(oldItemPosition);
                            AppInfo newItem = newList.get(newItemPosition);
                            return oldItem.getStatus().equals(newItem.getStatus()) && oldItem.getAppName().equals(newItem.getAppName());
                        }
                    });

                    // 使用 DiffUtil 结果更新适配器
                    appListAdapter.submitList(newList);
                    diffResult.dispatchUpdatesTo(appListAdapter);
                }
            });
        }
    }

    //修改loadApp为loadApps
    private void uninstallApp(AppInfo appInfo) {

        try {
            AdbHelper.uninstallApp(appInfo.getPackageName(), new AdbHelper.AdbCommandListener() {
                @Override
                public void onCommandOutput(String output) {
                    Log.d("ADB", output);
                }

                @Override
                public void onCommandComplete(int exitCode) {
                    runOnUiThread(() -> {
                        try {
                            if (exitCode == 0) {

                                Snackbar.make(coordinatorLayout, "Uninstall successfully: " + appInfo.getAppName(), Snackbar.LENGTH_SHORT).show();
                                appInfo.setStatus("Uninstalled");
                                mainViewModel.loadApps();
                            } else {
                                Snackbar.make(coordinatorLayout, "Uninstall failed, heading to system uninstall", Snackbar.LENGTH_SHORT).show();

                                // 使用 Handler 延迟启动系统卸载器，避  可能的线程问题
                                new Handler().postDelayed(() -> {
                                    try {
                                        Uri packageUrl = Uri.parse("package:" + appInfo.getPackageName());
                                        Intent intent = new Intent(Intent.ACTION_DELETE);
                                        intent.setData(packageUrl);
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        startActivity(intent);
                                    } catch (Exception e) {
                                        Log.e("MainActivity", "Error starting uninstall activity", e);
                                        Snackbar.make(coordinatorLayout, "Unable to initiate system uninstall:" + e.getMessage(), Snackbar.LENGTH_SHORT).show();
                                    }
                                }, 200);
                            }
                        } catch (Exception e) {
                            Log.e("MainActivity", "Error in onCommandComplete", e);
                            Snackbar.make(findViewById(R.id.root), "Error during uninstall: " + e.getMessage(), Snackbar.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onCommandError(Exception e) {
                    runOnUiThread(() -> {
                        Log.e("MainActivity", "ADB command error", e);
                        Snackbar.make(coordinatorLayout, "ADB error: " + e.getMessage(), Snackbar.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (Exception e) {
            Log.e("MainActivity", "Exception in uninstallApp", e);
            Snackbar.make(coordinatorLayout, "An error occurred while uninstalling: " + e.getMessage(), Snackbar.LENGTH_SHORT).show();
        }
    }

    //修改loadApp为loadApps
    private void toggleAppState(AppInfo appInfo) {

        progressIndicator.setVisibility(View.VISIBLE);

        executorService.execute(() -> {
            if ("Installed".equals(appInfo.getStatus())) {
                AdbHelper.disableApp(appInfo.getPackageName(), new AdbHelper.AdbCommandListener() {
                    @Override
                    public void onCommandOutput(String output) {
                        Log.d("ADB", output);
                    }

                    @Override
                    public void onCommandComplete(int exitCode) {
                        uiHandler.post(() -> {
                            progressIndicator.setVisibility(View.GONE);
                            if (exitCode == 0) {
                                appInfo.setStatus("Disabled");
                                mainViewModel.loadApps();
                                Snackbar.make(coordinatorLayout, "Disable successfully: " + appInfo.getAppName(), Snackbar.LENGTH_SHORT).show();
                            } else {
                                Snackbar.make(coordinatorLayout, "Disable failed: " + appInfo.getAppName(), Snackbar.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onCommandError(Exception e) {
                        uiHandler.post(() -> {
                            progressIndicator.setVisibility(View.GONE);
                            Snackbar.make(coordinatorLayout, "An error occurred: " + appInfo.getAppName(), Snackbar.LENGTH_SHORT).show();
                        });
                    }
                });

            } else if ("Disabled".equals(appInfo.getStatus())) {
                AdbHelper.enableApp(appInfo.getPackageName(), new AdbHelper.AdbCommandListener() {
                    @Override
                    public void onCommandOutput(String output) {
                        Log.d("ADB", output);
                    }

                    @Override
                    public void onCommandComplete(int exitCode) {
                        uiHandler.post(() -> {
                            if (exitCode == 0) {
                                appInfo.setStatus("Installed");
                                mainViewModel.loadApps();
                                Snackbar.make(coordinatorLayout, "Enable successfully: " + appInfo.getAppName(), Snackbar.LENGTH_SHORT).show();
                            } else {
                                Snackbar.make(coordinatorLayout, "Enable failed: " + appInfo.getAppName(), Snackbar.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onCommandError(Exception e) {
                        uiHandler.post(() ->
                                Snackbar.make(coordinatorLayout, "An error occurred: " + appInfo.getAppName(), Snackbar.LENGTH_SHORT).show()
                        );
                    }
                });
            }
        });
    }

    /**
     *   置SearchView中的内容，包括TabLayout和RecyclerView
     */
    private void setupSearchViewContent() {
        // 设置SearchView中的RecyclerView
        if (searchViewAppListAdapter == null) {
            searchViewAppListAdapter = new AppListAdapter();
            searchViewAppListAdapter.setOnAppItemClickListener(this::showAppOperationDialog);

            searchViewRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            searchViewRecyclerView.setAdapter(searchViewAppListAdapter);
        }

        // 复制主界面的应用���表数据到SearchView的列表
        if (appListAdapter != null) {
            appListAdapter.getCurrentList();// 应用当前的搜索过滤
            String query = searchView.getText().toString().trim().toLowerCase();
            if (!query.isEmpty()) {
                searchViewAppListAdapter.filterByQuery(query);
            } else {
                searchViewAppListAdapter.submitList(new ArrayList<>(appListAdapter.getCurrentList()));
            }
        }

        // 设置SearchView中的TabLayout
        if (searchViewTabLayout.getTabCount() > 0) {
            // 同步主界面的选中标签
            int selectedTabPosition = tabLayout.getSelectedTabPosition();
            if (selectedTabPosition >= 0 && selectedTabPosition < searchViewTabLayout.getTabCount()) {
                searchViewTabLayout.selectTab(searchViewTabLayout.getTabAt(selectedTabPosition));
            }

            // 设置标签选择监听器
            searchViewTabLayout.clearOnTabSelectedListeners(); // 清除之前的监听器，避免重   添加
            searchViewTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    int position = tab.getPosition();
                    // 更  主界面的TabLayout
                    tabLayout.selectTab(tabLayout.getTabAt(position));

                    // 获取当前搜索查询
                    String query = searchView.getText().toString().trim().toLowerCase();

                    // 更新RecyclerView��据
                    mainViewModel.updateCurrentShowApps(position);
                    if (!query.isEmpty()) {
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            if (searchViewAppListAdapter != null) {
                                searchViewAppListAdapter.filterByQuery(query);
                            }
                        }, 100);
                    }
                }

                @Override
                public void onTabUnselected(TabLayout.Tab tab) {
                }

                @Override
                public void onTabReselected(TabLayout.Tab tab) {
                }
            });
        }
    }

    private void onItemSelectedStateChanged(AppInfo appInfo) {
        long selectedItemCount = appListAdapter.getSelectedItemCount();
        if (selectedItemCount > 0 && AppListAdapter.isSelectionModeEnabled()) {
            contextualToolbar.setTitle(String.valueOf(selectedItemCount));
            expandContextualToolbar();
            // 更新菜单项可见性
            updateContextualToolbarMenuVisibility();
            // 隐藏FAB
            extendedFloatingActionButton.hide();
        } else {
            AppListAdapter.setSelectionModeEnabled(false);
            collapseContextualToolbar();
            // 显示FAB
            extendedFloatingActionButton.show();
        }
    }

    private void setupContextualToolbar() {
        contextualToolbar.setNavigationOnClickListener(v -> hideContextualToolbarAndClearSelection());
        contextualToolbar.inflateMenu(R.menu.contextual_toolbar_menu);

        // 更  菜单项可见性
        updateContextualToolbarMenuVisibility();

        contextualToolbar.setOnMenuItemClickListener(menuItem -> {
            int itemId = menuItem.getItemId();
            if (itemId == R.id.action_select_all) {
                setItemsSelected(true);
                contextualToolbar.setTitle(String.valueOf(appListAdapter.getSelectedItemCount()));
                return true;
            } else if (itemId == R.id.action_deselect_all) {
                // 反选功能：将所有项  选择状态反转
                invertItemsSelection();
                contextualToolbar.setTitle(String.valueOf(appListAdapter.getSelectedItemCount()));
                return true;
            } else if (itemId == R.id.action_backup_selected) {
                // 备份选中的应用
                backupSelectedApps();
                return true;
            } else if (itemId == R.id.action_disable_selected) {
                showBatchOperationDialog("Disable", "   定要禁用选中的 " + appListAdapter.getSelectedItemCount() + " 个应用吗？", appInfo -> {
                    if ("Installed".equals(appInfo.getStatus())) {
                        AdbHelper.disableApp(appInfo.getPackageName(), new AdbHelper.AdbCommandListener() {
                            @Override
                            public void onCommandOutput(String output) {
                                Log.d("ADB", output);
                            }

                            @Override
                            public void onCommandComplete(int exitCode) {
                                if (exitCode == 0) {
                                    appInfo.setStatus("Disabled");
                                    mainViewModel.loadApps();
                                    Snackbar.make(coordinatorLayout, "Disable successfully: " + appInfo.getAppName(), Snackbar.LENGTH_SHORT).show();
                                } else {
                                    Snackbar.make(coordinatorLayout, "Disable failed: " + appInfo.getAppName(), Snackbar.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onCommandError(Exception e) {
                                runOnUiThread(() -> Toast.makeText(MainActivity.this, "An error occurred: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                            }
                        });
                    }
                });
                return true;
            } else if (itemId == R.id.action_enable_selected) {
                showBatchOperationDialog("Enable", "确定要启用选中的 " + appListAdapter.getSelectedItemCount() + " 个应用吗？", appInfo -> {
                    if ("Disabled".equals(appInfo.getStatus())) {
                        AdbHelper.enableApp(appInfo.getPackageName(), new AdbHelper.AdbCommandListener() {
                            @Override
                            public void onCommandOutput(String output) {
                                Log.d("ADB", output);
                            }

                            @Override
                            public void onCommandComplete(int exitCode) {

                                if (exitCode == 0) {
                                    appInfo.setStatus("Installed");
                                    mainViewModel.loadApps();
                                    Snackbar.make(coordinatorLayout, "Enabled successfully: " + appInfo.getAppName(), Snackbar.LENGTH_SHORT).show();
                                } else {
                                    Snackbar.make(coordinatorLayout, "Enabled Failed: " + appInfo.getAppName(), Snackbar.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onCommandError(Exception e) {

                                runOnUiThread(() ->
                                        Snackbar.make(coordinatorLayout, "An error occurred: " + appInfo.getAppName(), Snackbar.LENGTH_SHORT).show()

                                );
                            }
                        });
                    }
                });
                return true;
            } else if (itemId == R.id.action_uninstall_selected) {
                // 使   单个卸载方法，而不是复制代码
                showBatchOperationDialog("Uninstall", "确定要卸载选中的 " + appListAdapter.getSelectedItemCount() + " 个应用吗？", this::uninstallApp);
                return true;
            } else if (itemId == R.id.action_install_selected) {
                // TODO: 实现批量安装功能
                Toast.makeText(this, "批量安装功能即将推出", Toast.LENGTH_SHORT).show();
                return true;
            } else if (itemId == R.id.action_extract_apk_selected) {
                // 批量提取APK
                batchExtractApks();
                return true;
            }
            return false;
        });
    }

    private void updateContextualToolbarMenuVisibility() {
        Menu menu = contextualToolbar.getMenu();
        boolean hasInstalledApps = false;
        boolean hasDisabledApps = false;
        boolean hasUninstalledApps = false;

        // 检查选中应用的状态
        for (AppInfo appInfo : appListAdapter.getCurrentList()) {
            if( appInfo.isSelected()) {
                if ("Installed".equals(appInfo.getStatus())) {
                    hasInstalledApps = true;
                } else if ("Disabled".equals(appInfo.getStatus())) {
                    hasDisabledApps = true;
                } else if ("Uninstalled".equals(appInfo.getStatus())) {
                    hasUninstalledApps = true;
                }
            }
        }

        // 更新菜单项可见性
        menu.findItem(R.id.action_disable_selected).setVisible(hasInstalledApps);
        menu.findItem(R.id.action_enable_selected).setVisible(hasDisabledApps);
        menu.findItem(R.id.action_uninstall_selected).setVisible(hasInstalledApps || hasDisabledApps);
        menu.findItem(R.id.action_install_selected).setVisible(hasUninstalledApps);
        menu.findItem(R.id.action_extract_apk_selected).setVisible(hasInstalledApps || hasDisabledApps);
    }

    private void showBatchOperationDialog(String operation, String message, BatchOperationCallback callback) {
        new MaterialAlertDialogBuilder(this).setTitle(operation + "应用").setMessage(message).setPositiveButton("确定", (dialog, which) -> {
            try {
                List<AppInfo> selectedApps = new ArrayList<>();
                for (AppInfo appInfo : appListAdapter.getCurrentList()) {
                    if (appInfo.isSelected()) {
                        selectedApps.add(appInfo);
                    }
                }

                // Use ExecutorService for better thread management
                executorService.execute(() -> {
                    int delay = 0;
                    for (AppInfo appInfo : selectedApps) {
                        // Schedule with increasing delay to avoid overwhelming ADB
                        uiHandler.postDelayed(() -> {
                            try {
                                callback.onOperation(appInfo);
                            } catch (Exception e) {
                                Log.e("MainActivity", "批量操作出错: " + e.getMessage(), e);
                                uiHandler.post(() ->
                                        Snackbar.make(coordinatorLayout, operation + "出错: " + e.getMessage(), Snackbar.LENGTH_SHORT).show()
                                );
                            }
                        }, delay);
                        delay += 150; // Reduced delay for faster execution
                    }
                });

                // Exit selection mode
                hideContextualToolbarAndClearSelection();
            } catch (Exception e) {
                Log.e("MainActivity", "批量操作对话框处理错误", e);
                Snackbar.make(findViewById(R.id.root), "批量" + operation + "过程中出错: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
            }
        }).setNegativeButton("取消", null).show();
    }

    private interface BatchOperationCallback {
        void onOperation(AppInfo appInfo);
    }

    private void hideContextualToolbarAndClearSelection() {
        AppListAdapter.setSelectionModeEnabled(false);
        if (collapseContextualToolbar()) {
            setItemsSelected(false);
            // 显示FAB
            extendedFloatingActionButton.show();
        }
    }

    private void expandContextualToolbar() {
        onBackPressedCallback.setEnabled(true);
        searchBar.expand(contextualToolbarContainer, appBarLayout);
    }

    private boolean collapseContextualToolbar() {
        onBackPressedCallback.setEnabled(false);
        return searchBar.collapse(contextualToolbarContainer, appBarLayout);
    }

    private void setItemsSelected(boolean selected) {
        appListAdapter.setItemsSelected(selected);
    }

    /**
     * 安装应用
     */
    private void installApp(AppInfo appInfo) {
        // 检查APK文件是否存在
        if (appInfo.getApkPath() != null && new File(appInfo.getApkPath()).exists()) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri apkUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", new File(appInfo.getApkPath()));
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } else {
            Toast.makeText(this, "APK文件不存在", Toast.LENGTH_SHORT).show();
        }
    }

    // 添加新方法来反转所有项的选择状态
    private void invertItemsSelection() {
        List<AppInfo> currentList = appListAdapter.getCurrentList();
        for (AppInfo appInfo : currentList) {
            appInfo.setSelected(!appInfo.isSelected());
        }
        appListAdapter.notifyDataSetChanged();

        // 触发选择状态改变监听器
        if (appListAdapter.getSelectedItemCount() > 0) {
            // 如果反选后仍有选中项，则更新工具栏标题
            contextualToolbar.setTitle(String.valueOf(appListAdapter.getSelectedItemCount()));
            // 直接调用onItemSelectedStateChanged方法
            onItemSelectedStateChanged(null);
        } else {
            // 如果反选后没有选中项，则退出选择模式
            AppListAdapter.setSelectionModeEnabled(false);
            collapseContextualToolbar();
        }
    }

    // 备份  中的应用
    private void backupSelectedApps() {
        // 获取选中的应用
        List<AppInfo> selectedApps = new ArrayList<>();
        for (AppInfo appInfo : appListAdapter.getCurrentList()) {
            if (appInfo.isSelected()) {
                selectedApps.add(appInfo);
            }
        }

        if (selectedApps.isEmpty()) {
            Toast.makeText(this, "没有选中的应用", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建备份记录
        backupRecords.clear();
        for (AppInfo appInfo : selectedApps) {
            backupRecords.add(new BackupRecord(appInfo.getPackageName(), appInfo.getStatus()));
        }

        // 生成备份文件名
        String time = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault()).format(new Date());
        String fileName = "selected_apps_backup_" + time + ".json";

        // 显示确认对话框
        new MaterialAlertDialogBuilder(this).setTitle("备份选中应用").setMessage("是否备份已选中的 " + selectedApps.size() + " 个应用？\n文件名：" + fileName).setPositiveButton("确定", (dialog, which) -> {
            // 执行备份操作
            createDocumentLauncher.launch(fileName);
            hideContextualToolbarAndClearSelection();
        }).setNegativeButton("取消", null).show();
    }

    private void extractSingleApk(AppInfo appInfo) {
        currentAppForExtraction = appInfo;
        String fileName = appInfo.getPackageName() + ".apk";
        createApkLauncher.launch(fileName);
    }

    private void extractAppApk(AppInfo appInfo) {
        // 判断是否处于选择模式
        if (AppListAdapter.isSelectionModeEnabled()) {
            // 在选择模式下，确保该应用被选中
            if (!appInfo.isSelected()) {
                appInfo.setSelected(true);
                appListAdapter.notifyItemChanged(appListAdapter.getCurrentList().indexOf(appInfo));
            }
            // 使用批量提取逻辑处理
            batchExtractApks();
        } else {
            // 正常模式下直接提取
            extractSingleApk(appInfo);
        }
    }

    /**
     * 批量提取APK文件
     */
    private void batchExtractApks() {
        // 获取选中的应用
        List<AppInfo> selectedApps = new ArrayList<>();
        for (AppInfo appInfo : appListAdapter.getCurrentList()) {
            if (appInfo.isSelected()) {
                selectedApps.add(appInfo);
            }
        }

        if (selectedApps.isEmpty()) {
            Toast.makeText(this, "没有选中的应用", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查是否只有一个应用被选中
        if (selectedApps.size() == 1) {
            // 单个应用提取，使用单个提取逻辑
            AppInfo singleApp = selectedApps.get(0);
            extractSingleApk(singleApp);
            return;
        }

        currentAppsForBatchExtraction = new ArrayList<>(selectedApps);

        // 显示确认对话框
        new MaterialAlertDialogBuilder(this)
                .setTitle("提取安装包")
                .setMessage("确定要提取选中的 " + selectedApps.size() + " 个应用的安装包吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    // Launch directory picker
                    openDirectoryLauncher.launch(null);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void batchExtractApksToUri(List<AppInfo> apps, Uri treeUri) {
        progressIndicator.setVisibility(View.VISIBLE);

        // Use a single reusable Snackbar instead of creating new ones
        final Snackbar progressSnackbar = Snackbar.make(
                coordinatorLayout,
                "正在提取应用... (0/" + apps.size() + ")",
                Snackbar.LENGTH_INDEFINITE);
        progressSnackbar.show();

        // Take persistable URI permission
        try {
            getContentResolver().takePersistableUriPermission(treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException e) {
            Log.e("MainActivity", "Failed to take persistable permissions", e);
        }

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicInteger processedCount = new AtomicInteger(0);

        // Use the shared executor service instead of creating a new thread
        executorService.execute(() -> {
            // Process in smaller batches for better memory efficiency
            int batchSize = 3;
            CountDownLatch allCompleted = new CountDownLatch(apps.size());

            for (int i = 0; i < apps.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, apps.size());
                List<AppInfo> batch = apps.subList(i, endIndex);

                // Process each batch
                for (AppInfo appInfo : batch) {
                    String fileName = appInfo.getAppName().replaceAll("[\\\\/:*?\"<>|]", "_") + "_" +
                            appInfo.getPackageName() + ".apk";

                    try {
                        Uri fileUri = DocumentsContract.createDocument(
                                getContentResolver(),
                                treeUri,
                                "application/vnd.android.package-archive",
                                fileName);

                        if (fileUri != null) {
                            AdbHelper.extractApk(MainActivity.this, appInfo.getApkPath(), fileUri,
                                    new AdbHelper.AdbCommandListener() {
                                        @Override
                                        public void onCommandOutput(String output) {
                                            Log.d("APK Extract Batch", output);
                                        }

                                        @Override
                                        public void onCommandComplete(int exitCode) {
                                            if (exitCode == 0) {
                                                successCount.incrementAndGet();
                                            } else {
                                                failCount.incrementAndGet();
                                            }

                                            int current = processedCount.incrementAndGet();
                                            // Update UI less frequently for better performance
                                            if (current % 2 == 0 || current == apps.size()) {
                                                uiHandler.post(() ->
                                                        progressSnackbar.setText("正在提取应用... (" + current + "/" + apps.size() + ")")
                                                );
                                            }
                                            allCompleted.countDown();
                                        }

                                        @Override
                                        public void onCommandError(Exception e) {
                                            Log.e("MainActivity", "Error extracting APK: " + e.getMessage(), e);
                                            failCount.incrementAndGet();
                                            allCompleted.countDown();
                                        }
                                    });
                        } else {
                            Log.e("MainActivity", "Failed to create document for " + fileName);
                            failCount.incrementAndGet();
                            allCompleted.countDown();
                        }
                    } catch (Exception e) {
                        Log.e("MainActivity", "Error creating document: " + e.getMessage(), e);
                        failCount.incrementAndGet();
                        allCompleted.countDown();
                    }
                }

                // Brief pause between batches to prevent overwhelming the system
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // Wait for all operations to complete
            try {
                allCompleted.await(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            uiHandler.post(() -> {
                progressSnackbar.dismiss();
                progressIndicator.setVisibility(View.GONE);
                showExtractResultDialog(successCount.get(), failCount.get());
                currentAppsForBatchExtraction = null;
            });
        });
    }

    private void showExtractResultDialog(int successCount, int failCount) {
        new MaterialAlertDialogBuilder(MainActivity.this)
                .setTitle("提取完成")
                .setMessage("成功: " + successCount +
                        "\n失败: " + failCount)
                .setPositiveButton("关闭", null)
                .setOnDismissListener(dialog -> {
                    // 退出选择模式
                    hideContextualToolbarAndClearSelection();
                })
                .show();
    }
    public int getScreenHeight(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics windowMetrics = windowManager.getCurrentWindowMetrics();
            return windowMetrics.getBounds().height();
        } else {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(displayMetrics);
            return displayMetrics.heightPixels;
        }
    }

}
