package com.garmin.android.apps.connectiq.sample.comm.utils;

import android.content.Intent;
import android.provider.MediaStore;

/**
 * Utility class for launching the device's camera application
 */
public class CameraLauncher {
    
    /**
     * Creates an intent to launch the device's default camera application
     * @return Intent configured to launch the camera app
     */
    public static Intent createCameraIntent() {
        return new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    }
} 