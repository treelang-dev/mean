package com.treelang.mean.data;

public class BackupRecord {
    private final String packageName;
    private final String appStatus; // "Installed", "Disabled", "Uninstalled"

    // 构造函数、getters 和 setters
    public BackupRecord(String packageName,String appStatus){
        this.packageName = packageName;
        this.appStatus = appStatus;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getAppStatus() {
        return appStatus;
    }
}