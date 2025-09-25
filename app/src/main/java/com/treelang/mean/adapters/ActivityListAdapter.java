package com.treelang.mean.adapters;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.divider.MaterialDivider;
import com.treelang.mean.R;

import java.util.List;

public class ActivityListAdapter extends ListAdapter<ActivityInfo, ActivityListAdapter.ViewHolder> {
    private OnActivityClickListener listener;
    private PackageManager packageManager;

    public interface OnActivityClickListener {
        void onActivityClick(ActivityInfo activity);
    }

    public ActivityListAdapter(PackageManager packageManager) {
        super(new DiffUtil.ItemCallback<ActivityInfo>() {
            @Override
            public boolean areItemsTheSame(@NonNull ActivityInfo oldItem, @NonNull ActivityInfo newItem) {
                return oldItem.name.equals(newItem.name);
            }

            @Override
            public boolean areContentsTheSame(@NonNull ActivityInfo oldItem, @NonNull ActivityInfo newItem) {
                return oldItem.name.equals(newItem.name) && 
                       oldItem.exported == newItem.exported &&
                       oldItem.loadLabel(packageManager).equals(newItem.loadLabel(packageManager));
            }
        });
        this.packageManager = packageManager;
    }

    public void setOnActivityClickListener(OnActivityClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_activity, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ActivityInfo activity = getItem(position);
        
        // 设置图标
        Drawable icon = activity.loadIcon(packageManager);
        holder.activityIcon.setImageDrawable(icon);
        
        // 设置标签和名称
        holder.activityLabel.setText(activity.loadLabel(packageManager));
        holder.activityName.setText(activity.name);

        // 根据导出状态设置样式
        if (!activity.exported) {
            holder.activityName.setTextColor(holder.itemView.getContext().getColor(android.R.color.darker_gray));
            holder.activityName.setPaintFlags(holder.activityName.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            holder.activityName.setTextColor(holder.itemView.getContext().getColor(android.R.color.black));
            holder.activityName.setPaintFlags(holder.activityName.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onActivityClick(activity);
            }
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView activityIcon;
        TextView activityLabel;
        TextView activityName;

        ViewHolder(View itemView) {
            super(itemView);
            activityIcon = itemView.findViewById(R.id.activity_icon);
            activityLabel = itemView.findViewById(R.id.activity_label);
            activityName = itemView.findViewById(R.id.activity_name);
        }
    }
} 