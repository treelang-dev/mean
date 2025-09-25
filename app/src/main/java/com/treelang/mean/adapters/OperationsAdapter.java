package com.treelang.mean.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.treelang.mean.R;

import java.util.List;

public class OperationsAdapter extends RecyclerView.Adapter<OperationsAdapter.OperationViewHolder> {

    // Performance optimization: Enable stable IDs
    {
        setHasStableIds(true);
    }

    public static class Operation {
        private final String text;
        private final int iconResId;
        private final String description;

        public Operation(String text, int iconResId, String description) {
            this.text = text;
            this.iconResId = iconResId;
            this.description = description;
        }

        // Keep backward compatibility with existing constructor
        public Operation(String text, int iconResId) {
            this(text, iconResId, getDefaultDescription(text));
        }

        private static String getDefaultDescription(String operationText) {
            switch (operationText) {
                case "Launch":
                    return "Start the application";
                case "Details":
                    return "View detailed app information";
                case "Disable":
                    return "Disable app without uninstalling";
                case "Enable":
                    return "Re-enable disabled application";
                case "Uninstall":
                    return "Remove app completely";
                case "Uninstall(For current User only)":
                    return "Remove for current user (system apps)";
                case "Install":
                    return "Reinstall previously removed app";
                case "Extract APK":
                    return "Save APK file to storage";
                case "Activities":
                    return "View app's activity components";
                case "More":
                    return "Open system app settings";
                default:
                    return "Perform operation";
            }
        }

        public String getText() { return text; }
        public int getIconResId() { return iconResId; }
        public String getDescription() { return description; }
    }

    private List<Operation> operations;
    private OnOperationClickListener listener;

    public interface OnOperationClickListener {
        void onOperationClick(String operation);
    }

    public OperationsAdapter(List<Operation> operations, OnOperationClickListener listener) {
        this.operations = operations;
        this.listener = listener;
    }

    @NonNull
    @Override
    public OperationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_operation, parent, false);
        return new OperationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OperationViewHolder holder, int position) {
        Operation operation = operations.get(position);
        holder.operationText.setText(operation.getText());
        holder.operationIcon.setImageResource(operation.getIconResId());
        holder.operationDescription.setText(operation.getDescription());

        // The MaterialCard handles touch feedback automatically
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onOperationClick(operation.getText());
            }
        });
    }

    @Override
    public int getItemCount() {
        return operations != null ? operations.size() : 0;
    }

    @Override
    public long getItemId(int position) {
        // Use operation text hash for stable IDs
        return operations.get(position).getText().hashCode();
    }

    static class OperationViewHolder extends RecyclerView.ViewHolder {
        TextView operationText;
        ImageView operationIcon;
        TextView operationDescription;

        OperationViewHolder(@NonNull View itemView) {
            super(itemView);
            operationText = itemView.findViewById(R.id.operation_text);
            operationIcon = itemView.findViewById(R.id.operation_icon);
            operationDescription = itemView.findViewById(R.id.operation_description);
        }
    }
}
