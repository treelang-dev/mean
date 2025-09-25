package com.treelang.mean.utils;

import android.content.pm.ApplicationInfo;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.LruCache;

import com.treelang.mean.data.AppInfo;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;

public class AppUtils {
    // 添加图标缓存
    private static final int ICON_CACHE_SIZE = 100; // 缓存大小
    private static final LruCache<String, Drawable> iconCache = new LruCache<>(ICON_CACHE_SIZE);

    // 从缓存获取图标，如不存在则返回null
    public static Drawable getIconFromCache(String packageName) {
        return iconCache.get(packageName);
    }

    // 添加图标到缓存
    public static void addIconToCache(String packageName, Drawable icon) {
        if (packageName != null && icon != null) {
            iconCache.put(packageName, icon);
        }
    }

    //从PackageInfo提取并填充AppInfo,
    public static AppInfo getAppInfoFromPackageInfo(PackageManager pm, PackageInfo packageInfo) {
        AppInfo appInfo = new AppInfo();
        try {
            appInfo.setAppName(packageInfo.applicationInfo.loadLabel(pm).toString());
            appInfo.setPackageName(packageInfo.packageName);
            appInfo.setVersionName(packageInfo.versionName);
            appInfo.setVersionCode((int) packageInfo.getLongVersionCode());

            // 设置API级别信息
            appInfo.setTargetSdkVersion(packageInfo.applicationInfo.targetSdkVersion);
            appInfo.setMinSdkVersion(packageInfo.applicationInfo.minSdkVersion);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                appInfo.setCompileSdkVersion(packageInfo.applicationInfo.compileSdkVersion);
            } else {
                appInfo.setCompileSdkVersion(-1);
            }

            // 直接使用传入的packageInfo获取安装时间
            appInfo.setFirstInstallTime(packageInfo.firstInstallTime);
            appInfo.setLastUpdateTime(packageInfo.lastUpdateTime);

            // 如果是系统应用且安装时间为0，尝试获取APK文件时间
            if ((packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && packageInfo.firstInstallTime == 0) {
                File apkFile = new File(packageInfo.applicationInfo.sourceDir);
                if (apkFile.exists()) {
                    long modifiedTime = apkFile.lastModified();
                    appInfo.setFirstInstallTime(modifiedTime);
                    appInfo.setLastUpdateTime(modifiedTime);
                }
            }

            // 获取安装来源
            try {
                String installingPackageName = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    InstallSourceInfo sourceInfo = pm.getInstallSourceInfo(packageInfo.packageName);
                    installingPackageName = sourceInfo.getInstallingPackageName();
                } else {
                    // 使用API 28兼容的方法获取安装源
                    installingPackageName = pm.getInstallerPackageName(packageInfo.packageName);
                }
                appInfo.setInstallerPackageName(installingPackageName != null ? installingPackageName : "未知来源");
            } catch (PackageManager.NameNotFoundException e) {
                appInfo.setInstallerPackageName("未知来源");
            }

            // 直接同步加载图标 - 修复问题
            // 异步加载会导致初始显示问题，我们改回同步加载
            appInfo.setAppIcon(packageInfo.applicationInfo.loadIcon(pm));

            // 添加到缓存，以便后续使用
            addIconToCache(packageInfo.packageName, appInfo.getAppIcon());

            // 设置应用类型标志
            setAppTypeFlags(packageInfo, appInfo);

            //设置大小
            appInfo.setAppSize(getApkSize(packageInfo));

            // 设置新增信息
            appInfo.setDataPath("/data/user/0/" + packageInfo.packageName);
            appInfo.setExternalDataPath("/storage/emulated/0/Android/data/" + packageInfo.packageName);
            appInfo.setApkPath(packageInfo.applicationInfo.sourceDir);
            appInfo.setUid(packageInfo.applicationInfo.uid);

            return appInfo;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // 获取 APK 文件大小
    public static long getApkSize(PackageInfo packageInfo) {
        File apkFile = new File(packageInfo.applicationInfo.publicSourceDir);
        if (apkFile.exists()) {
            return apkFile.length();
        }
        return 0;
    }

    private static void setAppTypeFlags(PackageInfo packageInfo, AppInfo appInfo) {
        //是否是系统应用
        boolean isSystem = isSystemApp(packageInfo);
        //是否是特权应用
        boolean isPrivileged = isPrivilegedApp(packageInfo);
        //是否是核心应用
        boolean isCore = isCoreApp(packageInfo);

        // 不允许重叠的分类逻辑：优先级 核心应用 > 特权应用 > 系统应用 > 用户应用
        if (isCore) {
            appInfo.setCoreApp(true);
            appInfo.setSystemApp(false);
            appInfo.setPrivilegedApp(false);
            appInfo.setUserApp(false);
        } else if (isPrivileged) {
            appInfo.setCoreApp(false);
            appInfo.setSystemApp(false);
            appInfo.setPrivilegedApp(true);
            appInfo.setUserApp(false);
        } else if (isSystem) {
            appInfo.setCoreApp(false);
            appInfo.setSystemApp(true);
            appInfo.setPrivilegedApp(false);
            appInfo.setUserApp(false);
        } else {
            // 如果不是核心、特权或系统应用，则为用户应用
            appInfo.setCoreApp(false);
            appInfo.setSystemApp(false);
            appInfo.setPrivilegedApp(false);
            appInfo.setUserApp(true);
        }
    }

    //判断是否是系统应用,判断flag和位置
    private static boolean isSystemApp(PackageInfo packageInfo) {
        String sourceDir = packageInfo.applicationInfo.sourceDir;
        // 检查是否在系统目录下的不可卸载应用
        boolean isSystemUnremovable = ((packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) && (
                // product 目录下的不可卸载应用
                sourceDir.startsWith("/system/product/app/") || sourceDir.startsWith("/product/app/") ||
                        // system_ext 目录
                        sourceDir.startsWith("/system/system_ext/app/") || sourceDir.startsWith("/system_ext/app/") ||
                        // vendor 目录
                        sourceDir.startsWith("/system/vendor/app/") || sourceDir.startsWith("/vendor/app/") ||
                        // oem 目录
                        sourceDir.startsWith("/system/oem/app/") || sourceDir.startsWith("/oem/app/"));

        // 检查是否在data-app目录下的可卸载系统应用
        boolean isSystemRemovable = ((packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) && (sourceDir.startsWith("/system/product/data-app/") || sourceDir.startsWith("/product/data-app/") || sourceDir.startsWith("/system/system_ext/data-app/") || sourceDir.startsWith("/system_ext/data-app/"));

        return isSystemUnremovable || isSystemRemovable;
    }

    //是否是特权应用
    private static boolean isPrivilegedApp(PackageInfo packageInfo) {
        try {
            Class<?> applicationInfoClass = ApplicationInfo.class;
            Field privateFlagsField = applicationInfoClass.getDeclaredField("privateFlags");
            privateFlagsField.setAccessible(true);
            Field privilegedFlagField = applicationInfoClass.getDeclaredField("PRIVATE_FLAG_PRIVILEGED");
            privilegedFlagField.setAccessible(true);
            int PRIVATE_FLAG_PRIVILEGED = (int) privilegedFlagField.get(null);
            int privateFlags = (int) privateFlagsField.get(packageInfo.applicationInfo);
            boolean hasPrivilegedFlag = (privateFlags & PRIVATE_FLAG_PRIVILEGED) != 0;

            String sourceDir = packageInfo.applicationInfo.sourceDir;
            // 检查是否在特权应用目录下的不可卸载应用
            boolean isInPrivilegedDir = ((packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) && (
                    // system priv-app
                    sourceDir.startsWith("/system/priv-app/") ||
                            // system_ext priv-app
                            sourceDir.startsWith("/system/system_ext/priv-app/") || sourceDir.startsWith("/system_ext/priv-app/") ||
                            // product priv-app
                            sourceDir.startsWith("/system/product/priv-app/") || sourceDir.startsWith("/product/priv-app/") ||
                            // vendor priv-app
                            sourceDir.startsWith("/system/vendor/priv-app/") || sourceDir.startsWith("/vendor/priv-app/") ||
                            // oem priv-app
                            sourceDir.startsWith("/system/oem/priv-app/") || sourceDir.startsWith("/oem/priv-app/"));
            return hasPrivilegedFlag && isInPrivilegedDir;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return false;
        }
    }

    //是否是核心应用
    private static boolean isCoreApp(PackageInfo packageInfo) {
        boolean hasSystemFlag = (packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        String sourceDir = packageInfo.applicationInfo.sourceDir;
        // 扩展核心应用目录检查
        boolean isInCoreDir = (sourceDir.startsWith("/system/app/") ||
                // 某些设备可能在system分区的子目录下也有核心应用
                sourceDir.startsWith("/system/system_app/"));
        return hasSystemFlag && isInCoreDir;
    }

    public enum SortType {
        NAME_ASC,           // 名称升序
        NAME_DESC,          // 名称降序
        INSTALL_TIME_ASC,   // 安装时间升序
        INSTALL_TIME_DESC,  // 安装时间降序
        UPDATE_TIME_ASC,    // 更新时间升序
        UPDATE_TIME_DESC,   // 更新时间降序
        SIZE_ASC,          // 大小升序
        SIZE_DESC,         // 大小降序
    }

    public static void sortAppList(List<AppInfo> appList, SortType sortType) {
        if (appList == null || appList.isEmpty()) {
            return;
        }

        // 创建新的排序后的列表
        List<AppInfo> sortedList = new java.util.ArrayList<>(appList);

        // 使用统一的Comparator实现
        switch (sortType) {
            case NAME_ASC:
                sortedList.sort(Comparator.comparing(AppInfo::getAppName, String::compareToIgnoreCase));
                break;
            case NAME_DESC:
                sortedList.sort(Comparator.comparing(AppInfo::getAppName, String::compareToIgnoreCase).reversed());
                break;
            case INSTALL_TIME_ASC:
                sortedList.sort(Comparator.comparingLong(AppInfo::getFirstInstallTime));
                break;
            case INSTALL_TIME_DESC:
                sortedList.sort(Comparator.comparingLong(AppInfo::getFirstInstallTime).reversed());
                break;
            case UPDATE_TIME_ASC:
                sortedList.sort(Comparator.comparingLong(AppInfo::getLastUpdateTime));
                break;
            case UPDATE_TIME_DESC:
                sortedList.sort(Comparator.comparingLong(AppInfo::getLastUpdateTime).reversed());
                break;
            case SIZE_ASC:
                sortedList.sort(Comparator.comparingLong(AppInfo::getAppSize));
                break;
            case SIZE_DESC:
                sortedList.sort(Comparator.comparingLong(AppInfo::getAppSize).reversed());
                break;
        }

        // 更新原列表
        appList.clear();
        appList.addAll(sortedList);

        // 如果是ArrayList，优化内存使用
        if (appList instanceof java.util.ArrayList) {
            ((java.util.ArrayList<?>) appList).trimToSize();
        }
    }
}