package com.treelang.mean.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.treelang.mean.data.BackupRecord;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public class FileUtils {
    private static final String TAG = "FileUtils";

    public static boolean exportBackupToFile(Context context, List<BackupRecord> records, Uri uri) {
        JSONArray jsonArray = new JSONArray();

        try {
            // 创建 JSON 数据
            for (BackupRecord record : records) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("packageName", record.getPackageName());
                jsonObject.put("appStatus", record.getAppStatus());
                jsonArray.put(jsonObject);
            }

            // 使用 SAF 写入文件
            try (OutputStream os = context.getContentResolver().openOutputStream(uri);
                 OutputStreamWriter writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                writer.write(jsonArray.toString(2)); // 使用 toString(2) 进行漂亮的打印（缩进）
            }

            return true;
        } catch (JSONException | IOException e) {
            Log.e(TAG, "Failed to export backup", e);
            return false;
        }
    }

    /**
     * 从Documents目录导入备份文件
     * @param context 上下文
     * @param uri 文件的Uri
     * @return 备份记录列表
     */
    public static List<BackupRecord> importBackupFromFile(Context context, Uri uri) {
        List<BackupRecord> records = new ArrayList<>();
        StringBuilder stringBuilder = new StringBuilder();

        try (InputStream is = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }

            JSONArray jsonArray = new JSONArray(stringBuilder.toString());
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String packageName = jsonObject.getString("packageName");
                String appStatus = jsonObject.getString("appStatus");

                // 验证字段值
                if (isValidPackageName(packageName) && isValidAppStatus(appStatus)) {
                    records.add(new BackupRecord(packageName, appStatus));
                } else {
                    Log.w(TAG, "Invalid package name or status at index " + i);
                }
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to import backup", e);
            return null; // 或者返回一个空列表，取决于错误处理策略
        }

        return records;
    }

    /**
     * 验证包名是否合法
     */
    private static boolean isValidPackageName(String packageName) {
        return packageName != null &&
                packageName.matches("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$");
    }

    /**
     * 验证应用状态是否合法
     */
    private static boolean isValidAppStatus(String status) {
        return status != null &&
                (status.equals("Installed") ||
                        status.equals("Uninstalled") ||
                        status.equals("Disabled"));
    }
}