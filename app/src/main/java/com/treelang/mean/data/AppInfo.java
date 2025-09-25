package com.treelang.mean.data;

import android.graphics.drawable.Drawable;

public class AppInfo {
    private String appName;
    private String packageName;
    private String versionName;
    private int versionCode;
    private long firstInstallTime;
    private long lastUpdateTime;
    private String installerPackageName;
    private Drawable appIcon;
    private boolean isSystemApp;
    private boolean isCoreApp;
    private boolean isPrivilegedApp;
    private String status = "Installed";
    private long appSize;  // 添加 appSize 属性
    private String dataPath;               // 数据目录路径
    private String externalDataPath;       // 外部存储数据路径
    private String apkPath;                // 安装包路径
    private int uid;                       // 应用 UID

    private int targetSdkVersion;          // Target API Level
    private int minSdkVersion;             // Minimum API Level
    private int compileSdkVersion;         // Compile API Level

    private boolean selected = false;

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public int getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(int versionCode) {
        this.versionCode = versionCode;
    }

    public long getFirstInstallTime() {
        return firstInstallTime;
    }

    public void setFirstInstallTime(long firstInstallTime) {
        this.firstInstallTime = firstInstallTime;
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(long lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public void setInstallerPackageName(String installerPackageName) {
        this.installerPackageName = installerPackageName;
    }

    public Drawable getAppIcon() {
        return appIcon;
    }

    public void setAppIcon(Drawable appIcon) {
        this.appIcon = appIcon;
    }
    public boolean isSystemApp() {
        return isSystemApp;
    }

    public void setSystemApp(boolean systemApp) {
        isSystemApp = systemApp;
    }
    public boolean isPrivilegedApp() {
        return isPrivilegedApp;
    }

    public void setPrivilegedApp(boolean privilegedApp) {
        isPrivilegedApp = privilegedApp;
    }

    public void setUserApp(boolean userApp) {
    }

    public boolean isCoreApp() {
        return isCoreApp;
    }
    public void setCoreApp(boolean coreApp) {
        isCoreApp = coreApp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getAppSize() {  // 新增
        return appSize;
    }

    public void setAppSize(long appSize) { // 新增
        this.appSize = appSize;
    }

    public String getDataPath() {
        return dataPath;
    }

    public void setDataPath(String dataPath) {
        this.dataPath = dataPath;
    }

    public String getExternalDataPath() {
        return externalDataPath;
    }

    public void setExternalDataPath(String externalDataPath) {
        this.externalDataPath = externalDataPath;
    }

    public String getApkPath() {
        return apkPath;
    }

    public void setApkPath(String apkPath) {
        this.apkPath = apkPath;
    }

    public int getUid() {
        return uid;
    }

    public void setUid(int uid) {
        this.uid = uid;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public int getTargetSdkVersion() {
        return targetSdkVersion;
    }

    public void setTargetSdkVersion(int targetSdkVersion) {
        this.targetSdkVersion = targetSdkVersion;
    }

    public int getMinSdkVersion() {
        return minSdkVersion;
    }

    public void setMinSdkVersion(int minSdkVersion) {
        this.minSdkVersion = minSdkVersion;
    }

    public int getCompileSdkVersion() {
        return compileSdkVersion;
    }

    public void setCompileSdkVersion(int compileSdkVersion) {
        this.compileSdkVersion = compileSdkVersion;
    }

}