package com.treelang.mean.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdbHelper {

    public static final ExecutorService executorService = Executors.newSingleThreadExecutor();


    public interface AdbCommandListener {
        void onCommandOutput(String output); // 每行输出

        void onCommandComplete(int exitCode); // 命令完成

        void onCommandError(Exception e);   // 发生错误
    }


    // 通过Shell执行ADB（需要root权限，或者工作在无线调试模式下）
    public static void executeShellCommandAsync(String command, AdbCommandListener listener) {
        executorService.submit(() -> {
            Process process;
            OutputStream outputStream = null;
            try {
                process = Runtime.getRuntime().exec("sh"); // 或者 "su" 如果需要 root
                outputStream = process.getOutputStream();
                //写入指令
                outputStream.write((command + "\n").getBytes());
                outputStream.write("exit\n".getBytes());// 确保退出 shell
                outputStream.flush(); // 刷新缓冲区，确保命令被发送


                //读取错误输出
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), java.nio.charset.StandardCharsets.UTF_8));
                new Thread(() -> {
                    String errorLine;
                    try {
                        while ((errorLine = errorReader.readLine()) != null) {
                            if (listener != null) {
                                listener.onCommandOutput(errorLine); // 每行输出
                            }
                        }
                    } catch (IOException e) {
                        if (listener != null) {
                            listener.onCommandError(e);
                        }
                    }
                }).start();


                // 读取标准输出
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                new Thread(() -> {
                    String line;
                    try {
                        while ((line = reader.readLine()) != null) {
                            if (listener != null) {
                                listener.onCommandOutput(line); // 每行输出
                            }
                        }
                    } catch (IOException e) {
                        if (listener != null) {
                            listener.onCommandError(e);
                        }
                    }

                }).start();


                int exitCode = process.waitFor();

                if (listener != null) {
                    listener.onCommandComplete(exitCode); // 命令完成
                }

            } catch (Exception e) {
                if (listener != null) {
                    listener.onCommandError(e);   // 发生错误
                }
            } finally {
                // 关闭 OutputStream
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        if (listener != null) {
                            listener.onCommandError(e);
                        }
                    }
                }
            }
        });
    }

    // 卸载应用
    public static void uninstallApp(String packageName, AdbCommandListener listener) {
        //executeAdbCommandAsync("adb uninstall " + packageName, listener);  //普通卸载
        executeShellCommandAsync("pm uninstall " + packageName, listener); //通过shell的方式静默卸载
    }


    // 禁用应用
    public static void disableApp(String packageName, AdbCommandListener listener) {
        //executeAdbCommandAsync("adb shell pm disable-user " + packageName, listener);
        executeShellCommandAsync("pm disable-user " + packageName, listener); //通过shell的方式
    }

    // 启用应用
    public static void enableApp(String packageName, AdbCommandListener listener) {
        //executeAdbCommandAsync("adb shell pm enable " + packageName, listener);
        executeShellCommandAsync("pm enable " + packageName, listener);
    }


    // 提取应用安装包（APK）
    public static void extractApk(Context context, String sourceApkPath, Uri destUri, AdbCommandListener listener) {
        executorService.execute(() -> {
            try {
                File sourceFile = new File(sourceApkPath);
                try (InputStream in = new FileInputStream(sourceFile);
                     OutputStream out = context.getContentResolver().openOutputStream(destUri)) {

                    if (out == null) {
                        throw new IOException("Failed to open output stream for URI: " + destUri);
                    }

                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = in.read(buffer)) > 0) {
                        out.write(buffer, 0, length);
                    }
                    out.flush();
                }

                if (listener != null) {
                    new Handler(Looper.getMainLooper()).post(() -> listener.onCommandComplete(0));
                }
            } catch (Exception e) {
                Log.e("AdbHelper", "APK extraction failed", e);
                if (listener != null) {
                    new Handler(Looper.getMainLooper()).post(() -> listener.onCommandError(e));
                }
            }
        });
    }
}

