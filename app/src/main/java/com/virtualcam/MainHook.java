package com.virtualcam;

import android.content.Context;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import java.io.File;

public class MainHook implements IXposedHookLoadPackage {
    
    private static final String TAG = "VirtualCamera";
    private static final String VIDEO_PATH = "/sdcard/DCIM/Camera1/virtual.mp4";
    private VideoDecoder videoDecoder;
    private Context appContext;
    
    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        // Пропускаем системные приложения
        if (lpparam.packageName.equals("android") || 
            lpparam.packageName.startsWith("com.android")) {
            return;
        }
        
        XposedBridge.log(TAG + ": Loading into " + lpparam.packageName);
        
        // Получаем Context приложения
        hookApplicationContext(lpparam);
        
        // Хук старого Camera API
        hookOldCameraAPI(lpparam);
        
        // Хук Camera2 API
        hookCamera2API(lpparam);
    }
    
    private void hookApplicationContext(LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application",
                lpparam.classLoader,
                "attach",
                Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        appContext = (Context) param.args[0];
                        XposedBridge.log(TAG + ": Context obtained for " + lpparam.packageName);
                        showToast("Virtual Camera загружен");
                        
                        // Проверяем наличие видео
                        File videoFile = new File(VIDEO_PATH);
                        if (videoFile.exists()) {
                            XposedBridge.log(TAG + ": Video file found: " + VIDEO_PATH);
                            showToast("Видео найдено: virtual.mp4");
                        } else {
                            XposedBridge.log(TAG + ": Video file NOT found: " + VIDEO_PATH);
                            showToast("ОШИБКА: virtual.mp4 не найден!");
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Error hooking Application context: " + t.getMessage());
        }
    }
    
    private void showToast(final String message) {
        if (appContext == null) {
            XposedBridge.log(TAG + ": Cannot show toast - no context");
            return;
        }
        
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void hookOldCameraAPI(LoadPackageParam lpparam) {
        try {
            // Хук для открытия камеры
            XposedHelpers.findAndHookMethod(
                "android.hardware.Camera",
                lpparam.classLoader,
                "open",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log(TAG + ": Camera.open() called");
                        showToast("Камера открыта (старый API)");
                    }
                }
            );
            
            // Хук для получения параметров камеры
            XposedHelpers.findAndHookMethod(
                "android.hardware.Camera",
                lpparam.classLoader,
                "getParameters",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Camera.Parameters params = (Camera.Parameters) param.getResult();
                        if (params != null) {
                            Camera.Size previewSize = params.getPreviewSize();
                            XposedBridge.log(TAG + ": Preview size: " + previewSize.width + "x" + previewSize.height);
                            showToast("Разрешение: " + previewSize.width + "x" + previewSize.height);
                        }
                    }
                }
            );
            
            // Хук для callback данных предпросмотра
            XposedHelpers.findAndHookMethod(
                "android.hardware.Camera",
                lpparam.classLoader,
                "setPreviewCallback",
                Camera.PreviewCallback.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Camera.PreviewCallback originalCallback = 
                            (Camera.PreviewCallback) param.args[0];
                        
                        if (originalCallback == null) return;
                        
                        // Проверяем, отключен ли модуль
                        if (Utils.isModuleDisabled()) {
                            XposedBridge.log(TAG + ": Module disabled by user");
                            return;
                        }
                        
                        param.args[0] = new Camera.PreviewCallback() {
                            @Override
                            public void onPreviewFrame(byte[] data, Camera camera) {
                                try {
                                    // Получаем данные из виртуального видео
                                    byte[] virtualData = getVirtualFrameData(data.length);
                                    
                                    if (virtualData != null && virtualData.length == data.length) {
                                        XposedBridge.log(TAG + ": Replacing frame with virtual data");
                                        originalCallback.onPreviewFrame(virtualData, camera);
                                    } else {
                                        originalCallback.onPreviewFrame(data, camera);
                                    }
                                } catch (Exception e) {
                                    XposedBridge.log(TAG + ": Error in preview callback: " + e.getMessage());
                                    originalCallback.onPreviewFrame(data, camera);
                                }
                            }
                        };
                    }
                }
            );
            
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Error hooking old Camera API: " + t.getMessage());
        }
    }
    
    private void hookCamera2API(LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.hardware.camera2.CameraManager",
                lpparam.classLoader,
                "openCamera",
                String.class,
                "android.hardware.camera2.CameraDevice$StateCallback",
                android.os.Handler.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String cameraId = (String) param.args[0];
                        XposedBridge.log(TAG + ": Camera2 opening, ID=" + cameraId);
                        showToast("Camera2 открыта, ID=" + cameraId);
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Error hooking Camera2 API: " + t.getMessage());
        }
    }
    
    private byte[] getVirtualFrameData(int requiredLength) {
        try {
            if (videoDecoder == null) {
                File videoFile = new File(VIDEO_PATH);
                if (!videoFile.exists()) {
                    XposedBridge.log(TAG + ": Video file not found: " + VIDEO_PATH);
                    return null;
                }
                videoDecoder = new VideoDecoder(VIDEO_PATH);
            }
            
            return videoDecoder.getNextFrame(requiredLength);
            
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Error getting virtual frame: " + e.getMessage());
            return null;
        }
    }
}
