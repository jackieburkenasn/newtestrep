package com.virtualcam;

import java.io.File;
import java.io.FileInputStream;

import de.robv.android.xposed.XposedBridge;

public class Utils {
    
    private static final String TAG = "VirtualCamera.Utils";
    private static final String CAMERA_DIR = "/sdcard/DCIM/Camera1/";
    private static final String DISABLE_FILE = CAMERA_DIR + "disable.jpg";
    private static final String PHOTO_FILE = CAMERA_DIR + "1000.bmp";
    
    public static boolean isModuleDisabled() {
        File disableFile = new File(DISABLE_FILE);
        return disableFile.exists();
    }
    
    public static byte[] getVirtualPhoto() {
        try {
            File photoFile = new File(PHOTO_FILE);
            if (!photoFile.exists()) {
                return null;
            }
            
            FileInputStream fis = new FileInputStream(photoFile);
            byte[] photoData = new byte[(int) photoFile.length()];
            fis.read(photoData);
            fis.close();
            
            XposedBridge.log(TAG + ": Virtual photo loaded, size: " + photoData.length);
            return photoData;
            
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Error loading virtual photo: " + e.getMessage());
            return null;
        }
    }
}
