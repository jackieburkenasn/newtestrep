package com.virtualcam;

import android.hardware.Camera;
import android.os.Environment;
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
    
    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        // Пропускаем системные приложения
        if (lpparam.packageName.equals("android") || 
            lpparam.packageName.startsWith("com.android")) {
            return;
        }
        
        XposedBridge.log(TAG + ": Loading into " + lpparam.packageName);
        
        // Хук старого Camera API
        hookOldCameraAPI(lpparam);
        
        // Хук Camera2 API
        hookCamera2API(lpparam);
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
                        XposedBridge.log(TAG + ": Camera.open() called in " + lpparam.packageName);
                        
                        // Проверяем наличие видеофайла
                        File videoFile = new File(VIDEO_PATH);
                        if (videoFile.exists()) {
                            XposedBridge.log(TAG + ": Virtual video file found");
                        } else {
                            XposedBridge.log(TAG + ": Virtual video file NOT found at " + VIDEO_PATH);
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
                            return;
                        }
                        
                        param.args[0] = new Camera.PreviewCallback() {
                            @Override
                            public void onPreviewFrame(byte[] data, Camera camera) {
                                try {
                                    // Получаем данные из виртуального видео
                                    byte[] virtualData = getVirtualFrameData(data.length);
                                    
                                    if (virtualData != null && virtualData.length == data.length) {
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
            
            // Хук для обработки фото
            XposedHelpers.findAndHookMethod(
                "android.hardware.Camera",
                lpparam.classLoader,
                "takePicture",
                Camera.ShutterCallback.class,
                Camera.PictureCallback.class,
                Camera.PictureCallback.class,
                Camera.PictureCallback.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log(TAG + ": takePicture() called");
                        
                        if (Utils.isModuleDisabled()) return;
                        
                        // Замена callback для JPEG
                        Camera.PictureCallback originalJpegCallback = 
                            (Camera.PictureCallback) param.args[3];
                        
                        if (originalJpegCallback != null) {
                            param.args[3] = new Camera.PictureCallback() {
                                @Override
                                public void onPictureTaken(byte[] data, Camera camera) {
                                    byte[] virtualPhoto = Utils.getVirtualPhoto();
                                    if (virtualPhoto != null) {
                                        originalJpegCallback.onPictureTaken(virtualPhoto, camera);
                                    } else {
                                        originalJpegCallback.onPictureTaken(data, camera);
                                    }
                                }
                            };
                        }
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
                        XposedBridge.log(TAG + ": Camera2 opening, ID=" + cameraId + 
                                       " in " + lpparam.packageName);
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
