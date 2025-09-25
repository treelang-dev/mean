package com.treelang.mean.viewmodels;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.treelang.mean.data.AppInfo;
import com.treelang.mean.utils.AppUtils;

import java.util.ArrayList;
import java.util.List;

public class MainViewModel extends AndroidViewModel {

    //注意这里的列表
    private final MutableLiveData<List<AppInfo>> userApps = new MutableLiveData<>(); //
    private final MutableLiveData<List<AppInfo>> systemApps = new MutableLiveData<>();
    private final MutableLiveData<List<AppInfo>> privilegeApps = new MutableLiveData<>();
    private final MutableLiveData<List<AppInfo>> coreApps = new MutableLiveData<>();

    private final Context context;

    //当前显示的具体是哪个类型的数据
    private final MutableLiveData<List<AppInfo>> currentShowApps = new MutableLiveData<>();

    private boolean isLoading = false;

    // 添加进度更新相关的LiveData
    private final MutableLiveData<Integer> loadingProgress = new MutableLiveData<>(0);
    private final MutableLiveData<String> loadingStatus = new MutableLiveData<>("");

    private int selectedTabPosition = 0;
    private int pendingTabPosition = -1; // 存储加载过程中请求的Tab位置

    // Add a field to store the current sort position
    private Integer currentSortPosition = 0;

    public MainViewModel(@NonNull Application application) {
        super(application);
        context = application.getApplicationContext();
        loadApps();
    }

    // 检查是否正在加载数据
    public boolean isLoading() {
        return isLoading;
    }

    // 获取加载进度
    public LiveData<Integer> getLoadingProgress() {
        return loadingProgress;
    }

    // 加载应用数据(分类加载)
    public void loadApps() {
        isLoading = true;
        loadingProgress.postValue(0);
        loadingStatus.postValue("正在加载应用列表...");
        
        new Thread(() -> {
            try {
                // 只获取一次所有应用包信息
                PackageManager pm = context.getPackageManager();
                List<PackageInfo> allPackages = pm.getInstalledPackages(
                    PackageManager.GET_META_DATA | 
                    PackageManager.GET_PERMISSIONS | 
                    PackageManager.GET_SIGNING_CERTIFICATES);
                
                int totalApps = allPackages.size();
                loadingStatus.postValue("共发现 " + totalApps + " 个应用，正在处理...");
                
                // 分类应用
                List<AppInfo> userAppList;
                List<AppInfo> systemAppList;
                List<AppInfo> privilegeAppList;
                List<AppInfo> coreAppList;
                
                // 预计算应用列表大小以减少动态扩容
                int initialCapacity = Math.max(50, allPackages.size() / 4);
                userAppList = new ArrayList<>(initialCapacity);
                systemAppList = new ArrayList<>(initialCapacity);
                privilegeAppList = new ArrayList<>(initialCapacity);
                coreAppList = new ArrayList<>(initialCapacity);
                
                // 单次遍历所有包信息进行分类
                int processedCount = 0;
                for (PackageInfo packageInfo : allPackages) {
                    AppInfo appInfo = AppUtils.getAppInfoFromPackageInfo(pm, packageInfo);
                    if (appInfo == null) continue;
                    
                    // 更新进度
                    processedCount++;
                    if (processedCount % 10 == 0 || processedCount == totalApps) {
                        int progress = (int)(((float)processedCount / totalApps) * 100);
                        loadingProgress.postValue(progress);
                        loadingStatus.postValue("处理中: " + processedCount + "/" + totalApps);
                    }
                    
                    // 用户应用
                    // 简化用户应用判断：只要不是系统应用标志位的，就认为是用户应用
                    boolean isUserApp = (packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0;

                    if (isUserApp) {
                        userAppList.add(appInfo);
                    }
                    
                    // 各类应用可能会重叠，一个应用可能同时属于多个类别
                    if (appInfo.isSystemApp()) {
                        systemAppList.add(appInfo);
                    }
                    
                    if (appInfo.isPrivilegedApp()) {
                        privilegeAppList.add(appInfo);
                    }
                    
                    if (appInfo.isCoreApp()) {
                        coreAppList.add(appInfo);
                    }
                }
                
                loadingStatus.postValue("数据整理中...");
                
                // 更新LiveData
                userApps.postValue(userAppList);
                systemApps.postValue(systemAppList);
                privilegeApps.postValue(privilegeAppList);
                coreApps.postValue(coreAppList);
                
                // 检查是否有待处理的Tab位置
                if (pendingTabPosition != -1) {
                    // 使用待处理的Tab位置
                    selectedTabPosition = pendingTabPosition;
                    pendingTabPosition = -1;
                }
                
                // 根据当前选中的Tab位置更新显示列表
                List<AppInfo> appsToShow = switch (selectedTabPosition) {
                    case 0 -> userAppList;
                    case 1 -> systemAppList;
                    case 2 -> privilegeAppList;
                    case 3 -> coreAppList;
                    default -> new ArrayList<>();
                };
                
                currentShowApps.postValue(appsToShow);
                
                loadingProgress.postValue(100);
                loadingStatus.postValue("加载完成");
            } catch (Exception e) {
                Log.e("MainViewModel", "Error loading apps: " + e.getMessage());
                // 发送空列表避免UI等待
                loadingStatus.postValue("加载失败: " + e.getMessage());
                currentShowApps.postValue(new ArrayList<>());
            } finally {
                isLoading = false;
            }
        }).start();
    }

    //添加一个方法，更新currentShowApps的值,
    public void updateCurrentShowApps(int position) {
        // 如果正在加载，记录请求的位置，等加载完成后再处理
        if (isLoading) {
            pendingTabPosition = position;
            return;
        }
        
        selectedTabPosition = position;
        List<AppInfo> filteredApps = switch (position) {
            case 0 -> filterApps(userApps.getValue());
            case 1 -> filterApps(systemApps.getValue());
            case 2 -> filterApps(privilegeApps.getValue());
            case 3 -> filterApps(coreApps.getValue());
            default -> new ArrayList<>();
        };
        currentShowApps.setValue(filteredApps);
    }
    //获取当前的应用
    public LiveData<List<AppInfo>> getCurrentShowApps(){
        return currentShowApps;
    }

    private List<AppInfo> filterApps(List<AppInfo> apps) {
        return apps;
    }

    // 保存当前选中的标签页位置
    public void setSelectedTabPosition(int position) {
        this.selectedTabPosition = position;
    }

    // 获取当前选中的标签页位置
    public int getSelectedTabPosition() {
        return selectedTabPosition;
    }
    
    // 设置当前显示的应用列表
    public void setCurrentShowApps(List<AppInfo> apps) {
        currentShowApps.setValue(apps);
    }

    /**
     * Gets the current sort position
     * @return The current sort position, or null if not set
     */
    public Integer getCurrentSortPosition() {
        return currentSortPosition;
    }

    /**
     * Sets the current sort position
     * @param position The sort position to set
     */
    public void setCurrentSortPosition(int position) {
        this.currentSortPosition = position;
    }
}