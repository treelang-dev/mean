package com.treelang.mean.adapters;

import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.treelang.mean.R;
import com.treelang.mean.data.AppInfo;
import com.treelang.mean.utils.AppUtils;

import android.content.Context;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.AppViewHolder> {

    private OnAppItemClickListener itemClickListener;
    private OnAppItemLongClickListener itemLongClickListener;
    private OnItemSelectedStateChangedListener onItemSelectedStateChangedListener;
    private static boolean selectionModeEnabled = false;

    // 添加用于搜索过滤的变量
    private List<AppInfo> originalList = new ArrayList<>(); // 保存原始列表
    private List<AppInfo> appList = new ArrayList<>();     // 当前显示的列表
    private String currentQuery = "";

    // Performance optimization: Payloads for efficient updates
    public static final String PAYLOAD_SELECTION_CHANGED = "selection_changed";
    public static final String PAYLOAD_ICON_LOADED = "icon_loaded";

    // Background executor for heavy operations
    private static final Executor backgroundExecutor = Executors.newFixedThreadPool(2);

    public interface OnAppItemClickListener{
        void onAppItemClick(AppInfo appInfo);
    }
    public interface  OnAppItemLongClickListener{
        boolean onAppItemLongClick(AppInfo appInfo);
    }
    public interface OnItemSelectedStateChangedListener {
        void onItemSelectedStateChanged(AppInfo appInfo);
    }

    public AppListAdapter() {
        super();
        // Enable stable IDs for better performance
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        // Use package name hash for stable IDs
        return appList.get(position).getPackageName().hashCode();
    }

    // Optimized DiffUtil implementation
    private static class AppDiffCallback extends DiffUtil.Callback {
        private final List<AppInfo> oldList;
        private final List<AppInfo> newList;

        AppDiffCallback(List<AppInfo> oldList, List<AppInfo> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

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
            return oldList.get(oldItemPosition).getPackageName().equals(
                    newList.get(newItemPosition).getPackageName());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            AppInfo oldItem = oldList.get(oldItemPosition);
            AppInfo newItem = newList.get(newItemPosition);
            return oldItem.getPackageName().equals(newItem.getPackageName()) &&
                   oldItem.isSelected() == newItem.isSelected() &&
                   oldItem.getAppName().equals(newItem.getAppName()) &&
                   oldItem.getVersionName().equals(newItem.getVersionName());
        }

        @Override
        public Object getChangePayload(int oldItemPosition, int newItemPosition) {
            AppInfo oldItem = oldList.get(oldItemPosition);
            AppInfo newItem = newList.get(newItemPosition);

            if (oldItem.isSelected() != newItem.isSelected()) {
                return PAYLOAD_SELECTION_CHANGED;
            }
            if (oldItem.getAppIcon() != newItem.getAppIcon()) {
                return PAYLOAD_ICON_LOADED;
            }
            return null;
        }
    }

    public void submitList(List<AppInfo> newList) {
        if (newList == null) {
            newList = new ArrayList<>();
        }

        // 更新原始列表
        originalList = new ArrayList<>(newList);

        // 如果有搜索查询，应用过滤
        if (currentQuery != null && !currentQuery.isEmpty()) {
            filterByQuery(currentQuery);
        } else {
            updateList(newList);
        }
    }

    private void updateList(List<AppInfo> newList) {
        // Run DiffUtil calculation on background thread for large lists
        if (newList.size() > 100 || appList.size() > 100) {
            backgroundExecutor.execute(() -> {
                AppDiffCallback diffCallback = new AppDiffCallback(appList, newList);
                DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);

                // Update UI on main thread
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    appList.clear();
                    appList.addAll(newList);
                    diffResult.dispatchUpdatesTo(this);
                });
            });
        } else {
            AppDiffCallback diffCallback = new AppDiffCallback(appList, newList);
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);
            appList = new ArrayList<>(newList);
            diffResult.dispatchUpdatesTo(this);
        }
    }

    // 添加搜索过滤方法
    public void filterByQuery(String query) {
        currentQuery = query;
        if (query == null || query.isEmpty()) {
            // 如果查询为空，显示所有应用
            updateList(originalList);
            return;
        }

        // 执行过滤
        List<AppInfo> filteredList = new ArrayList<>();
        for (AppInfo appInfo : originalList) {
            // 匹配应用名称或包名
            if (appInfo.getAppName().toLowerCase().contains(query.toLowerCase()) ||
                appInfo.getPackageName().toLowerCase().contains(query.toLowerCase())) {
                filteredList.add(appInfo);
            }
        }

        updateList(filteredList);
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    public AppInfo getItem(int position) {
        return appList.get(position);
    }

    public void setOnAppItemClickListener(OnAppItemClickListener listener){
        this.itemClickListener = listener;
    }

    public void setOnAppItemLongClickListener(OnAppItemLongClickListener listener){
        this.itemLongClickListener = listener;
    }

    public void setOnItemSelectedStateChangedListener(OnItemSelectedStateChangedListener listener) {
        this.onItemSelectedStateChangedListener = listener;
    }

    public static boolean isSelectionModeEnabled() {
        return selectionModeEnabled;
    }

    public static void setSelectionModeEnabled(boolean enabled) {
        selectionModeEnabled = enabled;
    }

    public void setItemsSelected(boolean selected) {
        for (AppInfo appInfo : appList) {
            appInfo.setSelected(selected);
        }
        notifyDataSetChanged();
        // 触发选择状态改变监听器
        if (onItemSelectedStateChangedListener != null) {
            onItemSelectedStateChangedListener.onItemSelectedStateChanged(null);
        }
    }

    public long getSelectedItemCount() {
        long count = 0;
        for (AppInfo appInfo : appList) {
            if (appInfo.isSelected()) {
                count++;
            }
        }
        return count;
    }
    public List<AppInfo> getSelectedItems() {
        List<AppInfo> selectedItems = new ArrayList<>();
        for (AppInfo appInfo : appList) {
            if (appInfo.isSelected()) {
                selectedItems.add(appInfo);
            }
        }
        return selectedItems;
    }

    // 添加获取当前列表的方法
    public List<AppInfo> getCurrentList() {
        return new ArrayList<>(appList);
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_list, parent, false);
        return new AppViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(AppViewHolder holder, int position) {
        onBindViewHolder(holder, position, new ArrayList<>());
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position, @NonNull List<Object> payloads) {
        AppInfo currentApp = getItem(position);

        if (payloads.isEmpty()) {
            // Full bind
            holder.bind(currentApp, itemClickListener, itemLongClickListener, onItemSelectedStateChangedListener);

            // 直接设置图标 - 使用已同步加载好的图标
            if (currentApp.getAppIcon() != null) {
                holder.appIconImageView.setImageDrawable(currentApp.getAppIcon());
            } else {
                // 如果某种原因导致图标为空，尝试从缓存中获取
                Context context = holder.itemView.getContext();
                android.graphics.drawable.Drawable cachedIcon = AppUtils.getIconFromCache(currentApp.getPackageName());

                if (cachedIcon != null) {
                    holder.appIconImageView.setImageDrawable(cachedIcon);
                    currentApp.setAppIcon(cachedIcon);
                } else {
                    // 如果缓存也没有，则设置默认图标
                    holder.appIconImageView.setImageDrawable(context.getPackageManager().getDefaultActivityIcon());
                }
            }

            // 设置选中状态
            holder.cardView.setChecked(currentApp.isSelected());
        } else {
            // Partial bind with payloads for better performance
            for (Object payload : payloads) {
                if (PAYLOAD_SELECTION_CHANGED.equals(payload)) {
                    holder.cardView.setChecked(currentApp.isSelected());
                } else if (PAYLOAD_ICON_LOADED.equals(payload)) {
                    if (currentApp.getAppIcon() != null) {
                        holder.appIconImageView.setImageDrawable(currentApp.getAppIcon());
                    }
                }
            }
        }
    }

    class AppViewHolder extends RecyclerView.ViewHolder {
        private TextView appNameTextView;
        private TextView packageNameTextView;
        private TextView appInfoTextView; // 合并后的信息文本
        private ImageView appIconImageView;
        private MaterialCardView cardView;

        AppViewHolder(View itemView) {
            super(itemView);
            appNameTextView = itemView.findViewById(R.id.app_name_text_view);
            packageNameTextView = itemView.findViewById(R.id.package_name_text_view);
            appInfoTextView = itemView.findViewById(R.id.app_info_text_view);
            appIconImageView = itemView.findViewById(R.id.app_icon_image_view);
            cardView = (MaterialCardView) itemView;
        }

        void bind(AppInfo appInfo,
                 OnAppItemClickListener clickListener,
                 OnAppItemLongClickListener longClickListener,
                 OnItemSelectedStateChangedListener selectionListener) {
            appNameTextView.setText(appInfo.getAppName());
            packageNameTextView.setText(appInfo.getPackageName());

            // 构建信息文本
            StringBuilder infoBuilder = new StringBuilder();

            // 添加版本信息
            infoBuilder.append(appInfo.getVersionName())
                      .append(" (")
                      .append(appInfo.getVersionCode())
                      .append(")");

            // 添加应用大小信息
            String sizeText = Formatter.formatFileSize(itemView.getContext(), appInfo.getAppSize());
            infoBuilder.append("  ").append(sizeText);

            // 处理分包信息
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
                            // 添加分包信息
                            String splitSizeFormatted = Formatter.formatFileSize(itemView.getContext(), totalSplitSize);
                            infoBuilder.append("  (split +").append(splitSizeFormatted).append(")");
                        }
                    }
                }
            }

            // 添加API级别信息
            infoBuilder.append("  API ").append(appInfo.getTargetSdkVersion());

            // 设置合并后的信息文本
            appInfoTextView.setText(infoBuilder.toString());

            // 为分包信息添加特殊颜色
            if (appInfo.getApkPath() != null) {
                String text = appInfoTextView.getText().toString();
                int splitIndex = text.indexOf("  (+");
                if (splitIndex != -1) {
                    // 找到")"的索引位置，计算出分包信息的范围
                    int splitEndIndex = text.indexOf(")", splitIndex);
                    if (splitEndIndex != -1) {
                        // 使用SpannableString设置颜色
                        android.text.SpannableString spannableString = new android.text.SpannableString(text);
                        spannableString.setSpan(
                            new android.text.style.ForegroundColorSpan(
                                itemView.getContext().getColor(android.R.color.holo_blue_dark)),
                            splitIndex,
                            splitEndIndex + 1,
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        appInfoTextView.setText(spannableString);
                    }
                }
            }

            // 设置选中状态
            cardView.setChecked(appInfo.isSelected());

            // 设置点击事件
            cardView.setOnClickListener(v -> {
                if (selectionModeEnabled) {
                    toggleItemSelection(appInfo, selectionListener);
                } else if (clickListener != null) {
                    clickListener.onAppItemClick(appInfo);
                }
            });

            // 设置长按事件
            cardView.setOnLongClickListener(v -> {
                if (selectionModeEnabled) {
                    return false;
                }
                if (longClickListener != null) {
                    return longClickListener.onAppItemLongClick(appInfo);
                }
                return false;
            });
        }

        private void toggleItemSelection(AppInfo appInfo, OnItemSelectedStateChangedListener listener) {
            appInfo.setSelected(!appInfo.isSelected());
            cardView.setChecked(appInfo.isSelected());
            if (listener != null) {
                listener.onItemSelectedStateChanged(appInfo);
            }
        }
    }
    // 格式化应用大小的显示 (B, KB, MB, GB)
    private String formatAppSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size)/Math.log10(1024));
        return new DecimalFormat("#,##0.##").format(size/Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
}
